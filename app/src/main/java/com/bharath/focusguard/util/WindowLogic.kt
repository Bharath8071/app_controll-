package com.bharath.focusguard.util

import com.bharath.focusguard.data.local.entities.BlockWindow
import com.bharath.focusguard.data.local.entities.DailySummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object WindowLogic {
    /** bit 0 = Monday ... bit 6 = Sunday */
    fun dayBit(day: DayOfWeek): Int = 1 shl ((day.value - 1) % 7)

    fun isWindowActive(now: LocalDateTime, window: BlockWindow): Boolean {
        if (!window.enabled) return false
        if (window.oneOffUntilEpochMs != null) {
            val until = java.time.Instant.ofEpochMilli(window.oneOffUntilEpochMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
            // One-off: active if now is after start today... simplified: active until expiry if created today.
            // Store start as startMinute today; active when now < until and now >= start.
            return !now.isAfter(until)
        }
        val start = window.startMinute; val end = window.endMinute
        if (start == end) return false
        val nowMin = now.hour * 60 + now.minute
        return if (end > start) {
            dayBit(now.dayOfWeek) and window.daysMask != 0 && nowMin in start until end
        } else {
            // Overnight: active late on start day, or early on next day (mask of start day counts)
            val prevDay = now.minusDays(1)
            (dayBit(now.dayOfWeek) and window.daysMask != 0 && nowMin >= start) ||
                (dayBit(prevDay.dayOfWeek) and window.daysMask != 0 && nowMin < end)
        }
    }

    fun activeWindow(now: LocalDateTime, windows: List<BlockWindow>): BlockWindow? =
        windows.firstOrNull { isWindowActive(now, it) }

    fun nextWindowStart(now: LocalDateTime, windows: List<BlockWindow>): LocalDateTime? {
        var best: LocalDateTime? = null
        for (w in windows) {
            if (!w.enabled || w.oneOffUntilEpochMs != null) continue
            for (d in 0..7) {
                val day = now.toLocalDate().plusDays(d.toLong())
                if (dayBit(day.dayOfWeek) and w.daysMask == 0) continue
                val cand = day.atTime(w.startMinute / 60, w.startMinute % 60)
                if (cand.isAfter(now) && (best == null || cand.isBefore(best))) best = cand
            }
        }
        return best
    }

    /** A day counts if no emergency pass and no window ended early. */
    fun streak(summaries: List<DailySummary>): Int {
        val sorted = summaries.sortedByDescending { it.dateEpochDay }
        var s = 0
        var expected = LocalDate.now().toEpochDay()
        for (row in sorted) {
            if (row.dateEpochDay > expected) continue
            if (row.dateEpochDay < expected) break
            if (row.emergencyPassUsed || row.windowsEndedEarly > 0) break
            s++; expected--
        }
        return s
    }

    fun domainMatches(pattern: String, host: String): Boolean {
        val p = pattern.trim().lowercase().removePrefix("http://").removePrefix("https://").substringBefore("/").removePrefix("www.")
        val h = host.trim().lowercase().substringBefore(":").removePrefix("www.")
        return h == p || h.endsWith(".$p")
    }
}
