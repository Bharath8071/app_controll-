package com.bharath.focusguard.ui.main

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharath.focusguard.data.local.entities.MonitoredApp
import com.bharath.focusguard.ui.onboarding.OnboardingActivity
import com.bharath.focusguard.util.PermissionUtils

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
            FocusGuardTheme {
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
fun FocusGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF0B0F19),
            surface = Color(0xFF1E293B),
            primary = Color(0xFF6366F1),
            secondary = Color(0xFF10B981)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var editingApp by remember { mutableStateOf<MonitoredApp?>(null) }

    val totalBudget = apps.sumOf { it.app.dailyBudgetMinutes }
    val totalUsed = apps.sumOf { it.minutesUsedToday }

    Scaffold(
        containerColor = Color(0xFF0B0F19),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛡️", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "FocusGuard",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    // Shield status pill
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Shield Active",
                                color = Color(0xFF34D399),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0F19))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Summary Dashboard Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "TODAY'S USAGE OVERVIEW",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                "${totalUsed}m used",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "of ${totalBudget}m total budget",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF6366F1).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "${apps.size} gated apps",
                                color = Color(0xFFA5B4FC),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val progress = if (totalBudget > 0) (totalUsed.toFloat() / totalBudget).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            progress >= 1f -> Color(0xFFF43F5E)
                            progress >= 0.7f -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
                        },
                        trackColor = Color(0xFF0F172A)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Monitored Applications",
                    color = Color(0xFFE2E8F0),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))

            if (apps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No apps monitored yet.\nTap below to add an app.",
                        color = Color(0xFF64748B),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(apps, key = { it.app.packageName }) { row ->
                        MonitoredAppCard(
                            row = row,
                            packageManager = packageManager,
                            onEditBudget = { editingApp = row.app },
                            onRemove = { onRemove(row.app) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action Buttons
            Button(
                onClick = { showPicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                )
            ) {
                Text("+ Add App to Gate", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showNotion = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (notionToken.isNotBlank()) Color(0xFF10B981) else Color(0xFF64748B),
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (notionToken.isBlank()) "Connect Notion Database" else "Notion Connected ✓",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
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

    editingApp?.let { app ->
        EditBudgetDialog(
            app = app,
            onDismiss = { editingApp = null },
            onSave = { minutes ->
                onUpdateBudget(app, minutes)
                editingApp = null
            }
        )
    }
}

@Composable
fun MonitoredAppCard(
    row: MonitoredAppUi,
    packageManager: PackageManager,
    onEditBudget: () -> Unit,
    onRemove: () -> Unit
) {
    val iconBitmap = remember(row.app.packageName) {
        try {
            val drawable = packageManager.getApplicationIcon(row.app.packageName)
            drawableToBitmap(drawable)
        } catch (e: Exception) {
            null
        }
    }

    val progress = if (row.app.dailyBudgetMinutes > 0) {
        (row.minutesUsedToday.toFloat() / row.app.dailyBudgetMinutes).coerceIn(0f, 1f)
    } else 0f

    val isExhausted = row.minutesLeft <= 0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap.asImageBitmap(),
                        contentDescription = row.app.displayName,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF334155), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(row.app.displayName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.app.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        "${row.minutesUsedToday}m / ${row.app.dailyBudgetMinutes}m used",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                // Status pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isExhausted) Color(0xFFF43F5E).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(
                        1.dp,
                        if (isExhausted) Color(0xFFF43F5E).copy(alpha = 0.4f) else Color(0xFF10B981).copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = if (isExhausted) "🔒 Locked" else "${row.minutesLeft}m left",
                        color = if (isExhausted) Color(0xFFFB7185) else Color(0xFF34D399),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when {
                    progress >= 1f -> Color(0xFFF43F5E)
                    progress >= 0.7f -> Color(0xFFF59E0B)
                    else -> Color(0xFF10B981)
                },
                trackColor = Color(0xFF0F172A)
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onEditBudget,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFA5B4FC))
                ) {
                    Text("Edit Budget", fontSize = 13.sp)
                }
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF43F5E).copy(alpha = 0.8f))
                ) {
                    Text("Remove", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun EditBudgetDialog(
    app: MonitoredApp,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var text by remember { mutableStateOf(app.dailyBudgetMinutes.toString()) }
    val presets = listOf(30, 45, 60, 90, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Text("Daily Limit for ${app.displayName}", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Select a daily budget or enter a custom amount in minutes:",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.take(4).forEach { preset ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { text = preset.toString() },
                            shape = RoundedCornerShape(8.dp),
                            color = if (text == preset.toString()) Color(0xFF6366F1) else Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text("${preset}m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    label = { Text("Budget (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { text.toIntOrNull()?.let { onSave(it) } },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Text("Save Limit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) }
        }
    )
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
        }.take(30)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = { Text("Select App to Gate", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search installed apps") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.packageName }) { info ->
                        val iconBitmap = remember(info.packageName) {
                            try {
                                val drawable = info.loadIcon(packageManager)
                                drawableToBitmap(drawable)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(info, 60) },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    info.loadLabel(packageManager).toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFF94A3B8)) }
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
        containerColor = Color(0xFF1E293B),
        title = { Text("Notion Checklist Setup", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Connect your Notion tasks database to show your daily priorities before opening distracting apps.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Integration Token (secret_...)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = databaseId,
                    onValueChange = { databaseId = it },
                    label = { Text("Database ID (32 characters)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(token, databaseId) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Text("Save & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) }
        }
    )
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
