package com.lemoneko.endfieldcharge.standalone

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.lemoneko.endfieldcharge.core.HudPlayMode
import com.lemoneko.endfieldcharge.root.RootShell

/**
 * Brings up the standalone HUD: a direct activity start first, then `am start` through root, then a
 * full-screen-intent notification as the last resort.
 *
 * On the OnePlus 5T (Android 8) background activity starts are not restricted, so the direct path
 * is normally enough. The root path covers ROMs and future Android versions that refuse it, and
 * keeps the "the HUD always shows on plug" promise without needing the overlay permission.
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
        Log.i(TAG, "launch mode=$mode fsiAllowed=$fsiAllowed")

        val directError = try {
            context.startActivity(intent)
            null
        } catch (t: Throwable) {
            t
        }
        if (directError == null) {
            // No exception does NOT guarantee the activity became visible on Android 10+, where the
            // system may silently deny a background start. This build runs on Android 8, so an
            // exception-free start is the normal, visible path.
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
