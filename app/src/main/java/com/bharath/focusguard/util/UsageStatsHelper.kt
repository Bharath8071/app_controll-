package com.bharath.focusguard.util

import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object UsageStatsHelper {
    /** Per-day, per-package real usage used by the analytics chart. */
    data class DayUsage(
        val epochDay: Long,
        val packageName: String,
        val minutes: Int
    )

    /** Minutes in monitored packages per day for the last 7 days (phone-wide real usage). */
    fun queryLast7Days(context: Context, packages: Set<String>): List<Pair<String, Long>> {
        if (packages.isEmpty() || !PermissionUtils.hasUsageAccess(context)) return emptyList()
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
            val end = System.currentTimeMillis()
            // BUG: this used to be 7 * 24 * 60_000 (2.8 hours), so almost all
            // weekly usage was dropped from the card.
            val start = end - TimeUnit.DAYS.toMillis(7)
            // Aggregate over the exact range; queryUsageStats(INTERVAL_DAILY)
            // returns whole daily buckets, including minutes outside the range.
            val stats = usm.queryAndAggregateUsageStats(start, end) ?: return emptyList()
            bucketize(stats.values.map { it.packageName to it.totalTimeInForeground }, packages)
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Same 7-day window, but split into calendar days and mapped to epoch days
     * so it can feed the analytics bar chart. Returns an empty list when the
     * Usage Access permission is missing.
     */
    fun queryLast7DaysPerDay(context: Context, packages: Set<String>): List<DayUsage> {
        if (packages.isEmpty() || !PermissionUtils.hasUsageAccess(context)) return emptyList()
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val firstDay = today.minusDays(6)
            val start = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: return emptyList()

            // A device can return more than one bucket for a day (timezone
            // changes, reboots), so sum everything that maps to the same date.
            val totals = mutableMapOf<Pair<Long, String>, Long>()
            for (s in stats) {
                if (s.packageName !in packages || s.totalTimeInForeground <= 0L) continue
                val day = Instant.ofEpochMilli(s.firstTimeStamp).atZone(zone).toLocalDate()
                if (day.isBefore(firstDay) || day.isAfter(today)) continue
                val key = day.toEpochDay() to s.packageName
                totals[key] = (totals[key] ?: 0L) + s.totalTimeInForeground
            }
            totals.map { (key, millis) ->
                DayUsage(key.first, key.second, (millis / 60_000L).toInt())
            }.filter { it.minutes > 0 }
        } catch (_: Exception) { emptyList() }
    }

    /** Pure: sum foreground millis per package for testability. */
    fun bucketize(rows: List<Pair<String, Long>>, packages: Set<String>): List<Pair<String, Long>> {
        val map = mutableMapOf<String, Long>()
        for ((pkg, ms) in rows) {
            if (pkg in packages) map[pkg] = (map[pkg] ?: 0L) + ms
        }
        return map.toList().sortedByDescending { it.second }
    }
}
