package com.lemoneko.endfieldcharge.standalone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Manifest-registered fallback receiver for charger plug/unplug.
 *
 * `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED` are protected system broadcasts and remain
 * deliverable to manifest receivers. On EMUI/HarmonyOS, however, iaware may drop them for a frozen
 * background app. The primary path is therefore [StandaloneMonitorService], which keeps the process
 * alive as a foreground service and registers the receiver dynamically; this class stays as a
 * best-effort fallback (e.g. right after boot or if the service is stopped).
 */
class ChargeEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ChargeEvents.handle(context, intent.action)
    }
}
