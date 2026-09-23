package com.bharath.focusguard.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "focusguard_prefs")

class UserPreferences(private val context: Context) {
    val notionToken: Flow<String> = context.dataStore.data.map { it[TOKEN] ?: "" }
    val notionDatabaseId: Flow<String> = context.dataStore.data.map { it[DATABASE_ID] ?: "" }

    suspend fun getToken(): String = context.dataStore.data.first()[TOKEN] ?: ""
    suspend fun getDatabaseId(): String = context.dataStore.data.first()[DATABASE_ID] ?: ""

    suspend fun setNotionCredentials(token: String, databaseId: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN] = token.trim()
            prefs[DATABASE_ID] = databaseId.trim()
        }
    }

    companion object {
        private val TOKEN = stringPreferencesKey("notion_token")
        private val DATABASE_ID = stringPreferencesKey("notion_database_id")
    }
}
