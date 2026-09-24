package com.bharath.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.bharath.focusguard.data.local.AppDatabase
import com.bharath.focusguard.data.local.entities.DailyUsage
import com.bharath.focusguard.data.local.entities.SessionState
import com.bharath.focusguard.util.PermissionUtils
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Event-driven engine using AccessibilityService.
 * Listens for foreground window changes with system/IME filtering,
 * persistent session expiry validation, and robust hard blocking.
 */
class FocusGuardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var db: AppDatabase
    private lateinit var overlayManager: OverlayManager

    private var lastForegroundPackage: String? = null
    private val sessionTimerJobs = mutableMapOf<String, Job>() // one countdown job per package

    override fun onServiceConnected() {
        super.onServiceConnected()
        db = AppDatabase.getInstance(applicationContext)
        overlayManager = OverlayManager(applicationContext).also { manager ->
            manager.onEmergencyExtend = { app -> grantEmergencyExtend(app.packageName) }
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
        // Soft keyboards / IMEs
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

        // Session Persistence: if the user already has an active session that has not expired,
        // let them continue without interruption (e.g. brief notification check or keyboard).
        if (activeSession != null && now < activeSession.sessionExpiresAtMillis) {
            overlayManager.hideAll()
            db.sessionStateDao().updateLastResumed(pkg, now)
            ensureTimerActive(pkg, activeSession.sessionExpiresAtMillis - now, activeSession.sessionLengthMinutes)
            return
        }

        // Clean up any expired or stale session
        if (activeSession != null) {
            db.sessionStateDao().endSession(pkg)
            sessionTimerJobs.remove(pkg)?.cancel()
        }

        val today = todayDateString()
        ensureUsageRow(pkg, today)
        val usage = db.dailyUsageDao().get(pkg, today)
        val minutesLeft = monitored.dailyBudgetMinutes - (usage?.minutesUsedToday ?: 0)

        if (minutesLeft <= 0) {
            // Hard blocked: daily budget is completely exhausted
            val canExtend = (usage?.extendUsedToday == false)
            overlayManager.showHardBlockScreen(monitored, canExtend = canExtend)
        } else {
            // Normal gated entry: show intentional pause checklist, then time picker
            overlayManager.showChecklistThenPicker(monitored, minutesLeft) { pickedMinutes ->
                startSession(pkg, pickedMinutes)
            }
        }
    }

    private suspend fun handleAppExited(pkg: String) {
        overlayManager.hideAll()

        val session = db.sessionStateDao().getActive(pkg) ?: return
        val now = System.currentTimeMillis()

        // Calculate actual seconds spent in this session
        val elapsedSeconds = ((now - session.sessionStartTimeMillis) / 1000).toInt()
            .coerceIn(0, session.sessionLengthMinutes * 60)

        val today = todayDateString()
        ensureUsageRow(pkg, today)
        db.dailyUsageDao().addSeconds(pkg, today, elapsedSeconds)
        db.sessionStateDao().endSession(pkg)
        sessionTimerJobs.remove(pkg)?.cancel()
    }

    /**
     * Starts an active session with an exact expiry timestamp and a countdown timer.
     */
    fun startSession(pkg: String, minutes: Int) {
        serviceScope.launch {
            val now = System.currentTimeMillis()
            val expiresAt = now + (minutes * 60_000L)

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
            overlayManager.hideAll()

            ensureTimerActive(pkg, minutes * 60_000L, minutes)
        }
    }

    private fun ensureTimerActive(pkg: String, remainingMillis: Long, totalMinutes: Int) {
        sessionTimerJobs.remove(pkg)?.cancel()
        val job = serviceScope.launch {
            delay(remainingMillis.coerceAtLeast(0L))
            onSessionTimerExpired(pkg, totalMinutes)
        }
        sessionTimerJobs[pkg] = job
    }

    private fun grantEmergencyExtend(pkg: String) {
        serviceScope.launch {
            val today = todayDateString()
            ensureUsageRow(pkg, today)
            db.dailyUsageDao().markExtendUsed(pkg, today)
            startSession(pkg, 5)
        }
    }

    private suspend fun onSessionTimerExpired(pkg: String, minutes: Int) {
        val today = todayDateString()
        ensureUsageRow(pkg, today)
        db.dailyUsageDao().addMinutes(pkg, today, minutes)
        db.sessionStateDao().endSession(pkg)
        sessionTimerJobs.remove(pkg)

        val monitored = db.monitoredAppDao().getByPackage(pkg) ?: return
        val usage = db.dailyUsageDao().get(pkg, today)
        val minutesLeft = monitored.dailyBudgetMinutes - (usage?.minutesUsedToday ?: 0)

        if (minutesLeft <= 0) {
            // Daily limit is reached: show Hard Block
            val canExtend = (usage?.extendUsedToday == false)
            overlayManager.showHardBlockScreen(monitored, canExtend = canExtend)
        } else {
            // Session is finished, but daily budget still remains: offer a break or next session
            overlayManager.showSessionFinishedScreen(monitored, minutesLeft) {
                // User opted to start another intentional session
                serviceScope.launch { handleAppEntered(pkg) }
            }
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
