package com.bharath.focusguard.data.local.dao

import androidx.room.*
import com.bharath.focusguard.data.local.entities.NotionTask
import kotlinx.coroutines.flow.Flow

@Dao
interface NotionTaskDao {
    @Query("SELECT * FROM notion_tasks ORDER BY lastSyncedAt DESC")
    fun getAll(): Flow<List<NotionTask>>

    @Query("SELECT * FROM notion_tasks ORDER BY isChecked ASC, priorityRank ASC, lastSyncedAt DESC LIMIT 3")
    suspend fun getTopPriorityOnce(): List<NotionTask>

    @Query("SELECT * FROM notion_tasks ORDER BY isChecked ASC, priorityRank ASC, lastSyncedAt DESC LIMIT 3")
    suspend fun getAllOnce(): List<NotionTask>

    @Upsert
    suspend fun upsertAll(tasks: List<NotionTask>)

    @Query("UPDATE notion_tasks SET isChecked = :checked WHERE notionPageId = :id")
    suspend fun setChecked(id: String, checked: Boolean)

    @Query("DELETE FROM notion_tasks")
    suspend fun clearAll()

    /**
     * BUG-015 fix: Atomically replace all tasks in a single transaction.
     * Prevents the checklist cache from being permanently empty if the process
     * is killed between clearAll() and upsertAll().
     */
    @Transaction
    suspend fun replaceAll(tasks: List<NotionTask>) {
        clearAll()
        upsertAll(tasks)
    }
}
