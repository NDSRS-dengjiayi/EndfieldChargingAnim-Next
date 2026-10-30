package com.lemoneko.endfieldcharge.core

import android.util.Log

/**
 * Single log tag so `adb logcat -s EndfieldCharge:*` shows everything the app does.
 * All app logging goes through here; do not scatter bare `Log` calls with other tags.
 */
internal object HudLog {

    const val TAG = "EndfieldCharge"

    fun i(scope: String, message: String) = emit(Log.INFO, scope, message, null)

    fun w(scope: String, message: String) = emit(Log.WARN, scope, message, null)

    fun w(scope: String, message: String, tr: Throwable) = emit(Log.WARN, scope, message, tr)

    fun e(scope: String, message: String, tr: Throwable? = null) = emit(Log.ERROR, scope, message, tr)

    private fun emit(priority: Int, scope: String, message: String, tr: Throwable?) {
        val text = "[$scope] $message"
        if (tr == null) {
            Log.println(priority, TAG, text)
        } else {
            Log.println(priority, TAG, "$text\n${Log.getStackTraceString(tr)}")
        }
    }
}
