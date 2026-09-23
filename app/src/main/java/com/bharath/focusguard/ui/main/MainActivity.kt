package com.bharath.focusguard.ui.main

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bharath.focusguard.data.local.entities.MonitoredApp
import com.bharath.focusguard.ui.onboarding.OnboardingActivity
import com.bharath.focusguard.util.PermissionUtils

/**
 * Home screen: shows currently monitored apps with today's remaining
 * budget, a "+ add app" button, and a Notion settings entry.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PermissionUtils.hasAccessibilityPermission(this) || !PermissionUtils.hasOverlayPermission(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContent {
            MaterialTheme {
                val apps by viewModel.monitoredApps.collectAsState()
                val token by viewModel.notionToken.collectAsState()
                val databaseId by viewModel.notionDatabaseId.collectAsState()
                MainScreen(
                    apps = apps,
                    installedApps = getInstalledUserApps(),
                    packageManager = packageManager,
                    notionToken = token,
                    notionDatabaseId = databaseId,
                    onAddApp = { info, budget -> viewModel.addApp(info, packageManager, budget) },
                    onUpdateBudget = viewModel::updateBudget,
                    onRemove = viewModel::removeApp,
                    onSaveNotion = viewModel::saveNotion
                )
            }
        }
    }

    private fun getInstalledUserApps(): List<ApplicationInfo> =
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
}

@Composable
fun MainScreen(
    apps: List<MonitoredAppUi>,
    installedApps: List<ApplicationInfo>,
    packageManager: PackageManager,
    notionToken: String,
    notionDatabaseId: String,
    onAddApp: (ApplicationInfo, Int) -> Unit,
    onUpdateBudget: (MonitoredApp, Int) -> Unit,
    onRemove: (MonitoredApp) -> Unit,
    onSaveNotion: (String, String, () -> Unit) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var showNotion by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Monitored apps", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        if (apps.isEmpty()) {
            Text("Instagram and YouTube will appear after first launch seed.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(apps, key = { it.app.packageName }) { row ->
                    MonitoredAppRow(row, onUpdateBudget, onRemove)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Add app to monitor")
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showNotion = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (notionToken.isBlank()) "Connect Notion" else "Notion connected")
        }
    }

    if (showPicker) {
        AppPickerDialog(
            installedApps = installedApps,
            packageManager = packageManager,
            alreadyMonitored = apps.map { it.app.packageName }.toSet(),
            onDismiss = { showPicker = false },
            onPick = { info, budget ->
                onAddApp(info, budget)
                showPicker = false
            }
        )
    }

    if (showNotion) {
        NotionSettingsDialog(
            initialToken = notionToken,
            initialDatabaseId = notionDatabaseId,
            onDismiss = { showNotion = false },
            onSave = { token, dbId ->
                onSaveNotion(token, dbId) { showNotion = false }
            }
        )
    }
}

@Composable
private fun MonitoredAppRow(
    row: MonitoredAppUi,
    onUpdateBudget: (MonitoredApp, Int) -> Unit,
    onRemove: (MonitoredApp) -> Unit
) {
    var budgetText by remember(row.app.packageName, row.app.dailyBudgetMinutes) {
        mutableStateOf(row.app.dailyBudgetMinutes.toString())
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(row.app.displayName, style = MaterialTheme.typography.titleMedium)
            Text("${row.minutesLeft}/${row.app.dailyBudgetMinutes} min left today")
            Text(row.app.packageName, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Daily budget (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    budgetText.toIntOrNull()?.let { onUpdateBudget(row.app, it) }
                }) { Text("Save") }
            }
            TextButton(onClick = { onRemove(row.app) }) { Text("Remove") }
        }
    }
}

@Composable
private fun AppPickerDialog(
    installedApps: List<ApplicationInfo>,
    packageManager: PackageManager,
    alreadyMonitored: Set<String>,
    onDismiss: () -> Unit,
    onPick: (ApplicationInfo, Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, installedApps) {
        installedApps.filter {
            it.loadLabel(packageManager).toString().contains(query, ignoreCase = true) &&
                it.packageName !in alreadyMonitored
        }.take(40)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add app to monitor") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(280.dp)) {
                    items(filtered, key = { it.packageName }) { info ->
                        TextButton(
                            onClick = { onPick(info, 60) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(info.loadLabel(packageManager).toString(), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun NotionSettingsDialog(
    initialToken: String,
    initialDatabaseId: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var token by remember { mutableStateOf(initialToken) }
    var databaseId by remember { mutableStateOf(initialDatabaseId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notion") },
        text = {
            Column {
                Text("Stored on-device via DataStore. Use a Title property and a Done checkbox in the database.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Integration token") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = databaseId,
                    onValueChange = { databaseId = it },
                    label = { Text("Database ID") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(token, databaseId) }) { Text("Save & sync") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
