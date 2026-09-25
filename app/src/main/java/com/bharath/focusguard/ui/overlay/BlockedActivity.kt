package com.bharath.focusguard.ui.overlay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.bharath.focusguard.data.local.AppDatabase
import com.bharath.focusguard.data.local.entities.MonitoredApp
import com.bharath.focusguard.service.FocusGuardAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated fullscreen blocking Activity.
 * When a monitored app's daily budget is exhausted, this Activity is launched
 * on top of the blocked app with FLAG_ACTIVITY_NEW_TASK.
 *
 * This forces the blocked app into the background (calling onStop on it),
 * ensuring the user cannot interact with it, while displaying the clear message:
 * "Time is completed, come back tomorrow."
 */
class BlockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkgName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: "This app"
        val budget = intent.getIntExtra(EXTRA_DAILY_BUDGET, 60)
        val canExtend = intent.getBooleanExtra(EXTRA_CAN_EXTEND, false)

        val app = MonitoredApp(
            packageName = pkgName,
            displayName = displayName,
            dailyBudgetMinutes = budget,
            isEnabled = true
        )

        setContent {
            MaterialTheme {
                BackHandler {
                    goHome()
                }

                BlockScreen(
                    app = app,
                    isHardBlock = true,
                    minutesLeft = 0,
                    showExtend = canExtend,
                    onGoHome = { goHome() },
                    onExtend = {
                        grantEmergencyPass(pkgName)
                    },
                    onNewSession = { }
                )
            }
        }
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }

    private fun grantEmergencyPass(pkg: String) {
        val db = AppDatabase.getInstance(applicationContext)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        CoroutineScope(Dispatchers.IO).launch {
            db.dailyUsageDao().markExtendUsed(pkg, today)
            // Allow 5 minutes emergency session
            val now = System.currentTimeMillis()
            db.sessionStateDao().upsert(
                com.bharath.focusguard.data.local.entities.SessionState(
                    packageName = pkg,
                    sessionStartTimeMillis = now,
                    sessionLengthMinutes = 5,
                    sessionExpiresAtMillis = now + (5 * 60_000L),
                    lastResumedAtMillis = now,
                    isActive = true
                )
            )
            finish()
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
        const val EXTRA_DAILY_BUDGET = "extra_daily_budget"
        const val EXTRA_CAN_EXTEND = "extra_can_extend"
    }
}
