package com.bharath.focusguard.ui.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bharath.focusguard.data.local.entities.MonitoredApp
import com.bharath.focusguard.ui.theme.FgColor
import kotlinx.coroutines.delay

@Composable
fun CooldownBlockScreen(
    app: MonitoredApp,
    cooldownExpiresAtMillis: Long,
    minutesLeft: Int,
    onGoHome: () -> Unit,
    onCooldownFinished: () -> Unit = {}
) {
    var remaining by remember(cooldownExpiresAtMillis) { mutableStateOf((cooldownExpiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)) }
    LaunchedEffect(cooldownExpiresAtMillis) {
        while (true) {
            val rem = (cooldownExpiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            remaining = rem
            if (rem <= 0L) break
            delay(500L)
        }
    }
    val total = 10 * 60_000L
    val progress = (1f - remaining.toFloat() / total).coerceIn(0f, 1f)
    OverlayScaffold(fieldColor = FgColor.TideField) {
        Text("Take a pause.", style = MaterialTheme.typography.displayLarge, color = FgColor.Ink)
        Spacer(Modifier.height(8.dp))
        Text("${app.displayName} opens again soon.", style = MaterialTheme.typography.bodyLarge, color = FgColor.Ink)
        Spacer(Modifier.height(16.dp))
        TideLine(progress = progress)
        Spacer(Modifier.height(16.dp))
        if (remaining <= 0L) {
            Button(onClick = onCooldownFinished, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = FgColor.Tide)) {
                Text("Unlock now")
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = FgColor.Lock)) {
            Text("Back to home")
        }
    }
}
