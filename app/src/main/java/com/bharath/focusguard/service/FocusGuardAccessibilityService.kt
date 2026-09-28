package com.bharath.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import java.util.concurrent.ConcurrentHashMap

/**
 * FocusGuard Core Engine.
 *
 * Full-screen App Blocker & Session Enforcer:
 *  - When user leaves a monitored app -> overlay is IMMEDIATELY removed so keyboards & other apps work 100% free.
 *  - When session timer expires -> FORCIBLY EXITS the monitored app to Home and notifies user.
 *  - When daily budget is exhausted -> FORCIBLY EXITS the app to Home, and on re-opening shows full-screen Hard Block overlay with only option to return Home.
 *  - Un-killable 1-second ticker on Main Looper ensures timers fire with second-level precision, immune to system interruptions.
 */
class FocusGuardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var db: AppDatabase
    private lateinit var overlayManager: OverlayManager
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Tracks which package is currently in the foreground. */
    private var currentForegroundPkg: String? = null

    /** Tracks the last monitored app package that was opened. */
    private var lastMonitoredPkg: String? = null

    /** In-memory map of active session package -> expiry time in millis. */
    private val activeSessionMap = ConcurrentHashMap<String, Long>()

    /** Timestamp when exit to home was triggered, used to suppress re-entrant events from the dying monitored app. */
    @Volatile
    private var lastExitToHomeTimeMillis = 0L

    private fun markExitingToHome() {
        lastExitToHomeTimeMillis = System.currentTimeMillis()
    }

    private fun isTransitioningToHome(): Boolean {
        val elapsed = System.currentTimeMillis() - lastExitToHomeTimeMillis
        return elapsed < 1500L
    }

    /** 1-second ticker running on the Main Looper */
    private val timerRunnable = object : Runnable {
        override fun run() {
            checkActiveSessions()
            mainHandler.postDelayed(this, 1000L)
        }
    }

    /** BroadcastReceiver to dismiss any active overlay immediately when screen turns off. */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                overlayManager.hideAll()
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        db = AppDatabase.getInstance(applicationContext)
        overlayManager = OverlayManager(this).also { manager ->
            manager.onEmergencyExtend = { app -> grantEmergencyExtend(app) }
            manager.onGoHomeAction = { exitToHome() }
        }
        if (!PermissionUtils.hasOverlayPermission(this)) {
            overlayManager.showTamperLock()
        }

        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            registerReceiver(screenOffReceiver, filter)
        } catch (e: Exception) {
            android.util.Log.e("FocusGuard", "Failed to register screenOffReceiver", e)
        }

        // Restore any active sessions from Room DB
        serviceScope.launch {
            try {
                val now = System.currentTimeMillis()
                val activeList = db.sessionStateDao().getAllActive()
                for (s in activeList) {
                    if (now < s.sessionExpiresAtMillis) {
                        activeSessionMap[s.packageName] = s.sessionExpiresAtMillis
                    } else {
                        db.sessionStateDao().endSession(s.packageName)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FocusGuard", "Failed to restore active sessions", e)
            }
        }

        // Start 1-second ticker
        mainHandler.removeCallbacks(timerRunnable)
        mainHandler.post(timerRunnable)
    }

    // ─────────────────────────────────────────────────────────
    // Event Processing
    // ─────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val newPkg = event.packageName?.toString() ?: return

        // If screen is locked or keyguard active, dismiss overlay immediately so user is never trapped on lockscreen
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true) {
            currentForegroundPkg = newPkg
            lastMonitoredPkg = null
            overlayManager.hideAll()
            return
        }

        // Ignore transient system packages (keyboards, system status bar, notifications, our own overlay)
        if (isIgnoredTransientPackage(newPkg)) return

        currentForegroundPkg = newPkg

        serviceScope.launch {
            val monitored = db.monitoredAppDao().getByPackage(newPkg)
            if (monitored == null || !monitored.isEnabled) {
                // User is NOT in a monitored app (Home, Chrome, WhatsApp, Settings, etc.)
                lastMonitoredPkg = null
                withContext(Dispatchers.Main) {
                    overlayManager.hideAll()
                }
                return@launch
            }

            // If user just tapped Exit/Return to Home, suppress transient window events from the backgrounding app!
            if (isTransitioningToHome()) {
                return@launch
            }

            // User IS in a monitored app!
            lastMonitoredPkg = newPkg
            handleMonitoredAppEntered(monitored)
        }
    }

    private fun isIgnoredTransientPackage(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg == "com.android.systemui" || pkg == "android") return true
        // Keyboards / IMEs
        if (pkg.contains("inputmethod") || pkg.contains("keyboard") ||
            pkg.contains("honeyboard") || pkg.contains("swiftkey") || pkg.contains("gboard") ||
            pkg.contains("ime") || pkg.contains("sogou") || pkg.contains("baidu")) {
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
            activeSessionMap[pkg] = session.sessionExpiresAtMillis
            withContext(Dispatchers.Main) {
                overlayManager.hideAll()
            }
            return
        }

        // ── 2. No valid active session. Clean up stale session row if any ──
        if (session != null) {
            db.sessionStateDao().endSession(pkg)
        }
        activeSessionMap.remove(pkg)

        // ── 3. Prevent re-entrant window events from resetting Screen 2 back to Screen 1 ──
        if (overlayManager.isGating(pkg)) {
            // User is already interacting with Screen 1 (Checklist) or Screen 2 (TimePicker) for this app!
            return
        }

        // ── 4. Prevent re-triggering block screen if already displayed ──
        if (overlayManager.isBlocking(pkg)) {
            return
        }

        // ── 5. Check daily budget from DB ──
        val usage       = db.dailyUsageDao().get(pkg, today)
        val used        = usage?.minutesUsedToday ?: 0
        val minutesLeft = monitored.dailyBudgetMinutes - used

        withContext(Dispatchers.Main) {
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
    }

    // ─────────────────────────────────────────────────────────
    // Session Management
    // ─────────────────────────────────────────────────────────

    /**
     * Starts an intentional session:
     * - Immediately allocates the chosen minutes to today's DB usage (upfront deduction).
     * - Persists sessionExpiresAtMillis so app remains unlocked until that exact time.
     * - Hides the overlay and registers session with the 1-second active ticker.
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

            // Register in in-memory 1-second active ticker
            activeSessionMap[pkg] = expiresAt

            withContext(Dispatchers.Main) {
                // Remove overlay so user can use the app
                overlayManager.hideAll()

                // Display exact closing time
                val closeStr = fmtTime(expiresAt)
                showToast("${monitored.displayName} unlocked until $closeStr")
            }
        }
    }

    /**
     * Checks all active sessions every 1000ms.
     * Guaranteed to fire promptly on the Main Looper.
     */
    private fun checkActiveSessions() {
        val now = System.currentTimeMillis()
        val expiredPackages = mutableListOf<String>()

        for ((pkg, expiresAt) in activeSessionMap) {
            if (now >= expiresAt) {
                expiredPackages.add(pkg)
            }
        }

        for (pkg in expiredPackages) {
            activeSessionMap.remove(pkg)
        }

        for (pkg in expiredPackages) {
            onTimerExpired(pkg)
        }
    }

    /**
     * Called the instant the session countdown expires.
     * Forcibly exits the monitored app to Home, and notifies the user.
     */
    private fun onTimerExpired(pkg: String) {
        activeSessionMap.remove(pkg)
        serviceScope.launch {
            db.sessionStateDao().endSession(pkg)

            val monitored   = db.monitoredAppDao().getByPackage(pkg) ?: return@launch
            val today       = todayDateString()
            val usage       = db.dailyUsageDao().get(pkg, today)
            val used        = usage?.minutesUsedToday ?: 0
            val minutesLeft = (monitored.dailyBudgetMinutes - used).coerceAtLeast(0)

            withContext(Dispatchers.Main) {
                val inMonitoredApp = (currentForegroundPkg == pkg || lastMonitoredPkg == pkg)
                if (inMonitoredApp) {
                    val canExtend = (usage?.extendUsedToday == false)
                    if (minutesLeft <= 0) {
                        overlayManager.showHardBlockScreen(monitored, canExtend = canExtend)
                    } else {
                        overlayManager.showSessionFinishedScreen(
                            app = monitored,
                            minutesLeft = minutesLeft,
                            onNewSession = {
                                overlayManager.showTimePicker(monitored, minutesLeft) { pickedMinutes ->
                                    startSession(monitored, pickedMinutes)
                                }
                            }
                        )
                    }
                    exitToHome()
                } else {
                    overlayManager.hideAll()
                }

                if (minutesLeft <= 0) {
                    showToast("🔒 Time's up! Daily limit reached for ${monitored.displayName}.")
                } else {
                    showToast("⏱️ Session ended for ${monitored.displayName}! ($minutesLeft min left today).")
                }
            }
        }
    }

    /**
     * Forcibly brings the user to the device's Home screen without circular back-button recursion.
     */
    private fun exitToHome() {
        markExitingToHome()
        performGlobalAction(GLOBAL_ACTION_HOME)
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        try {
            startActivity(home)
        } catch (e: Exception) {
            android.util.Log.e("FocusGuard", "Failed to start home activity", e)
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
        // Intentionally keep activeSessionMap running so system accessibility interruptions
        // do not kill focus timers.
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
        }
        mainHandler.removeCallbacks(timerRunnable)
        activeSessionMap.clear()
        serviceScope.cancel()
        super.onDestroy()
    }
}
