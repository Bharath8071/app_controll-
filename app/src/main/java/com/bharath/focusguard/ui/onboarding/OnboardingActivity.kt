package com.bharath.focusguard.ui.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bharath.focusguard.ui.main.FocusGuardTheme
import com.bharath.focusguard.ui.main.MainActivity
import com.bharath.focusguard.util.PermissionUtils

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FocusGuardTheme {
                OnboardingScreen(
                    onDone = {
                        startActivity(android.content.Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var accessibilityGranted by remember { mutableStateOf(PermissionUtils.hasAccessibilityPermission(context)) }
    var overlayGranted by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    var adminGranted by remember { mutableStateOf(PermissionUtils.hasDeviceAdminPermission(context)) }

    // Re-check every time the user returns from system Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityGranted = PermissionUtils.hasAccessibilityPermission(context)
                overlayGranted = PermissionUtils.hasOverlayPermission(context)
                adminGranted = PermissionUtils.hasDeviceAdminPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val canProceed = accessibilityGranted && overlayGranted

    Scaffold(
        containerColor = Color(0xFF0B0F19)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(24.dp))

            // Branding Header
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF6366F1).copy(alpha = 0.35f), Color(0xFF6366F1).copy(alpha = 0.05f))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("🛡️", fontSize = 32.sp)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Welcome to FocusGuard",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Set up your focus shield in three quick steps to regain intentional control of your digital time.",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(24.dp))

            // Step 1: Accessibility Engine
            PermissionStepCard(
                icon = "⚡",
                stepNumber = "STEP 1 (REQUIRED)",
                title = "Accessibility Engine",
                description = "Detects when distracting apps like Instagram or YouTube open with zero background battery drain.",
                isGranted = accessibilityGranted,
                actionText = "Enable in Settings ➔",
                onAction = { PermissionUtils.requestAccessibilityPermission(context) }
            )

            Spacer(Modifier.height(12.dp))

            // Step 2: Overlay Permission
            PermissionStepCard(
                icon = "🪟",
                stepNumber = "STEP 2 (REQUIRED)",
                title = "Focus Shield Overlay",
                description = "Draws your priority checklists, countdown timers, and block screens on top of gated apps.",
                isGranted = overlayGranted,
                actionText = "Grant Overlay ➔",
                onAction = { PermissionUtils.requestOverlayPermission(context) }
            )

            Spacer(Modifier.height(12.dp))

            // Step 3: Anti-Tamper Protection
            PermissionStepCard(
                icon = "🔒",
                stepNumber = "STEP 3 (RECOMMENDED)",
                title = "Anti-Tamper Protection",
                description = "Device Administrator • Stops impulsive or casual uninstalls during moments of weak willpower.",
                isGranted = adminGranted,
                actionText = "Activate Protection",
                onAction = { PermissionUtils.requestDeviceAdminPermission(context) }
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onDone,
                enabled = canProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF1E293B),
                    disabledContentColor = Color(0xFF64748B)
                )
            ) {
                Text(
                    text = if (canProceed) "Enter FocusGuard ➔" else "Complete Required Steps Above",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun PermissionStepCard(
    icon: String,
    stepNumber: String,
    title: String,
    description: String,
    isGranted: Boolean,
    actionText: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(
            1.dp,
            if (isGranted) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF334155)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stepNumber,
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                    border = BorderStroke(
                        1.dp,
                        if (isGranted) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = if (isGranted) "Granted ✓" else "Action Needed",
                        color = if (isGranted) Color(0xFF34D399) else Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                description,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            if (!isGranted) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White
                    )
                ) {
                    Text(actionText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
