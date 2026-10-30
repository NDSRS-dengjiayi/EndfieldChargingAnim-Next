package com.lemoneko.endfieldcharge.core.settings

/**
 * Contract shared by the in-process settings channel.
 *
 * Settings live in the app's own [android.content.ContentProvider]
 * (`com.lemoneko.endfieldcharge.settings.SettingsProvider`, authority `<package>.settings`), and
 * the UI / monitor service read them over the resolver. This object only holds the shared
 * authority and JSON bundle key so both sides use the same strings.
 */
object ContentSettings {

    private const val KEY_JSON = "json"

    /** Authority of the app's settings provider. */
    fun authority(packageName: String): String = "$packageName.settings"

    /** Bundle key used by both sides. */
    fun jsonKey(): String = KEY_JSON
}
