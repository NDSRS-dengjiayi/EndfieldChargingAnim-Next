package com.lemoneko.endfieldcharge.standalone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lemoneko.endfieldcharge.core.HudPlayMode

/**
 * Full-screen-intent notification used to bring up [StandaloneChargeActivity] from the background.
 *
 * Android 12+ blocks ordinary activities started from a background broadcast receiver. A
 * full-screen-intent notification is the supported escape hatch: while the screen is off / locked
 * the intent launches immediately (exactly the plug-in case); while unlocked it appears as a
 * heads-up notification. On Android 12 (the target device) USE_FULL_SCREEN_INTENT is granted at
 * install time; the Android 13+ notification permission gate does not block full-screen intents.
 */
object StandaloneNotifier {

    private const val CHANNEL_ID = "standalone_charge"
    private const val NOTIFICATION_ID = 4701
    private const val CHANNEL_NAME = "Charging animation"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Full-screen Endfield charging HUD"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
    }

    fun show(context: Context, mode: HudPlayMode) {
        ensureChannel(context)

        val intent = Intent(context, StandaloneChargeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(StandaloneChargeActivity.EXTRA_MODE, mode.name)
        }
        val pending = PendingIntent.getActivity(
            context,
            mode.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Endfield Charge")
            .setContentText(
                if (mode == HudPlayMode.CHARGE) "Charger connected" else "Charger disconnected",
            )
            .setOngoing(true)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
