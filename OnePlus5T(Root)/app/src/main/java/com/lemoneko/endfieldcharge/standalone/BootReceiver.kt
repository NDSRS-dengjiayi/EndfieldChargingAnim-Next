package com.lemoneko.endfieldcharge.standalone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lemoneko.endfieldcharge.root.RootShell

/**
 * Restores the monitor after a reboot so plugging in works without opening the app first.
 *
 * The rooted OnePlus 5T build deliberately keeps boot auto-start (the Harmony build cannot, because
 * EMUI blocks third-party boot broadcasts). `ACTION_BOOT_COMPLETED` is delivered after the user
 * unlock on file-based-encryption devices, so [StandalonePrefs] is readable here.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "boot action=$action")

        // Credential-encrypted prefs are unavailable before the unlock, and this receiver ignores
        // LOCKED_BOOT_COMPLETED on purpose.
        val enabled = runCatching { StandalonePrefs.isEnabled(context) }.getOrDefault(false)
        if (!enabled) {
            Log.i(TAG, "standalone mode disabled, nothing to start")
            return
        }

        // Re-apply the root grants: an OTA / permission reset clears them.
        RootShell.applyGrantsAsync(context) { Log.i(TAG, "grants: $it") }
        StandaloneMonitorService.start(context)
    }

    private companion object {
        const val TAG = "EndfieldCharge/Boot"
    }
}
