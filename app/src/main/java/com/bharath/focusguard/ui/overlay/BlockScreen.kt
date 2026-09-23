package com.bharath.focusguard.ui.overlay

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bharath.focusguard.data.local.entities.MonitoredApp

@Composable
fun BlockScreen(
    app: MonitoredApp,
    showExtend: Boolean,
    onGoHome: () -> Unit,
    onExtend: () -> Unit
) {
    OverlayScaffold {
        Text("${app.displayName} is blocked", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Today's ${app.dailyBudgetMinutes}-minute budget is used up. Come back tomorrow.")
        Spacer(Modifier.height(20.dp))
        if (showExtend) {
            Button(onClick = onExtend, modifier = Modifier.fillMaxWidth()) {
                Text("+5 min emergency extend")
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) {
            Text("Go home")
        }
    }
}
