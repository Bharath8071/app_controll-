package com.bharath.focusguard.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TimePickerScreen(
    appName: String,
    minutesLeft: Int,
    onPicked: (Int) -> Unit,
    onGoHome: () -> Unit = {}
) {
    val presets = listOf(5, 10, 20)
    val effectiveBudget = minutesLeft.coerceAtLeast(1)
    var selectedMinutes by remember {
        mutableStateOf(presets.firstOrNull { it <= effectiveBudget } ?: effectiveBudget)
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
                text = "⏳ Session Timer • $appName",
                color = Color(0xFFA5B4FC),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Set an Intentional Limit",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        // Budget remaining pill
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF10B981).copy(alpha = 0.15f),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "● $minutesLeft min left in today's budget",
                    color = Color(0xFF34D399),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "SELECT DURATION",
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(8.dp))

        // Preset Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                val isAvailable = preset <= effectiveBudget
                val isSelected = selectedMinutes == preset

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clickable(enabled = isAvailable) {
                            selectedMinutes = preset
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isSelected -> Color(0xFF6366F1)
                        !isAvailable -> Color(0xFF1E293B).copy(alpha = 0.4f)
                        else -> Color(0xFF1E293B)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isSelected -> Color(0xFF818CF8)
                            !isAvailable -> Color(0xFF334155).copy(alpha = 0.4f)
                            else -> Color(0xFF334155)
                        }
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${preset}m",
                            color = when {
                                isSelected -> Color.White
                                !isAvailable -> Color(0xFF475569)
                                else -> Color(0xFFE2E8F0)
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Touch Stepper for custom minutes (No keyboard required)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(
                    onClick = {
                        selectedMinutes = (selectedMinutes - 1).coerceAtLeast(1)
                    },
                    enabled = selectedMinutes > 1,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White
                    )
                ) {
                    Text("−", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$selectedMinutes min",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "tap + / − to adjust",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        selectedMinutes = (selectedMinutes + 1).coerceAtMost(effectiveBudget)
                    },
                    enabled = selectedMinutes < effectiveBudget,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White
                    )
                ) {
                    Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        val context = androidx.compose.ui.platform.LocalContext.current
        val closeTimePreview = remember(selectedMinutes, effectiveBudget) {
            val mins = selectedMinutes.coerceIn(1, effectiveBudget)
            val format = android.text.format.DateFormat.getTimeFormat(context)
            format.format(java.util.Date(System.currentTimeMillis() + (mins * 60_000L)))
        }

        Button(
            onClick = { onPicked(selectedMinutes.coerceIn(1, effectiveBudget)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6366F1),
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Unlock until $closeTimePreview ➔",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
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
            Text("Cancel & Return Home", fontWeight = FontWeight.Medium)
        }
    }
}
