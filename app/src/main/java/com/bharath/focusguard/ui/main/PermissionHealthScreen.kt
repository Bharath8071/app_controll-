package com.bharath.focusguard.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharath.focusguard.ui.theme.FgColor
import com.bharath.focusguard.ui.theme.SketchButton
import com.bharath.focusguard.ui.theme.SketchCard
import com.bharath.focusguard.ui.theme.dotGridBackground
import com.bharath.focusguard.util.PermissionUtils

@Composable
fun PermissionHealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    key(tick) {
        val accessOn = PermissionUtils.hasAccessibilityPermission(context)
        val overlayOn = PermissionUtils.hasOverlayPermission(context)
        val battOk   = PermissionUtils.isBatteryUnrestricted(context)
        val notifOn  = PermissionUtils.hasNotificationPermission(context)
        val adminOn  = PermissionUtils.hasDeviceAdminPermission(context)
        val usageOn  = PermissionUtils.hasUsageAccess(context)
        val showAutostart = PermissionUtils.oemNeedsAutostart()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .dotGridBackground()
                .padding(18.dp)
        ) {
            Text(
                "Permissions",
                style = MaterialTheme.typography.headlineMedium,
                color = FgColor.Ink,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            Text("Required", color = FgColor.Slate, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            PermRowView("Accessibility", "Notices when you open a monitored app.", accessOn) {
                PermissionUtils.requestAccessibilityPermission(context); tick++
            }
            PermRowView("Display over other apps", "Shows the pause screen on top.", overlayOn) {
                PermissionUtils.requestOverlayPermission(context); tick++
            }

            Spacer(Modifier.height(10.dp))
            Text("Recommended", color = FgColor.Slate, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            PermRowView("Battery unrestricted", "Keeps blocking reliable in background.", battOk) {
                PermissionUtils.requestBatteryUnrestricted(context); tick++
            }
            if (showAutostart) {
                PermRowView(
                    name   = PermissionUtils.autostartLabel(),
                    reason = PermissionUtils.autostartReason(),
                    on     = false
                ) { PermissionUtils.requestAutostartPermission(context); tick++ }
            }
            PermRowView("Notifications", "1-min warnings before session ends.", notifOn) {
                PermissionUtils.openNotificationSettings(context); tick++
            }
            PermRowView("Uninstall protection", "Harder to remove on impulse.", adminOn) {
                PermissionUtils.requestDeviceAdminPermission(context); tick++
            }

            Spacer(Modifier.height(10.dp))
            Text("Optional", color = FgColor.Slate, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            PermRowView("Usage access", "Shows your usage report.", usageOn) {
                PermissionUtils.requestUsageAccess(context); tick++
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Samsung may need restricted settings before Accessibility can be enabled. Xiaomi and Oppo also need autostart + unrestricted battery.",
                color = FgColor.Slate,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))
            SketchButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                fillColor = FgColor.Paper, textColor = FgColor.Ink
            ) {
                Text("← Back", color = FgColor.Ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PermRowView(name: String, reason: String, on: Boolean, action: () -> Unit) {
    SketchCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        bgColor = if (on) FgColor.GreenSoft else FgColor.Paper,
        borderColor = if (on) FgColor.Green else FgColor.Flag,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (on) "✅" else "⚠️",
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 10.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(name, color = FgColor.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(reason, color = FgColor.Slate, fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (!on) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = action,
                    colors = ButtonDefaults.buttonColors(containerColor = FgColor.Blue),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Turn on", color = FgColor.Paper, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun PermissionBanner(onFix: () -> Unit) {
    val context = LocalContext.current
    val missing = remember { PermissionUtils.criticalMissing(context) }
    if (missing.isEmpty()) return
    SketchCard(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        bgColor = FgColor.FlagField,
        borderColor = FgColor.Flag,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⚠️", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(missing.first(), color = FgColor.Ink, modifier = Modifier.weight(1f), fontSize = 13.sp)
            TextButton(onClick = onFix) {
                Text("Fix", color = FgColor.Red, fontWeight = FontWeight.Bold)
            }
        }
    }
}
