package com.bharath.focusguard.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bharath.focusguard.ui.theme.FgColor
import kotlinx.coroutines.delay

@Composable
fun TimePickerScreen(
    appName: String,
    minutesLeft: Int,
    onPicked: (Int) -> Unit,
    onGoHome: () -> Unit = {}
) {
    val presets = listOf(5, 10, 20)
    val effectiveBudget = minutesLeft.coerceAtLeast(1)
    var selected by remember { mutableStateOf(presets.firstOrNull { it <= effectiveBudget } ?: effectiveBudget) }
    OverlayScaffold(fieldColor = FgColor.Mist) {
        Text("How long do you need?", style = MaterialTheme.typography.headlineLarge, color = FgColor.Ink)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                val avail = preset <= effectiveBudget
                val sel = selected == preset
                Surface(modifier = Modifier.weight(1f).height(48.dp).clickable(enabled = avail) { selected = preset },
                    shape = RoundedCornerShape(8.dp),
                    color = if (sel) FgColor.Ink else FgColor.Paper,
                    border = BorderStroke(1.dp, FgColor.Line)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${preset} min", color = if (sel) androidx.compose.ui.graphics.Color.White else FgColor.Ink,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        if (presets.any { it > effectiveBudget }) {
            Text("Over today's budget", color = FgColor.Slate, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = FgColor.Paper, border = BorderStroke(1.dp, FgColor.Line), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                FilledTonalIconButton(onClick = { selected = (selected - 1).coerceAtLeast(1) }, enabled = selected > 1) { Text("−") }
                Text("$selected min", color = FgColor.Ink, fontWeight = FontWeight.Bold)
                FilledTonalIconButton(onClick = { selected = (selected + 1).coerceAtMost(effectiveBudget) }, enabled = selected < effectiveBudget) { Text("+") }
            }
        }
        Spacer(Modifier.height(12.dp))
        val context = LocalContext.current
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { delay(1000L); now = System.currentTimeMillis() } }
        val preview = remember(now, selected) {
            android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(now + selected * 60_000L))
        }
        Text("Open until $preview", color = FgColor.Ink, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onPicked(selected.coerceIn(1, effectiveBudget)) }, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = FgColor.Ink)) {
            Text("Start")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) { Text("Go home", color = FgColor.Lock) }
    }
}
