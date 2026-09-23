package com.bharath.focusguard

import android.app.Application
import androidx.work.*
import com.bharath.focusguard.data.local.AppDatabase
import com.bharath.focusguard.data.local.entities.MonitoredApp
import com.bharath.focusguard.service.DailyResetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class FocusGuardApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scheduleDailyResetWorker()
        seedDefaultMonitoredApps()
        // No service to start here anymore — FocusGuardAccessibilityService
        // is started/stopped by the OS itself once the user enables it under
        // Settings > Accessibility, independent of this app process.
    }

    private fun seedDefaultMonitoredApps() {
        appScope.launch {
            val dao = AppDatabase.getInstance(this@FocusGuardApp).monitoredAppDao()
            if (dao.getByPackage(INSTAGRAM_PACKAGE) == null) {
                dao.upsert(
                    MonitoredApp(
                        packageName = INSTAGRAM_PACKAGE,
                        displayName = "Instagram",
                        dailyBudgetMinutes = 60,
                        isEnabled = true
                    )
                )
            }
            if (dao.getByPackage(YOUTUBE_PACKAGE) == null) {
                dao.upsert(
                    MonitoredApp(
                        packageName = YOUTUBE_PACKAGE,
                        displayName = "YouTube",
                        dailyBudgetMinutes = 60,
                        isEnabled = true
                    )
                )
            }
        }
    }

    private fun scheduleDailyResetWorker() {
        val request = PeriodicWorkRequestBuilder<DailyResetWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_reset",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }
}
