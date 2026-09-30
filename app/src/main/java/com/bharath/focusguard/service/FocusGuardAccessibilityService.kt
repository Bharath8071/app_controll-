package com.bharath.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.bharath.focusguard.data.local.AppDatabase
import com.bharath.focusguard.data.local.entities.DailyUsage
import com.bharath.focusguard.data.local.entities.MonitoredApp
import com.bharath.focusguard.data.local.entities.SessionState
import com.bharath.focusguard.data.prefs.CooldownManager
import com.bharath.focusguard.util.NotificationHelper
import com.bharath.focusguard.util.PermissionUtils
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/**
 * Tracks an active session in memory, including notification flags for 30-second
 * closing alerts and per-minute emergency countdown notifications.
 */
data class ActiveSessionInfo(
    val packageName: String,
    val sessionLengthMinutes: Int,
    val sessionExpiresAtMillis: Long,
    val isEmergency: Boolean = false,
    var hasNotifiedClosingWarning: Boolean = false,
    val notifiedEmergencyMinutes: MutableSet<Int> = mutableSetOf()
)

/**
 * FocusGuard Core Engine.
 *
 * Full-screen App Blocker & Session Enforcer:
 *  - When user leaves a monitored app -> overlay is IMMEDIATELY removed so keyboards & other apps work 100% free.
 *  - When session timer expires -> enforces standard 10-minute cooldown blocker, FORCIBLY EXITS the monitored app to Home, and notifies user.
 *  - During active sessions:
 *      * Sub-func 1: Sends 30-second closing warning notification.
 *      * Sub-func 2: In 5-minute emergency sessions, notifies every minute with remaining time.
 *  - When daily budget is exhausted -> FORCIBLY EXITS the app to Home, showing full-screen Hard Block overlay.
 *  - Un-killable 1-second ticker on Main Looper ensures timers fire with second-level precision, immune to system interruptions.
 */
class FocusGuardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var db: AppDatabase
    private lateinit var overlayManager: OverlayManager
    private lateinit var cooldownManager: CooldownManager
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Tracks which package is currently in the foreground. */
    @Volatile
    private var currentForegroundPkg: String? = null

    /** Tracks the last monitored app package that was opened. */
    @Volatile
    private var lastMonitoredPkg: String? = null

    /** In-memory map of active session package -> ActiveSessionInfo. */
    private val activeSessionMap = ConcurrentHashMap<String, ActiveSessionInfo>()

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
        cooldownManager = CooldownManager.getInstance(applicationContext)
        NotificationHelper.createNotificationChannel(applicationContext)

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
                        val alreadyPastWarning = (s.sessionExpiresAtMillis - now <= 30_000L)
                        activeSessionMap[s.packageName] = ActiveSessionInfo(
                            packageName = s.packageName,
                            sessionLengthMinutes = s.sessionLengthMinutes,
                            sessionExpiresAtMillis = s.sessionExpiresAtMillis,
                            isEmergency = false,
                            hasNotifiedClosingWarning = alreadyPastWarning
                        )
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

        // 1. If transitioning to home, ignore re-entrant events from dying windows
        if (isTransitioningToHome()) {
            return
        }

        // 2. If screen is locked or keyguard active, dismiss overlay immediately so user is never trapped on lockscreen
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true) {
            currentForegroundPkg = newPkg
            lastMonitoredPkg = null
            overlayManager.hideAll()
            return
        }

        // 3. Ignore transient system packages (keyboards, system status bar, notifications, volume panels, overlays)
        if (isIgnoredTransientPackage(newPkg)) {
            return
        }

        currentForegroundPkg = newPkg

        serviceScope.launch {
            val monitored = db.monitoredAppDao().getByPackage(newPkg)
            if (monitored == null || !monitored.isEnabled) {
                // User navigated to a non-monitored app or launcher
                if (isLauncherPackage(newPkg)) {
                    lastMonitoredPkg = null
                }
                withContext(Dispatchers.Main) {
                    overlayManager.hideAll()
                }
                return@launch
            }

            // User IS in a monitored app!
            lastMonitoredPkg = newPkg
            handleMonitoredAppEntered(monitored)
        }
    }

    /**
     * Checks if a package belongs to transient system UI, heads-up notifications,
     * keyboards, or system dialogs across various Android OEM vendors (Samsung, Xiaomi,
     * OnePlus, Oppo, Vivo, Motorola, Pixel).
     */
    private fun isIgnoredTransientPackage(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg == "android") return true
        val lower = pkg.lowercase(Locale.ROOT)
        return lower.contains("systemui") ||
                lower.contains("notification") ||
                lower.contains("overlay") ||
                lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.contains("honeyboard") ||
                lower.contains("swiftkey") ||
                lower.contains("gboard") ||
                lower.contains("ime") ||
                lower.contains("sogou") ||
                lower.contains("baidu") ||
                lower.contains("permissioncontroller") ||
                lower.contains("gms") ||
                lower.contains("smartcapture") ||
                lower.contains("screenshot") ||
                lower.contains("gametools") ||
                lower.contains("edge") ||
                lower.contains("sidepanel") ||
                lower.contains("volume") ||
                lower.contains("toast") ||
                lower.contains("autofill") ||
                lower.contains("facemoji")
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        val lower = pkg.lowercase(Locale.ROOT)
        if (lower.contains("launcher") || lower.contains("home") || lower.contains("nexuslauncher")) {
            return true
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == pkg
    }

    /**
     * Accurately determines if the target app is currently visible or focused on the screen,
     * querying live active windows directly from Android Window Manager.
     */
    private fun isAppInForeground(pkg: String): Boolean {
        try {
            val rootPkg = rootInActiveWindow?.packageName?.toString()
            if (rootPkg == pkg) return true
        } catch (e: Exception) {
        }
        if (currentForegroundPkg == pkg || lastMonitoredPkg == pkg) return true
        try {
            val hasWindow = windows?.any { it.root?.packageName?.toString() == pkg } == true
            if (hasWindow) return true
        } catch (e: Exception) {
        }
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

        val usage       = db.dailyUsageDao().get(pkg, today)
        val used        = usage?.minutesUsedToday ?: 0
        val minutesLeft = (monitored.dailyBudgetMinutes - used).coerceAtLeast(0)

        // ── 0. Check Cooldown Blocker First! ──
        // Function 2: If the standard 10-minute cooldown is active, block app use immediately!
        // The user cannot use the app for the next 10 minutes even if they have remaining total time.
        val cooldownExpiresAt = cooldownManager.getCooldownExpiresAt(pkg)
        if (cooldownExpiresAt != null && now < cooldownExpiresAt) {
            if (overlayManager.isBlocking(pkg)) {
                return
            }
            withContext(Dispatchers.Main) {
                overlayManager.showCooldownBlockScreen(
                    app = monitored,
                    cooldownExpiresAtMillis = cooldownExpiresAt,
                    minutesLeft = minutesLeft,
                    onCooldownFinished = {
                        serviceScope.launch {
                            handleMonitoredAppEntered(monitored)
                        }
                    }
                )
            }
            return
        }

        // ── 1. Check if there's an ACTIVE, unexpired session window ──
        val session = db.sessionStateDao().getActive(pkg)
        if (session != null && now < session.sessionExpiresAtMillis) {
            // App is unlocked for this session window! Hide overlay and let user use it.
            if (!activeSessionMap.containsKey(pkg)) {
                activeSessionMap[pkg] = ActiveSessionInfo(
                    packageName = pkg,
                    sessionLengthMinutes = session.sessionLengthMinutes,
                    sessionExpiresAtMillis = session.sessionExpiresAtMillis,
                    isEmergency = false
                )
            }
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
            return
        }

        // ── 4. Prevent re-triggering block screen if already displayed ──
        if (overlayManager.isBlocking(pkg)) {
            return
        }

        // ── 5. Check daily budget from DB ──
        withContext(Dispatchers.Main) {
            if (minutesLeft <= 0) {
                // ── HARD BLOCK: Daily limit exhausted! ──
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
     * - Clears any existing cooldown for this app.
     * - Hides the overlay and registers session with the 1-second active ticker.
     */
    fun startSession(monitored: MonitoredApp, minutes: Int, isEmergency: Boolean = false) {
        val pkg = monitored.packageName
        cooldownManager.clearCooldown(pkg)
        NotificationHelper.cancelSessionNotification(applicationContext, pkg)

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

            // Register in in-memory 1-second active ticker with notification tracking
            activeSessionMap[pkg] = ActiveSessionInfo(
                packageName            = pkg,
                sessionLengthMinutes   = minutes,
                sessionExpiresAtMillis = expiresAt,
                isEmergency            = isEmergency
            )

            withContext(Dispatchers.Main) {
                // Remove overlay so user can use the app
                overlayManager.hideAll()

                // Display exact closing time
                val closeStr = fmtTime(expiresAt)
                val prefix = if (isEmergency) "⚡ Emergency Pass:" else ""
                showToast("$prefix ${monitored.displayName} unlocked until $closeStr")
            }
        }
    }

    /**
     * Checks all active sessions every 1000ms.
     * Guaranteed to fire promptly on the Main Looper.
     * Also checks and fires session closing warnings and emergency countdown notifications.
     */
    private fun checkActiveSessions() {
        val now = System.currentTimeMillis()
        val expiredPackages = mutableListOf<String>()

        for ((pkg, session) in activeSessionMap) {
            val remainingMillis = session.sessionExpiresAtMillis - now
            if (remainingMillis <= 0) {
                expiredPackages.add(pkg)
                continue
            }

            // ── Sub-function 1: 30-second closing warning notification ──
            if (!session.isEmergency) {
                if (remainingMillis <= 30_000L && !session.hasNotifiedClosingWarning) {
                    session.hasNotifiedClosingWarning = true
                    serviceScope.launch {
                        val monitored = db.monitoredAppDao().getByPackage(pkg)
                        if (monitored != null) {
                            NotificationHelper.showClosingWarningNotification(
                                context = applicationContext,
                                appName = monitored.displayName,
                                packageName = pkg
                            )
                        }
                    }
                }
            }

            // ── Sub-function 2: Emergency 5-min per-minute notification ──
            if (session.isEmergency) {
                val remainingMinutes = ceil(remainingMillis / 60_000.0).toInt().coerceIn(1, 4)
                if (remainingMinutes !in session.notifiedEmergencyMinutes) {
                    session.notifiedEmergencyMinutes.add(remainingMinutes)
                    serviceScope.launch {
                        val monitored = db.monitoredAppDao().getByPackage(pkg)
                        if (monitored != null) {
                            NotificationHelper.showEmergencyRemainingNotification(
                                context = applicationContext,
                                appName = monitored.displayName,
                                packageName = pkg,
                                minutesRemaining = remainingMinutes
                            )
                        }
                    }
                }
            }
        }

        // Secondary continuous defense: actively enforce cooldown for any monitored app in foreground
        // Zero-battery optimization: ONLY queries rootInActiveWindow if an app is currently in cooldown
        if (cooldownManager.hasActiveCooldowns() && !isTransitioningToHome()) {
            try {
                val topPkg = rootInActiveWindow?.packageName?.toString()
                if (topPkg != null && !isIgnoredTransientPackage(topPkg)) {
                    val cooldownExpiry = cooldownManager.getCooldownExpiresAt(topPkg)
                    if (cooldownExpiry != null && now < cooldownExpiry) {
                        if (!overlayManager.isBlocking(topPkg)) {
                            serviceScope.launch {
                                val monitored = db.monitoredAppDao().getByPackage(topPkg)
                                if (monitored != null && monitored.isEnabled) {
                                    val today = todayDateString()
                                    val usage = db.dailyUsageDao().get(topPkg, today)
                                    val used = usage?.minutesUsedToday ?: 0
                                    val minutesLeft = (monitored.dailyBudgetMinutes - used).coerceAtLeast(0)
                                    withContext(Dispatchers.Main) {
                                        overlayManager.showCooldownBlockScreen(
                                            app = monitored,
                                            cooldownExpiresAtMillis = cooldownExpiry,
                                            minutesLeft = minutesLeft,
                                            onCooldownFinished = {
                                                serviceScope.launch {
                                                    handleMonitoredAppEntered(monitored)
                                                }
                                            }
                                        )
                                        exitToHome()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
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
     * Enforces the standard 10-minute cooldown blocker, forcibly exits the monitored app to Home,
     * and notifies the user.
     */
    private fun onTimerExpired(pkg: String) {
        activeSessionMap.remove(pkg)
        NotificationHelper.cancelSessionNotification(applicationContext, pkg)

        serviceScope.launch {
            db.sessionStateDao().endSession(pkg)

            val monitored   = db.monitoredAppDao().getByPackage(pkg) ?: return@launch
            val today       = todayDateString()
            val usage       = db.dailyUsageDao().get(pkg, today)
            val used        = usage?.minutesUsedToday ?: 0
            val minutesLeft = (monitored.dailyBudgetMinutes - used).coerceAtLeast(0)

            // Function 2: After the session time ends, block the app for standard 10 minutes!
            val cooldownExpiresAt = cooldownManager.startCooldown(pkg)

            withContext(Dispatchers.Main) {
                val inMonitoredApp = isAppInForeground(pkg)
                if (inMonitoredApp) {
                    val canExtend = (usage?.extendUsedToday == false)
                    if (minutesLeft <= 0) {
                        overlayManager.showHardBlockScreen(monitored, canExtend = canExtend)
                    } else {
                        // Dedicated 10-minute Cooldown Block Screen
                        overlayManager.showCooldownBlockScreen(
                            app = monitored,
                            cooldownExpiresAtMillis = cooldownExpiresAt,
                            minutesLeft = minutesLeft,
                            onCooldownFinished = {
                                serviceScope.launch {
                                    handleMonitoredAppEntered(monitored)
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
                    showToast("⏸️ 10-minute focus break active for ${monitored.displayName}.")
                }
            }
        }
    }

    /**
     * Forcibly brings the user to the device's Home screen using multi-tier execution:
     * Tier 1: System Accessibility Global Action Home
     * Tier 2: Explicit Android Home Intent
     * Tier 3: Direct resolution and launch of the device's default launcher package
     * Tier 4: Delayed verification pass ensuring the user reached the Home screen
     */
    private fun exitToHome() {
        markExitingToHome()

        // Tier 1: System Accessibility Global Action Home
        performGlobalAction(GLOBAL_ACTION_HOME)

        // Tier 2: Explicit Android Home Intent
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        try {
            startActivity(home)
        } catch (e: Exception) {
            android.util.Log.e("FocusGuard", "Failed to start home activity", e)
        }

        // Tier 3: Direct Default Launcher launch intent fallback
        try {
            val resolveInfo = packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY
            )
            val launcherPkg = resolveInfo?.activityInfo?.packageName
            if (launcherPkg != null && launcherPkg != packageName) {
                val launchIntent = packageManager.getLaunchIntentForPackage(launcherPkg)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FocusGuard", "Failed to launch default launcher", e)
        }

        // Tier 4: Re-verify after 350ms - if still focused on a non-home window, trigger GLOBAL_ACTION_HOME again
        mainHandler.postDelayed({
            try {
                val currentPkg = rootInActiveWindow?.packageName?.toString()
                if (currentPkg != null && !isIgnoredTransientPackage(currentPkg) && !isLauncherPackage(currentPkg)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            } catch (e: Exception) {
            }
        }, 350L)
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
            // Starts emergency 5-min session with per-minute notifications
            startSession(monitored, 5, isEmergency = true)
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
