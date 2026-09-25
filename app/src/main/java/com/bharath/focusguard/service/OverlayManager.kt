package com.bharath.focusguard.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.bharath.focusguard.data.local.AppDatabase
import com.bharath.focusguard.data.local.entities.MonitoredApp
import com.bharath.focusguard.data.local.entities.NotionTask
import com.bharath.focusguard.data.prefs.UserPreferences
import com.bharath.focusguard.data.remote.NotionClient
import com.bharath.focusguard.data.remote.NotionRepository
import com.bharath.focusguard.ui.overlay.BlockScreen
import com.bharath.focusguard.ui.overlay.ChecklistScreen
import com.bharath.focusguard.ui.overlay.TamperLockScreen
import com.bharath.focusguard.ui.overlay.TimePickerScreen
import com.bharath.focusguard.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wraps WindowManager to render Compose overlays on top of foreground apps.
 * Traps all gestures and touches to prevent leakage to blocked apps.
 */
class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var currentOverlayView: ComposeView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val db = AppDatabase.getInstance(context)
    private val prefs = UserPreferences(context.applicationContext)

    var onEmergencyExtend: ((MonitoredApp) -> Unit)? = null
    var onGoHomeAction: (() -> Unit)? = null

    private fun overlayLayoutParams(fullScreenBlocking: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER
    }

    private fun replaceOverlay(content: ComposeView, fullScreenBlocking: Boolean) {
        runOnMain {
            removeCurrentOverlay()
            currentOverlayView = content
            try {
                windowManager.addView(content, overlayLayoutParams(fullScreenBlocking))
            } catch (e: Exception) {
                android.util.Log.e("FocusGuard", "Failed to add WindowManager overlay", e)
                currentOverlayView = null
            }
        }
    }

    private fun removeCurrentOverlay() {
        currentOverlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
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
        scope.launch {
            // Instant render from local Room cache
            val cachedTasks = withContext(Dispatchers.IO) { db.notionTaskDao().getAllOnce() }
            
            // Asynchronous Notion background sync
            launch(Dispatchers.IO) { syncNotionInBackground() }

            val view = context.createOverlayComposeView(onBackPressed = { goHome() }) {
                MaterialTheme {
                    var current by remember { mutableStateOf(cachedTasks) }
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
            replaceOverlay(view, fullScreenBlocking = true)
        }
    }

    fun showTimePicker(app: MonitoredApp, minutesLeft: Int, onSessionPicked: (Int) -> Unit) {
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
     * Displayed when a user's planned session ends, but they still have daily budget left.
     */
    fun showSessionFinishedScreen(
        app: MonitoredApp,
        minutesLeft: Int,
        onNewSession: () -> Unit
    ) {
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

    fun showTamperLock() {
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

    fun hideAll() = runOnMain { removeCurrentOverlay() }

    fun goHome() {
        hideAll()
        try {
            onGoHomeAction?.invoke()
        } catch (e: Exception) {
        }
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(home)
        } catch (e: Exception) {
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
