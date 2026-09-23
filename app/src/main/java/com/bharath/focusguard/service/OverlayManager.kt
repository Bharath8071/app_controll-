package com.bharath.focusguard.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
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
 * Wraps the WindowManager plumbing for drawing Compose screens on top of
 * whatever app is in the foreground. Only one overlay is visible at a time:
 * checklist -> picker -> (hidden while app runs) -> block/tamper.
 */
class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var currentOverlayView: ComposeView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val db = AppDatabase.getInstance(context)
    private val prefs = UserPreferences(context.applicationContext)

    var onEmergencyExtend: ((MonitoredApp) -> Unit)? = null

    private fun overlayLayoutParams(fullScreenBlocking: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
        if (fullScreenBlocking) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        },
        PixelFormat.TRANSLUCENT
    )

    private fun replaceOverlay(content: ComposeView, fullScreenBlocking: Boolean) {
        runOnMain {
            removeCurrentOverlay()
            currentOverlayView = content
            try {
                windowManager.addView(content, overlayLayoutParams(fullScreenBlocking))
            } catch (e: Exception) {
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

    /** onSessionPicked is wired by the caller to FocusGuardAccessibilityService.startSession(). */
    fun showChecklistThenPicker(app: MonitoredApp, minutesLeft: Int, onSessionPicked: (Int) -> Unit) {
        scope.launch {
            val tasks = loadTasks()
            val view = context.createOverlayComposeView {
                MaterialTheme {
                    var current by remember { mutableStateOf(tasks) }
                    ChecklistScreen(
                        appName = app.displayName,
                        tasks = current,
                        onToggle = { task, checked ->
                            current = current.map {
                                if (it.notionPageId == task.notionPageId) it.copy(isChecked = checked) else it
                            }
                            scope.launch { persistTaskCheck(task, checked) }
                        },
                        onContinue = { showTimePicker(app, minutesLeft, onSessionPicked) }
                    )
                }
            }
            replaceOverlay(view, fullScreenBlocking = true)
        }
    }

    fun showTimePicker(app: MonitoredApp, minutesLeft: Int, onSessionPicked: (Int) -> Unit) {
        val view = context.createOverlayComposeView {
            MaterialTheme {
                TimePickerScreen(
                    appName = app.displayName,
                    minutesLeft = minutesLeft.coerceAtLeast(1),
                    onPicked = { minutes -> onSessionPicked(minutes) }
                )
            }
        }
        replaceOverlay(view, fullScreenBlocking = true)
    }

    fun showBlockScreen(app: MonitoredApp) {
        showBlock(app, showExtend = false)
    }

    fun showBlockScreenWithExtendOption(app: MonitoredApp) {
        showBlock(app, showExtend = true)
    }

    private fun showBlock(app: MonitoredApp, showExtend: Boolean) {
        val view = context.createOverlayComposeView {
            MaterialTheme {
                BlockScreen(
                    app = app,
                    showExtend = showExtend,
                    onGoHome = { goHome() },
                    onExtend = {
                        hideAll()
                        onEmergencyExtend?.invoke(app)
                    }
                )
            }
        }
        replaceOverlay(view, fullScreenBlocking = true)
    }

    fun showTamperLock() {
        val view = context.createOverlayComposeView {
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
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(home)
    }

    private suspend fun loadTasks(): List<NotionTask> {
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
        return withContext(Dispatchers.IO) { db.notionTaskDao().getAllOnce() }
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
