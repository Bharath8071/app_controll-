package com.bharath.focusguard.ui.main

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharath.focusguard.data.local.entities.MonitoredApp
import com.bharath.focusguard.ui.onboarding.OnboardingActivity
import com.bharath.focusguard.ui.theme.FgColor
import com.bharath.focusguard.ui.theme.SketchButton
import com.bharath.focusguard.ui.theme.SketchCard
import com.bharath.focusguard.ui.theme.SketchTheme
import com.bharath.focusguard.ui.theme.TideLine
import com.bharath.focusguard.ui.theme.dotGridBackground
import com.bharath.focusguard.ui.theme.sketchBorder
import com.bharath.focusguard.util.PermissionUtils
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** Keep Usage Access state in Compose state so returning from Settings refreshes the UI. */
    private val usageAccessGranted = mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usageAccessGranted.value = PermissionUtils.hasUsageAccess(this)

        if (!PermissionUtils.hasAccessibilityPermission(this) || !PermissionUtils.hasOverlayPermission(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionUtils.hasNotificationPermission(this)) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            SketchTheme {
                var showPerms by remember { mutableStateOf(false) }
                var tab by remember { mutableStateOf(0) }
                var showEditor by remember { mutableStateOf(false) }
                var showBlockNow by remember { mutableStateOf(false) }
                if (showPerms) {
                    PermissionHealthScreen(onBack = { showPerms = false })
                } else {
                    val apps by viewModel.monitoredApps.collectAsState()
                    val token by viewModel.notionToken.collectAsState()
                    val databaseId by viewModel.notionDatabaseId.collectAsState()
                    val controllerEnabled by viewModel.controllerEnabled.collectAsState()

                    val installedApps by produceState<List<ApplicationInfo>>(initialValue = emptyList()) {
                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                                .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
                        }
                    }

                    MainScreen(
                        apps = apps,
                        installedApps = installedApps,
                        packageManager = packageManager,
                        notionToken = token,
                        notionDatabaseId = databaseId,
                        controllerEnabled = controllerEnabled,
                        onSetControllerEnabled = viewModel::setControllerEnabled,
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                PermissionUtils.openNotificationSettings(this@MainActivity)
                            }
                        },
                        onAddApp = { info, budget -> viewModel.addApp(info, packageManager, budget) },
                        onUpdateBudget = viewModel::updateBudget,
                        onRemove = viewModel::removeApp,
                        onSaveNotion = viewModel::saveNotion,
                        onOpenPermissions = { showPerms = true },
                        currentTab = tab,
                        onTab = {
                            tab = it
                            // Refresh whenever the Stats tab is opened; otherwise
                            // session/usage rows written while the app was in the
                            // background may not be reflected until the next resume.
                            if (it == 1) {
                                viewModel.refreshStats()
                                viewModel.loadUsageCard(application)
                            }
                        },
                        windows = viewModel.windows.collectAsState().value,
                        streak = viewModel.streakDays.collectAsState().value,
                        weekly = viewModel.weeklyAppMinutes,
                        realWeekly = viewModel.weeklyRealUsage,
                        budgetPerDay = apps.sumOf { it.app.dailyBudgetMinutes },
                        onToggleWindow = { viewModel.saveWindow(it) },
                        onDeleteWindow = { viewModel.deleteWindow(it) },
                        onAddWindow = { showEditor = true },
                        onBlockNow = { showBlockNow = true },
                        usageGranted = usageAccessGranted.value,
                        usageTop = viewModel.usageTop,
                        onRequestUsage = { PermissionUtils.requestUsageAccess(this@MainActivity) },
                        appMode = viewModel.appMode.collectAsState().value,
                        onSetAppMode = viewModel::setAppMode,
                    )
                    if (showEditor) {
                        WindowEditorDialog(
                            existing = viewModel.windows.collectAsState().value,
                            onDismiss = { showEditor = false },
                            onSave = { viewModel.saveWindow(it) { showEditor = false } })
                    }
                    if (showBlockNow) {
                        AlertDialog(
                            onDismissRequest = { showBlockNow = false },
                            containerColor = FgColor.Paper,
                            title = {
                                Text("Block now", fontWeight = FontWeight.Bold, color = FgColor.Ink)
                            },
                            text = {
                                Text("Start a focus block immediately.", color = FgColor.Slate)
                            },
                            confirmButton = {
                                Column {
                                    listOf(30, 60, 120).forEach { m ->
                                        TextButton(onClick = { viewModel.blockNow(m); showBlockNow = false }) {
                                            Text("$m min", color = FgColor.Blue)
                                        }
                                    }
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showBlockNow = false }) {
                                    Text("Cancel", color = FgColor.Slate)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        usageAccessGranted.value = PermissionUtils.hasUsageAccess(this)
        viewModel.refreshStats()
        viewModel.loadUsageCard(application)
        com.bharath.focusguard.service.WindowAlarmScheduler.rescheduleAll(this)
        if (!PermissionUtils.hasAccessibilityPermission(this) || !PermissionUtils.hasOverlayPermission(this)) {
            com.bharath.focusguard.util.NotificationHelper.showPermissionLost(this)
        }
    }
}

// Keep alias so any stale reference compiles
@Composable
fun FocusGuardTheme(content: @Composable () -> Unit) = SketchTheme(content = content)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    apps: List<MonitoredAppUi>,
    installedApps: List<ApplicationInfo>,
    packageManager: PackageManager,
    notionToken: String,
    notionDatabaseId: String,
    controllerEnabled: Boolean = true,
    onSetControllerEnabled: (Boolean) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onAddApp: (ApplicationInfo, Int) -> Unit,
    onUpdateBudget: (MonitoredApp, Int) -> Unit,
    onRemove: (MonitoredApp) -> Unit,
    onSaveNotion: (String, String, () -> Unit) -> Unit,
    onOpenPermissions: () -> Unit = {},
    currentTab: Int = 0, onTab: (Int) -> Unit = {},
    windows: List<com.bharath.focusguard.data.local.entities.BlockWindow> = emptyList(),
    streak: Int = 0,
    weekly: List<com.bharath.focusguard.data.local.entities.AppDaily> = emptyList(),
    realWeekly: List<com.bharath.focusguard.data.local.entities.AppDaily> = emptyList(),
    budgetPerDay: Int = 0,
    onToggleWindow: (com.bharath.focusguard.data.local.entities.BlockWindow) -> Unit = {},
    onDeleteWindow: (Long) -> Unit = {},
    onAddWindow: () -> Unit = {},
    onBlockNow: () -> Unit = {},
    usageGranted: Boolean = false,
    usageTop: List<Pair<String, Long>> = emptyList(),
    onRequestUsage: () -> Unit = {},
    appMode: Boolean = true,
    onSetAppMode: (Boolean) -> Unit = {},
) {
    var showPicker by remember { mutableStateOf(false) }
    var showNotion by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<MonitoredApp?>(null) }

    // Real package names are ugly (com.instagram.android would show as
    // "android" with substringAfterLast); use the monitored app label.
    val appLabels = remember(apps) {
        apps.associate { it.app.packageName to it.app.displayName }
    }

    val totalBudget = apps.sumOf { it.app.dailyBudgetMinutes }
    val totalUsed = apps.sumOf { it.minutesUsedToday }

    // Sketch tab bar colours
    val tabColors = listOf(FgColor.YellowPad, FgColor.BlueSoft, FgColor.GreenSoft)
    val tabLabels = listOf("Home", "Stats", "Settings")
    val tabEmojis = listOf("🏠", "📊", "⚙️")

    Scaffold(
        containerColor = FgColor.BgDot,
        bottomBar = {
            // Hand-drawn sketch tab bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FgColor.Paper)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .sketchBorder(color = FgColor.Line, strokeWidth = 1.5.dp, cornerRadius = 0.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tabLabels.forEachIndexed { i, label ->
                    val selected = currentTab == i
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) tabColors[i] else Color.Transparent)
                            .clickable { onTab(i) }
                            .then(
                                if (selected) Modifier.sketchBorder(
                                    color = FgColor.Ink, strokeWidth = 1.8.dp, cornerRadius = 12.dp
                                ) else Modifier
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(tabEmojis[i], fontSize = 18.sp)
                            Text(
                                label,
                                color = FgColor.Ink,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
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
                            color = FgColor.Ink
                        )
                    }
                },
                actions = {
                    // Shield status pill — sketch style
                    val pillBg = if (controllerEnabled) FgColor.GreenSoft else FgColor.YellowPad
                    val pillText = if (controllerEnabled) "Active" else "Paused"
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(pillBg)
                            .sketchBorder(
                                color = if (controllerEnabled) FgColor.Green else FgColor.Red,
                                strokeWidth = 1.5.dp, cornerRadius = 50.dp
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            pillText,
                            color = if (controllerEnabled) FgColor.TideInk else FgColor.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FgColor.Paper)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dotGridBackground()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp)
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                var hasNotificationAccess by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }
                PermissionBanner(onFix = onOpenPermissions)

                when (currentTab) {
                    1 -> {
                        AnalyticsScreen(
                            weekly = weekly,
                            budgetPerDay = budgetPerDay,
                            usageGranted = usageGranted,
                            realWeekly = realWeekly,
                            onRequestUsage = onRequestUsage,
                            appLabels = appLabels,
                        )
                        return@Column
                    }
                    2 -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item {
                                BlockWindowsScreen(
                                    windows = windows, activeNow = false,
                                    onToggle = onToggleWindow, onDelete = onDeleteWindow, onAdd = onAddWindow
                                )
                            }
                            item {
                                // ── Gate Mode Toggle ──
                                SketchCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    bgColor = if (appMode) FgColor.BlueSoft else FgColor.GreenSoft
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(if (appMode) "📱" else "🚪", fontSize = 22.sp)
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Gate Mode",
                                                color = FgColor.Ink,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                if (appMode) "Overlay — appears on top of the app"
                                                else "App — opens FocusGuard screen",
                                                color = FgColor.Slate,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Switch(
                                            checked = appMode,
                                            onCheckedChange = { onSetAppMode(it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = FgColor.Paper,
                                                checkedTrackColor = FgColor.Blue,
                                                uncheckedThumbColor = FgColor.Slate,
                                                uncheckedTrackColor = FgColor.Line,
                                                uncheckedBorderColor = FgColor.Slate
                                            )
                                        )
                                    }
                                }
                            }
                            item {
                                SketchButton(
                                    onClick = onOpenPermissions,
                                    modifier = Modifier.fillMaxWidth(),
                                    fillColor = FgColor.Paper,
                                    textColor = FgColor.Ink
                                ) {
                                    Text("🔑  Permissions", color = FgColor.Ink, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            item {
                                Text(
                                    "version ${com.bharath.focusguard.BuildConfig.VERSION_NAME} (${com.bharath.focusguard.BuildConfig.VERSION_CODE})",
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    color = FgColor.Slate,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        return@Column
                    }
                }

                // ── Tab 0: Home ──
                // Scroll the whole tab as one page. Previously the outer Column
                // was fixed, so on shorter screens the cards above the app list
                // consumed the height and the page could not scroll.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                HomeCards(
                    minutesLeft = (budgetPerDay - apps.sumOf { it.minutesUsedToday }).coerceAtLeast(0),
                    budgetTotal = budgetPerDay, windows = windows, streak = streak,
                    onBlockNow = onBlockNow, onOpenAnalytics = { onTab(1) },
                    usageGranted = usageGranted, usageTop = usageTop, onRequestUsage = onRequestUsage,
                    appLabels = appLabels
                )

                Spacer(Modifier.height(8.dp))

                // Notification banner (sketch amber card)
                if (!hasNotificationAccess) {
                    SketchCard(
                        modifier = Modifier.fillMaxWidth(),
                        bgColor = FgColor.YellowPad
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔔", fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Session Alerts Off",
                                    color = FgColor.Ink,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "Enable to get 1-min warnings.",
                                    color = FgColor.Slate,
                                    fontSize = 11.sp
                                )
                            }
                            TextButton(onClick = {
                                onRequestNotificationPermission()
                                hasNotificationAccess = PermissionUtils.hasNotificationPermission(context)
                            }) {
                                Text("Enable", color = FgColor.Blue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Usage overview sketch card (yellow sticky)
                SketchCard(
                    modifier = Modifier.fillMaxWidth().rotate(-0.5f),
                    bgColor = FgColor.YellowPad
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "TODAY'S USAGE",
                            color = FgColor.Slate,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    "${totalUsed}m used",
                                    color = FgColor.Ink,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("of ${totalBudget}m budget", color = FgColor.Slate, fontSize = 12.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(FgColor.BlueSoft)
                                    .sketchBorder(FgColor.Blue, strokeWidth = 1.5.dp, cornerRadius = 8.dp)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "${apps.size} gated",
                                    color = FgColor.Blue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        val progress = if (totalBudget > 0) (totalUsed.toFloat() / totalBudget).coerceIn(0f, 1f) else 0f
                        TideLine(
                            progress = progress,
                            color = when {
                                progress >= 1f -> FgColor.Red
                                progress >= 0.7f -> Color(0xFFF59E0B)
                                else -> FgColor.Green
                            },
                            modifier = Modifier.fillMaxWidth(),
                            height = 10.dp
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // App Controller master toggle — sketch card
                SketchCard(
                    modifier = Modifier.fillMaxWidth(),
                    bgColor = if (controllerEnabled) FgColor.GreenSoft else FgColor.YellowPad
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (controllerEnabled) "🛡️" else "⏸️", fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "App Controller",
                                color = FgColor.Ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                if (controllerEnabled) "Blocking active"
                                else "Paused — resumes at midnight",
                                color = if (controllerEnabled) FgColor.TideInk else FgColor.Red,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = controllerEnabled,
                            onCheckedChange = { onSetControllerEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = FgColor.Paper,
                                checkedTrackColor = FgColor.Green,
                                uncheckedThumbColor = FgColor.Slate,
                                uncheckedTrackColor = FgColor.Line,
                                uncheckedBorderColor = FgColor.Slate
                            )
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    "Monitored Apps",
                    color = FgColor.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                if (apps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No apps yet.\nTap below to add one.",
                            color = FgColor.Slate,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        apps.forEach { row ->
                            MonitoredAppCard(
                                row = row,
                                packageManager = packageManager,
                                onEditBudget = { editingApp = row.app },
                                onRemove = { onRemove(row.app) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                SketchButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    fillColor = FgColor.YellowPad, hatched = true, hatchColor1 = FgColor.YellowPad, hatchColor2 = FgColor.Yellow
                ) {
                    Text("+ Add App to Gate", color = FgColor.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(Modifier.height(8.dp))

                SketchButton(
                    onClick = { showNotion = true },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    fillColor = FgColor.Paper, textColor = FgColor.Ink
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (notionToken.isNotBlank()) FgColor.Green else FgColor.Slate,
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (notionToken.isBlank()) "Connect Notion" else "Notion Connected ✓",
                            color = FgColor.Ink,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                } // end Home tab scroll
            }
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

    SketchCard(
        modifier = Modifier.fillMaxWidth(),
        bgColor = if (isExhausted) FgColor.FlagField else FgColor.Paper
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // App Icon
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap.asImageBitmap(),
                        contentDescription = row.app.displayName,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(FgColor.BlueSoft)
                            .sketchBorder(FgColor.Blue, strokeWidth = 1.5.dp, cornerRadius = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(row.app.displayName.take(1), color = FgColor.Blue, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.app.displayName,
                        color = FgColor.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        "${row.minutesUsedToday}m / ${row.app.dailyBudgetMinutes}m",
                        color = FgColor.Slate,
                        fontSize = 12.sp
                    )
                }

                // Status pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isExhausted) FgColor.FlagField else FgColor.GreenSoft)
                        .sketchBorder(
                            if (isExhausted) FgColor.Red else FgColor.Green,
                            strokeWidth = 1.5.dp, cornerRadius = 8.dp
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isExhausted) "🔒 Locked" else "${row.minutesLeft}m left",
                        color = if (isExhausted) FgColor.Red else FgColor.TideInk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            TideLine(
                progress = progress,
                color = when {
                    progress >= 1f -> FgColor.Red
                    progress >= 0.7f -> Color(0xFFF59E0B)
                    else -> FgColor.Green
                },
                modifier = Modifier.fillMaxWidth(),
                height = 8.dp
            )

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEditBudget) {
                    Text("Edit Budget", color = FgColor.Blue, fontSize = 13.sp)
                }
                TextButton(onClick = onRemove) {
                    Text("Remove", color = FgColor.Red, fontSize = 13.sp)
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
    val parsedMinutes = text.toIntOrNull()
    val isValid = parsedMinutes != null && parsedMinutes >= 1

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FgColor.Paper,
        title = {
            Text("Daily Limit — ${app.displayName}", color = FgColor.Ink, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Pick a preset or enter minutes:",
                    color = FgColor.Slate,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.take(4).forEach { preset ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (text == preset.toString()) FgColor.YellowPad else FgColor.BgDot)
                                .sketchBorder(
                                    if (text == preset.toString()) FgColor.Ink else FgColor.Line,
                                    strokeWidth = 1.5.dp, cornerRadius = 8.dp
                                )
                                .clickable { text = preset.toString() }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${preset}m",
                                color = FgColor.Ink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    label = { Text("Budget (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !isValid && text.isNotEmpty(),
                    supportingText = {
                        if (!isValid && text.isNotEmpty()) {
                            Text("Minimum 1 minute", color = FgColor.Red, fontSize = 11.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FgColor.Blue,
                        unfocusedBorderColor = FgColor.Line,
                        focusedTextColor = FgColor.Ink,
                        unfocusedTextColor = FgColor.Ink,
                        errorBorderColor = FgColor.Red
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (isValid) onSave(parsedMinutes!!) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FgColor.Blue,
                    disabledContainerColor = FgColor.Line
                )
            ) {
                Text("Save Limit", color = FgColor.Paper)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = FgColor.Slate) }
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
        containerColor = FgColor.Paper,
        title = { Text("Select App to Gate", color = FgColor.Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FgColor.Blue,
                        unfocusedBorderColor = FgColor.Line,
                        focusedTextColor = FgColor.Ink,
                        unfocusedTextColor = FgColor.Ink
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(FgColor.BgDot)
                                .sketchBorder(FgColor.Line, strokeWidth = 1.5.dp, cornerRadius = 10.dp)
                                .clickable { onPick(info, 60) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    info.loadLabel(packageManager).toString(),
                                    color = FgColor.Ink,
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
            TextButton(onClick = onDismiss) { Text("Close", color = FgColor.Slate) }
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
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val testContext = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FgColor.Paper,
        title = { Text("Notion Checklist", color = FgColor.Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Connect your Notion tasks database to show priorities before opening distracting apps.",
                    color = FgColor.Slate,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Integration Token (secret_...)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FgColor.Blue,
                        unfocusedBorderColor = FgColor.Line,
                        focusedTextColor = FgColor.Ink,
                        unfocusedTextColor = FgColor.Ink
                    )
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = databaseId,
                    onValueChange = { databaseId = it },
                    label = { Text("Database ID (32 chars)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FgColor.Blue,
                        unfocusedBorderColor = FgColor.Line,
                        focusedTextColor = FgColor.Ink,
                        unfocusedTextColor = FgColor.Ink
                    )
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            testing = true; testResult = null
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val ok = try {
                                    val db = com.bharath.focusguard.data.local.AppDatabase.getInstance(testContext)
                                    com.bharath.focusguard.data.remote.NotionRepository(
                                        api = com.bharath.focusguard.data.remote.NotionClient.create(token),
                                        dao = db.notionTaskDao(), databaseId = databaseId
                                    ).testConnection()
                                } catch (_: Exception) { false }
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    testing = false; testResult = ok
                                }
                            }
                        },
                        enabled = token.isNotBlank() && databaseId.isNotBlank() && !testing,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FgColor.Blue)
                    ) { Text(if (testing) "Testing…" else "Test connection") }
                    Spacer(Modifier.width(8.dp))
                    when (testResult) {
                        true -> Text("Connected ✓", color = FgColor.Green, fontSize = 13.sp)
                        false -> Text("Failed. Check token & DB ID.", color = FgColor.Red, fontSize = 12.sp)
                        null -> {}
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(token, databaseId) },
                colors = ButtonDefaults.buttonColors(containerColor = FgColor.Blue)
            ) {
                Text("Save & Sync", color = FgColor.Paper)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = FgColor.Slate) }
        }
    )
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    // BUG-017 fix: Cap icon dimensions at 96px to prevent loading 22MB+ of adaptive icon bitmaps.
    val maxSize = 96
    val rawWidth = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else maxSize
    val rawHeight = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else maxSize
    val width = minOf(rawWidth, maxSize)
    val height = minOf(rawHeight, maxSize)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
