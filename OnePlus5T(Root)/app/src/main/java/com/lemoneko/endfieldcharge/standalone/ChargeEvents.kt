package com.lemoneko.endfieldcharge.standalone

import android.content.Context
import android.content.Intent
import android.util.Log
import com.lemoneko.endfieldcharge.core.HudPlayMode
import com.lemoneko.endfieldcharge.core.settings.HudSettings
import com.lemoneko.endfieldcharge.settings.SettingsRepository
import java.util.concurrent.Executors

/**
 * Shared handling of charger plug/unplug events, used by both the manifest receiver and the
 * foreground service's dynamically registered receiver.
 *
 * The actual work runs on a single worker thread: the rooted launch fallback shells out to `su`,
 * which can block for seconds the first time the root grant prompt is shown, and receivers are
 * delivered on the main thread.
 */
object ChargeEvents {

    private const val TAG = "EndfieldCharge/Rx"

    // The manifest receiver and the FGS dynamic receiver can both fire for one event.
    private var lastAction: String? = null
    private var lastActionAt = 0L

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "charge-events").apply { isDaemon = true }
    }

    @Synchronized
    fun handle(context: Context, action: String?) {
        val now = System.currentTimeMillis()
        if (action == lastAction && now - lastActionAt < DEBOUNCE_MS) {
            Log.i(TAG, "duplicate action=$action ignored")
            return
        }
        lastAction = action
        lastActionAt = now

        val app = context.applicationContext
        // A charger event proves the process is alive, so make sure the persistent monitor is too:
        // if the ROM killed the service since boot, the next event would otherwise have no receiver.
        if (StandalonePrefs.isEnabled(app)) StandaloneMonitorService.start(app)
        worker.execute { dispatch(app, action) }
    }

    private fun dispatch(context: Context, action: String?) {
        Log.i(TAG, "handle action=$action standaloneEnabled=${StandalonePrefs.isEnabled(context)}")
        if (!StandalonePrefs.isEnabled(context)) return
        val settings = runCatching { SettingsRepository.current() }
            .getOrDefault(HudSettings.Default)

        when (action) {
            Intent.ACTION_POWER_CONNECTED -> {
                Log.i(TAG, "power connected -> launch CHARGE")
                if (settings.wakeOnPlug) StandaloneScreen.wake(context)
                StandaloneLauncher.launch(context, HudPlayMode.CHARGE)
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                // Delivery during deep doze (screen off) is best effort and cannot be guaranteed
                // without system privileges.
                Log.i(TAG, "power disconnected -> playOnUnplug=${settings.playOnUnplug}")
                if (settings.playOnUnplug) {
                    StandaloneLauncher.launch(context, HudPlayMode.UNPLUG)
                }
            }
        }
    }

    private const val DEBOUNCE_MS = 2000L
}
