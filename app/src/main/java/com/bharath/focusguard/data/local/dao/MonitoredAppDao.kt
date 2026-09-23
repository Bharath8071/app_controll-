package com.bharath.focusguard.data.local.dao

import androidx.room.*
import com.bharath.focusguard.data.local.entities.MonitoredApp
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_apps WHERE isEnabled = 1")
    fun getEnabledApps(): Flow<List<MonitoredApp>>

    @Query("SELECT * FROM monitored_apps")
    fun getAllApps(): Flow<List<MonitoredApp>>

    @Query("SELECT * FROM monitored_apps WHERE packageName = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): MonitoredApp?

    @Upsert
    suspend fun upsert(app: MonitoredApp)

    @Delete
    suspend fun delete(app: MonitoredApp)
}
