package com.bharath.focusguard.ui.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bharath.focusguard.ui.theme.FgColor
import kotlinx.coroutines.delay

@Composable
fun BlockWindowScreen(
    appName: String,
    windowName: String?,
    untilText: String,
    progress: Float,
    breaksLeft: Int,
    onBackHome: () -> Unit,
    onTakeBreak: () -> Unit,
    onLeaveEarly: () -> Unit,
) {
    var showLeaveSheet by remember { mutableStateOf(false) }
    OverlayScaffold(fieldColor = FgColor.LockField) {
        Text("$appName is closed until $untilText.", style = MaterialTheme.typography.displayLarge, color = FgColor.Ink)
        Spacer(Modifier.height(8.dp))
        Text(if (windowName.isNullOrBlank()) "Your block is on." else "Your $windowName block is on.",
            style = MaterialTheme.typography.bodyLarge, color = FgColor.Ink)
        Spacer(Modifier.height(16.dp))
        TideLine(progress = progress)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBackHome, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = FgColor.Lock)) {
            Text("Back to home")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onTakeBreak, enabled = breaksLeft > 0, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(if (breaksLeft > 0) "Take a break ($breaksLeft left)" else "No breaks left")
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { showLeaveSheet = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Leave early", color = FgColor.Lock)
        }
    }
    if (showLeaveSheet) {
        LeaveEarlySheet(onDismiss = { showLeaveSheet = false }, onEnd = { onLeaveEarly(); showLeaveSheet = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveEarlySheet(onDismiss: () -> Unit, onEnd: () -> Unit) {
    var waited by remember { mutableStateOf(0) } // 0..20
    LaunchedEffect(Unit) { repeat(20) { delay(1000L); waited++ } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp)) {
            Text("Leaving early ends this block for today.", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { waited / 20f }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Button(onClick = onEnd, enabled = waited >= 20, modifier = Modifier.fillMaxWidth()) {
                Text(if (waited >= 20) "End block" else "Hold on a moment")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Keep the block") }
        }
    }
}
