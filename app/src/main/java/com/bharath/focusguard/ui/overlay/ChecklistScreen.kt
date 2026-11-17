package com.bharath.focusguard.ui.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharath.focusguard.data.local.entities.NotionTask
import com.bharath.focusguard.ui.theme.*
import kotlinx.coroutines.delay

// ══════════════════════════════════════════════════════════════════
//  CHECKLIST SCREEN — sticky-note style with washi tape
// ══════════════════════════════════════════════════════════════════

@Composable
fun ChecklistScreen(
    appName: String,
    tasks: List<NotionTask>,
    onToggle: (NotionTask, Boolean) -> Unit,
    onContinue: () -> Unit,
    onGoHome: () -> Unit = {},
) {
    // 10-second lock before Continue button activates
    var isButtonEnabled by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(10) }
    LaunchedEffect(Unit) {
        repeat(10) {
            delay(1000L)
            countdown--
        }
        isButtonEnabled = true
    }
    val tideProgress by animateFloatAsState(
        targetValue = if (isButtonEnabled) 1f else (10 - countdown) / 10f,
        animationSpec = tween(900, easing = LinearEasing),
        label = "tide",
    )

    // Full-screen parchment canvas with dot grid
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FgColor.BgDot)
            .dotGridBackground()
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        // Sticky-note card — slightly rotated, yellow bg
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .rotate(-1.1f)
                .background(FgColor.YellowPad, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .sketchBorder(FgColor.Ink, strokeWidth = 2.dp, cornerRadius = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(1.1f)  // counter-rotate content so text is straight
                    .padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header
                Column {
                    MetaLabel("before you open")
                    Text(
                        appName,
                        style = MaterialTheme.typography.headlineLarge,
                        color = FgColor.Ink,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    "your top tasks",
                    style = MaterialTheme.typography.titleMedium,
                    color = FgColor.Slate,
                )

                // Task list
                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FgColor.Paper.copy(0.7f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .sketchBorder(FgColor.Ink.copy(0.4f), strokeWidth = 1.5.dp, cornerRadius = 12.dp)
                            .padding(14.dp),
                    ) {
                        Text(
                            "can't reach Notion. check your connection.",
                            color = FgColor.Slate,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    tasks.take(3).forEach { task ->
                        SketchTaskRow(task = task, onToggle = onToggle)
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Tide progress bar (10s countdown)
                TideLine(
                    progress = tideProgress,
                    color = FgColor.Ink,
                    trackColor = FgColor.Ink.copy(0.18f),
                    height = 5.dp,
                )

                Spacer(Modifier.height(4.dp))

                // Continue button
                SketchButton(
                    onClick = onContinue,
                    text = if (isButtonEnabled) "continue →" else "wait  $countdown s",
                    fillColor = FgColor.Ink,
                    textColor = FgColor.Paper,
                    enabled = isButtonEnabled,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                )

                // Go home link
                TextButton(
                    onClick = onGoHome,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "go home",
                        color = FgColor.Ink.copy(0.55f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        // Washi tape at top of card
        WashiTape(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-10).dp),
            width = 90.dp,
            height = 22.dp,
        )
    }
}

// ──────────────────────────────────────────────────────────────────
//  Single task row with sketch checkbox
// ──────────────────────────────────────────────────────────────────

@Composable
private fun SketchTaskRow(
    task: NotionTask,
    onToggle: (NotionTask, Boolean) -> Unit,
) {
    val strikeAnim by animateFloatAsState(
        targetValue = if (task.isChecked) 1f else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "strike",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FgColor.Paper.copy(0.6f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .sketchBorder(FgColor.Ink.copy(0.35f), strokeWidth = 1.5.dp, cornerRadius = 12.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SketchCheckbox(
            checked = task.isChecked,
            onCheckedChange = { onToggle(task, it) },
            size = 22.dp,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (task.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                ),
                color = if (task.isChecked) FgColor.Slate else FgColor.Ink,
            )
            Text(
                priorityLabel(task.priorityRank),
                style = MaterialTheme.typography.labelSmall,
                color = FgColor.Slate,
            )
        }
    }
}

private fun priorityLabel(rank: Int) = when (rank) { 1 -> "⬆ high"; 2 -> "→ medium"; else -> "⬇ low" }

// ══════════════════════════════════════════════════════════════════
//  OVERLAY SCAFFOLD — kept for other overlay screens that use it
// ══════════════════════════════════════════════════════════════════

@Composable
fun OverlayScaffold(
    fieldColor: Color = FgColor.BgDot,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(fieldColor)
            .dotGridBackground()
            .windowInsetsPadding(WindowInsets.systemBars)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

// Keep the TideLine accessible from other screens in this package
// (re-exported via import of SketchComponents)
