package com.bharath.focusguard.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bharath.focusguard.data.local.entities.NotionTask

@Composable
fun ChecklistScreen(
    appName: String,
    tasks: List<NotionTask>,
    onToggle: (NotionTask, Boolean) -> Unit,
    onContinue: () -> Unit
) {
    OverlayScaffold {
        Text("Before $appName", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Today's tasks — optional reminder. Continue whenever you're ready.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        if (tasks.isEmpty()) {
            Text("No cached tasks. Connect Notion or continue anyway.")
        } else {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(tasks, key = { it.notionPageId }) { task ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = task.isChecked,
                            onCheckedChange = { onToggle(task, it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(task.title)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}

@Composable
fun OverlayScaffold(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6121212)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(Modifier.padding(20.dp), content = content)
        }
    }
}
