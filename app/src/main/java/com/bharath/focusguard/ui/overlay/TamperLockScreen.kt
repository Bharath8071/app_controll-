package com.bharath.focusguard.ui.overlay

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TamperLockScreen(onOpenSettings: () -> Unit) {
    OverlayScaffold {
        Text("FocusGuard protection is off", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Accessibility or overlay permission was turned off. " +
                "Re-enable both so monitored apps stay gated."
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Open settings")
        }
    }
}
