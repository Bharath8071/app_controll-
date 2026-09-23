package com.bharath.focusguard.ui.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bharath.focusguard.ui.main.MainActivity
import com.bharath.focusguard.util.PermissionUtils

/**
 * First-run flow: needs Accessibility Service + overlay permission to
 * function at all; Device Admin is offered as the anti-tamper layer (can be
 * skipped, but strongly recommended given the "strict gatekeeper" design).
 * Each permission is granted in system Settings, so this screen re-checks
 * state on every onResume() rather than trusting a one-time check.
 */
class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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

    // Re-check every time the user comes back from Settings
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

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("FocusGuard needs a few permissions to enforce blocks properly:")

        Spacer(Modifier.height(20.dp))
        Text(if (accessibilityGranted) "✓ Accessibility service enabled" else "Accessibility service — detects the instant Instagram/YouTube opens")
        if (!accessibilityGranted) {
            Button(onClick = { PermissionUtils.requestAccessibilityPermission(context) }) {
                Text("Enable in Settings")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(if (overlayGranted) "✓ Overlay permission granted" else "Draw over other apps — shows the checklist/timer/block screens")
        if (!overlayGranted) {
            Button(onClick = { PermissionUtils.requestOverlayPermission(context) }) {
                Text("Grant overlay permission")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(if (adminGranted) "✓ Anti-tamper protection enabled" else "Device admin (recommended) — stops a casual uninstall from bypassing a block")
        if (!adminGranted) {
            Button(onClick = { PermissionUtils.requestDeviceAdminPermission(context) }) {
                Text("Enable anti-tamper")
            }
        }

        Spacer(Modifier.height(28.dp))
        if (accessibilityGranted && overlayGranted) {
            Button(onClick = onDone) { Text(if (adminGranted) "Continue" else "Continue without anti-tamper") }
        }
    }
}
