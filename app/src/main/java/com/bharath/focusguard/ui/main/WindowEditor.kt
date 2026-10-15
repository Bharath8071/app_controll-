package com.bharath.focusguard.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bharath.focusguard.data.local.entities.BlockWindow
import com.bharath.focusguard.ui.theme.FgColor

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun WindowEditorDialog(
    initial: BlockWindow? = null,
    existing: List<BlockWindow> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (BlockWindow) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var mask by remember { mutableStateOf(initial?.daysMask ?: 31) }
    var startH by remember { mutableStateOf((initial?.startMinute ?: 540) / 60) }
    var startM by remember { mutableStateOf((initial?.startMinute ?: 540) % 60) }
    var endH by remember { mutableStateOf((initial?.endMinute ?: 1020) / 60) }
    var endM by remember { mutableStateOf((initial?.endMinute ?: 1020) % 60) }
    val startMin = startH * 60 + startM
    val endMin = endH * 60 + endM
    val overlap = remember(mask, startMin, endMin, existing) {
        existing.filter { it.id != initial?.id && it.enabled }.any { w ->
            (w.daysMask and mask) != 0 && rangesOverlap(startMin, endMin, w.startMinute, w.endMinute)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add block window" else "Edit block window") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_LABELS.forEachIndexed { i, label ->
                        val bit = 1 shl i
                        FilterChip(selected = mask and bit != 0, onClick = { mask = mask xor bit }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Start ${startH}:${"%02d".format(startM)}  End ${endH}:${"%02d".format(endM)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = startH.toString(), onValueChange = { startH = it.toIntOrNull()?.coerceIn(0, 23) ?: startH },
                        label = { Text("Start h") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = endH.toString(), onValueChange = { endH = it.toIntOrNull()?.coerceIn(0, 23) ?: endH },
                        label = { Text("End h") }, modifier = Modifier.weight(1f))
                }
                if (mask == 0) Text("Choose at least one day.", color = FgColor.Flag)
                if (startMin == endMin) Text("Start and end can't be the same time.", color = FgColor.Flag)
                if (overlap) Text("This overlaps another window.", color = FgColor.Flag)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(BlockWindow(id = initial?.id ?: 0, name = name.ifBlank { "Block" },
                daysMask = mask, startMinute = startMin, endMinute = endMin)) },
                enabled = mask != 0 && startMin != endMin) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun rangesOverlap(s1: Int, e1: Int, s2: Int, e2: Int): Boolean {
    fun split(s: Int, e: Int): List<IntRange> = if (e > s) listOf(s until e) else listOf(s until 1440, 0 until e)
    return split(s1, e1).any { a -> split(s2, e2).any { b -> a.first < b.last && b.first < a.last } }
}
