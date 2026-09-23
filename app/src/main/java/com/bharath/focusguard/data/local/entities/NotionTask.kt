package com.bharath.focusguard.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached copy of a task pulled from the Notion database. */
@Entity(tableName = "notion_tasks")
data class NotionTask(
    @PrimaryKey val notionPageId: String,
    val title: String,
    val isChecked: Boolean,
    val lastSyncedAt: Long
)
