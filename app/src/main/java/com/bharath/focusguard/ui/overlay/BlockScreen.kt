package com.bharath.focusguard.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharath.focusguard.data.local.entities.MonitoredApp

@Composable
fun BlockScreen(
    app: MonitoredApp,
    isHardBlock: Boolean,
    minutesLeft: Int,
    showExtend: Boolean,
    onGoHome: () -> Unit,
    onExtend: () -> Unit,
    onNewSession: () -> Unit = {}
) {
    OverlayScaffold {
        if (isHardBlock) {
            // HARD BLOCK UI (Daily Budget Depleted)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFF43F5E).copy(alpha = 0.3f), Color(0xFFF43F5E).copy(alpha = 0.05f))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("🔒", fontSize = 28.sp)
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "${app.displayName} is Locked",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Time is completed! Come back tomorrow.",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "You've reached your daily limit of ${app.dailyBudgetMinutes} minutes for today.",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = "💡 Protect your attention",
                        color = Color(0xFFE2E8F0),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Great effort staying disciplined today! Step away, stretch, or focus on your real-world goals.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (showExtend) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "⚡ Emergency Pass Available",
                            color = Color(0xFFFBBF24),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Need to finish something urgent? You get one final 5-minute extension for today.",
                            color = Color(0xFFFDE68A),
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onExtend,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF59E0B),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Use 5-Min Emergency Pass", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
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

        } else {
            // SESSION FINISHED UI (Budget Still Remains)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF6366F1).copy(alpha = 0.3f), Color(0xFF6366F1).copy(alpha = 0.05f))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("⏱️", fontSize = 28.sp)
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Session Complete!",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Your planned focus session on ${app.displayName} is up.",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(14.dp))

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
                    Text(
                        text = "$minutesLeft min remaining in today's budget",
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

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
                Text("Take a Break (Go Home)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onNewSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA5B4FC)),
                border = BorderStroke(1.dp, Color(0xFF475569))
            ) {
                Text("Start Another Session", fontWeight = FontWeight.Medium)
            }
        }
    }
}
