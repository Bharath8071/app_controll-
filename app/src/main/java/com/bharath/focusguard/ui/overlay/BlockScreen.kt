package com.bharath.focusguard.ui.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bharath.focusguard.data.local.entities.MonitoredApp
import com.bharath.focusguard.ui.theme.FgColor
import kotlinx.coroutines.delay

@Composable
fun BlockScreen(
    app: MonitoredApp,
    isHardBlock: Boolean,
    minutesLeft: Int,
    showExtend: Boolean,
    onGoHome: () -> Unit,
    onExtend: () -> Unit,
    onNewSession: () -> Unit = {}
) {
    OverlayScaffold(fieldColor = FgColor.LockField) {
        if (isHardBlock) {
            Text("You're done with ${app.displayName} for today.", style = MaterialTheme.typography.displayLarge, color = FgColor.Ink,
                modifier = Modifier.semantics { contentDescription = "Blocked. You're done with ${app.displayName} for today." })
            Spacer(Modifier.height(8.dp))
            Text("Your budget resets at midnight.", style = MaterialTheme.typography.bodyLarge, color = FgColor.Ink)
            Spacer(Modifier.height(16.dp))
            if (showExtend) {
                OutlinedButton(onClick = onExtend, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Use 5-min emergency pass") }
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = FgColor.Lock)) {
                Text("Exit to home")
            }
        } else {
            Text("Session complete.", style = MaterialTheme.typography.displayLarge, color = FgColor.Ink)
            Spacer(Modifier.height(8.dp))
            Text("Your planned session on ${app.displayName} is up. $minutesLeft min left today.", style = MaterialTheme.typography.bodyLarge, color = FgColor.Ink)
            Spacer(Modifier.height(16.dp))
            if (minutesLeft > 0) {
                Button(onClick = onNewSession, modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = FgColor.Lock)) {
                    Text("Start another session")
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = FgColor.Lock)) {
                Text("Exit to home")
            }
        }
    }
}
