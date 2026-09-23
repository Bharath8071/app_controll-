package com.bharath.focusguard.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per app the user has chosen to gate. */
@Entity(tableName = "monitored_apps")
data class MonitoredApp(
    @PrimaryKey val packageName: String,   // e.g. "com.instagram.android"
    val displayName: String,               // e.g. "Instagram"
    val dailyBudgetMinutes: Int,           // e.g. 60
    val isEnabled: Boolean = true
)
