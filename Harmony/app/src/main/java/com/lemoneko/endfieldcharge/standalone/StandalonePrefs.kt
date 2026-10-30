package com.lemoneko.endfieldcharge.standalone

import android.content.Context
import android.util.Log

/**
 * App-local preferences for the no-root standalone mode (file `standalone`): the master enable
 * switch and the battery total capacity.
 *
 * The battery total capacity is auto-detected once from the framework's
 * [android.os.BatteryManager]/PowerProfile profile, falling back to [FALLBACK_CAPACITY_MAH], and
 * the user can override it manually. The HUD derives charged mAh as total * level%.
 */
object StandalonePrefs {

    private const val TAG = "EndfieldCharge"
    private const val FILE = "standalone"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CAPACITY_MAH = "capacity_mah"
    private const val KEY_CAPACITY_DETECTED = "capacity_detected"

    /** Used when the framework reports no usable capacity. */
    const val FALLBACK_CAPACITY_MAH = 1000
    private const val MIN_CAPACITY_MAH = 500
    private const val MAX_CAPACITY_MAH = 20_000

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /**
     * Returns the configured total battery capacity in mAh. On first ever call it runs
     * [detectCapacityMah] once and stores the result (or the fallback), so the user's later manual
     * edits are never overwritten by re-detection.
     */
    fun getCapacityMah(context: Context): Int {
        val sp = prefs(context)
        if (!sp.getBoolean(KEY_CAPACITY_DETECTED, false)) {
            val detected = detectCapacityMah(context)
            sp.edit()
                .putInt(KEY_CAPACITY_MAH, detected)
                .putBoolean(KEY_CAPACITY_DETECTED, true)
                .apply()
            return detected
        }
        return sp.getInt(KEY_CAPACITY_MAH, FALLBACK_CAPACITY_MAH)
    }

    /** Manual override; clamped to a sane phone-battery range. */
    fun setCapacityMah(context: Context, mah: Int) {
        val clamped = mah.coerceIn(MIN_CAPACITY_MAH, MAX_CAPACITY_MAH)
        prefs(context).edit()
            .putInt(KEY_CAPACITY_MAH, clamped)
            .putBoolean(KEY_CAPACITY_DETECTED, true)
            .apply()
    }

    /**
     * Reads the framework power profile's design capacity (PowerProfile#getBatteryCapacity, mAh).
     * The class is hidden but on the light grey list, so reflective access works for ordinary apps
     * without root. Returns [FALLBACK_CAPACITY_MAH] when unavailable or non-positive.
     */
    fun detectCapacityMah(context: Context): Int = runCatching {
        val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
        val powerProfile = powerProfileClass
            .getConstructor(Context::class.java)
            .newInstance(context)
        val value = powerProfileClass
            .getMethod("getBatteryCapacity")
            .invoke(powerProfile) as? Double
        value?.takeIf { it.isFinite() && it >= MIN_CAPACITY_MAH }?.toInt()
    }.onFailure { Log.w(TAG, "battery capacity detection failed", it) }
        .getOrNull()
        ?: FALLBACK_CAPACITY_MAH

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
