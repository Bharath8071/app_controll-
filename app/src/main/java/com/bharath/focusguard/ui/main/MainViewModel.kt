package com.bharath.focusguard.ui.main

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bharath.focusguard.data.local.AppDatabase
import com.bharath.focusguard.data.local.entities.MonitoredApp
import com.bharath.focusguard.data.prefs.UserPreferences
import com.bharath.focusguard.data.remote.NotionClient
import com.bharath.focusguard.data.remote.NotionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MonitoredAppUi(
    val app: MonitoredApp,
    val minutesUsedToday: Int
) {
    val minutesLeft: Int get() = (app.dailyBudgetMinutes - minutesUsedToday).coerceAtLeast(0)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val prefs = UserPreferences(application)

    val monitoredApps: StateFlow<List<MonitoredAppUi>> = combine(
        db.monitoredAppDao().getAllApps(),
        db.dailyUsageDao().observeForDate(today())
    ) { apps, usage ->
        val usageMap = usage.associateBy { it.packageName }
        apps.map { app ->
            MonitoredAppUi(app, usageMap[app.packageName]?.minutesUsedToday ?: 0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notionToken = prefs.notionToken.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val notionDatabaseId = prefs.notionDatabaseId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun addApp(info: ApplicationInfo, pm: PackageManager, budgetMinutes: Int = 60) {
        viewModelScope.launch {
            db.monitoredAppDao().upsert(
                MonitoredApp(
                    packageName = info.packageName,
                    displayName = info.loadLabel(pm).toString(),
                    dailyBudgetMinutes = budgetMinutes,
                    isEnabled = true
                )
            )
        }
    }

    fun updateBudget(app: MonitoredApp, minutes: Int) {
        viewModelScope.launch {
            db.monitoredAppDao().upsert(app.copy(dailyBudgetMinutes = minutes.coerceAtLeast(1)))
        }
    }

    fun removeApp(app: MonitoredApp) {
        viewModelScope.launch { db.monitoredAppDao().delete(app) }
    }

    fun saveNotion(token: String, databaseId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.setNotionCredentials(token, databaseId)
            if (token.isNotBlank() && databaseId.isNotBlank()) {
                NotionRepository(
                    api = NotionClient.create(token),
                    dao = db.notionTaskDao(),
                    databaseId = databaseId
                ).syncTasksFromNotion()
            }
            onDone()
        }
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
