package com.bharath.focusguard.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharath.focusguard.data.local.entities.NotionTask
import kotlinx.coroutines.delay

@Composable
fun ChecklistScreen(
    appName: String,
    tasks: List<NotionTask>,
    onToggle: (NotionTask, Boolean) -> Unit,
    onContinue: () -> Unit,
    onGoHome: () -> Unit = {}
) {
    // 10-second reflection pause matching Chrome extension (initially disabled, no countdown text)
    var isButtonEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(10_000L) // 10 seconds intentional friction pause
        isButtonEnabled = true
    }

    OverlayScaffold {
        // App header pill
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF6366F1).copy(alpha = 0.2f), Color(0xFF818CF8).copy(alpha = 0.2f))
                    ),
                    shape = CircleShape
                )
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "🛡️ FocusGuard • $appName",
                color = Color(0xFFA5B4FC),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Pause with Intention",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Review your top priority tasks before opening $appName:",
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✨ No pending tasks",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Take a breath. Decide if this visit is truly intentional.",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks.take(3), key = { it.notionPageId }) { task ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (task.isChecked) Color(0xFF1E293B).copy(alpha = 0.6f) else Color(0xFF1E293B),
                        border = BorderStroke(
                            1.dp,
                            if (task.isChecked) Color(0xFF334155) else Color(0xFF475569)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isChecked,
                                onCheckedChange = { onToggle(task, it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF6366F1),
                                    uncheckedColor = Color(0xFF94A3B8),
                                    checkmarkColor = Color.White
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    color = if (task.isChecked) Color(0xFF64748B) else Color(0xFFF1F5F9),
                                    textDecoration = if (task.isChecked) TextDecoration.LineThrough else null,
                                    fontSize = 14.sp,
                                    fontWeight = if (task.isChecked) FontWeight.Normal else FontWeight.Medium
                                )
                                if (task.priorityRank == 1) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "⚡ High Priority",
                                        color = Color(0xFFFBBF24),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Reflection pause button: disabled for 10s without countdown text, auto-enables after 10s
        Button(
            onClick = onContinue,
            enabled = isButtonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6366F1),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF1E293B),
                disabledContentColor = Color(0xFF64748B)
            )
        ) {
            Text("Proceed to Timer ➔", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onGoHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Text("Exit to Home", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun OverlayScaffold(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16)) // 100% opaque solid dark background: completely hides the app underneath
            .windowInsetsPadding(WindowInsets.systemBars)
            .pointerInput(Unit) {
                // Consume all touch events completely so touches never leak to the app underneath
                detectTapGestures { }
            },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                content = content
            )
        }
    }
}
