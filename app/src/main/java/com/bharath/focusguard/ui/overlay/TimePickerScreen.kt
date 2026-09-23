package com.bharath.focusguard.ui.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun TimePickerScreen(
    appName: String,
    minutesLeft: Int,
    onPicked: (Int) -> Unit
) {
    var customText by remember { mutableStateOf("") }
    val presets = listOf(5, 10, 20)

    OverlayScaffold {
        Text("How long for $appName?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("$minutesLeft min left in today's budget.")
        Spacer(Modifier.height(16.dp))
        presets.forEach { preset ->
            val capped = preset.coerceAtMost(minutesLeft)
            val overBudget = preset > minutesLeft
            Button(
                onClick = { onPicked(capped) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    if (overBudget) "$preset min (only $minutesLeft min left today)"
                    else "$preset min"
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = customText,
            onValueChange = { customText = it.filter(Char::isDigit).take(3) },
            label = { Text("Custom minutes") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors()
        )
        Spacer(Modifier.height(8.dp))
        val custom = customText.toIntOrNull()
        val customOver = custom != null && custom > minutesLeft
        Button(
            onClick = {
                val requested = custom ?: return@Button
                onPicked(requested.coerceIn(1, minutesLeft))
            },
            enabled = custom != null && custom > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (customOver) "Use remaining $minutesLeft min"
                else "Start custom session"
            )
        }
    }
}
