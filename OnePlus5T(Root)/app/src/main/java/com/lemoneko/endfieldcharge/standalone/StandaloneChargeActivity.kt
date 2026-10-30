package com.lemoneko.endfieldcharge.standalone

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import com.lemoneko.endfieldcharge.core.BatterySnapshot
import com.lemoneko.endfieldcharge.core.HudMetrics
import com.lemoneko.endfieldcharge.core.HudPlayMode
import com.lemoneko.endfieldcharge.core.settings.HudSettings
import com.lemoneko.endfieldcharge.core.timeline.HudTimeline
import com.lemoneko.endfieldcharge.settings.SettingsRepository
import com.lemoneko.endfieldcharge.ui.EndfieldHudView

/**
 * Host for the charging HUD on the rooted OnePlus 5T build.
 *
 * A translucent, show-when-locked activity that plays the [EndfieldHudView] / [HudTimeline] in the
 * app process. It shows directly over the keyguard without unlocking the device, then finishes
 * itself when the timeline ends (tap also dismisses).
 *
 * This build runs on Android 8, so the API 27 `setShowWhenLocked` group and the API 30
 * WindowInsetsController are guarded and replaced by their deprecated flag equivalents.
 */
class StandaloneChargeActivity : Activity() {

    private var animator: ValueAnimator? = null
    private lateinit var hud: EndfieldHudView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("EndfieldCharge/Hud", "StandaloneChargeActivity onCreate")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        // NB: hideSystemBars() needs the DecorView, which only exists after setContentView().

        val mode = runCatching {
            HudPlayMode.valueOf(intent?.getStringExtra(EXTRA_MODE) ?: HudPlayMode.CHARGE.name)
        }.getOrDefault(HudPlayMode.CHARGE)
        val settings = runCatching { SettingsRepository.current() }
            .getOrDefault(HudSettings.Default)

        val root = FrameLayout(this).apply {
            // Transparent by theme; a very slight scrim keeps the pill legible over a bright
            // lockscreen wallpaper without pretending to be a system surface.
            setBackgroundColor(Color.argb(40, 0, 0, 0))
            setOnClickListener { finish() }
        }

        hud = EndfieldHudView(this).apply {
            widthRatio = settings.widthRatio
            language = settings.language
            setSnapshot(currentSnapshot(StandalonePrefs.getCapacityMah(this@StandaloneChargeActivity)))
        }
        root.addView(
            hud,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = HudMetrics.topMarginPx(this@StandaloneChargeActivity, settings.topGapDp) },
        )
        setContentView(root)
        hideSystemBars()
        StandaloneNotifier.cancel(this)

        val timeline = HudTimeline(settings.animationOptions())
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = timeline.durationMillis.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val cue = (animation.animatedValue as Float).toDouble()
                hud.render(
                    when (mode) {
                        HudPlayMode.CHARGE -> timeline.evaluateCharge(cue)
                        HudPlayMode.UNPLUG -> timeline.evaluateUnplug(cue)
                    },
                )
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()
            })
        }.also { it.start() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        animator?.cancel()
        animator = null
        super.onDestroy()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    /**
     * Level/plug/status/voltage come from the sticky [Intent.ACTION_BATTERY_CHANGED] intent.
     * Unprivileged apps cannot read the battery sysfs nodes on most ROMs, so the total capacity is
     * the user-configured/auto-detected [capacityMah] and charged mAh is derived as total * level%.
     */
    private fun currentSnapshot(capacityMah: Int): BatterySnapshot {
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) {
            val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
            // EXTRA_VOLTAGE is delivered in millivolts.
            val voltageMv = sticky.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            return BatterySnapshot(
                level = pct,
                plugged = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
                status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
                voltageUv = if (voltageMv > 0) voltageMv * 1000L else -1L,
                chargeFullUah = capacityMah * 1000L,
                chargeCounterRaw = -1L,
            )
        }

        val battery = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return BatterySnapshot(
            level = runCatching {
                battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            }.getOrDefault(-1),
            plugged = 0,
            status = 0,
            voltageUv = -1L,
            chargeFullUah = capacityMah * 1000L,
            chargeCounterRaw = -1L,
        )
    }

    companion object {
        /** A [HudPlayMode] name: CHARGE (plug in) or UNPLUG. */
        const val EXTRA_MODE = "mode"
    }
}
