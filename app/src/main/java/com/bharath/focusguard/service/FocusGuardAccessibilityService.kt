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
 * FocusGuard Core Engine:
 * - Unlocks the app for the full duration chosen (e.g. 5 min); does NOT re-prompt on exit/re-enter during the window.
 * - Broadcasts exact close clock time message (e.g. "Instagram will close at 10:25 PM").
 * - Immediately kicks the user out (GLOBAL_ACTION_HOME) when daily budget is exhausted or session expires.
 */
class FocusGuardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var db: AppDatabase
    private lateinit var overlayManager: OverlayManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastForegroundPackage: String? = null
    private val sessionTimerJobs = mutableMapOf<String, Job>() // one countdown job per package

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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val newPackage = event.packageName?.toString() ?: return

        // 1. Ignore transient system overlays (keyboards, notifications, system dialogs, our own app)
        if (isIgnoredTransientPackage(newPackage)) return

        // 2. Ignore internal navigation within the same app
        if (newPackage == lastForegroundPackage) return

        val previousPackage = lastForegroundPackage
        lastForegroundPackage = newPackage

        serviceScope.launch {
            previousPackage?.let { handleAppExited(it) }
            handleAppEntered(newPackage)
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
        if (pkg.contains("permissioncontroller") || pkg == "com.google.android.gms") {
            return true
        }
        return false
    }

    private suspend fun handleAppEntered(pkg: String) {
        val monitored = db.monitoredAppDao().getByPackage(pkg) ?: return
        if (!monitored.isEnabled) return

        val now = System.currentTimeMillis()
        val activeSession = db.sessionStateDao().getActive(pkg)

        // Session Persistence: If the user chose e.g. 5 min, the app remains UNLOCKED for the full 5 min!
        // Returning to the app before the time ends DOES NOT open the popup.
        if (activeSession != null && now < activeSession.sessionExpiresAtMillis) {
            overlayManager.hideAll()
            val remainingMillis = activeSession.sessionExpiresAtMillis - now
            ensureTimerActive(pkg, remainingMillis)
            
            // Remind user when it will close
            val closeTimeStr = DateFormat.getTimeFormat(applicationContext).format(Date(activeSession.sessionExpiresAtMillis))
            showToast("${monitored.displayName} unlocked until $closeTimeStr")
            return
        }

        // Clean up expired session if one existed
        if (activeSession != null) {
            db.sessionStateDao().endSession(pkg)
            sessionTimerJobs.remove(pkg)?.cancel()
        }

        val today = todayDateString()
        ensureUsageRow(pkg, today)
        val usage = db.dailyUsageDao().get(pkg, today)
        val minutesLeft = monitored.dailyBudgetMinutes - (usage?.minutesUsedToday ?: 0)

        if (minutesLeft <= 0) {
            // HARD BLOCK: Daily budget exhausted!
            val canExtend = (usage?.extendUsedToday == false)
            if (!canExtend) {
                // Immediately kick to Home so the user cannot use the app!
                performGlobalAction(GLOBAL_ACTION_HOME)
                showToast("${monitored.displayName} is locked for today! Daily limit reached.")
            }
            overlayManager.showHardBlockScreen(monitored, canExtend = canExtend)
        } else {
            // Normal gated entry: show checklist, then duration picker capped to remaining budget
            overlayManager.showChecklistThenPicker(monitored, minutesLeft) { pickedMinutes ->
                startSession(monitored, pickedMinutes)
            }
        }
    }

    private suspend fun handleAppExited(pkg: String) {
        overlayManager.hideAll()
        // Note: We do NOT cancel the active session here!
        // The user's chosen session window remains valid until sessionExpiresAtMillis.
    }

    /**
     * Starts an intentional session:
     * - Immediately allocates the chosen minutes to today's usage.
     * - Sets sessionExpiresAtMillis so the app remains unlocked until that exact time without re-prompting.
     * - Displays the exact closing clock time (e.g. "Instagram will close at 10:25 PM").
     * - Starts a countdown timer that kicks the user out when time expires.
     */
    fun startSession(monitored: MonitoredApp, minutes: Int) {
        val pkg = monitored.packageName
        serviceScope.launch {
            val now = System.currentTimeMillis()
            val expiresAt = now + (minutes * 60_000L)

            // Deduct the chosen minutes from today's budget immediately
            val today = todayDateString()
            ensureUsageRow(pkg, today)
            db.dailyUsageDao().addMinutes(pkg, today, minutes)

            // Persist the session window
            db.sessionStateDao().upsert(
                SessionState(
                    packageName = pkg,
                    sessionStartTimeMillis = now,
                    sessionLengthMinutes = minutes,
                    sessionExpiresAtMillis = expiresAt,
                    lastResumedAtMillis = now,
                    isActive = true
                )
            )

            // Remove any overlay
            overlayManager.hideAll()

            // Broadcast the exact closing clock time (not the duration)
            val timeFormat = DateFormat.getTimeFormat(applicationContext)
            val closeTimeStr = timeFormat.format(Date(expiresAt))
            showToast("${monitored.displayName} will close at $closeTimeStr")

            // Run the timer countdown
            ensureTimerActive(pkg, minutes * 60_000L)
        }
    }

    private fun ensureTimerActive(pkg: String, remainingMillis: Long) {
        sessionTimerJobs.remove(pkg)?.cancel()
        val job = serviceScope.launch {
            delay(remainingMillis.coerceAtLeast(0L))
            onSessionTimerExpired(pkg)
        }
        sessionTimerJobs[pkg] = job
    }

    private fun grantEmergencyExtend(monitored: MonitoredApp) {
        serviceScope.launch {
            val pkg = monitored.packageName
            val today = todayDateString()
            ensureUsageRow(pkg, today)
            db.dailyUsageDao().markExtendUsed(pkg, today)
            startSession(monitored, 5)
        }
    }

    private suspend fun onSessionTimerExpired(pkg: String) {
        db.sessionStateDao().endSession(pkg)
        sessionTimerJobs.remove(pkg)

        val monitored = db.monitoredAppDao().getByPackage(pkg) ?: return
        val today = todayDateString()
        val usage = db.dailyUsageDao().get(pkg, today)
        val minutesLeft = monitored.dailyBudgetMinutes - (usage?.minutesUsedToday ?: 0)

        val isCurrentlyInForeground = (lastForegroundPackage == pkg)

        if (minutesLeft <= 0) {
            // Daily limit reached: Hard Block!
            val canExtend = (usage?.extendUsedToday == false)
            if (isCurrentlyInForeground) {
                // Immediately kick to Home!
                performGlobalAction(GLOBAL_ACTION_HOME)
                showToast("${monitored.displayName} time is up! Daily limit reached.")
            }
            overlayManager.showHardBlockScreen(monitored, canExtend = canExtend)
        } else {
            // Session is finished, but budget remains
            if (isCurrentlyInForeground) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                showToast("${monitored.displayName} session finished! $minutesLeft min left today.")
            }
            overlayManager.showSessionFinishedScreen(monitored, minutesLeft) {
                // User chose "Start Another Session"
                serviceScope.launch { handleAppEntered(pkg) }
            }
        }
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun ensureUsageRow(pkg: String, today: String) {
        if (db.dailyUsageDao().get(pkg, today) == null) {
            db.dailyUsageDao().upsert(DailyUsage(packageName = pkg, date = today))
        }
    }

    private fun todayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    override fun onInterrupt() {
        sessionTimerJobs.values.forEach { it.cancel() }
        sessionTimerJobs.clear()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
