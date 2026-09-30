package com.bharath.focusguard.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    const val CHANNEL_ID = "focus_guard_session_alerts"
    private const val CHANNEL_NAME = "FocusGuard Session Alerts"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                importance
            ).apply {
                description = "Alerts for session closing warnings and emergency extension countdowns"
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun getNotificationId(packageName: String): Int {
        return (packageName.hashCode() and 0x7FFFFFFF) % 100000 + 1000
    }

    /**
     * Sub-func 1: Warns the user that the app will close in 1 minute.
     * Triggered when remaining time hits 1 minute (only for sessions longer than 1 min).
     */
    fun showClosingWarningNotification(context: Context, appName: String, packageName: String) {
        if (!PermissionUtils.hasNotificationPermission(context)) return

        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⏳ 1 Minute Remaining")
            .setContentText("$appName will close in 1 minute. Wrap up your activity!")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$appName will close in 1 minute. FocusGuard will return you to Home when time is up."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(getNotificationId(packageName), notification)
        } catch (e: SecurityException) {
            // Permission missing
        } catch (e: Exception) {
            android.util.Log.e("FocusGuard", "Failed to show closing warning notification", e)
        }
    }

    /**
     * Sub-func 2: While in the emergency 5 min, notifies every minute with the remaining time.
     * Triggered ONLY during the 5-minute emergency pass.
     */
    fun showEmergencyRemainingNotification(context: Context, appName: String, packageName: String, minutesRemaining: Int) {
        if (!PermissionUtils.hasNotificationPermission(context)) return

        createNotificationChannel(context)

        val minWord = if (minutesRemaining == 1) "1 minute" else "$minutesRemaining minutes"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 Emergency Pass: $minWord remaining")
            .setContentText("Emergency time for $appName: $minWord left before hard lock.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Emergency 5-min extension in progress for $appName. You have $minWord remaining before the app locks."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(getNotificationId(packageName), notification)
        } catch (e: SecurityException) {
            // Permission missing
        } catch (e: Exception) {
            android.util.Log.e("FocusGuard", "Failed to show emergency notification", e)
        }
    }

    fun cancelSessionNotification(context: Context, packageName: String) {
        try {
            NotificationManagerCompat.from(context).cancel(getNotificationId(packageName))
        } catch (e: Exception) {
        }
    }
}
