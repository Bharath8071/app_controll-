package com.bharath.focusguard.data.local.dao

import androidx.room.*
import com.bharath.focusguard.data.local.entities.NotionTask
import kotlinx.coroutines.flow.Flow

@Dao
interface NotionTaskDao {
    @Query("SELECT * FROM notion_tasks ORDER BY lastSyncedAt DESC")
    fun getAll(): Flow<List<NotionTask>>

    @Query("SELECT * FROM notion_tasks ORDER BY lastSyncedAt DESC")
    suspend fun getAllOnce(): List<NotionTask>

    @Upsert
    suspend fun upsertAll(tasks: List<NotionTask>)

    @Query("UPDATE notion_tasks SET isChecked = :checked WHERE notionPageId = :id")
    suspend fun setChecked(id: String, checked: Boolean)
}
