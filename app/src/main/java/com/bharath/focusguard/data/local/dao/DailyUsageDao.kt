package com.bharath.focusguard.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.bharath.focusguard.data.local.entities.DailyUsage

@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_usage WHERE packageName = :pkg AND date = :date LIMIT 1")
    suspend fun get(pkg: String, date: String): DailyUsage?

    @Query("SELECT * FROM daily_usage WHERE date = :date")
    fun observeForDate(date: String): Flow<List<DailyUsage>>

    @Upsert
    suspend fun upsert(usage: DailyUsage)

    @Query("UPDATE daily_usage SET secondsUsedToday = secondsUsedToday + :seconds, minutesUsedToday = (secondsUsedToday + :seconds) / 60 WHERE packageName = :pkg AND date = :date")
    suspend fun addSeconds(pkg: String, date: String, seconds: Int)

    @Query("UPDATE daily_usage SET minutesUsedToday = minutesUsedToday + :minutes, secondsUsedToday = secondsUsedToday + (:minutes * 60) WHERE packageName = :pkg AND date = :date")
    suspend fun addMinutes(pkg: String, date: String, minutes: Int)

    @Query("UPDATE daily_usage SET extendUsedToday = 1 WHERE packageName = :pkg AND date = :date")
    suspend fun markExtendUsed(pkg: String, date: String)

    @Query("DELETE FROM daily_usage WHERE date != :today")
    suspend fun clearOldDays(today: String)
}
