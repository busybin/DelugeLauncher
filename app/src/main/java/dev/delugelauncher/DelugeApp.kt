// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher

import dev.delugelauncher.data.LanguagePrefs

import android.app.Application
import dev.delugelauncher.data.AppLock
import dev.delugelauncher.data.AppRepository
import dev.delugelauncher.data.FreezeManager
import dev.delugelauncher.data.IconPackRepository
import dev.delugelauncher.data.CrashLog
import dev.delugelauncher.data.Prefs
import dev.delugelauncher.service.FreezeAutoService
import dev.delugelauncher.service.FreezeShortcuts
import dev.delugelauncher.widget.ClockWidgetBattery
import dev.delugelauncher.widget.DelugeAppWidgetHost
import androidx.core.content.ContextCompat
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
    lateinit var freezeManager: FreezeManager
        private set
    lateinit var iconPackRepository: IconPackRepository
        private set
    lateinit var widgetHost: DelugeAppWidgetHost
        private set

    /** Outlives any screen, for work that must finish even as the launcher is left behind. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Runs [block] off the main thread, for work that must not hold up the first frame after
     * a resume. The clock widget's refresh is the case for this: binder round trips into the
     * system server on a process that has been sitting in the background, producing nothing
     * the waiting screen shows.
     */
    fun offMain(block: () -> Unit) {
        appScope.launch { runCatching { block() } }
    }

    /**
     * Runs suspending work that has to finish even though its caller — a tile, a shortcut
     * trampoline — is gone the moment it returns.
     */
    fun runInBackground(block: suspend () -> Unit) {
        appScope.launch { runCatching { block() } }
    }

    override fun onCreate() {
        super.onCreate()
        // First, so a crash on the way up is still recorded.
        CrashLog.install(this)
        prefs = Prefs(this)
        freezeManager = FreezeManager(this, prefs)
        appRepository = AppRepository(this, prefs, appScope, freezeManager)
        iconPackRepository = IconPackRepository(this)
        widgetHost = DelugeAppWidgetHost(this, HOST_ID)
        // Nothing else keeps the clock widget's battery line current; see the object.
        ClockWidgetBattery.watch(this)
        // A locked app that was opened stays open for the timeout; the screen going off is the
        // moment that should stop being true, since the phone may have been handed over since.
        ContextCompat.registerReceiver(
            this,
            object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                    appScope.launch {
                        if (prefs.appLockScreenOffNow()) AppLock.forgetAll()
                        // The screen going off is the same boundary as leaving the launcher:
                        // an app opened through the lock goes back out of sight, and the
                        // hidden flag stops whatever it was still doing.
                        appRepository.rehideLockedApps()
                    }
                }
            },
            android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Settings offers an Unhide button for a hidden package. Hiding is still the right
        // first layer (it keeps the app out of every launcher), but it is not a boundary on
        // its own. This catches that unhide and puts the package back; suspension, applied
        // beside hiding, is what stops the app from opening in the moment before the re-hide
        // lands. The receiver is only a repair path — a killed launcher leaves the
        // suspension in place.
        ContextCompat.registerReceiver(
            this,
            object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                    val packageName = intent.data?.schemeSpecificPart ?: return
                    appScope.launch { appRepository.rehideLockedApps(only = packageName) }
                }
            },
            android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
                addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
                addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // The automatic freeze is a running watcher rather than a scheduled job: it is started
        // and stopped from here alone, so the preference and the notification can never
        // disagree about whether something is watching the screen.
        appScope.launch {
            // Before anything reads it: an older build's state has to be in this build's shape.
            freezeManager.migrateLegacyState()
            kotlinx.coroutines.flow.combine(
                prefs.freezeEnabled,
                prefs.freezeAutoEnabled,
            ) { feature, auto -> feature && auto }.collect { enabled ->
                FreezeAutoService.sync(this@DelugeApp, enabled)
            }
        }
        // The long-press shortcuts are part of the feature, so they appear and disappear with it.
        appScope.launch {
            prefs.freezeEnabled.collect { enabled ->
                FreezeShortcuts.sync(this@DelugeApp, enabled)
            }
        }
    }

    companion object {
        const val HOST_ID = 1024
    }
}