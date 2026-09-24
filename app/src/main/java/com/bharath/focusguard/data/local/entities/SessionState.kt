package com.bharath.focusguard.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * At most one active row per app at a time. Created when the user picks a
 * session length on the time picker overlay; ended when the Accessibility
 * Service sees the app leave the foreground, or when the countdown expires
 * while the app is still in front.
 */
@Entity(tableName = "session_state")
data class SessionState(
    @PrimaryKey val packageName: String,
    val sessionStartTimeMillis: Long,
    val sessionLengthMinutes: Int,
    val sessionExpiresAtMillis: Long = sessionStartTimeMillis + (sessionLengthMinutes * 60_000L),
    val lastResumedAtMillis: Long = sessionStartTimeMillis,
    val isActive: Boolean = true
)
