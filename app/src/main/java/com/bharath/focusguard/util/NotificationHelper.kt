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
     * Sub-func 1: Warns the user that the app will close in 30 seconds.
     * Triggered when remaining time hits 30 seconds.
     */
    fun showClosingWarningNotification(context: Context, appName: String, packageName: String) {
        if (!PermissionUtils.hasNotificationPermission(context)) return

        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⏳ 30 Seconds Remaining")
            .setContentText("$appName will close in 30 seconds. Wrap up your activity!")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$appName will close in 30 seconds. FocusGuard will return you to Home when time is up."))
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

    const val CHANNEL_WARNINGS = "focus_guard_warnings"
    const val CHANNEL_WINDOWS = "focus_guard_windows"

    private fun ensureChannels(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            mgr.createNotificationChannel(android.app.NotificationChannel(CHANNEL_ID, "Session alerts", android.app.NotificationManager.IMPORTANCE_HIGH))
            mgr.createNotificationChannel(android.app.NotificationChannel(CHANNEL_WARNINGS, "Warnings", android.app.NotificationManager.IMPORTANCE_HIGH))
            mgr.createNotificationChannel(android.app.NotificationChannel(CHANNEL_WINDOWS, "Block windows", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun showPermissionLost(context: Context) {
        if (!PermissionUtils.hasNotificationPermission(context)) return
        ensureChannels(context)
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = intent?.let { android.app.PendingIntent.getActivity(context, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT) }
        val n = NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Accessibility is off. FocusGuard can't block apps.")
            .setContentText("Tap to open Permissions and turn it back on.")
            .setContentIntent(pi).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(9001, n) } catch (_: Exception) {}
    }

    fun showBudgetLow(context: Context, appName: String, packageName: String) {
        if (!PermissionUtils.hasNotificationPermission(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("5 minutes left in your daily budget.")
            .setContentText("$appName: 5 minutes left.")
            .setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(getNotificationId(packageName) + 500, n) } catch (_: Exception) {}
    }

    fun showBlockStartingSoon(context: Context, windowName: String) {
        if (!PermissionUtils.hasNotificationPermission(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_WINDOWS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$windowName starts in 5 minutes.")
            .setContentText("Your apps will close when the block starts.")
            .setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(9100 + windowName.hashCode() % 100, n) } catch (_: Exception) {}
    }

    fun showBlockEnded(context: Context, windowName: String) {
        if (!PermissionUtils.hasNotificationPermission(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_WINDOWS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$windowName is over. Your apps are open again.")
            .setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(9200 + windowName.hashCode() % 100, n) } catch (_: Exception) {}
    }
}
