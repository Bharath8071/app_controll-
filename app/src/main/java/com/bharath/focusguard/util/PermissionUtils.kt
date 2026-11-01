package com.bharath.focusguard.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import com.bharath.focusguard.service.FocusGuardAccessibilityService
import com.bharath.focusguard.service.FocusGuardDeviceAdminReceiver

object PermissionUtils {

    /** Draw checklist/timer/block overlays on top of other apps. */
    fun hasOverlayPermission(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun requestOverlayPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Accessibility services can't be requested with a permission dialog —
     * the only way is sending the user to Settings > Accessibility and
     * having them flip it on manually. We check enabled state by reading the
     * system's enabled-services list.
     */
    fun hasAccessibilityPermission(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = ComponentName(context, FocusGuardAccessibilityService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (component in splitter) {
            if (ComponentName.unflattenFromString(component) == expected) return true
        }
        return false
    }

    fun requestAccessibilityPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Anti-tamper: makes plain uninstall require deactivating admin first. */
    fun hasDeviceAdminPermission(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(admin)
    }

    fun requestDeviceAdminPermission(context: Context) {
        val admin = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Required so FocusGuard can't be casually uninstalled to bypass a block."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Notification permission for session closing warnings and emergency alerts. */
    fun hasNotificationPermission(context: Context): Boolean {
        if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun openNotificationSettings(context: Context) {
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun isBatteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestBatteryUnrestricted(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    "android:get_usage_stats", android.os.Process.myUid(), context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    "android:get_usage_stats", android.os.Process.myUid(), context.packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    fun requestUsageAccess(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ── Fix-OEM-4: OEM Autostart detection ─────────────────────────────────
    // Xiaomi (MIUI), Oppo (ColorOS), Vivo (FuntouchOS), and Realme (realmeUI)
    // all ship a proprietary "Autostart" permission separate from Android's
    // standard battery optimisation toggle. Without it the AccessibilityService
    // is silently killed after a reboot or when the ROM's memory manager trims
    // background processes.
    //
    // There is no public API to read the autostart state, so we detect the OEM
    // from Build fields and surface a "Turn on" button that deep-links into the
    // correct OEM settings screen.

    enum class OemKind { NONE, XIAOMI, OPPO_REALME, VIVO, HUAWEI, SAMSUNG }

    fun detectOem(): OemKind {
        val brand = android.os.Build.BRAND.lowercase(java.util.Locale.ROOT)
        val manufacturer = android.os.Build.MANUFACTURER.lowercase(java.util.Locale.ROOT)
        return when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")
                    || manufacturer.contains("xiaomi") -> OemKind.XIAOMI
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus")
                    || manufacturer.contains("oppo") -> OemKind.OPPO_REALME
            brand.contains("vivo") || manufacturer.contains("vivo") -> OemKind.VIVO
            brand.contains("huawei") || brand.contains("honor")
                    || manufacturer.contains("huawei") -> OemKind.HUAWEI
            brand.contains("samsung") || manufacturer.contains("samsung") -> OemKind.SAMSUNG
            else -> OemKind.NONE
        }
    }

    /** Returns true if this OEM ships a separate autostart permission screen. */
    fun oemNeedsAutostart(): Boolean = detectOem() != OemKind.NONE

    /** OEM-specific label for the autostart row in the health/onboarding screen. */
    fun autostartLabel(): String = when (detectOem()) {
        OemKind.XIAOMI      -> "Xiaomi: enable Autostart"
        OemKind.OPPO_REALME -> "Oppo/Realme: enable Autostart"
        OemKind.VIVO        -> "Vivo: enable Background App Refresh"
        OemKind.HUAWEI      -> "Huawei: enable Auto-launch"
        OemKind.SAMSUNG     -> "Samsung: allow Restricted settings"
        OemKind.NONE        -> "Allow autostart"
    }

    /** OEM-specific explanation shown below the autostart row. */
    fun autostartReason(): String = when (detectOem()) {
        OemKind.XIAOMI      -> "MIUI kills background services — enable Autostart so blocking survives reboots."
        OemKind.OPPO_REALME -> "ColorOS kills background services — enable Autostart so blocking survives reboots."
        OemKind.VIVO        -> "FuntouchOS kills background services — enable Background App Refresh."
        OemKind.HUAWEI      -> "EMUI kills background services — enable Auto-launch in Phone Manager."
        OemKind.SAMSUNG     -> "OneUI requires Restricted settings to be unlocked before Accessibility can be enabled."
        OemKind.NONE        -> "Allow background autostart so blocking survives reboots."
    }

    /**
     * Opens the best-effort deep-link into the OEM's autostart/background-app
     * settings screen. Falls back to the standard app-info page if the OEM
     * intent doesn't resolve on this specific ROM version.
     */
    fun requestAutostartPermission(context: Context) {
        val candidates: List<Intent> = when (detectOem()) {
            OemKind.XIAOMI -> listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent().setComponent(ComponentName("com.miui.securitycenter",
                    "com.miui.securitycenter.MainActivity"))
            )
            OemKind.OPPO_REALME -> listOf(
                Intent().setComponent(ComponentName("com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"))
            )
            OemKind.VIVO -> listOf(
                Intent().setComponent(ComponentName("com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                Intent().setComponent(ComponentName("com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.PurviewTabActivity"))
            )
            OemKind.HUAWEI -> listOf(
                Intent().setComponent(ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
            )
            OemKind.SAMSUNG -> listOf(
                // Samsung: direct to this app's battery details where "Unrestricted" is set
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"))
            )
            OemKind.NONE -> emptyList()
        }

        val pm = context.packageManager
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (pm.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    context.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }
        // Final fallback: standard app info
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}
    }
    // ───────────────────────────────────────────────────────────────────────

    data class PermRow(val id: String, val critical: Boolean)

    fun criticalMissing(context: Context): List<String> {
        val out = mutableListOf<String>()
        if (!hasAccessibilityPermission(context)) out += "Accessibility is off. FocusGuard can't block apps."
        if (!hasOverlayPermission(context)) out += "Display over other apps is off."
        return out
    }
}
