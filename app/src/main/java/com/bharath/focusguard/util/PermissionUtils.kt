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
}
