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
 * Replaces the old polling-based UsageMonitorService. Android calls
 * onAccessibilityEvent() the instant the foreground window changes — this is
 * the event-driven "check only when the app opens" behavior instead of
 * checking on a timer. No battery cost while the user is just sitting on
 * their home screen or in an unmonitored app.
 *
 * Runs with system-managed lifecycle once enabled in
 * Settings > Accessibility > FocusGuard — no boot receiver or foreground
 * service needed to keep it alive.
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
        }
        if (!PermissionUtils.hasOverlayPermission(this)) {
            overlayManager.showTamperLock()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val newPackage = event.packageName?.toString() ?: return
        if (newPackage == packageName) return // ignore our own overlay/app windows
        if (newPackage == lastForegroundPackage) return // internal navigation, not a real switch

        val previousPackage = lastForegroundPackage
        lastForegroundPackage = newPackage

        serviceScope.launch {
            previousPackage?.let { handleAppExited(it) }
            handleAppEntered(newPackage)
        }
    }

    private suspend fun handleAppEntered(pkg: String) {
        val monitored = db.monitoredAppDao().getByPackage(pkg) ?: return
        if (!monitored.isEnabled) return

        // Safety: clean up any stale session from a crash/kill before starting fresh
        db.sessionStateDao().getActive(pkg)?.let { db.sessionStateDao().endSession(pkg) }
        sessionTimerJobs.remove(pkg)?.cancel()

        val today = todayDateString()
        ensureUsageRow(pkg, today)
        val usage = db.dailyUsageDao().get(pkg, today)
        val minutesLeft = monitored.dailyBudgetMinutes - (usage?.minutesUsedToday ?: 0)

        if (minutesLeft <= 0) {
            if (usage?.extendUsedToday == false) {
                overlayManager.showBlockScreenWithExtendOption(monitored)
            } else {
                overlayManager.showBlockScreen(monitored)
            }
        } else {
            // Every open shows the checklist first, then the time picker,
            // capped to whatever's left of today's budget. The picker's
            // choice comes back here via startSession().
            overlayManager.showChecklistThenPicker(monitored, minutesLeft) { pickedMinutes ->
                startSession(pkg, pickedMinutes)
            }
        }
    }

    private suspend fun handleAppExited(pkg: String) {
        overlayManager.hideAll()
        sessionTimerJobs.remove(pkg)?.cancel()

        val session = db.sessionStateDao().getActive(pkg) ?: return
        val today = todayDateString()
        ensureUsageRow(pkg, today)
        val elapsedMinutes = ((System.currentTimeMillis() - session.sessionStartTimeMillis) / 60000)
            .toInt()
            .coerceIn(0, session.sessionLengthMinutes) // never credit more than what was picked
        db.dailyUsageDao().addMinutes(pkg, today, elapsedMinutes)
        db.sessionStateDao().endSession(pkg)
    }

    /**
     * Called from the time-picker overlay's callback once the user picks a
     * duration. Starts the DB session record AND a coroutine countdown that
     * fires the block screen if the user is still in the app when time's up
     * (as opposed to leaving early, which handleAppExited() covers instead).
     */
    fun startSession(pkg: String, minutes: Int) {
        serviceScope.launch {
            db.sessionStateDao().upsert(
                SessionState(
                    packageName = pkg,
                    sessionStartTimeMillis = System.currentTimeMillis(),
                    sessionLengthMinutes = minutes,
                    isActive = true
                )
            )
            overlayManager.hideAll() // let the app become visible/usable

            val job = launch {
                delay(minutes * 60_000L)
                onSessionTimerExpired(pkg, minutes)
            }
            sessionTimerJobs[pkg] = job
        }
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
        db.dailyUsageDao().addMinutes(pkg, today, minutes) // full session was used
        db.sessionStateDao().endSession(pkg)
        sessionTimerJobs.remove(pkg)

        val monitored = db.monitoredAppDao().getByPackage(pkg) ?: return
        overlayManager.showBlockScreen(monitored) // this open is done; re-opening re-checks budget
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
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
