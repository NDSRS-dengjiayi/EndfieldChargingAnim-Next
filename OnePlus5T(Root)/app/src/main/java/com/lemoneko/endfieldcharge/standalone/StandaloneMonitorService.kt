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
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.lemoneko.endfieldcharge.ui.MainActivity

/**
 * Persistent foreground service that keeps the process unfrozen and registers the charger
 * receivers dynamically.
 *
 * Why this exists: ROMs may drop implicit broadcasts (including ACTION_POWER_CONNECTED) for a
 * frozen background app, so a manifest receiver alone is not reliable. A foreground service
 * process is exempt from freezing, and its dynamic receiver is delivered to a live process.
 *
 * Two event sources are registered here:
 *
 * * `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED` - the normal, documented signals.
 * * `ACTION_BATTERY_CHANGED` - some vendor ROMs (including H2OS) only surface the plug through the
 *   `EXTRA_PLUGGED` extra of the sticky battery broadcast. The plugged flag is edge-detected here
 *   and turned into the same CHARGE / UNPLUG events, so both ROM behaviours end up in one path.
 */
class StandaloneMonitorService : Service() {

    /** Previous `EXTRA_PLUGGED` value; `null` until the first battery broadcast seeds it. */
    private var lastPlugged: Int? = null

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            ChargeEvents.handle(context.applicationContext, intent.action)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val previous = lastPlugged
            lastPlugged = plugged
            if (previous == null || previous == plugged) return

            val action = if (plugged != 0) {
                Intent.ACTION_POWER_CONNECTED
            } else {
                Intent.ACTION_POWER_DISCONNECTED
            }
            Log.i(TAG, "battery broadcast plugged=$previous->$plugged, synthesised $action")
            ChargeEvents.handle(context.applicationContext, action)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        val powerFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        // System-only broadcasts; never exported from other apps.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerReceiver, powerFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(batteryReceiver, batteryFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(powerReceiver, powerFilter)
            registerReceiver(batteryReceiver, batteryFilter)
        }
        Log.i(TAG, "monitor service started, receivers registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(powerReceiver) }
        runCatching { unregisterReceiver(batteryReceiver) }
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

        /**
         * Starts (or keeps) the monitor. Safe to call from any component: repeated calls while it
         * already runs only re-deliver [onStartCommand].
         */
        fun start(context: Context) {
            val intent = Intent(context, StandaloneMonitorService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "monitor start refused", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StandaloneMonitorService::class.java))
        }
    }
}
