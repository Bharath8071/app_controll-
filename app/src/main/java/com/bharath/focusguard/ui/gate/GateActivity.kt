package com.bharath.focusguard.ui.gate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.bharath.focusguard.data.local.AppDatabase
import com.bharath.focusguard.data.local.entities.NotionTask
import com.bharath.focusguard.service.FocusGuardAccessibilityService
import com.bharath.focusguard.ui.overlay.ChecklistScreen
import com.bharath.focusguard.ui.overlay.TimePickerScreen
import kotlinx.coroutines.*

/**
 * Activity Mode gate screen.
 *
 * Opened by FocusGuardAccessibilityService when appMode = false.
 * Shows the same ChecklistScreen → TimePickerScreen flow as the overlay.
 *
 * On time picked:
 *   1. Sends ACTION_SESSION_START broadcast → service calls startSession().
 *   2. Auto-launches the monitored app.
 *   3. Finishes itself.
 *
 * On dismiss / back (no pick):
 *   - Goes to home screen. The monitored app stays blocked.
 */
class GateActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg        = intent.getStringExtra(FocusGuardAccessibilityService.EXTRA_PKG) ?: run { goHome(); return }
        val minutesLeft = intent.getIntExtra(FocusGuardAccessibilityService.EXTRA_MINUTES, 0).coerceAtLeast(1)
        val appName    = intent.getStringExtra(FocusGuardAccessibilityService.EXTRA_APP_NAME) ?: pkg

        val db = AppDatabase.getInstance(applicationContext)

        setContent {
            MaterialTheme {
                var showPicker by remember { mutableStateOf(false) }
                var tasks by remember { mutableStateOf<List<NotionTask>>(emptyList()) }

                // Load cached Notion tasks on IO, then refresh from network
                LaunchedEffect(Unit) {
                    val cached = withContext(Dispatchers.IO) {
                        db.notionTaskDao().getTopPriorityOnce()
                    }
                    tasks = cached
                    // Background Notion sync (best-effort)
                    withContext(Dispatchers.IO) {
                        try {
                            val prefs = com.bharath.focusguard.data.prefs.UserPreferences(applicationContext)
                            val token = prefs.getToken()
                            val dbId  = prefs.getDatabaseId()
                            if (token.isNotBlank() && dbId.isNotBlank()) {
                                com.bharath.focusguard.data.remote.NotionRepository(
                                    api = com.bharath.focusguard.data.remote.NotionClient.create(token),
                                    dao = db.notionTaskDao(),
                                    databaseId = dbId
                                ).syncTasksFromNotion()
                                val updated = db.notionTaskDao().getTopPriorityOnce()
                                withContext(Dispatchers.Main) { tasks = updated }
                            }
                        } catch (_: Exception) {}
                    }
                }

                if (!showPicker) {
                    ChecklistScreen(
                        appName  = appName,
                        tasks    = tasks,
                        onToggle = { task, checked ->
                            activityScope.launch(Dispatchers.IO) {
                                db.notionTaskDao().setChecked(task.notionPageId, checked)
                            }
                        },
                        onContinue = { showPicker = true },
                        onGoHome   = { goHome() }
                    )
                } else {
                    TimePickerScreen(
                        appName    = appName,
                        minutesLeft = minutesLeft,
                        onPicked   = { minutes -> startSessionAndLaunch(pkg, minutes) },
                        onGoHome   = { goHome() }
                    )
                }
            }
        }
    }

    override fun onBackPressed() {
        goHome()
    }

    /**
     * Signals the service to start a session, then auto-launches the monitored app.
     */
    private fun startSessionAndLaunch(pkg: String, minutes: Int) {
        // 1. Broadcast session-start to AccessibilityService
        val broadcast = Intent(FocusGuardAccessibilityService.ACTION_SESSION_START).apply {
            setPackage(packageName)
            putExtra(FocusGuardAccessibilityService.EXTRA_PKG, pkg)
            putExtra(FocusGuardAccessibilityService.EXTRA_MINUTES, minutes)
        }
        sendBroadcast(broadcast)

        // 2. Auto-launch the monitored app
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("GateActivity", "Failed to launch $pkg", e)
        }

        // 3. Close GateActivity
        finish()
    }

    /**
     * Dismissed without picking a time → go to home screen.
     * The monitored app remains blocked (no session was started).
     */
    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
