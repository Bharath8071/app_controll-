package com.bharath.focusguard.ui.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharath.focusguard.ui.theme.*
import kotlinx.coroutines.delay

// ══════════════════════════════════════════════════════════════════
//  TIME PICKER SCREEN — paper card, hand-drawn time buttons
// ══════════════════════════════════════════════════════════════════

@Composable
fun TimePickerScreen(
    appName: String,
    minutesLeft: Int,
    onPicked: (Int) -> Unit,
    onGoHome: () -> Unit = {},
) {
    val presets = listOf(5, 10, 20)
    val effectiveBudget = minutesLeft.coerceAtLeast(1)
    var selected by remember { mutableIntStateOf(presets.firstOrNull { it <= effectiveBudget } ?: effectiveBudget) }

    // Live clock
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000L); now = System.currentTimeMillis() } }
    val previewTime = remember(now, selected) {
        android.text.format.DateFormat.getTimeFormat(context)
            .format(java.util.Date(now + selected * 60_000L))
    }

    // Full-screen parchment canvas with dot grid
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FgColor.BgDot)
            .dotGridBackground()
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        // Paper card — slight rotation
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .rotate(0.8f)
                .background(FgColor.Paper, RoundedCornerShape(18.dp))
                .sketchBorder(FgColor.Ink, strokeWidth = 2.dp, cornerRadius = 18.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .rotate(-0.8f)
                    .padding(horizontal = 22.dp, vertical = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header
                Column {
                    MetaLabel("how long do you need?")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        appName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = FgColor.Ink,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Preset buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    presets.forEach { preset ->
                        val avail = preset <= effectiveBudget
                        val isSelected = selected == preset
                        PresetChip(
                            label = "$preset min",
                            selected = isSelected,
                            enabled = avail,
                            modifier = Modifier.weight(1f),
                            onClick = { if (avail) selected = preset },
                        )
                    }
                }

                if (presets.any { it > effectiveBudget }) {
                    Text(
                        "some presets exceed today's budget",
                        style = MaterialTheme.typography.labelSmall,
                        color = FgColor.Slate,
                    )
                }

                // Fine-tune stepper
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FgColor.BgDot, RoundedCornerShape(14.dp))
                        .sketchBorder(FgColor.Ink.copy(0.4f), strokeWidth = 1.5.dp, cornerRadius = 14.dp)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepperButton(
                        label = "−",
                        enabled = selected > 1,
                        onClick = { selected = (selected - 1).coerceAtLeast(1) },
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$selected",
                            style = MaterialTheme.typography.displayLarge,
                            color = FgColor.Ink,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FgColor.Slate,
                        )
                    }
                    StepperButton(
                        label = "+",
                        enabled = selected < effectiveBudget,
                        onClick = { selected = (selected + 1).coerceAtMost(effectiveBudget) },
                    )
                }

                // "open until XX:XX" preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FgColor.BlueSoft, RoundedCornerShape(10.dp))
                        .sketchBorder(FgColor.Blue, strokeWidth = 1.5.dp, cornerRadius = 10.dp)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "open until $previewTime",
                        style = MaterialTheme.typography.titleMedium,
                        color = FgColor.Blue,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Start button — hatched red-pink fill
                SketchButton(
                    onClick = { onPicked(selected.coerceIn(1, effectiveBudget)) },
                    text = "start →",
                    hatched = true,
                    hatchColor1 = FgColor.Peach,
                    hatchColor2 = FgColor.Pink,
                    textColor = FgColor.Ink,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                )

                // Go home link
                TextButton(
                    onClick = onGoHome,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("go home", color = FgColor.Ink.copy(0.5f), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // Washi tape at top of card
        WashiTape(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-10).dp),
            width = 70.dp,
            height = 20.dp,
            color1 = FgColor.BlueSoft,
            color2 = FgColor.Blue.copy(0.3f),
        )
    }
}

// ──────────────────────────────────────────────────────────────────
//  Preset chip
// ──────────────────────────────────────────────────────────────────

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .background(
                if (selected) FgColor.Ink else if (!enabled) FgColor.Line else FgColor.Paper,
                RoundedCornerShape(10.dp),
            )
            .sketchBorder(
                if (enabled) FgColor.Ink else FgColor.Line,
                strokeWidth = 1.5.dp,
                cornerRadius = 10.dp,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) FgColor.Paper else if (!enabled) FgColor.Slate else FgColor.Ink,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// ──────────────────────────────────────────────────────────────────
//  Round stepper button  (+/−)
// ──────────────────────────────────────────────────────────────────

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "stepScale",
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(if (enabled) FgColor.Paper else FgColor.BgDot, CircleShape)
            .border(2.dp, if (enabled) FgColor.Ink else FgColor.Line, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) FgColor.Ink else FgColor.Line,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )
    }
}
