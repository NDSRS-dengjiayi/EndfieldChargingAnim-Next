package com.lemoneko.endfieldcharge.standalone

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * Headless activity that re-arms the monitor after a reboot.
 *
 * H2OS drops third-party `BOOT_COMPLETED` broadcasts, so boot autostart is driven by the Magisk
 * hook in `/data/adb/service.d`, which runs as root. A root shell process cannot start this app's
 * non-exported service, but it can start an exported activity, so the hook starts this activity as
 * the entry point and it finishes immediately without ever drawing a window.
 */
class BootProxyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val enabled = runCatching { StandalonePrefs.isEnabled(this) }.getOrDefault(true)
        if (enabled) {
            StandaloneMonitorService.start(this)
            Log.i(TAG, "monitor armed by boot hook")
        }
        finish()
    }

    private companion object {
        const val TAG = "EndfieldCharge/BootProxy"
    }
}
