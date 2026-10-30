package com.lemoneko.endfieldcharge.standalone

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.lemoneko.endfieldcharge.core.HudPlayMode

/**
 * Brings up the standalone HUD, trying a direct activity start first and falling back to a
 * full-screen-intent notification when the system forbids background activity starts.
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
            // No exception does NOT guarantee the activity became visible: Android 10+ may silently
            // deny the background activity start. The logcat "Background activity start" line is
            // the ground truth; logging here marks the attempt.
            Log.i(TAG, "startActivity returned without exception")
            StandaloneNotifier.cancel(context)
        } else {
            Log.w(TAG, "direct startActivity failed, falling back to FSI notification", directError)
            StandaloneNotifier.show(context, mode)
        }
    }
}
