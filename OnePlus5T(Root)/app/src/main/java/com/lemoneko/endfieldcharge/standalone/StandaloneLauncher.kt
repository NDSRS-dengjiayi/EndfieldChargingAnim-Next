package com.lemoneko.endfieldcharge.standalone

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.lemoneko.endfieldcharge.core.HudPlayMode
import com.lemoneko.endfieldcharge.root.RootShell

/**
 * Brings up the standalone HUD, in the order that is most likely to work on this device.
 *
 * Android 10 refuses background activity starts *silently* (no exception is thrown) unless the app
 * holds `SYSTEM_ALERT_WINDOW`. So when the overlay app-op is missing the direct path cannot work
 * and is skipped: `am start` as uid 0 goes first, and a full-screen-intent notification is the last
 * resort. When the overlay permission is present (rooted installs grant it through `appops`), the
 * direct start is the normal path because the system explicitly allows it.
 */
object StandaloneLauncher {

    private const val TAG = "EndfieldCharge/Launch"

    fun launch(context: Context, mode: HudPlayMode) {
        val intent = Intent(context, StandaloneChargeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(StandaloneChargeActivity.EXTRA_MODE, mode.name)
        }

        val fsiAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else {
            true
        }
        val canOverlay = Settings.canDrawOverlays(context)
        Log.i(TAG, "launch mode=$mode fsiAllowed=$fsiAllowed canOverlay=$canOverlay")

        if (!canOverlay) {
            // Direct start would be dropped without any error, so the rooted path is the only
            // reliable way to show the HUD from the background.
            if (RootShell.startHud(context, mode.name)) {
                Log.i(TAG, "root launch succeeded (no overlay permission)")
                StandaloneNotifier.cancel(context)
                return
            }
            Log.w(TAG, "root launch unavailable, falling back to the FSI notification")
            StandaloneNotifier.show(context, mode)
            return
        }

        val directError = try {
            context.startActivity(intent)
            null
        } catch (t: Throwable) {
            t
        }
        if (directError == null) {
            Log.i(TAG, "startActivity returned without exception")
            StandaloneNotifier.cancel(context)
            return
        }

        Log.w(TAG, "direct startActivity failed, trying root launch", directError)
        if (RootShell.startHud(context, mode.name)) {
            Log.i(TAG, "root launch succeeded")
            StandaloneNotifier.cancel(context)
            return
        }

        Log.w(TAG, "root launch unavailable, falling back to the FSI notification")
        StandaloneNotifier.show(context, mode)
    }
}
