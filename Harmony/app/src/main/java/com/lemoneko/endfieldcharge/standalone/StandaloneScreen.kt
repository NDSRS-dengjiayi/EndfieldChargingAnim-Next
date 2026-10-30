package com.lemoneko.endfieldcharge.standalone

import android.content.Context
import android.os.PowerManager

/**
 * Turns the screen on when the charger is connected while the screen is off.
 *
 * A normal app cannot call the hidden `PowerManager.wakeUp` (it is a system-only API), so the
 * standalone app uses a deprecated but still functional full wake lock with
 * [PowerManager.ACQUIRE_CAUSES_WAKEUP]. Requires only the ordinary WAKE_LOCK permission. The
 * timed acquisition releases itself, there is nothing to clean up.
 */
object StandaloneScreen {

    private const val TAG = "endfieldcharge:plug"
    private const val WAKE_HOLD_MILLIS = 5_000L

    @Suppress("DEPRECATION")
    fun wake(context: Context) {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (power.isInteractive) return
        runCatching {
            val lock = power.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                TAG,
            )
            lock.acquire(WAKE_HOLD_MILLIS)
        }
    }
}
