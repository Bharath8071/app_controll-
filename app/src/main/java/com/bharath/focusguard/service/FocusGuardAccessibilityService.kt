package com.bharath.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.bharath.focusguard.data.local.AppDatabase
import com.bharath.focusguard.data.local.entities.DailyUsage
import com.bharath.focusguard.data.local.entities.MonitoredApp
import com.bharath.focusguard.data.local.entities.SessionState
import com.bharath.focusguard.util.PermissionUtils
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * FocusGuard Core Engine.
 *
 * Blocking logic (simple and reliable):
 *
 *  1. User opens Instagram → check today's minutesUsedToday in DB vs dailyBudgetMinutes.
 *  2. If budget exhausted → show HARD BLOCK popup immediately. No usage allowed.
 *  3. If budget remains and NO active session → show checklist + time-picker.
 *  4. If budget remains and an active unexpired session EXISTS → let the user in silently.
 *  5. When the session countdown timer fires → mark session ended, show block or "session done".
 *  6. If the user re-opens the app after the timer has already fired (i.e. session expired in DB)
 *     → the "minutesLeft <= 0" check at step 1/2 blocks them because budget was deducted upfront.
 *
 * This means the block works even if the Accessibility Service was restarted by Android
 * (killed + restarted) because the budget check is ALWAYS done fresh from the DB on every open.
 */
class FocusGuardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var db: AppDatabase
    private lateinit var overlayManager: OverlayManager
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Tracks which package is in the foreground right now. */
    private var currentForegroundPkg: String? = null

    /** In-memory session timers. These are re-armed on re-enter if session is still valid in DB. */
    private val sessionTimerJobs = mutableMapOf<String, Job>()

    // ─────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        db = AppDatabase.getInstance(applicationContext)
        overlayManager = OverlayManager(applicationContext).also { manager ->
            manager.onEmergencyExtend = { app -> grantEmergencyExtend(app) }
            manager.onGoHomeAction = { performGlobalAction(GLOBAL_ACTION_HOME) }
        }
        if (!PermissionUtils.hasOverlayPermission(this)) {
            overlayManager.showTamperLock()
        }
    }

    // ─────────────────────────────────────────────────────────
    // Event Processing
    // ─────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val newPkg = event.packageName?.toString() ?: return

        if (isIgnoredTransientPackage(newPkg)) return

        // IMPORTANT: same-package re-entry must still be revalidated.
        // If the user leaves the app, returns to the same package later, or a
        // session expires while the app is already in foreground, we still need
        // to run the budget/session checks again.
        currentForegroundPkg = newPkg
        serviceScope.launch { handleAppEntered(newPkg) }
    }

    private fun isIgnoredTransientPackage(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg == "com.android.systemui" || pkg == "android") return true
        if (pkg.contains("inputmethod") || pkg.contains("keyboard") ||
            pkg.contains("honeyboard") || pkg.contains("swiftkey") || pkg.contains("gboard")) {
            return true
        }
        if (pkg.contains("permissioncontroller") || pkg == "com.google.android.gms") return true
        return false
    }

    // ─────────────────────────────────────────────────────────
    // Core: handleAppEntered
    // ─────────────────────────────────────────────────────────

    private suspend fun handleAppEntered(pkg: String) {
        val monitored = db.monitoredAppDao().getByPackage(pkg) ?: return
        if (!monitored.isEnabled) return

        val now    = System.currentTimeMillis()
        val today  = todayDateString()
        ensureUsageRow(pkg, today)

        // ── STEP 1: Check if there's an ACTIVE (non-expired) session window. ──
        // If yes → user is in their allowed window. Let them in silently.
        val session = db.sessionStateDao().getActive(pkg)
        if (session != null && now < session.sessionExpiresAtMillis) {
            overlayManager.hideAll()
            // Re-arm the in-memory timer (handles service restart scenario)
            val remaining = session.sessionExpiresAtMillis - now
            armTimer(pkg, remaining)
            val closeStr = fmtTime(session.sessionExpiresAtMillis)
            showToast("${monitored.displayName} unlocked until $closeStr")
            return
        }

        // ── STEP 2: No valid active session. Clean up any stale DB session row. ──
        if (session != null) {
            db.sessionStateDao().endSession(pkg)
        }
        sessionTimerJobs.remove(pkg)?.cancel()

        // ── STEP 3: Check the daily budget STRICTLY from the DB. ──
        val usage      = db.dailyUsageDao().get(pkg, today)
        val used       = usage?.minutesUsedToday ?: 0
        val minutesLeft = monitored.dailyBudgetMinutes - used

        if (minutesLeft <= 0) {
            // ── BLOCKED: Daily limit reached. Show the hard-block screen. ──
            val canExtend = (usage?.extendUsedToday == false)
            launchBlockedActivity(monitored, canExtend)
            overlayManager.showHardBlockScreen(monitored, canExtend = canExtend)
        } else {
            // ── GATE: Budget remaining. Show checklist → time picker. ──
            overlayManager.showChecklistThenPicker(monitored, minutesLeft) { pickedMinutes ->
                startSession(monitored, pickedMinutes)
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Session Start
    // ─────────────────────────────────────────────────────────

    /**
     * Called when the user picks a session duration.
     *
     * • Deducts the full chosen duration from today's budget IMMEDIATELY (upfront deduction).
     *   This means even if the service is restarted, the budget is already written to the DB
     *   and the block will fire correctly on the next app open.
     *
     * • Stores sessionExpiresAtMillis in the DB so the "active session" check is also
     *   persisted across service restarts.
     *
     * • Arms an in-memory countdown that calls onTimerExpired when the window closes.
     */
    fun startSession(monitored: MonitoredApp, minutes: Int) {
        val pkg = monitored.packageName
        serviceScope.launch {
            val now       = System.currentTimeMillis()
            val expiresAt = now + (minutes * 60_000L)
            val today     = todayDateString()

            // Upfront deduction
            ensureUsageRow(pkg, today)
            db.dailyUsageDao().addMinutes(pkg, today, minutes)

            // Persist the session window
            db.sessionStateDao().upsert(
                SessionState(
                    packageName          = pkg,
                    sessionStartTimeMillis = now,
                    sessionLengthMinutes = minutes,
                    sessionExpiresAtMillis = expiresAt,
                    lastResumedAtMillis  = now,
                    isActive             = true
                )
            )

            overlayManager.hideAll()

            // Tell user the exact clock time it will lock
            val closeStr = fmtTime(expiresAt)
            showToast("${monitored.displayName} will close at $closeStr")

            // Arm countdown
            armTimer(pkg, minutes * 60_000L)
        }
    }

    // ─────────────────────────────────────────────────────────
    // Timer
    // ─────────────────────────────────────────────────────────

    private fun armTimer(pkg: String, remainingMillis: Long) {
        sessionTimerJobs.remove(pkg)?.cancel()
        val job = serviceScope.launch {
            delay(remainingMillis.coerceAtLeast(0L))
            onTimerExpired(pkg)
        }
        sessionTimerJobs[pkg] = job
    }

    /**
     * Called when the session countdown window has elapsed.
     * The budget is already deducted (upfront), so we just:
     *  - End the session row in DB.
     *  - Kick the user to Home if they're currently in the app.
     *  - Show the appropriate popup (Hard Block or "Session Done").
     */
    private suspend fun onTimerExpired(pkg: String) {
        db.sessionStateDao().endSession(pkg)
        sessionTimerJobs.remove(pkg)

        val monitored   = db.monitoredAppDao().getByPackage(pkg) ?: return
        val today       = todayDateString()
        val usage       = db.dailyUsageDao().get(pkg, today)
        val used        = usage?.minutesUsedToday ?: 0
        val minutesLeft = monitored.dailyBudgetMinutes - used

        val inForeground = (currentForegroundPkg == pkg)

        if (minutesLeft <= 0) {
            // Daily budget fully used — hard block!
            val canExtend = (usage?.extendUsedToday == false)
            launchBlockedActivity(monitored, canExtend)
            overlayManager.showHardBlockScreen(monitored, canExtend = canExtend)
        } else {
            // This session finished but budget remains — "Session Done" screen
            if (inForeground) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                showToast("${monitored.displayName} session over. $minutesLeft min left today.")
            }
            overlayManager.showSessionFinishedScreen(monitored, minutesLeft) {
                serviceScope.launch { handleAppEntered(pkg) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Emergency Extension
    // ─────────────────────────────────────────────────────────

    private fun grantEmergencyExtend(monitored: MonitoredApp) {
        serviceScope.launch {
            val pkg   = monitored.packageName
            val today = todayDateString()
            ensureUsageRow(pkg, today)
            db.dailyUsageDao().markExtendUsed(pkg, today)
            startSession(monitored, 5)
        }
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private suspend fun ensureUsageRow(pkg: String, today: String) {
        if (db.dailyUsageDao().get(pkg, today) == null) {
            db.dailyUsageDao().upsert(DailyUsage(packageName = pkg, date = today))
        }
    }

    private fun todayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun fmtTime(millis: Long): String =
        DateFormat.getTimeFormat(applicationContext).format(Date(millis))

    private fun launchBlockedActivity(monitored: MonitoredApp, canExtend: Boolean) {
        try {
            val intent = android.content.Intent(applicationContext, com.bharath.focusguard.ui.overlay.BlockedActivity::class.java).apply {
                putExtra(com.bharath.focusguard.ui.overlay.BlockedActivity.EXTRA_PACKAGE_NAME, monitored.packageName)
                putExtra(com.bharath.focusguard.ui.overlay.BlockedActivity.EXTRA_DISPLAY_NAME, monitored.displayName)
                putExtra(com.bharath.focusguard.ui.overlay.BlockedActivity.EXTRA_DAILY_BUDGET, monitored.dailyBudgetMinutes)
                putExtra(com.bharath.focusguard.ui.overlay.BlockedActivity.EXTRA_CAN_EXTEND, canExtend)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("FocusGuard", "Failed to launch BlockedActivity", e)
        }
    }

    private fun showToast(message: String) {
        mainHandler.post { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
    }

    override fun onInterrupt() {
        sessionTimerJobs.values.forEach { it.cancel() }
        sessionTimerJobs.clear()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
