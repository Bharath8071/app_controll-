package com.bharath.focusguard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.bharath.focusguard.R
import com.bharath.focusguard.data.local.AppDatabase
import com.bharath.focusguard.util.WindowLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Phase 8: 2x1 widget — Block now button + static status line. No live countdown. */
class FocusGuardWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val wins = try { db.blockWindowDao().getEnabledOnce() } catch (_: Exception) { emptyList() }
            val active = WindowLogic.activeWindow(LocalDateTime.now(), wins)
            val status = if (active != null) "Block on" else "Ready"
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_focusguard)
                views.setTextViewText(R.id.widget_status, status)
                val pi = PendingIntent.getActivity(context, 0,
                    Intent(context, BlockNowActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                views.setOnClickPendingIntent(R.id.widget_block_now, pi)
                mgr.updateAppWidget(id, views)
            }
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, FocusGuardWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    context.sendBroadcast(Intent(context, FocusGuardWidgetProvider::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
                }
            } catch (_: Exception) {}
        }
    }
}
