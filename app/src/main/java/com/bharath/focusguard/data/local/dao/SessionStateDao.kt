package com.bharath.focusguard.data.local.dao

import androidx.room.*
import com.bharath.focusguard.data.local.entities.SessionState

@Dao
interface SessionStateDao {
    @Query("SELECT * FROM session_state WHERE packageName = :pkg AND isActive = 1 LIMIT 1")
    suspend fun getActive(pkg: String): SessionState?

    @Upsert
    suspend fun upsert(session: SessionState)

    @Query("UPDATE session_state SET isActive = 0 WHERE packageName = :pkg")
    suspend fun endSession(pkg: String)
}
