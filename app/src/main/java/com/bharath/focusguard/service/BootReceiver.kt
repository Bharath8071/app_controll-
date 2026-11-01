package com.bharath.focusguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Fix-Boot: Reschedules WorkManager jobs and AlarmManager window alarms after
 * the device reboots. Android clears all alarms and WorkManager state on reboot,
 * so without this receiver the app would stop enforcing block windows and daily
 * resets until the user manually opens the app.
 *
 * Handles both BOOT_COMPLETED (standard) and QUICKBOOT_POWERON (Xiaomi HyperOS/MIUI
 * fast-boot sequence, which does not fire a standard BOOT_COMPLETED on some ROMs).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON") return

        // Re-enqueue the daily reset worker (WorkManager survives DB but not alarms)
        try {
            val request = PeriodicWorkRequestBuilder<DailyResetWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_reset",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (_: Exception) {}

        // Reschedule window start/end alarms
        WindowAlarmScheduler.rescheduleAll(context)
    }
}

