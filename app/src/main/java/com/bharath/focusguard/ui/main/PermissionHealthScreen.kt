package com.bharath.focusguard.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bharath.focusguard.ui.theme.FgColor
import com.bharath.focusguard.util.PermissionUtils

@Composable
fun PermissionHealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    // Re-check on every recomposition triggered by resume (caller calls on resume)
    key(tick) {
        val accessOn = PermissionUtils.hasAccessibilityPermission(context)
        val overlayOn = PermissionUtils.hasOverlayPermission(context)
        val battOk = PermissionUtils.isBatteryUnrestricted(context)
        val notifOn = PermissionUtils.hasNotificationPermission(context)
        val adminOn = PermissionUtils.hasDeviceAdminPermission(context)
        val usageOn = PermissionUtils.hasUsageAccess(context)
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Permissions", style = MaterialTheme.typography.headlineLarge, color = FgColor.Ink)
            Spacer(Modifier.height(12.dp))
            Text("Needed", style = MaterialTheme.typography.titleLarge, color = FgColor.Ink)
            PermRowView("Accessibility", "Notices when you open a monitored app.", accessOn, { PermissionUtils.requestAccessibilityPermission(context); tick++ })
            PermRowView("Display over other apps", "Shows the pause screen on top of apps.", overlayOn, { PermissionUtils.requestOverlayPermission(context); tick++ })
            Spacer(Modifier.height(8.dp))
            Text("Recommended", style = MaterialTheme.typography.titleLarge, color = FgColor.Ink)
            PermRowView("Battery unrestricted", "Keeps blocking reliable.", battOk, { PermissionUtils.requestBatteryUnrestricted(context); tick++ })
            PermRowView("Notifications", "Get a warning before a session ends.", notifOn, { PermissionUtils.openNotificationSettings(context); tick++ })
            PermRowView("Uninstall protection", "Harder to remove on impulse.", adminOn, { PermissionUtils.requestDeviceAdminPermission(context); tick++ })
            Spacer(Modifier.height(8.dp))
            Text("Optional", style = MaterialTheme.typography.titleLarge, color = FgColor.Ink)
            PermRowView("Usage access", "Shows your usage report.", usageOn, { PermissionUtils.requestUsageAccess(context); tick++ })
            Spacer(Modifier.height(16.dp))
            Text("Samsung may need restricted settings before Accessibility can be enabled. Xiaomi and Oppo also need autostart plus unrestricted battery.",
                color = FgColor.Slate, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        }
    }
}

@Composable
private fun PermRowView(name: String, reason: String, on: Boolean, action: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = FgColor.Paper,
        border = BorderStroke(1.dp, if (on) FgColor.Line else FgColor.Flag), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, color = FgColor.Ink, style = MaterialTheme.typography.labelLarge)
                Text(reason, color = FgColor.Slate, style = MaterialTheme.typography.bodyLarge)
                Text(if (on) "On" else "Off", color = if (on) FgColor.TideInk else FgColor.Ink)
            }
            if (!on) Button(onClick = action) { Text("Turn on") }
        }
    }
}

@Composable
fun PermissionBanner(onFix: () -> Unit) {
    val context = LocalContext.current
    val missing = remember { PermissionUtils.criticalMissing(context) }
    if (missing.isEmpty()) return
    Surface(shape = RoundedCornerShape(14.dp), color = FgColor.FlagField, border = BorderStroke(1.dp, FgColor.Flag),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(missing.first(), color = FgColor.Ink, modifier = Modifier.weight(1f))
            TextButton(onClick = onFix) { Text("Fix") }
        }
    }
}
