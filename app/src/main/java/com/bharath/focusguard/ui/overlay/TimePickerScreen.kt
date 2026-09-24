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
    val presets = listOf(5, 10, 15, 25)
    var selectedMinutes by remember {
        mutableStateOf(presets.firstOrNull { it <= minutesLeft } ?: minutesLeft.coerceAtLeast(1))
    }
    var customText by remember { mutableStateOf("") }
    val effectiveBudget = minutesLeft.coerceAtLeast(1)

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
                val isSelected = selectedMinutes == preset && customText.isBlank()

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clickable(enabled = isAvailable) {
                            selectedMinutes = preset
                            customText = ""
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

        // Custom minutes input
        OutlinedTextField(
            value = customText,
            onValueChange = { input ->
                val digits = input.filter(Char::isDigit).take(3)
                customText = digits
                digits.toIntOrNull()?.let {
                    selectedMinutes = it.coerceIn(1, effectiveBudget)
                }
            },
            label = { Text("Custom minutes (max $effectiveBudget)") },
            placeholder = { Text("e.g. 8") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF6366F1),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color(0xFFE2E8F0),
                focusedLabelColor = Color(0xFFA5B4FC),
                unfocusedLabelColor = Color(0xFF94A3B8)
            )
        )

        Spacer(Modifier.height(20.dp))

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
                text = "Start Session (${selectedMinutes.coerceIn(1, effectiveBudget)} min) ➔",
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
