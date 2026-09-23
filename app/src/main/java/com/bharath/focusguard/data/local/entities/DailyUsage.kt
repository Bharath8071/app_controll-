package com.bharath.focusguard.data.local.entities

import androidx.room.Entity

/** Composite key of (packageName, date). One row per app per day. */
@Entity(tableName = "daily_usage", primaryKeys = ["packageName", "date"])
data class DailyUsage(
    val packageName: String,
    val date: String,               // "yyyy-MM-dd", local device date
    val minutesUsedToday: Int = 0,
    val extendUsedToday: Boolean = false  // the one-time +5 min emergency extend
)
