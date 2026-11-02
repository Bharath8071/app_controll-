package com.bharath.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import com.bharath.focusguard.data.local.AppDatabase
import com.bharath.focusguard.data.local.entities.MonitoredApp
import com.bharath.focusguard.data.local.entities.NotionTask
import com.bharath.focusguard.data.prefs.UserPreferences
import com.bharath.focusguard.data.remote.NotionClient
import com.bharath.focusguard.data.remote.NotionRepository
import com.bharath.focusguard.ui.overlay.BlockScreen
import com.bharath.focusguard.ui.overlay.BlockWindowScreen
import com.bharath.focusguard.ui.overlay.ChecklistScreen
import com.bharath.focusguard.ui.overlay.CooldownBlockScreen
import com.bharath.focusguard.ui.overlay.TamperLockScreen
import com.bharath.focusguard.ui.overlay.TimePickerScreen
import com.bharath.focusguard.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OverlayState {
    IDLE,
    CHECKLIST,
    TIME_PICKER,
    SESSION_FINISHED,
    COOLDOWN_BLOCK,
    HARD_BLOCK,
    BLOCK_WINDOW,
    TAMPER_LOCK
}

/**
 * Wraps WindowManager to render Compose overlays on top of foreground apps.
 * Traps all gestures and touches to prevent leakage to blocked apps while keeping
 * system navigation bar gestures accessible.
 */
class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var currentOverlayView: ComposeView? = null

    /**
     * Every overlay window this manager has successfully attached.
     * A single reference allowed a failed/skipped removal to permanently leak
     * a full-screen window (which then survived Home/Back until process death).
     * Keeping the full set lets every hide clean up leaked windows too.
     */
    private val attachedViews = mutableSetOf<ComposeView>()

    /**
     * Invalidates asynchronous overlay builders (the checklist loads tasks on IO
     * before attaching its view). Without this, a slow builder could attach a
     * stale checklist *after* the picker or after everything was hidden, which
     * looked like "choosing a time sends me back to page 1".
     */
    @Volatile private var overlayGeneration = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val db = AppDatabase.getInstance(context)
    private val prefs = UserPreferences(context.applicationContext)

    // BUG-O2 fix: @Volatile ensures cross-thread visibility of these fields.
    // OverlayManager methods are called from both Main and coroutine threads.
    @Volatile var currentState: OverlayState = OverlayState.IDLE
        private set
    @Volatile var currentPackage: String? = null
        private set

    var onEmergencyExtend: ((MonitoredApp) -> Unit)? = null
    var onGoHomeAction: ((String?) -> Unit)? = null

    fun isGating(pkg: String): Boolean =
        currentPackage == pkg && (currentState == OverlayState.CHECKLIST || currentState == OverlayState.TIME_PICKER)

    // BUG-018 fix: Include TAMPER_LOCK so it's recognized as blocking and won't be replaced.
    fun isBlocking(pkg: String): Boolean =
        currentPackage == pkg && (currentState == OverlayState.HARD_BLOCK ||
                currentState == OverlayState.SESSION_FINISHED ||
                currentState == OverlayState.BLOCK_WINDOW ||
                currentState == OverlayState.COOLDOWN_BLOCK) ||
                currentState == OverlayState.TAMPER_LOCK

    // BUG-019 fix: Allow the AccessibilityService to cancel this scope in onDestroy().
    fun cancelScope() = scope.cancel()

    private fun overlayLayoutParams(fullScreenBlocking: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (context is AccessibilityService)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
        (if (fullScreenBlocking) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            // BUG-O1 fix: Show overlay even during lock screen transition (e.g. after session timeout)`r
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER
    }

    private fun replaceOverlay(content: ComposeView, fullScreenBlocking: Boolean) {
        runOnMain {
            removeAllOverlays()
            currentOverlayView = content
            try {
                windowManager.addView(content, overlayLayoutParams(fullScreenBlocking))
                attachedViews.add(content)
            } catch (e: Exception) {
                android.util.Log.e("FocusGuard", "Failed to add WindowManager overlay", e)
                currentOverlayView = null
                currentState = OverlayState.IDLE
                currentPackage = null
            }
        }
    }

    /**
     * Removes EVERY overlay window this manager has attached, not just the most
     * recent one. Removal is attempted per view so one failure can never strand
     * the rest; a view that still cannot be removed stays registered so a later
     * hide can retry instead of silently losing the only handle to it.
     */
    private fun removeAllOverlays() {
        val views = attachedViews.toMutableSet()
        currentOverlayView?.let { views.add(it) }
        for (view in views) {
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                } else {
                    windowManager.removeView(view)
                }
                attachedViews.remove(view)
            } catch (e: Exception) {
                try {
                    windowManager.removeViewImmediate(view)
                    attachedViews.remove(view)
                } catch (e2: Exception) {
                    // Keep it registered: the next hide will try again.
                }
            }
        }
        currentOverlayView = null
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }

    /**
     * Shows the checklist overlay with zero network delay by reading the local cache immediately,
     * while scheduling an asynchronous background Notion sync.
     */
    fun showChecklistThenPicker(app: MonitoredApp, minutesLeft: Int, onSessionPicked: (Int) -> Unit) {
        currentState = OverlayState.CHECKLIST
        currentPackage = app.packageName
        val generation = ++overlayGeneration
        scope.launch {
            // Instant render from local Room cache
            val cachedTasks = withContext(Dispatchers.IO) { db.notionTaskDao().getTopPriorityOnce() }

            // The flow may have advanced (time picker, hideAll, another overlay)
            // while the task cache was loading. Never attach this stale checklist.
            if (generation != overlayGeneration) return@launch

            val view = context.createOverlayComposeView(onBackPressed = { goHome() }) {
                MaterialTheme {
                    var current by remember { mutableStateOf(cachedTasks) }

                    // Asynchronous Notion background sync updates tasks live
                    LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) {
                            syncNotionInBackground()
                            val updated = db.notionTaskDao().getTopPriorityOnce()
                            withContext(Dispatchers.Main) {
                                current = updated
                            }
                        }
                    }

                    ChecklistScreen(
                        appName = app.displayName,
                        tasks = current,
                        onToggle = { task, checked ->
                            current = current.map {
                                if (it.notionPageId == task.notionPageId) it.copy(isChecked = checked) else it
                            }
                            scope.launch { persistTaskCheck(task, checked) }
                        },
                        onContinue = { showTimePicker(app, minutesLeft, onSessionPicked) },
                        onGoHome = { goHome() }
                    )
                }
            }
            if (generation != overlayGeneration) return@launch
            replaceOverlay(view, fullScreenBlocking = true)
        }
    }

    fun showTimePicker(app: MonitoredApp, minutesLeft: Int, onSessionPicked: (Int) -> Unit) {
        currentState = OverlayState.TIME_PICKER
        currentPackage = app.packageName
        overlayGeneration++
        val view = context.createOverlayComposeView(onBackPressed = { goHome() }) {
            MaterialTheme {
                TimePickerScreen(
                    appName = app.displayName,
                    minutesLeft = minutesLeft.coerceAtLeast(1),
                    onPicked = { minutes -> onSessionPicked(minutes) },
                    onGoHome = { goHome() }
                )
            }
        }
        replaceOverlay(view, fullScreenBlocking = true)
    }

    /**
     * FLAW-002 fix: This method is superseded by the CooldownBlockScreen flow.
     * Per spec, after ANY planned session ends the user always enters a 10-minute cooldown
     * (via showCooldownBlockScreen), not this screen. Once the cooldown ends, 
     * handleMonitoredAppEntered() re-evaluates and routes to checklist/picker if budget remains.
     *
     * This method is preserved for potential future use (e.g., a "quick re-enter" bypass for
     * premium users) but is NOT part of the current enforcement flow.
     *
     * Currently called: NOWHERE (intentionally - the cooldown flow replaced it)
     */
    @Suppress("unused")
    fun showSessionFinishedScreen(
        app: MonitoredApp,
        minutesLeft: Int,
        onNewSession: () -> Unit
    ) {
        currentState = OverlayState.SESSION_FINISHED
        currentPackage = app.packageName
        overlayGeneration++
        val view = context.createOverlayComposeView(onBackPressed = { goHome() }) {
            MaterialTheme {
                BlockScreen(
                    app = app,
                    isHardBlock = false,
                    minutesLeft = minutesLeft,
                    showExtend = false,
                    onGoHome = { goHome() },
                    onExtend = { },
                    onNewSession = {
                        hideAll()
                        onNewSession()
                    }
                )
            }
        }
        replaceOverlay(view, fullScreenBlocking = true)
    }

    /**
     * Displayed when the user has exhausted their daily budget for the app.
     */
    fun showHardBlockScreen(app: MonitoredApp, canExtend: Boolean) {
        currentState = OverlayState.HARD_BLOCK
        currentPackage = app.packageName
        overlayGeneration++
        val view = context.createOverlayComposeView(onBackPressed = { goHome() }) {
            MaterialTheme {
                BlockScreen(
                    app = app,
                    isHardBlock = true,
                    minutesLeft = 0,
                    showExtend = canExtend,
                    onGoHome = { goHome() },
                    onExtend = {
                        hideAll()
                        onEmergencyExtend?.invoke(app)
                    },
                    onNewSession = { }
                )
            }
        }
        replaceOverlay(view, fullScreenBlocking = true)
    }

    /**
     * Displayed after a planned session ends: enforces a standard 10-minute cooldown
     * before the user can resume using the app, even if daily budget remains.
     */
    fun showCooldownBlockScreen(
        app: MonitoredApp,
        cooldownExpiresAtMillis: Long,
        minutesLeft: Int,
        onCooldownFinished: (() -> Unit)? = null
    ) {
        currentState = OverlayState.COOLDOWN_BLOCK
        currentPackage = app.packageName
        overlayGeneration++
        var finishedHandled = false
        val view = context.createOverlayComposeView(onBackPressed = { goHome() }) {
            MaterialTheme {
                CooldownBlockScreen(
                    app = app,
                    cooldownExpiresAtMillis = cooldownExpiresAtMillis,
                    minutesLeft = minutesLeft,
                    onGoHome = { goHome() },
                    onCooldownFinished = {
                        if (!finishedHandled) {
                            finishedHandled = true
                            hideAll()
                            onCooldownFinished?.invoke()
                        }
                    }
                )
            }
        }
        replaceOverlay(view, fullScreenBlocking = true)
    }

    fun showBlockWindow(
        app: MonitoredApp,
        windowName: String?,
        untilText: String,
        progress: Float,
        breaksLeft: Int,
        onTakeBreak: () -> Unit,
        onLeaveEarly: () -> Unit,
    ) {
        currentState = OverlayState.BLOCK_WINDOW
        currentPackage = app.packageName
        overlayGeneration++
        val view = context.createOverlayComposeView(onBackPressed = { goHome() }) {
            MaterialTheme {
                BlockWindowScreen(
                    appName = app.displayName, windowName = windowName, untilText = untilText,
                    progress = progress, breaksLeft = breaksLeft,
                    onBackHome = { goHome() }, onTakeBreak = onTakeBreak, onLeaveEarly = onLeaveEarly,
                )
            }
        }
        replaceOverlay(view, fullScreenBlocking = true)
    }

    fun showTamperLock() {
        currentState = OverlayState.TAMPER_LOCK
        currentPackage = null
        overlayGeneration++
        val view = context.createOverlayComposeView(onBackPressed = { goHome() }) {
            MaterialTheme {
                TamperLockScreen(
                    onOpenSettings = {
                        if (!PermissionUtils.hasOverlayPermission(context)) {
                            PermissionUtils.requestOverlayPermission(context)
                        } else {
                            PermissionUtils.requestAccessibilityPermission(context)
                        }
                    }
                )
            }
        }
        replaceOverlay(view, fullScreenBlocking = true)
    }

    fun hideAll() {
        runOnMain {
            overlayGeneration++      // cancel any async builder still in flight
            removeAllOverlays()
            currentState = OverlayState.IDLE
            currentPackage = null
        }
    }

    fun goHome() {
        runOnMain {
            overlayGeneration++
            val pkg = currentPackage
            try {
                onGoHomeAction?.invoke(pkg)
            } catch (e: Exception) {
                android.util.Log.e("FocusGuard", "Failed to invoke onGoHomeAction", e)
            }

            // Immediately remove all overlays so all touches, gestures, and keys return to Android OS
            removeAllOverlays()
            currentState = OverlayState.IDLE
            currentPackage = null

            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            try {
                context.startActivity(home)
            } catch (e: Exception) {
                android.util.Log.e("FocusGuard", "Failed to start home activity", e)
            }
        }
    }

    private suspend fun syncNotionInBackground() {
        try {
            val token = prefs.getToken()
            val databaseId = prefs.getDatabaseId()
            if (token.isNotBlank() && databaseId.isNotBlank()) {
                val repo = NotionRepository(
                    api = NotionClient.create(token),
                    dao = db.notionTaskDao(),
                    databaseId = databaseId
                )
                repo.syncTasksFromNotion()
            }
        } catch (e: Exception) {
        }
    }

    private suspend fun persistTaskCheck(task: NotionTask, checked: Boolean) {
        val token = prefs.getToken()
        val databaseId = prefs.getDatabaseId()
        if (token.isBlank() || databaseId.isBlank()) {
            db.notionTaskDao().setChecked(task.notionPageId, checked)
            return
        }
        NotionRepository(
            api = NotionClient.create(token),
            dao = db.notionTaskDao(),
            databaseId = databaseId
        ).setTaskChecked(task, checked)
    }
}
