package com.bharath.focusguard.ui.onboarding

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.bharath.focusguard.data.local.AppDatabase
import com.bharath.focusguard.data.local.entities.MonitoredApp
import com.bharath.focusguard.data.prefs.UserPreferences
import com.bharath.focusguard.ui.main.MainActivity
import com.bharath.focusguard.ui.theme.FgColor
import com.bharath.focusguard.ui.theme.FocusGuardLightTheme
import com.bharath.focusguard.util.PermissionUtils
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FocusGuardLightTheme {
                OnboardingFlow(onDone = {
                    startActivity(android.content.Intent(this, MainActivity::class.java))
                    finish()
                })
            }
        }
    }
}

/** Phase 9: 9 one-decision steps, resumable via DataStore. */
@Composable
fun OnboardingFlow(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPreferences(context) }
    val savedStep by prefs.onboardingStep.collectAsState(initial = 0)
    var step by remember(savedStep) { mutableStateOf(savedStep.coerceIn(0, 8)) }
    var tick by remember { mutableStateOf(0) }
    key(tick) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) tick++
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    fun advance(to: Int = step + 1) {
        scope.launch { prefs.setOnboardingStep(to.coerceAtMost(8)) }
        if (to > 8) {
            scope.launch { prefs.setOnboardingStep(8) }
            onDone()
        } else step = to
    }
    fun back() { step = (step - 1).coerceAtLeast(0); scope.launch { prefs.setOnboardingStep(step) } }

    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { advance() }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        LinearProgressIndicator(progress = { (step + 1) / 9f }, modifier = Modifier.fillMaxWidth(),
            color = FgColor.Tide, trackColor = FgColor.Line)
        Spacer(Modifier.height(16.dp))
        when (step) {
            0 -> OnboardPage("FocusGuard adds a pause before the apps that pull you in.",
                "Get started", onNext = { advance() }, showBack = false, onBack = ::back)
            1 -> OnboardPage("FocusGuard needs Accessibility to notice when you open a monitored app. It doesn't record what you type, and your usage stays on this phone. If you turn on website blocking, it also reads the address in Chrome.",
                if (PermissionUtils.hasAccessibilityPermission(context)) "Continue" else "Open settings",
                onNext = { if (PermissionUtils.hasAccessibilityPermission(context)) advance() else PermissionUtils.requestAccessibilityPermission(context) },
                showBack = true, onBack = ::back)
            2 -> OnboardPage("This lets FocusGuard show the pause screen on top of apps.",
                if (PermissionUtils.hasOverlayPermission(context)) "Continue" else "Open settings",
                onNext = { if (PermissionUtils.hasOverlayPermission(context)) advance() else PermissionUtils.requestOverlayPermission(context) },
                showBack = true, onBack = ::back)
            3 -> OnboardPage("Some phones stop background apps. Set FocusGuard to Unrestricted so blocking keeps working. Samsung: check restricted settings. Xiaomi and Oppo: allow autostart.",
                if (PermissionUtils.isBatteryUnrestricted(context)) "Continue" else "Open settings",
                onNext = { if (PermissionUtils.isBatteryUnrestricted(context)) advance() else PermissionUtils.requestBatteryUnrestricted(context) },
                showBack = true, onBack = ::back)
            4 -> OnboardPage("Get a warning before a session ends.", "Allow",
                onNext = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        if (PermissionUtils.hasNotificationPermission(context)) advance()
                        else notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else advance()
                }, showBack = true, onBack = ::back, skippable = true, onSkip = { advance() })
            5 -> OnboardPage("Makes it harder to remove FocusGuard on impulse.", "Turn on",
                onNext = { if (PermissionUtils.hasDeviceAdminPermission(context)) advance() else PermissionUtils.requestDeviceAdminPermission(context) },
                showBack = true, onBack = ::back, skippable = true, onSkip = { advance() })
            6 -> OnboardPage("Turn on usage access to see where your time goes on Home.", "Turn on",
                onNext = { if (PermissionUtils.hasUsageAccess(context)) advance() else PermissionUtils.requestUsageAccess(context) },
                showBack = true, onBack = ::back, skippable = true, onSkip = { advance() })
            7 -> NotionStep(onBack = ::back, onNext = { advance() }, onSkip = { advance() })
            else -> AppsStep(onBack = ::back, onDone = {
                scope.launch { prefs.setOnboardingStep(8) }; onDone()
            })
        }
    }
}

@Composable
private fun OnboardPage(text: String, action: String, onNext: () -> Unit, showBack: Boolean, onBack: () -> Unit,
    skippable: Boolean = false, onSkip: () -> Unit = {}) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = FgColor.Ink)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = FgColor.Ink)) {
        Text(action)
    }
    if (skippable) TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
    if (showBack) TextButton(onClick = onBack) { Text("Back") }
}

@Composable
private fun NotionStep(onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPreferences(context) }
    var token by remember { mutableStateOf("") }
    var dbId by remember { mutableStateOf("") }
    Text("Show your top tasks before you open an app.", style = MaterialTheme.typography.headlineSmall, color = FgColor.Ink)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Integration token") },
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = dbId, onValueChange = { dbId = it }, label = { Text("Database ID") }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    Button(onClick = {
        scope.launch { prefs.setNotionCredentials(token, dbId); onNext() }
    }, modifier = Modifier.fillMaxWidth(), enabled = token.isNotBlank() && dbId.isNotBlank()) { Text("Connect") }
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
    TextButton(onClick = onBack) { Text("Back") }
}

@Composable
private fun ColumnScope.AppsStep(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val installed by produceState<List<ApplicationInfo>>(emptyList()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
        }
    }
    val picked = remember { mutableStateMapOf<String, Boolean>() }
    Text("Pick monitored apps and set the daily budget.", style = MaterialTheme.typography.headlineSmall, color = FgColor.Ink)
    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search apps") }, modifier = Modifier.fillMaxWidth())
    LazyColumn(Modifier.weight(1f)) {
        items(installed.filter { it.loadLabel(context.packageManager).toString().contains(query, ignoreCase = true) }.take(40),
            key = { it.packageName }) { info ->
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = picked[info.packageName] == true,
                    onCheckedChange = { picked[info.packageName] = it })
                Text(info.loadLabel(context.packageManager).toString(), Modifier.weight(1f), color = FgColor.Ink)
            }
        }
    }
    Button(onClick = {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            picked.filterValues { it }.keys.forEach { pkg ->
                val info = installed.firstOrNull { it.packageName == pkg } ?: return@forEach
                db.monitoredAppDao().upsert(MonitoredApp(pkg, info.loadLabel(context.packageManager).toString(), 60, true))
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
        }
    }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    TextButton(onClick = onBack) { Text("Back") }
}
