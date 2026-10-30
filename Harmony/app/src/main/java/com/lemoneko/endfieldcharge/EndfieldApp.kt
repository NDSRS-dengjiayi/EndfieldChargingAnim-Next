package com.lemoneko.endfieldcharge

import android.app.Application
import com.lemoneko.endfieldcharge.settings.SettingsRepository
import com.lemoneko.endfieldcharge.standalone.StandaloneNotifier

/**
 * Application entry.
 *
 * Both the settings UI and the manifest-registered charge receiver run in this same process, so
 * repository wiring and the standalone notification channel are initialised once here.
 */
class EndfieldApp : Application() {

    override fun onCreate() {
        super.onCreate()
        SettingsRepository.start(this)
        StandaloneNotifier.ensureChannel(this)
    }
}
