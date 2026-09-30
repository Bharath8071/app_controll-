package com.bharath.focusguard.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharath.focusguard.data.local.entities.MonitoredApp
import kotlinx.coroutines.delay

/**
 * Separate, dedicated block screen displayed when a planned session ends.
 * Enforces a standard 10-minute cooldown pause before the user can enter the app again,
 * even if they have remaining total daily budget.
 */
@Composable
fun CooldownBlockScreen(
    app: MonitoredApp,
    cooldownExpiresAtMillis: Long,
    minutesLeft: Int,
    onGoHome: () -> Unit,
    onCooldownFinished: () -> Unit = {}
) {
    var remainingMillis by remember(cooldownExpiresAtMillis) {
        mutableStateOf((cooldownExpiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    LaunchedEffect(cooldownExpiresAtMillis) {
        while (true) {
            val rem = (cooldownExpiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            remainingMillis = rem
            if (rem <= 0L) {
                break
            }
            delay(500L)
        }
    }

    val isFinished = remainingMillis <= 0L

    OverlayScaffold {
        // Icon Header with gentle amber/orange glow
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(
                            Color(0xFFF59E0B).copy(alpha = 0.35f),
                            Color(0xFFF59E0B).copy(alpha = 0.05f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("☕", fontSize = 30.sp)
        }

        Spacer(Modifier.height(14.dp))

        // Badge Pill
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
        ) {
            Text(
                text = "10-MINUTE COOLDOWN ACTIVE",
                color = Color(0xFFFBBF24),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "${app.displayName} is Paused",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Your session ended. A standard 10-minute break is active to break mindless habit loops.",
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        )

        Spacer(Modifier.height(18.dp))

        // Daily Budget Remaining Pill
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF10B981).copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📊", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "$minutesLeft min remaining in today's budget",
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Your budget is safe. App is paused for 10 min to reset dopamine.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (isFinished) {
            Button(
                onClick = onCooldownFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.White
                )
            ) {
                Text("Break Finished — Unlock Now ➔", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = onGoHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6366F1),
                contentColor = Color.White
            )
        ) {
            Text("Exit to Home Screen", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}
