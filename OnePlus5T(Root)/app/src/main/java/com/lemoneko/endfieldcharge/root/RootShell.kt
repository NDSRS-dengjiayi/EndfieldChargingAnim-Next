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
    private const val BOOT_HOOK = "/data/adb/service.d/endfieldcharge_boot.sh"

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
     * Grants the permissions the monitor needs and installs the boot hook, then reports a human
     * readable summary through [onDone] (called on the worker thread).
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
            // Keeps the foreground monitor from being frozen when H2OS puts the app in the background.
            val background = step("background run") {
                exec("appops set ${app.packageName} RUN_ANY_IN_BACKGROUND allow")
            }
            val boot = step("boot hook") {
                installBootHook(app.packageName)
            }
            val summary = "root: $overlay, $battery, $background, $boot"
            Log.i(TAG, summary)
            onDone(summary)
        }.apply { isDaemon = true }.start()
    }

    /**
     * Removes the boot hook, so a disabled switch also survives a reboot.
     *
     * H2OS drops third-party `BOOT_COMPLETED` broadcasts, so boot autostart cannot rely on
     * [com.lemoneko.endfieldcharge.standalone.BootReceiver] alone; the Magisk hook is the part that
     * actually runs.
     */
    fun removeBootHookAsync(onDone: (String) -> Unit) {
        Thread {
            if (!isAvailable()) {
                onDone("No root access")
                return@Thread
            }
            val result = runCatching {
                exec("rm -f $BOOT_HOOK")
                "boot hook removed"
            }.getOrElse { "boot hook removal failed: ${it.message}" }
            Log.i(TAG, result)
            onDone(result)
        }.apply { isDaemon = true }.start()
    }

    /**
     * Writes `/data/adb/service.d/endfieldcharge_boot.sh`, which Magisk runs as root after boot.
     *
     * The script waits for `sys.boot_completed`, then starts the monitor repeatedly until it shows
     * up as a foreground service, and logs its attempts to `/data/local/tmp` for debugging.
     */
    private fun installBootHook(packageName: String): String {
        val script = """
            #!/system/bin/sh
            # Installed by EndfieldCharge (rooted build). Removed when the switch is turned off.
            PKG=$packageName
            CLS=${'$'}PKG/com.lemoneko.endfieldcharge.standalone.BootProxyActivity
            LOG=/data/local/tmp/endfieldcharge_boot.log
            until [ "${'$'}(getprop sys.boot_completed)" = "1" ]; do sleep 1; done
            sleep 10
            echo "${'$'}(date) boot hook start" >> ${'$'}LOG
            n=0
            while [ ${'$'}n -lt 5 ]; do
              am start -n ${'$'}CLS >> ${'$'}LOG 2>&1
              sleep 8
              if dumpsys activity services ${'$'}PKG | grep -q isForeground; then
                echo "${'$'}(date) monitor running" >> ${'$'}LOG
                break
              fi
              n=${'$'}((n+1))
            done
            exit 0
        """.trimIndent()

        val encoded = java.util.Base64.getEncoder().encodeToString(script.toByteArray())
        exec(
            "mkdir -p /data/adb/service.d; " +
                "echo '$encoded' | base64 -d > $BOOT_HOOK; " +
                "chmod 755 $BOOT_HOOK",
        )
        val written = exec("wc -c < $BOOT_HOOK").trim().toIntOrNull() ?: 0
        if (written <= 0) throw IllegalStateException("boot hook is empty")
        Log.i(TAG, "boot hook written ($written bytes)")
        return "boot hook installed"
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
