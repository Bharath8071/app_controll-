package com.bharath.focusguard.data.remote

import com.bharath.focusguard.data.local.dao.NotionTaskDao
import com.bharath.focusguard.data.local.entities.NotionTask

class NotionRepository(
    private val api: NotionApiService,
    private val dao: NotionTaskDao,
    private val databaseId: String
) {
    /**
     * Fetches incomplete tasks from Notion, sorts them by Priority (High > Medium > Low),
     * and caches the top 3 highest priority tasks in local Room database.
     */
    suspend fun syncTasksFromNotion() {
        try {
            val response = api.queryDatabase(databaseId)
            val incompletePages = response.results.filter { !isTaskCompleted(it) }
            val sortedPages = incompletePages.sortedBy { extractPriorityRank(it) }
            val topTasks = sortedPages.take(3).map { page ->
                NotionTask(
                    notionPageId = page.id,
                    title = extractTitle(page),
                    isChecked = false,
                    lastSyncedAt = System.currentTimeMillis(),
                    priorityRank = extractPriorityRank(page)
                )
            }
            dao.clearAll()
            dao.upsertAll(topTasks)
        } catch (e: Exception) {
            // Notion unreachable — checklist overlay shows last cached state.
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
     * Checks if task is already completed (Status == "Done"/"Completed" OR any checkbox property is true).
     * Matches the logic in the Chrome extension.
     */
    @Suppress("UNCHECKED_CAST")
    private fun isTaskCompleted(page: NotionPage): Boolean {
        val props = page.properties

        // Priority 1: Check Status property
        for (prop in props.values) {
            val map = prop as? Map<*, *> ?: continue
            if (map["type"] == "status") {
                val statusMap = map["status"] as? Map<*, *>
                val name = statusMap?.get("name") as? String ?: ""
                if (name.equals("Done", ignoreCase = true) || name.equals("Completed", ignoreCase = true)) {
                    return true
                }
            }
        }

        // Priority 2: Check Checkbox properties
        for (prop in props.values) {
            val map = prop as? Map<*, *> ?: continue
            if (map["type"] == "checkbox" && map["checkbox"] == true) {
                return true
            }
        }

        return false
    }

    /**
     * Extracts priority ranking:
     * 1 = Urgent / High / P1
     * 2 = Medium / P2
     * 3 = Low / P3
     * 4 = Unspecified
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractPriorityRank(page: NotionPage): Int {
        val props = page.properties
        val priorityProp = props["Priority"] ?: props["Urgency"] ?: props.entries.firstOrNull {
            it.key.contains("priority", ignoreCase = true) || it.key.contains("urgency", ignoreCase = true)
        }?.value

        val map = priorityProp as? Map<*, *> ?: return 4
        val selectMap = map["select"] as? Map<*, *>
        val statusMap = map["status"] as? Map<*, *>
        val name = (selectMap?.get("name") as? String)
            ?: (statusMap?.get("name") as? String)
            ?: return 4

        return when {
            name.contains("urgent", ignoreCase = true) ||
            name.contains("high", ignoreCase = true) ||
            name.equals("p1", ignoreCase = true) -> 1

            name.contains("medium", ignoreCase = true) ||
            name.equals("p2", ignoreCase = true) -> 2

            name.contains("low", ignoreCase = true) ||
            name.equals("p3", ignoreCase = true) -> 3

            else -> 4
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractTitle(page: NotionPage): String {
        val props = page.properties
        val titleProp = props["Title"] ?: props["Name"] ?: props.values.firstOrNull { value ->
            (value as? Map<*, *>)?.get("type") == "title"
        }
        val map = titleProp as? Map<*, *> ?: return "Untitled Task"
        val titleList = map["title"] as? List<*> ?: return "Untitled Task"
        val first = titleList.firstOrNull() as? Map<*, *> ?: return "Untitled Task"
        val plain = first["plain_text"] as? String
        if (!plain.isNullOrBlank()) return plain
        val text = first["text"] as? Map<*, *>
        return (text?.get("content") as? String)?.ifBlank { "Untitled Task" } ?: "Untitled Task"
    }
}
