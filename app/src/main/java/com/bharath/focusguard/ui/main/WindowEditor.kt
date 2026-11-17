package com.bharath.focusguard.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharath.focusguard.data.local.entities.BlockWindow
import com.bharath.focusguard.ui.theme.FgColor
import com.bharath.focusguard.ui.theme.sketchBorder

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@OptIn(ExperimentalMaterial3Api::class)
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
        containerColor = FgColor.Paper,
        title = {
            Text(
                if (initial == null) "Add Block Window" else "Edit Block Window",
                color = FgColor.Ink,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FgColor.Blue,
                        unfocusedBorderColor = FgColor.Line,
                        focusedTextColor = FgColor.Ink,
                        unfocusedTextColor = FgColor.Ink
                    )
                )

                // Day selector — sketch chip row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DAY_LABELS.forEachIndexed { i, label ->
                        val bit = 1 shl i
                        val selected = mask and bit != 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) FgColor.YellowPad else FgColor.BgDot)
                                .sketchBorder(
                                    color = if (selected) FgColor.Ink else FgColor.Line,
                                    strokeWidth = 1.5.dp,
                                    cornerRadius = 8.dp
                                )
                                .clickable { mask = mask xor bit },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = FgColor.Ink,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Text(
                    "Start $startH:%02d  End $endH:%02d".format(startM, endM),
                    color = FgColor.Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startH.toString(),
                        onValueChange = { startH = it.toIntOrNull()?.coerceIn(0, 23) ?: startH },
                        label = { Text("Start h") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FgColor.Blue,
                            unfocusedBorderColor = FgColor.Line,
                            focusedTextColor = FgColor.Ink,
                            unfocusedTextColor = FgColor.Ink
                        )
                    )
                    OutlinedTextField(
                        value = endH.toString(),
                        onValueChange = { endH = it.toIntOrNull()?.coerceIn(0, 23) ?: endH },
                        label = { Text("End h") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FgColor.Blue,
                            unfocusedBorderColor = FgColor.Line,
                            focusedTextColor = FgColor.Ink,
                            unfocusedTextColor = FgColor.Ink
                        )
                    )
                }

                if (mask == 0) Text("Pick at least one day.", color = FgColor.Flag, fontSize = 12.sp)
                if (startMin == endMin) Text("Start and end can't be the same.", color = FgColor.Flag, fontSize = 12.sp)
                if (overlap) Text("This overlaps another window.", color = FgColor.Flag, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        BlockWindow(
                            id = initial?.id ?: 0,
                            name = name.ifBlank { "Block" },
                            daysMask = mask,
                            startMinute = startMin,
                            endMinute = endMin
                        )
                    )
                },
                enabled = mask != 0 && startMin != endMin && !overlap,
                colors = ButtonDefaults.buttonColors(containerColor = FgColor.Blue)
            ) { Text("Save", color = FgColor.Paper) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = FgColor.Slate) }
        },
    )
}

private fun rangesOverlap(s1: Int, e1: Int, s2: Int, e2: Int): Boolean {
    fun split(s: Int, e: Int): List<IntRange> = if (e > s) listOf(s until e) else listOf(s until 1440, 0 until e)
    return split(s1, e1).any { a -> split(s2, e2).any { b -> a.first < b.last && b.first < a.last } }
}
