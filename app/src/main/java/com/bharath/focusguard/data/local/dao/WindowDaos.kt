package com.bharath.focusguard.data.local.dao

import androidx.room.*
import com.bharath.focusguard.data.local.entities.AppDaily
import com.bharath.focusguard.data.local.entities.BlockWindow
import com.bharath.focusguard.data.local.entities.DailySummary
import com.bharath.focusguard.data.local.entities.WindowLog
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockWindowDao {
    @Query("SELECT * FROM block_windows ORDER BY startMinute")
    fun observeAll(): Flow<List<BlockWindow>>
    @Query("SELECT * FROM block_windows WHERE enabled = 1")
    suspend fun getEnabledOnce(): List<BlockWindow>
    @Query("SELECT * FROM block_windows WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BlockWindow?
    @Upsert suspend fun upsert(w: BlockWindow): Long
    @Query("DELETE FROM block_windows WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface WindowLogDao {
    @Query("SELECT * FROM window_log WHERE windowId = :windowId AND dateEpochDay = :day LIMIT 1")
    suspend fun get(windowId: Long, day: Long): WindowLog?
    @Upsert suspend fun upsert(log: WindowLog)
}

@Dao
interface DailySummaryDao {
    @Query("SELECT * FROM daily_summary ORDER BY dateEpochDay DESC LIMIT :n")
    suspend fun recent(n: Int): List<DailySummary>
    @Query("SELECT * FROM daily_summary WHERE dateEpochDay = :day LIMIT 1")
    suspend fun get(day: Long): DailySummary?
    @Upsert suspend fun upsert(s: DailySummary)
}

@Dao
interface AppDailyDao {
    @Query("SELECT * FROM app_daily WHERE dateEpochDay >= :fromDay ORDER BY dateEpochDay DESC")
    suspend fun since(fromDay: Long): List<AppDaily>
    @Query("UPDATE app_daily SET minutes = minutes + :m WHERE dateEpochDay = :day AND packageName = :pkg")
    suspend fun addMinutes(day: Long, pkg: String, m: Int): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: AppDaily)
}
