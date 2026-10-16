package com.bharath.focusguard.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bharath.focusguard.data.local.entities.BlockWindow
import com.bharath.focusguard.ui.theme.FgColor
import com.bharath.focusguard.util.WindowLogic
import java.time.LocalDate
import java.time.LocalDateTime

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
) {
    // Today card
    Surface(shape = RoundedCornerShape(14.dp), color = FgColor.Paper, border = BorderStroke(1.dp, FgColor.Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Today", style = MaterialTheme.typography.titleLarge, color = FgColor.Ink)
            Text(if (minutesLeft <= 0) "0 min left. Your budget resets at midnight." else "$minutesLeft min left",
                style = MaterialTheme.typography.displayLarge, color = FgColor.Ink)
            LinearProgressIndicator(progress = { if (budgetTotal > 0) 1f - minutesLeft.toFloat() / budgetTotal else 0f },
                modifier = Modifier.fillMaxWidth(), color = FgColor.Tide, trackColor = FgColor.Line)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBlockNow) { Text("Block now") }
        }
    }
    Spacer(Modifier.height(12.dp))
    // Next block card
    val next = remember(windows) { WindowLogic.nextWindowStart(LocalDateTime.now(), windows) }
    val active = remember(windows) { WindowLogic.activeWindow(LocalDateTime.now(), windows) }
    Surface(shape = RoundedCornerShape(14.dp), color = FgColor.Paper, border = BorderStroke(1.dp, FgColor.Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Next block", style = MaterialTheme.typography.titleLarge, color = FgColor.Ink)
            when {
                active != null -> Text("Block on until end.", color = FgColor.Ink)
                next != null -> Text(next.toString(), color = FgColor.Ink)
                else -> Text("No block windows yet. Add one to close your apps during work hours.", color = FgColor.Slate)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    // Streak card
    Surface(shape = RoundedCornerShape(14.dp), color = FgColor.Paper, border = BorderStroke(1.dp, FgColor.Line),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Streak $streak days" }) {
        Column(Modifier.padding(16.dp)) {
            Text("Streak", style = MaterialTheme.typography.titleLarge, color = FgColor.Ink)
            Text(if (streak <= 0) "Your streak starts today." else "$streak days", color = FgColor.Ink)
        }
    }
    Spacer(Modifier.height(12.dp))
    // Phase 6: usage report card (real phone-wide screen time, optional permission)
    Surface(shape = RoundedCornerShape(14.dp), color = FgColor.Paper, border = BorderStroke(1.dp, FgColor.Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("This week in your apps", style = MaterialTheme.typography.titleLarge, color = FgColor.Ink)
            if (!usageGranted) {
                Text("Turn on usage access to see where your time goes.", color = FgColor.Slate)
                OutlinedButton(onClick = onRequestUsage) { Text("Turn on") }
            } else if (usageTop.isEmpty()) {
                Text("No usage yet this week.", color = FgColor.Slate)
            } else {
                val maxMs = usageTop.maxOf { it.second }.coerceAtLeast(1L)
                usageTop.take(5).forEach { (pkg, ms) ->
                    val mins = (ms / 60_000L).toInt()
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(pkg.substringAfterLast("."), Modifier.weight(1f), color = FgColor.Ink)
                        Text("$mins m", color = FgColor.Ink)
                    }
                    LinearProgressIndicator(progress = { mins.toFloat() / (maxMs / 60_000L).coerceAtLeast(1L) },
                        modifier = Modifier.fillMaxWidth(), color = FgColor.Tide, trackColor = FgColor.Line)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onOpenAnalytics) { Text("Monitored apps") }
}

@Composable
fun AnalyticsScreen(weekly: List<com.bharath.focusguard.data.local.entities.AppDaily>, budgetPerDay: Int) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Analytics", style = MaterialTheme.typography.headlineLarge, color = FgColor.Ink)
        Spacer(Modifier.height(12.dp))
        Text("Time in your apps", style = MaterialTheme.typography.titleLarge, color = FgColor.Ink)
        // 7 bars: minutes used per day (last 7 days)
        val days = remember(weekly) {
            val today = LocalDate.now()
            (0..6).map { d ->
                val day = today.minusDays((6 - d).toLong())
                day to weekly.filter { it.dateEpochDay == day.toEpochDay() }.sumOf { it.minutes }
            }
        }
        val max = (days.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)
            .semantics { contentDescription = days.joinToString { "${it.first.dayOfWeek}: ${it.second} minutes" } }) {
            val bw = size.width / 7
            days.forEachIndexed { i, (_, mins) ->
                val h = size.height * (mins.toFloat() / max)
                drawRect(FgColor.Tide, topLeft = androidx.compose.ui.geometry.Offset(i * bw + bw * 0.2f, size.height - h),
                    size = androidx.compose.ui.geometry.Size(bw * 0.6f, h))
            }
            // budget dashed line approximation: solid thin Lock line
            val bh = size.height * (budgetPerDay.coerceAtLeast(1).toFloat() / max.coerceAtLeast(budgetPerDay))
            drawLine(FgColor.Lock, start = androidx.compose.ui.geometry.Offset(0f, size.height - bh),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height - bh), strokeWidth = 3f)
        }
        Spacer(Modifier.height(12.dp))
        Text("By app, this week", style = MaterialTheme.typography.titleLarge, color = FgColor.Ink)
        val byApp = weekly.groupBy { it.packageName }.mapValues { (_, v) -> v.sumOf { it.minutes } }.toList().sortedByDescending { it.second }.take(10)
        if (byApp.isEmpty()) Text("Nothing to show yet. Your first week of data will appear here.", color = FgColor.Slate)
        byApp.forEach { (pkg, mins) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pkg.substringAfterLast("."), Modifier.weight(1f), color = FgColor.Ink)
                Text(if (mins >= 60) "${mins / 60} h ${mins % 60} m" else "$mins m", color = FgColor.Ink)
            }
        }
    }
}

@Composable
fun BlockWindowsScreen(
    windows: List<BlockWindow>,
    activeNow: Boolean,
    onToggle: (BlockWindow) -> Unit,
    onDelete: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Block windows", style = MaterialTheme.typography.headlineLarge, color = FgColor.Ink)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(windows, key = { it.id }) { w ->
                Surface(shape = RoundedCornerShape(14.dp), color = FgColor.Paper, border = BorderStroke(1.dp, FgColor.Line), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(w.name.ifBlank { "Block" }, color = FgColor.Ink)
                            Text("${w.startMinute / 60}:${"%02d".format(w.startMinute % 60)}â€“${w.endMinute / 60}:${"%02d".format(w.endMinute % 60)}", color = FgColor.Slate)
                        }
                        if (activeNow) {
                            Text("On", color = FgColor.TideInk)
                        } else {
                            Switch(checked = w.enabled, onCheckedChange = { onToggle(w.copy(enabled = it)) })
                            TextButton(onClick = { onDelete(w.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
        if (activeNow) Text("This block is on. You can leave early from the block screen.", color = FgColor.Slate)
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("+ Add block window") }
    }
}
