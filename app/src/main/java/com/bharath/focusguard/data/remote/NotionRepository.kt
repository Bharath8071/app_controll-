package com.bharath.focusguard.data.remote

import com.bharath.focusguard.data.local.dao.NotionTaskDao
import com.bharath.focusguard.data.local.entities.NotionTask

class NotionRepository(
    private val api: NotionApiService,
    private val dao: NotionTaskDao,
    private val databaseId: String
) {
    suspend fun syncTasksFromNotion() {
        try {
            val response = api.queryDatabase(databaseId)
            val tasks = response.results.map { page ->
                NotionTask(
                    notionPageId = page.id,
                    title = extractTitle(page),
                    isChecked = extractChecked(page),
                    lastSyncedAt = System.currentTimeMillis()
                )
            }
            dao.upsertAll(tasks)
        } catch (e: Exception) {
            // Notion unreachable — checklist overlay just shows last cached state.
        }
    }

    suspend fun setTaskChecked(task: NotionTask, checked: Boolean) {
        dao.setChecked(task.notionPageId, checked)
        try {
            api.updatePageCheckbox(
                task.notionPageId,
                NotionCheckboxUpdate(properties = mapOf("Done" to mapOf("checkbox" to checked)))
            )
        } catch (e: Exception) {
            // Queue for retry next sync if offline
        }
    }

    /**
     * Expects a Notion database with a title property ("Title" or "Name")
     * and a checkbox property named "Done".
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractTitle(page: NotionPage): String {
        val props = page.properties
        val titleProp = props["Title"] ?: props["Name"] ?: props.values.firstOrNull { value ->
            (value as? Map<*, *>)?.get("type") == "title"
        }
        val map = titleProp as? Map<*, *> ?: return "Untitled"
        val titleList = map["title"] as? List<*> ?: return "Untitled"
        val first = titleList.firstOrNull() as? Map<*, *> ?: return "Untitled"
        val plain = first["plain_text"] as? String
        if (!plain.isNullOrBlank()) return plain
        val text = first["text"] as? Map<*, *>
        return (text?.get("content") as? String)?.ifBlank { "Untitled" } ?: "Untitled"
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractChecked(page: NotionPage): Boolean {
        val props = page.properties
        val doneProp = props["Done"] ?: props.values.firstOrNull { value ->
            (value as? Map<*, *>)?.get("type") == "checkbox"
        }
        val map = doneProp as? Map<*, *> ?: return false
        return map["checkbox"] as? Boolean ?: false
    }
}
