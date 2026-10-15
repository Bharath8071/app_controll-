package com.bharath.focusguard.util

import android.app.usage.UsageStatsManager
import android.content.Context

object UsageStatsHelper {
    /** Minutes in monitored packages per day for the last 7 days (phone-wide real usage). */
    fun queryLast7Days(context: Context, packages: Set<String>): List<Pair<String, Long>> {
        if (!PermissionUtils.hasUsageAccess(context)) return emptyList()
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
            val end = System.currentTimeMillis()
            val start = end - 7 * 24 * 60_000L
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: return emptyList()
            bucketize(stats.map { it.packageName to it.totalTimeInForeground }, packages)
        } catch (_: Exception) { emptyList() }
    }

    /** Pure: sum foreground millis per day-key... simplified to per-package totals for testability. */
    fun bucketize(rows: List<Pair<String, Long>>, packages: Set<String>): List<Pair<String, Long>> {
        val map = mutableMapOf<String, Long>()
        for ((pkg, ms) in rows) {
            if (pkg in packages) map[pkg] = (map[pkg] ?: 0L) + ms
        }
        return map.toList().sortedByDescending { it.second }
    }
}
