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
 * Full-screen App Blocker Overlay Architecture:
 *  - When user leaves a monitored app -> overlay is IMMEDIATELY removed so keyboards & other apps work 100% free.
 *  - When user opens a monitored app whose daily limit is completed -> full-screen opaque overlay covers the ENTIRE app.
 *  - When user's chosen session timer expires -> overlay immediately covers the ENTIRE app with "Return to Home".
 *  - The user has ONLY the option to return home; no app access is allowed while blocked.
 */
class FocusGuardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var db: AppDatabase
    private lateinit var overlayManager: OverlayManager
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Tracks which package is currently in the foreground. */
    private var currentForegroundPkg: String? = null

    /** In-memory session timers per package. */
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

        // Ignore transient system packages (keyboards, system status bar, notifications)
        if (isIgnoredTransientPackage(newPkg)) return

        currentForegroundPkg = newPkg

        serviceScope.launch {
            val monitored = db.monitoredAppDao().getByPackage(newPkg)
            if (monitored == null || !monitored.isEnabled) {
                // User is NOT in a monitored app (Home, Chrome, WhatsApp, Settings, etc.)
                // IMMEDIATELY remove any overlay so the keyboard and all apps are completely unblocked!
                overlayManager.hideAll()
                return@launch
            }

            // User IS in a monitored app!
            handleMonitoredAppEntered(monitored)
        }
    }

    private fun isIgnoredTransientPackage(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg == "com.android.systemui" || pkg == "android") return true
        // Keyboards / IMEs
        if (pkg.contains("inputmethod") || pkg.contains("keyboard") ||
            pkg.contains("honeyboard") || pkg.contains("swiftkey") || pkg.contains("gboard")) {
            return true
        }
        // System permission & credential sheets
        if (pkg.contains("permissioncontroller") || pkg == "com.google.android.gms") return true
        return false
    }

    // ─────────────────────────────────────────────────────────
    // Core: handleMonitoredAppEntered
    // ─────────────────────────────────────────────────────────

    private suspend fun handleMonitoredAppEntered(monitored: MonitoredApp) {
        val pkg   = monitored.packageName
        val now   = System.currentTimeMillis()
        val today = todayDateString()
        ensureUsageRow(pkg, today)

        // ── 1. Check if there's an ACTIVE, unexpired session window ──
        val session = db.sessionStateDao().getActive(pkg)
        if (session != null && now < session.sessionExpiresAtMillis) {
            // App is unlocked for this session window! Hide overlay and let user use it.
            overlayManager.hideAll()
            val remaining = session.sessionExpiresAtMillis - now
            armTimer(pkg, remaining)
            val closeStr = fmtTime(session.sessionExpiresAtMillis)
            showToast("${monitored.displayName} unlocked until $closeStr")
            return
        }

        // ── 2. No valid active session. Clean up stale session row if any ──
        if (session != null) {
            db.sessionStateDao().endSession(pkg)
        }
        sessionTimerJobs.remove(pkg)?.cancel()

        // ── 3. Check daily budget from DB ──
        val usage       = db.dailyUsageDao().get(pkg, today)
        val used        = usage?.minutesUsedToday ?: 0
        val minutesLeft = monitored.dailyBudgetMinutes - used

        if (minutesLeft <= 0) {
            // ── HARD BLOCK: Daily limit exhausted! ──
            // Blocker overlay covers the entire app and gives only the option to return home.
            val canExtend = (usage?.extendUsedToday == false)
            overlayManager.showHardBlockScreen(monitored, canExtend = canExtend)
        } else {
            // ── GATE: Budget remains. Show checklist → duration picker ──
            overlayManager.showChecklistThenPicker(monitored, minutesLeft) { pickedMinutes ->
                startSession(monitored, pickedMinutes)
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Session Management
    // ─────────────────────────────────────────────────────────

    /**
     * Starts an intentional session:
     * - Immediately allocates the chosen minutes to today's DB usage (upfront deduction).
     * - Persists sessionExpiresAtMillis so app remains unlocked until that exact time.
     * - Hides the overlay and starts countdown timer.
     */
    fun startSession(monitored: MonitoredApp, minutes: Int) {
        val pkg = monitored.packageName
        serviceScope.launch {
            val now       = System.currentTimeMillis()
            val expiresAt = now + (minutes * 60_000L)
            val today     = todayDateString()

            // Deduct chosen minutes from today's budget immediately
            ensureUsageRow(pkg, today)
            db.dailyUsageDao().addMinutes(pkg, today, minutes)

            // Persist session window
            db.sessionStateDao().upsert(
                SessionState(
                    packageName            = pkg,
                    sessionStartTimeMillis = now,
                    sessionLengthMinutes   = minutes,
                    sessionExpiresAtMillis = expiresAt,
                    lastResumedAtMillis    = now,
                    isActive               = true
                )
            )

            // Remove overlay so user can use the app
            overlayManager.hideAll()

            // Display exact closing time
            val closeStr = fmtTime(expiresAt)
            showToast("${monitored.displayName} will close at $closeStr")

            // Arm in-memory countdown
            armTimer(pkg, minutes * 60_000L)
        }
    }

    private fun armTimer(pkg: String, remainingMillis: Long) {
        sessionTimerJobs.remove(pkg)?.cancel()
        val job = serviceScope.launch {
            delay(remainingMillis.coerceAtLeast(0L))
            onTimerExpired(pkg)
        }
        sessionTimerJobs[pkg] = job
    }

    /**
     * Called the instant the session countdown expires.
     * If user is currently looking at the app, the blocker overlay IMMEDIATELY
     * covers the ENTIRE app, giving only the option to return home!
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
        if (!inForeground) return

        // Time is up while user is in the app:
        // Cover the ENTIRE app with the blocker overlay!
        if (minutesLeft <= 0) {
            val canExtend = (usage?.extendUsedToday == false)
            overlayManager.showHardBlockScreen(monitored, canExtend = canExtend)
        } else {
            overlayManager.showSessionFinishedScreen(monitored, minutesLeft) {
                serviceScope.launch { handleMonitoredAppEntered(monitored) }
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
