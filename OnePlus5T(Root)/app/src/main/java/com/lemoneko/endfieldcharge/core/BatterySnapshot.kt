package com.lemoneko.endfieldcharge.core

import android.content.Intent
import android.os.BatteryManager
import java.io.File

/**
 * One battery reading.
 *
 * [chargeFullUah] and friends come straight out of sysfs because the framework does not expose
 * full capacity at all. See `docs/investigations/01-feasibility.md` section 4 for the unit
 * caveat on [chargeCounterRaw].
 */
data class BatterySnapshot(
    val level: Int,
    val plugged: Int,
    val status: Int,
    val voltageUv: Long,
    val chargeFullUah: Long,
    val chargeCounterRaw: Long,
) {

    val isPluggedIn: Boolean
        get() = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
            plugged == BatteryManager.BATTERY_PLUGGED_USB ||
            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS

    val isFull: Boolean
        get() = status == BatteryManager.BATTERY_STATUS_FULL

    /**
     * Nominal pack voltage used to turn charge (uAh) into energy (mWh).
     *
     * `voltage_now` swings by more than a volt between empty and full, so using it directly would
     * make the displayed mWh drift with the charger rather than with the charge. A fixed nominal
     * value keeps the number monotonic, which is what the HUD is actually communicating.
     */
    val nominalVolt: Float
        get() = DEFAULT_NOMINAL_VOLT

    /** Full capacity in mWh. */
    val fullMwh: Float
        get() = chargeFullUah / 1000f * nominalVolt

    /** Remaining capacity in mWh, derived from the level. */
    val remainingMwh: Float
        get() = fullMwh * level / 100f

    /** Full capacity in mAh (charge_full is in micro-amp-hours). */
    val fullMah: Float
        get() = chargeFullUah / 1000f

    /** Remaining capacity in mAh, derived from the level. */
    val remainingMah: Float
        get() = fullMah * level / 100f

    companion object {
        const val DEFAULT_NOMINAL_VOLT = 3.87f

        private const val SYSFS = "/sys/class/power_supply/battery"

        /** Huawei/Kirin kernels expose the supply capitalised as "Battery". */
        private val SYSFS_DIRS = listOf(SYSFS, "/sys/class/power_supply/Battery")

        fun from(intent: Intent): BatterySnapshot {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
            return BatterySnapshot(
                level = pct,
                plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
                voltageUv = readLong("voltage_now"),
                chargeFullUah = readLong("charge_full"),
                chargeCounterRaw = readLong("charge_counter"),
            )
        }

        private fun readLong(node: String): Long =
            SYSFS_DIRS
                .map { runCatching { File(it, node).readText().trim().toLong() }.getOrNull() }
                .firstOrNull { it != null }
                ?: -1L
    }
}
