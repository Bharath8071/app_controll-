package com.bharath.focusguard.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharath.focusguard.data.local.entities.NotionTask
import com.bharath.focusguard.ui.theme.FgColor
import kotlinx.coroutines.delay

@Composable
fun ChecklistScreen(
    appName: String,
    tasks: List<NotionTask>,
    onToggle: (NotionTask, Boolean) -> Unit,
    onContinue: () -> Unit,
    onGoHome: () -> Unit = {}
) {
    var isButtonEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(10_000L); isButtonEnabled = true }

    OverlayScaffold(fieldColor = FgColor.Mist) {
        Text("Before you open $appName", style = MaterialTheme.typography.headlineLarge, color = FgColor.Ink)
        Spacer(Modifier.height(12.dp))
        Text("Your top tasks", style = MaterialTheme.typography.titleLarge, color = FgColor.Ink)
        Spacer(Modifier.height(8.dp))
        if (tasks.isEmpty()) {
            Surface(shape = RoundedCornerShape(14.dp), color = FgColor.Paper, border = BorderStroke(1.dp, FgColor.Line), modifier = Modifier.fillMaxWidth()) {
                Text("Can't reach Notion. Check your connection.", color = FgColor.Ink, modifier = Modifier.padding(16.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks.take(3), key = { it.notionPageId }) { task ->
                    Surface(shape = RoundedCornerShape(14.dp), color = FgColor.Paper, border = BorderStroke(1.dp, FgColor.Line), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = task.isChecked, onCheckedChange = { onToggle(task, it) })
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(task.title, color = FgColor.Ink, textDecoration = if (task.isChecked) TextDecoration.LineThrough else null, fontSize = 14.sp)
                                Text(priorityLabel(task.priorityRank), color = FgColor.Slate, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Slow-filling tide line during 10s pause (no digits)
        TideLine(progress = if (isButtonEnabled) 1f else 0.3f)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onContinue, enabled = isButtonEnabled, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = FgColor.Ink, disabledContainerColor = FgColor.Line)) {
            Text("Continue")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) { Text("Go home", color = FgColor.Lock) }
    }
}

private fun priorityLabel(rank: Int) = when (rank) { 1 -> "High"; 2 -> "Medium"; else -> "Low" }

@Composable
fun TideLine(progress: Float) {
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp)
        .semantics { contentDescription = "Progress ${(progress * 100).toInt()} percent" },
        color = FgColor.Tide, trackColor = FgColor.Line)
}

@Composable
fun OverlayScaffold(
    fieldColor: androidx.compose.ui.graphics.Color = FgColor.LockField,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(fieldColor)
            .windowInsetsPadding(WindowInsets.systemBars)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()), content = content)
    }
}
