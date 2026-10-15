package com.bharath.focusguard.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_windows")
data class BlockWindow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val daysMask: Int,              // bit 0 = Monday ... bit 6 = Sunday
    val startMinute: Int,           // minutes after midnight, 0..1439
    val endMinute: Int,             // may be < startMinute for overnight windows
    val enabled: Boolean = true,
    val oneOffUntilEpochMs: Long? = null   // set for "Block now"; null for recurring
)

@Entity(tableName = "window_log")
data class WindowLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val windowId: Long,
    val dateEpochDay: Long,
    val endedEarly: Boolean = false,
    val breaksUsed: Int = 0,
    val breakUntilEpochMs: Long? = null
)

@Entity(tableName = "daily_summary", primaryKeys = ["dateEpochDay"])
data class DailySummary(
    val dateEpochDay: Long,
    val budgetMinutes: Int = 0,
    val usedMinutes: Int = 0,
    val emergencyPassUsed: Boolean = false,
    val windowsCompleted: Int = 0,
    val windowsEndedEarly: Int = 0
)

@Entity(tableName = "app_daily", primaryKeys = ["dateEpochDay", "packageName"])
data class AppDaily(
    val dateEpochDay: Long,
    val packageName: String,
    val minutes: Int = 0
)
