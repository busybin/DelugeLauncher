// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher

import dev.delugelauncher.data.LanguagePrefs

import android.app.Application
import dev.delugelauncher.data.AppRepository
import dev.delugelauncher.data.IconPackRepository
import dev.delugelauncher.data.CrashLog
import dev.delugelauncher.data.Prefs
import dev.delugelauncher.widget.ClockWidgetBattery
import dev.delugelauncher.widget.DelugeAppWidgetHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DelugeApp : Application() {

    /**
     * The language reaches every Context the app builds, not only the Activity's: the
     * notification-listener and accessibility services, the widget providers and the
     * AppWidget hosts all inflate from their own Context, and one of them left in the
     * system language is a widget drawn in a language the rest of the phone is not in.
     */
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(LanguagePrefs.wrap(base))
    }


    lateinit var prefs: Prefs
        private set
    lateinit var appRepository: AppRepository
        private set
    lateinit var iconPackRepository: IconPackRepository
        private set
    lateinit var widgetHost: DelugeAppWidgetHost
        private set

    /** Outlives any screen, for work that must finish even as the launcher is left behind. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Runs [block] off the main thread, for work that must not hold up the first frame after
     * a resume. The clock widget's refresh is the case for this: binder round trips into the
     * system server on a process that has been sitting in the background, producing nothing
     * the waiting screen shows.
     */
    fun offMain(block: () -> Unit) {
        appScope.launch { block() }
    }

    override fun onCreate() {
        super.onCreate()
        // First, so a crash on the way up is still recorded.
        CrashLog.install(this)
        prefs = Prefs(this)
        appRepository = AppRepository(this, prefs, appScope)
        iconPackRepository = IconPackRepository(this)
        widgetHost = DelugeAppWidgetHost(this, HOST_ID)
        // Nothing else keeps the clock widget's battery line current; see the object.
        ClockWidgetBattery.watch(this)
    }

    companion object {
        const val HOST_ID = 1024
    }
}