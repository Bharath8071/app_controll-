package com.bharath.focusguard.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    /**
     * Whether the app controller (blocking) is currently enabled.
     * Defaults to true (always on). When disabled manually, it stores the date the user
     * disabled it so the service can auto-re-enable it at midnight.
     */
    val controllerEnabled: Flow<Boolean> = context.dataStore.data.map { it[CONTROLLER_ENABLED] ?: true }

    /**
     * The yyyy-MM-dd date string on which the controller was disabled.
     * Used by the service to detect a day rollover and auto-re-enable the controller.
     * Empty string means the controller has never been manually disabled.
     */
    val disabledUntilDate: Flow<String> = context.dataStore.data.map { it[DISABLED_UNTIL_DATE] ?: "" }

    suspend fun getToken(): String = context.dataStore.data.first()[TOKEN] ?: ""
    suspend fun getDatabaseId(): String = context.dataStore.data.first()[DATABASE_ID] ?: ""

    suspend fun setNotionCredentials(token: String, databaseId: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN] = token.trim()
            prefs[DATABASE_ID] = databaseId.trim()
        }
    }

    /** Disable the controller until midnight. Records today's date so the service can auto-re-enable at midnight. */
    suspend fun disableController(todayDate: String) {
        context.dataStore.edit { prefs ->
            prefs[CONTROLLER_ENABLED] = false
            prefs[DISABLED_UNTIL_DATE] = todayDate
        }
    }

    /** Enable (or re-enable) the controller and clear the disabled-until date. */
    suspend fun enableController() {
        context.dataStore.edit { prefs ->
            prefs[CONTROLLER_ENABLED] = true
            prefs[DISABLED_UNTIL_DATE] = ""
        }
    }

    companion object {
        private val TOKEN = stringPreferencesKey("notion_token")
        private val DATABASE_ID = stringPreferencesKey("notion_database_id")
        private val CONTROLLER_ENABLED = booleanPreferencesKey("controller_enabled")
        private val DISABLED_UNTIL_DATE = stringPreferencesKey("disabled_until_date")
    }
}
