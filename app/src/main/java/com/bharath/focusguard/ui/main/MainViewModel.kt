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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

    /**
     * Live state of the App Controller master switch.
     * Defaults to true (enabled). When disabled, the accessibility service skips all blocking.
     * Auto-re-enables at midnight (day rollover detected by the service's 1-second ticker).
     */
    val controllerEnabled: StateFlow<Boolean> =
        prefs.controllerEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * Toggles the controller on/off.
     * When turning OFF â†’ records today's date so the service auto-re-enables after midnight.
     * When turning ON  â†’ clears the disabled-date immediately.
     */
    fun setControllerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                prefs.enableController()
            } else {
                prefs.disableController(today())
            }
        }
    }

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

    // â”€â”€ Phase 3 + 5: windows, summaries, streak â”€â”€
    val windows: StateFlow<List<com.bharath.focusguard.data.local.entities.BlockWindow>> =
        db.blockWindowDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val summariesFlow = kotlinx.coroutines.flow.MutableStateFlow<List<com.bharath.focusguard.data.local.entities.DailySummary>>(emptyList())
    val streakDays: StateFlow<Int> = summariesFlow.combine(db.monitoredAppDao().getAllApps()) { sums, _ ->
        com.bharath.focusguard.util.WindowLogic.streak(sums)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var weeklyAppMinutes by androidx.compose.runtime.mutableStateOf<List<com.bharath.focusguard.data.local.entities.AppDaily>>(emptyList())
        private set

    // â”€â”€ Phase 6: real phone-wide usage (optional permission; never queried when denied) â”€â”€
    var usageTop by androidx.compose.runtime.mutableStateOf<List<Pair<String, Long>>>(emptyList())
        private set

    fun loadUsageCard(app: android.app.Application, packages: Set<String>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val rows = com.bharath.focusguard.util.UsageStatsHelper.queryLast7Days(app, packages)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { usageTop = rows }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val today = java.time.LocalDate.now()
            summariesFlow.value = db.dailySummaryDao().recent(30)
            weeklyAppMinutes = db.appDailyDao().since(today.minusDays(6).toEpochDay())
        }
    }

    fun saveWindow(w: com.bharath.focusguard.data.local.entities.BlockWindow, onDone: () -> Unit = {}) {
        viewModelScope.launch { db.blockWindowDao().upsert(w); onDone() }
    }
    fun deleteWindow(id: Long) { viewModelScope.launch { db.blockWindowDao().deleteById(id) } }

    fun blockNow(minutes: Int) {
        viewModelScope.launch {
            val until = System.currentTimeMillis() + minutes * 60_000L
            db.blockWindowDao().upsert(com.bharath.focusguard.data.local.entities.BlockWindow(
                name = "Focus block", daysMask = 127, startMinute = 0, endMinute = 0,
                enabled = true, oneOffUntilEpochMs = until))
        }
    }

    private fun today(): String =
        // BUG-022 fix: Locale.US ensures consistent yyyy-MM-dd keys regardless of device locale
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
