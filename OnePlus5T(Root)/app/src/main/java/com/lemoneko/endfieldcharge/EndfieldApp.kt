package com.lemoneko.endfieldcharge

import android.app.Application
import com.lemoneko.endfieldcharge.root.RootShell
import com.lemoneko.endfieldcharge.settings.SettingsRepository
import com.lemoneko.endfieldcharge.standalone.StandaloneMonitorService
import com.lemoneko.endfieldcharge.standalone.StandaloneNotifier
import com.lemoneko.endfieldcharge.standalone.StandalonePrefs

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
        // Cached root probe: keeps the first charger event off a cold, blocking `su` prompt.
        RootShell.warmUpAsync()
        // Any process start (launcher, boot receiver, charger receiver) re-arms the monitor, so a
        // ROM kill cannot leave the device without a receiver.
        if (StandalonePrefs.isEnabled(this)) StandaloneMonitorService.start(this)
    }
}
