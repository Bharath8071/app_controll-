package com.bharath.focusguard.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharath.focusguard.data.local.entities.BlockWindow
import com.bharath.focusguard.ui.theme.*
import com.bharath.focusguard.util.WindowLogic
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ══════════════════════════════════════════════════════════════════
//  HOME CARDS — today, next block, streak, usage
// ══════════════════════════════════════════════════════════════════

@Composable
fun HomeCards(
    minutesLeft: Int,
    budgetTotal: Int,
    windows: List<BlockWindow>,
    streak: Int,
    onBlockNow: () -> Unit,
    onOpenAnalytics: () -> Unit,
    usageGranted: Boolean = false,
    usageTop: List<Pair<String, Long>> = emptyList(),
    onRequestUsage: () -> Unit = {},
    appLabels: Map<String, String> = emptyMap(),
) {
    // ── Today Card (yellow sticky note, rotated slightly) ──────────
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(-0.7f)
            .background(FgColor.YellowPad, RoundedCornerShape(8.dp))
            .sketchBorder(FgColor.Ink, strokeWidth = 2.dp, cornerRadius = 8.dp)
            .padding(16.dp),
    ) {
        // Washi tape
        Box(modifier = Modifier.align(Alignment.TopCenter).offset(y = (-14).dp)) {
            WashiTape(width = 70.dp, height = 18.dp)
        }
        Column(modifier = Modifier.rotate(0.7f)) {
            MetaLabel("today's budget")
            Spacer(Modifier.height(4.dp))
            Text(
                if (minutesLeft <= 0) "0 min left" else "$minutesLeft min left",
                style = MaterialTheme.typography.displayLarge,
                color = FgColor.Ink,
                fontWeight = FontWeight.Bold,
            )
            if (budgetTotal > 0) {
                Text(
                    "of ${budgetTotal}m total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FgColor.Slate,
                )
                Spacer(Modifier.height(10.dp))
                TideLine(
                    progress = if (budgetTotal > 0) 1f - minutesLeft.toFloat() / budgetTotal else 0f,
                    color = FgColor.Red,
                    trackColor = FgColor.Ink.copy(0.18f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SketchButton(
                    onClick = onBlockNow,
                    text = "block now",
                    fillColor = FgColor.Ink,
                    textColor = FgColor.Paper,
                    modifier = Modifier.height(40.dp),
                )
                SketchButton(
                    onClick = onOpenAnalytics,
                    text = "analytics →",
                    hatched = true,
                    hatchColor1 = FgColor.BgDot,
                    hatchColor2 = FgColor.Line,
                    textColor = FgColor.Ink,
                    modifier = Modifier.height(40.dp),
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    // ── Streak Card (peach index tab style) ───────────────────────
    val next = remember(windows) { WindowLogic.nextWindowStart(LocalDateTime.now(), windows) }
    val active = remember(windows) { WindowLogic.activeWindow(LocalDateTime.now(), windows) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Streak
        Box(
            modifier = Modifier
                .weight(1f)
                .background(FgColor.Peach, RoundedCornerShape(14.dp))
                .sketchBorder(FgColor.Ink, strokeWidth = 2.dp, cornerRadius = 14.dp)
                .padding(14.dp),
        ) {
            Column {
                MetaLabel("streak")
                Spacer(Modifier.height(4.dp))
                Text(
                    if (streak <= 0) "starts today!" else "${streak}d 🔥",
                    style = MaterialTheme.typography.headlineMedium,
                    color = FgColor.Ink,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Next block
        Box(
            modifier = Modifier
                .weight(1f)
                .background(FgColor.BlueSoft, RoundedCornerShape(14.dp))
                .sketchBorder(FgColor.Blue, strokeWidth = 2.dp, cornerRadius = 14.dp)
                .padding(14.dp),
        ) {
            Column {
                MetaLabel("next block", color = FgColor.Blue.copy(0.7f))
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        active != null -> "active now!"
                        next  != null  -> next.format(
                            java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm")
                        )
                        else           -> "none set"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = FgColor.Blue,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    // ── Usage Card ────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(FgColor.Paper, RoundedCornerShape(16.dp))
            .sketchBorder(FgColor.Ink, strokeWidth = 2.dp, cornerRadius = 16.dp)
            .padding(14.dp),
    ) {
        Column {
            MetaLabel("this week in your apps")
            Spacer(Modifier.height(6.dp))
            if (!usageGranted) {
                Text("turn on usage access to see where your time goes.",
                    color = FgColor.Slate, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                SketchButton(
                    onClick = onRequestUsage,
                    text = "turn on",
                    fillColor = FgColor.Ink,
                    textColor = FgColor.Paper,
                    modifier = Modifier.height(38.dp),
                )
            } else if (usageTop.isEmpty()) {
                Text("no usage yet this week.", color = FgColor.Slate, style = MaterialTheme.typography.bodyMedium)
            } else {
                val maxMs = usageTop.maxOf { it.second }.coerceAtLeast(1L)
                usageTop.take(5).forEach { (pkg, ms) ->
                    val mins = (ms / 60_000L).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            appLabels[pkg] ?: shortAppName(pkg),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = FgColor.Ink,
                        )
                        PillTag("${mins}m", bgColor = FgColor.Yellow)
                    }
                    TideLine(
                        progress = mins.toFloat() / (maxMs / 60_000L).coerceAtLeast(1L),
                        color = FgColor.Green,
                        trackColor = FgColor.Line,
                        height = 5.dp,
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

// ══════════════════════════════════════════════════════════════════
//  ANALYTICS SCREEN — sketchy bar chart
// ══════════════════════════════════════════════════════════════════

@Composable
fun AnalyticsScreen(
    weekly: List<com.bharath.focusguard.data.local.entities.AppDaily>,
    budgetPerDay: Int,
    usageGranted: Boolean = false,
    realWeekly: List<com.bharath.focusguard.data.local.entities.AppDaily> = emptyList(),
    onRequestUsage: () -> Unit = {},
    appLabels: Map<String, String> = emptyMap(),
) {
    // Prefer real phone usage when Usage Access is granted; otherwise fall
    // back to FocusGuard's own session minutes.
    val usingRealUsage = usageGranted && realWeekly.isNotEmpty()
    val data = if (usingRealUsage) realWeekly else weekly
    val today = LocalDate.now()

    // The chart always shows the last 7 days. Tapping a bar selects that day
    // and updates the per-app breakdown below; the default is today.
    var selectedDay by remember { mutableStateOf(today) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("analytics", style = MaterialTheme.typography.headlineLarge, color = FgColor.Ink)
        }

        if (!usageGranted) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FgColor.YellowPad, RoundedCornerShape(12.dp))
                        .sketchBorder(FgColor.Ink.copy(0.4f), strokeWidth = 1.5.dp, cornerRadius = 12.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "usage access off — showing FocusGuard sessions only",
                        modifier = Modifier.weight(1f),
                        color = FgColor.Ink,
                        fontSize = 11.sp,
                    )
                    TextButton(onClick = onRequestUsage) {
                        Text("turn on", color = FgColor.Blue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            // Bar chart card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FgColor.Paper, RoundedCornerShape(16.dp))
                    .sketchBorder(FgColor.Ink, strokeWidth = 2.dp, cornerRadius = 16.dp)
                    .padding(14.dp),
            ) {
                Column {
                    MetaLabel(
                        if (usingRealUsage) "phone usage — last 7 days"
                        else "focus sessions — last 7 days"
                    )
                    Spacer(Modifier.height(10.dp))

                    val days = remember(data, today) {
                        (0..6).map { d ->
                            val day = today.minusDays((6 - d).toLong())
                            day to data.filter { it.dateEpochDay == day.toEpochDay() }.sumOf { it.minutes }
                        }
                    }
                    val maxDay = (days.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                    // Scale to the tallest bar OR the budget line, whichever is
                    // bigger, so the guide line is never pinned incorrectly.
                    val scale = maxOf(maxDay, budgetPerDay, 1)

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .pointerInput(days) {
                                detectTapGestures { offset ->
                                    val index = (offset.x / (size.width / 7f))
                                        .toInt()
                                        .coerceIn(0, 6)
                                    selectedDay = days[index].first
                                }
                            }
                            .semantics {
                                contentDescription = days.joinToString {
                                    "${it.first.dayOfWeek.name.take(2)}: ${it.second}m"
                                } + ". Tap a bar to see that day's apps."
                            }
                    ) {
                        val bw = size.width / 7f
                        days.forEachIndexed { i, (day, mins) ->
                            val barH = size.height * 0.85f * (mins.toFloat() / scale)
                            val left = i * bw + bw * 0.15f
                            val barWidth = bw * 0.70f
                            val isSelected = day == selectedDay

                            // Selected-day column highlight
                            if (isSelected) {
                                drawRect(
                                    color = FgColor.Blue.copy(alpha = 0.09f),
                                    topLeft = Offset(i * bw, 0f),
                                    size = Size(bw, size.height * 0.86f),
                                )
                            }

                            // Bar fill
                            if (barH > 0) {
                                val overBudget = budgetPerDay > 0 && mins > budgetPerDay
                                val barColor = if (overBudget) FgColor.Red else FgColor.Green
                                drawRect(
                                    color = barColor,
                                    topLeft = Offset(left, size.height * 0.85f - barH),
                                    size = Size(barWidth, barH),
                                )
                                // Sketch-style top border; blue and thicker when selected
                                drawLine(
                                    color = if (isSelected) FgColor.Blue else FgColor.Ink,
                                    start = Offset(left, size.height * 0.85f - barH),
                                    end = Offset(left + barWidth, size.height * 0.85f - barH),
                                    strokeWidth = if (isSelected) 4f else 2.5f,
                                )
                            }
                        }
                        // Budget guide line
                        if (budgetPerDay > 0) {
                            val bh = size.height * 0.85f * (budgetPerDay.toFloat() / scale)
                            drawLine(
                                FgColor.Blue,
                                Offset(0f, size.height * 0.85f - bh),
                                Offset(size.width, size.height * 0.85f - bh),
                                strokeWidth = 2.5f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
                            )
                        }
                        // Baseline
                        drawLine(FgColor.Ink, Offset(0f, size.height * 0.86f), Offset(size.width, size.height * 0.86f), strokeWidth = 2f)
                    }

                    // Day labels under the bars. The old chart created a
                    // fixed Mo–Su label list but never drew it, so the bars
                    // had no dates and could not match the day they showed.
                    Row(modifier = Modifier.fillMaxWidth()) {
                        days.forEach { (day, _) ->
                            val label = day.dayOfWeek.name.take(2)
                                .lowercase()
                                .replaceFirstChar { it.uppercase() }
                            val isSelected = day == selectedDay
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = 10.sp,
                                color = when {
                                    isSelected -> FgColor.Blue
                                    day == today -> FgColor.Ink
                                    else -> FgColor.Slate
                                },
                                fontWeight = if (isSelected || day == today) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    val selectedMinutes = days.firstOrNull { it.first == selectedDay }?.second ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                dayDisplayLabel(selectedDay, today),
                                style = MaterialTheme.typography.titleMedium,
                                color = FgColor.Ink,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "tap a bar to see that day's apps",
                                color = FgColor.Slate,
                                fontSize = 10.sp,
                            )
                        }
                        Text(
                            formatMinutes(selectedMinutes),
                            style = MaterialTheme.typography.titleLarge,
                            color = FgColor.Ink,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (usingRealUsage) "from Android usage access"
                        else "recorded by FocusGuard sessions — enable usage access for real phone time",
                        color = FgColor.Slate,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        item {
            MetaLabel(appDayHeader(selectedDay, today))
        }

        val byApp = data.filter { it.dateEpochDay == selectedDay.toEpochDay() }
            .groupBy { it.packageName }
            .mapValues { (_, v) -> v.sumOf { it.minutes } }
            .toList().sortedByDescending { it.second }.take(10)

        if (byApp.isEmpty()) {
            item {
                Text("nothing recorded for this day.", color = FgColor.Slate, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(byApp) { (pkg, mins) ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FgColor.Paper, RoundedCornerShape(12.dp))
                        .sketchBorder(FgColor.Ink.copy(0.4f), strokeWidth = 1.5.dp, cornerRadius = 12.dp)
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            appLabels[pkg] ?: shortAppName(pkg),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = FgColor.Ink,
                        )
                        PillTag(
                            formatMinutes(mins),
                            bgColor = FgColor.Yellow,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ══════════════════════════════════════════════════════════════════
//  BLOCK WINDOWS SCREEN — sketch list of time-block windows
// ══════════════════════════════════════════════════════════════════

@Composable
fun BlockWindowsScreen(
    windows: List<BlockWindow>,
    activeNow: Boolean,
    onToggle: (BlockWindow) -> Unit,
    onDelete: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text("block windows", style = MaterialTheme.typography.headlineLarge, color = FgColor.Ink)
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(windows, key = { it.id }) { w ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (w.enabled) FgColor.GreenSoft else FgColor.Paper,
                            RoundedCornerShape(14.dp),
                        )
                        .sketchBorder(
                            if (w.enabled) FgColor.Green else FgColor.Line,
                            strokeWidth = 2.dp,
                            cornerRadius = 14.dp,
                        )
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                w.name.ifBlank { "Block" },
                                style = MaterialTheme.typography.titleMedium,
                                color = FgColor.Ink,
                            )
                            Text(
                                "${w.startMinute / 60}:${"%02d".format(w.startMinute % 60)} – ${w.endMinute / 60}:${"%02d".format(w.endMinute % 60)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = FgColor.Slate,
                            )
                        }
                        if (activeNow) {
                            PillTag("on", bgColor = FgColor.GreenSoft, textColor = FgColor.TideInk)
                        } else {
                            Switch(
                                checked = w.enabled,
                                onCheckedChange = { onToggle(w.copy(enabled = it)) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = FgColor.Paper,
                                    checkedTrackColor = FgColor.Green,
                                    uncheckedThumbColor = FgColor.Slate,
                                    uncheckedTrackColor = FgColor.Line,
                                ),
                            )
                            TextButton(onClick = { onDelete(w.id) }) {
                                Text("delete", color = FgColor.Red, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        if (activeNow) {
            Text("block is active. leave early from the block screen.", color = FgColor.Slate,
                style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))
        SketchButton(
            onClick = onAdd,
            text = "+ add block window",
            fillColor = FgColor.Ink,
            textColor = FgColor.Paper,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )
    }
}

/**
 * Human-friendly fallback when the monitored-app label map is unavailable.
 * `com.instagram.android` would otherwise display as "android" because the
 * last package segment is the generic ".android" suffix.
 */
private fun shortAppName(pkg: String): String {
    val parts = pkg.split('.')
    return when {
        parts.size >= 2 && parts.last() == "android" -> parts[parts.size - 2]
        else -> parts.lastOrNull()?.takeIf { it.isNotBlank() } ?: pkg
    }
}

private fun formatMinutes(mins: Int): String =
    if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"

private fun dayDisplayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "today"
    today.minusDays(1) -> "yesterday"
    else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM"))
}

private fun appDayHeader(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "by app today"
    today.minusDays(1) -> "by app yesterday"
    else -> "by app — " + day.format(DateTimeFormatter.ofPattern("EEE d MMM"))
}
