package com.bharath.focusguard.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.bharath.focusguard.data.local.AppDatabase
import com.bharath.focusguard.data.local.entities.BlockWindow
import kotlinx.coroutines.launch

/** Translucent picker opened from the widget: 30 min / 1 h / 2 h / custom. */
class BlockNowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var custom by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("45") }
                Column(Modifier.padding(24.dp)) {
                    Text("Start a focus block", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    listOf(30, 60, 120).forEach { m ->
                        Button(onClick = { startBlock(m); finish() }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(if (m >= 60) "${m / 60} h" else "$m min")
                        }
                    }
                    OutlinedTextField(value = custom, onValueChange = { custom = it.filter(Char::isDigit).take(3) },
                        label = { Text("Custom minutes") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { startBlock(custom.toIntOrNull()?.coerceIn(5, 480) ?: 45); finish() },
                        modifier = Modifier.fillMaxWidth(), enabled = (custom.toIntOrNull() ?: 0) >= 5) {
                        Text("Start custom")
                    }
                    TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            }
        }
    }

    private fun startBlock(minutes: Int) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(this@BlockNowActivity)
                db.blockWindowDao().upsert(BlockWindow(name = "Focus block", daysMask = 127,
                    startMinute = 0, endMinute = 0, enabled = true,
                    oneOffUntilEpochMs = System.currentTimeMillis() + minutes * 60_000L))
                FocusGuardWidgetProvider.refreshAll(this@BlockNowActivity)
            } catch (_: Exception) {}
        }
    }
}
