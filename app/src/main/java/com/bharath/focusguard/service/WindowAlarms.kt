package com.bharath.focusguard.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bharath.focusguard.data.local.AppDatabase
import com.bharath.focusguard.util.NotificationHelper
import com.bharath.focusguard.util.WindowLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Phase 4: schedules window start/end alerts; each fires once via AlarmManager. */
object WindowAlarmScheduler {
    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val wins = db.blockWindowDao().getEnabledOnce()
                val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@launch
                val now = LocalDateTime.now()
                // "Starts soon" alert 5 min before next start
                val next = WindowLogic.nextWindowStart(now, wins) ?: return@launch
                val triggerAt = next.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 5 * 60_000L
                if (triggerAt > System.currentTimeMillis()) {
                    val pi = PendingIntent.getBroadcast(
                        context, 7001,
                        Intent(context, WindowAlertReceiver::class.java).putExtra("kind", "soon"),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    // Fix-API31: setExactAndAllowWhileIdle requires SCHEDULE_EXACT_ALARM on API 31+.
                    // canScheduleExactAlarms() guards the call; fall back to setAndAllowWhileIdle
                    // (inexact, ±few minutes) so the notification still fires on restricted devices.
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (am.canScheduleExactAlarms()) {
                                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                            } else {
                                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                            }
                        } else {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                        }
                    } catch (_: Exception) {
                        // Last-resort fallback — inexact but won't crash
                        try { am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi) } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }
}

class WindowAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val wins = db.blockWindowDao().getEnabledOnce()
                val next = WindowLogic.nextWindowStart(LocalDateTime.now(), wins)
                if (intent.getStringExtra("kind") == "soon" && next != null) {
                    val match = wins.minByOrNull { w ->
                        val s = WindowLogic.nextWindowStart(LocalDateTime.now(), listOf(w))
                        s?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
                    }
                    NotificationHelper.showBlockStartingSoon(context, match?.name?.ifBlank { "Block" } ?: "Block")
                }
            } catch (_: Exception) {}
        }
    }
}
