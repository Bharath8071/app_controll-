package com.bharath.focusguard.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Once the user activates FocusGuard as a device admin (via the onboarding
 * flow), Android requires going through Settings > Security > Device admin
 * apps > deactivate before an uninstall is allowed — plain "uninstall from
 * launcher" no longer works. This is the anti-tamper layer we discussed:
 * it doesn't make removal impossible, but it removes the "long-press and
 * uninstall in 3 seconds" escape hatch that defeats the strict-gatekeeper
 * intent in a moment of low willpower.
 *
 * Phase 1 keeps this minimal — no forced password policy, no wiping. Just
 * the uninstall friction.
 */
class FocusGuardDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "FocusGuard admin protection enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Shown to the user when they try to deactivate admin rights —
        // last line of friction before they can uninstall.
        return "Turning this off removes FocusGuard's app-blocking protection. Are you sure?"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "FocusGuard admin protection disabled", Toast.LENGTH_SHORT).show()
    }
}
