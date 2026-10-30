package com.lemoneko.endfieldcharge.root

import android.content.Context
import android.util.Log
import com.lemoneko.endfieldcharge.standalone.StandaloneChargeActivity
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around `su` for the rooted OnePlus 5T build.
 *
 * Two jobs:
 *
 * 1. **One-click grants.** Displaying the HUD from the background needs `SYSTEM_ALERT_WINDOW` on
 *    modern ROMs, and a foreground service is only reliable while the app is exempt from battery
 *    optimisation. Both are normally two trips through the system settings; with root they are a
 *    single `appops` / `deviceidle` command.
 * 2. **Launch fallback.** If `startActivity` from the background is refused, `am start` executed
 *    as uid 0 still goes through, which keeps the "always show on plug" guarantee.
 *
 * Every method here may block for seconds (the su daemon may prompt for a grant the first time),
 * so callers must stay off the main thread.
 */
object RootShell {

    private const val TAG = "EndfieldCharge/Su"
    private const val TIMEOUT_SECONDS = 15L

    /** `null` = not probed yet. Cached afterwards, since granting root is a one-time decision. */
    @Volatile
    private var available: Boolean? = null

    /** Probes `su` once, off the main thread, so later checks are instant. */
    fun warmUpAsync() {
        if (available != null) return
        Thread {
            runCatching { isAvailable() }
        }.apply { isDaemon = true }.start()
    }

    fun isAvailable(): Boolean {
        available?.let { return it }
        val rooted = runCatching { exec("id") }.getOrNull()?.contains("uid=0") == true
        available = rooted
        Log.i(TAG, "root available=$rooted")
        return rooted
    }

    /**
     * Grants the two permissions the monitor needs, then reports a human readable summary through
     * [onDone] (called on the worker thread).
     */
    fun applyGrantsAsync(context: Context, onDone: (String) -> Unit) {
        val app = context.applicationContext
        Thread {
            if (!isAvailable()) {
                onDone("No root access - grant the permissions manually")
                return@Thread
            }
            val overlay = step("overlay") {
                exec("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
            }
            val battery = step("battery whitelist") {
                exec("dumpsys deviceidle whitelist +${app.packageName}")
            }
            val summary = "root: $overlay, $battery"
            Log.i(TAG, summary)
            onDone(summary)
        }.apply { isDaemon = true }.start()
    }

    /**
     * Starts [StandaloneChargeActivity] as root. Returns false when root is missing or the command
     * failed, so the caller can fall back to a full-screen-intent notification.
     */
    fun startHud(context: Context, mode: String): Boolean {
        val app = context.applicationContext
        if (!isAvailable()) return false
        val component = "${app.packageName}/${StandaloneChargeActivity::class.java.name}"
        return runCatching {
            val output = exec("am start -n $component --es mode $mode")
            val failed = output.contains("Error") || output.contains("does not exist")
            Log.i(TAG, "root am start ok=${!failed} output=${output.trim()}")
            !failed
        }.onFailure { Log.w(TAG, "root am start failed", it) }
            .getOrDefault(false)
    }

    private fun step(name: String, block: () -> Unit): String =
        runCatching(block).fold({ "$name granted" }, { "$name failed" })

    /** Runs `su -c <command>`, merging stderr, and returns the output. Throws on a non-zero exit. */
    private fun exec(command: String): String {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroy()
            throw IllegalStateException("timeout: $command")
        }
        val code = process.exitValue()
        if (code != 0) throw IllegalStateException("exit $code: $command -> ${output.trim()}")
        return output
    }
}
