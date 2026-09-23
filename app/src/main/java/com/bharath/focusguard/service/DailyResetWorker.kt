package com.bharath.focusguard.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bharath.focusguard.data.local.AppDatabase
import com.bharath.focusguard.util.PermissionUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * DailyUsage rows are keyed by date, so "reset" just means today's date has
 * no row yet -> minutesUsedToday starts at 0 automatically. This worker's
 * job is cleanup of old rows. (No liveness-check needed anymore — the
 * Accessibility Service doesn't need a watchdog the way the old polling
 * foreground service did; the OS keeps it alive on its own.)
 */
class DailyResetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db.dailyUsageDao().clearOldDays(today)

        val accessibilityOn = PermissionUtils.hasAccessibilityPermission(applicationContext)
        val overlayOn = PermissionUtils.hasOverlayPermission(applicationContext)
        if (!accessibilityOn || !overlayOn) {
            OverlayManager(applicationContext).showTamperLock()
        }
        return Result.success()
    }
}
