package com.lemoneko.endfieldcharge.standalone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.lemoneko.endfieldcharge.ui.MainActivity

/**
 * Persistent foreground service that keeps the process unfrozen and registers the charger
 * receiver dynamically.
 *
 * Why this exists: on EMUI/HarmonyOS the iaware power manager drops implicit broadcasts (including
 * ACTION_POWER_CONNECTED) for frozen background apps, so a manifest receiver alone is never woken.
 * A foreground service process is exempt from freezing, its dynamic receiver is delivered to a live
 * process synchronously, and the service is also allowed to start activities from the background.
 */
class StandaloneMonitorService : Service() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            ChargeEvents.handle(context.applicationContext, intent.action)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // System-only broadcasts; never exported from other apps.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        Log.i(TAG, "monitor service started, receiver registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        Log.i(TAG, "monitor service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Charge animation monitor",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Keeps the Endfield charge animation ready for charger events"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Endfield Charge")
            .setContentText("Waiting for charger events")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "EndfieldCharge/Svc"
        private const val CHANNEL_ID = "standalone_monitor"
        private const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, StandaloneMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StandaloneMonitorService::class.java))
        }
    }
}
