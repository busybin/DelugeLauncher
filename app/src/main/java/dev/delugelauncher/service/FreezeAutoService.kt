// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.delugelauncher.DelugeApp
import dev.delugelauncher.MainActivity
import dev.delugelauncher.R
import dev.delugelauncher.data.FreezeOutcome
import dev.delugelauncher.data.FreezeRunLog
import dev.delugelauncher.data.FreezeTargetKey
import dev.delugelauncher.data.RuleContext
import dev.delugelauncher.data.decision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Freezes the list a while after the screen goes off.
 *
 * Screen-off is a broadcast that may only be received by a registered receiver — one in a
 * manifest is not woken for it — so this has to be a running process. A foreground service
 * is the only kind the system leaves alone long enough for that to be worth anything, and it
 * carries a notification for as long as the feature is on, which is the honest way to say
 * something is watching the screen.
 */
class FreezeAutoService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The freeze waiting out its delay, cancelled by anything that cancels the lock. */
    private var pending: Job? = null

    /** The pending rule evaluation; a burst of network/audio events yields one run. */
    private var rulesJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = requestRules("network")
        override fun onLost(network: Network) = requestRules("network")
    }

    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = requestRules("audio")
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = requestRules("audio")
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = requestRules("battery")
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> schedule()
                // Screen-on as well as unlock: the lift has to be finished by the time the
                // user taps something, and an app frozen before the lock would otherwise be
                // opened while still suspended.
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> unfreezeIfAsked()
            }
        }
    }

    private val app get() = application as DelugeApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundWithNotification()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(networkCallback)
        }
        runCatching {
            getSystemService(AudioManager::class.java)
                ?.registerAudioDeviceCallback(audioCallback, Handler(Looper.getMainLooper()))
        }
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        requestRules("start")
        // The clock is the one condition with no broadcast; a minute is close enough for a
        // time window, and asking only while a rule exists keeps an idle service quiet.
        scope.launch {
            while (true) {
                delay(60_000)
                if (app.prefs.sleepRulesNow().any { it.enabled }) requestRules("tick")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A later start means the app is asking again — typically after the notification
        // permission was just granted, when the first post was dropped.
        startForegroundWithNotification()
        // The screen may already be off when the service starts; nothing else would arm it.
        if (isScreenOff()) schedule()
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(batteryReceiver) }
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback)
        }
        runCatching {
            getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(audioCallback)
        }
        rulesJob?.cancel()
        pending?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun requestRules(source: String) {
        rulesJob?.cancel()
        rulesJob = scope.launch {
            // A network change arrives as several callbacks; one run a second later is enough.
            delay(1_200)
            applyRules(source)
        }
    }

    /**
     * Applies the sleep rules once, and says in the ongoing notification what changed.
     */
    private suspend fun applyRules(source: String) {
        val log = applyRulesOnce(app, source) ?: return
        updateNotification(getString(R.string.freeze_rule_done, log.frozen.size, log.thawed.size))
    }

    private fun schedule() {
        pending?.cancel()
        pending = scope.launch {
            // The watcher also runs for sleep rules; the screen-off freeze-all is a separate
            // switch and must not fire just because a rule needed the service.
            if (!app.prefs.freezeAutoEnabledNow()) return@launch
            val seconds = app.prefs.freezeAutoDelaySeconds.first()
            if (seconds > 0) delay(seconds * 1000L)
            // Checked when the freeze is due, not when the screen went off: a phone put on a
            // charger in between is charging now, which is what the setting is about.
            if (app.prefs.freezeAutoSkipChargingNow() && isCharging()) {
                updateNotification(getString(R.string.freeze_auto_skipped_charging))
                return@launch
            }
            val result = app.freezeManager.freezeAll(auto = true)
            updateNotification(
                when {
                    result.noAccess -> getString(R.string.freeze_auto_no_access)
                    result.done > 0 -> getString(R.string.freeze_auto_done, result.done)
                    result.failed > 0 -> getString(R.string.freeze_auto_failed, result.failed)
                    else -> null
                }
            )
        }
    }

    private fun isCharging(): Boolean =
        getSystemService(BatteryManager::class.java)?.isCharging == true

    /** Says in the ongoing notification what the last screen-off run managed, or why not. */
    private fun updateNotification(detail: String?) {
        if (detail == null) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, notification(detail)) }
    }

    private fun cancelPending() {
        pending?.cancel()
        pending = null
    }

    private fun unfreezeIfAsked() {
        cancelPending()
        scope.launch {
            // A handover is the owner lending the phone: waking it must not lift the whole
            // list behind the guest's back. The tile keeps showing ACTIVE because it still is.
            if (!app.prefs.handoverNow().active &&
                app.prefs.freezeAutoEnabledNow() &&
                app.prefs.freezeUnfreezeOnUnlock.first()
            ) {
                app.freezeManager.unfreezeAll()
            }
            // Whatever a rule wanted while the screen was off is asked again now: an unlock
            // may have thawed exactly the apps the rule keeps asleep.
            requestRules("screen")
        }
    }

    private fun isScreenOff(): Boolean {
        val power = getSystemService(PowerManager::class.java) ?: return false
        return !power.isInteractive
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.freeze_auto_channel),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.freeze_auto_channel_detail)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun notification(detail: String? = null): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_freeze_notification)
            .setContentTitle(getString(R.string.freeze_auto_notification_title))
            .setContentText(detail ?: getString(R.string.freeze_auto_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "freeze_auto"
        private const val NOTIFICATION_ID = 4210

        /**
         * Applies the rules once from outside the service, for the daily-limit rollover.
         * Works whether or not the watcher is running; it only evaluates the rules.
         */
        fun applyRulesNow(context: Context, source: String) {
            val app = context.applicationContext as? DelugeApp ?: return
            app.runInBackground { applyRulesOnce(app, source) }
        }

        /** Starts or stops the watcher to match [enabled]. */
        fun sync(context: Context, enabled: Boolean) {
            val intent = Intent(context, FreezeAutoService::class.java)
            if (enabled) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                context.stopService(intent)
            }
        }
    }
}

/**
 * Applies the sleep rules once: wakes what a THAW rule names and freezes what a FREEZE rule
 * names, skipping the app in front so a rule cannot take the screen out from under the user.
 * A run with nothing to change writes no log and returns null.
 *
 * Lives outside the service because a daily-limit freeze lifted at midnight has to be handed
 * straight to the rules: without that, an app a rule keeps asleep stays open until the next
 * network, audio, battery or clock event wakes the watcher.
 */
private suspend fun applyRulesOnce(app: DelugeApp, source: String): FreezeRunLog? =
    rulesGate.withLock { applyRulesOnceLocked(app, source) }

/**
 * Both entry points can run at once: the watcher's clock tick and the daily-limit rollover.
 * Their decisions are computed from the same rules, so serialise the run instead of letting
 * the two write different halves of the log.
 */
private val rulesGate = Mutex()

private suspend fun applyRulesOnceLocked(app: DelugeApp, source: String): FreezeRunLog? {
    val rules = app.prefs.sleepRulesNow().filter { it.enabled }
    if (rules.isEmpty()) return null
    val listed = app.prefs.freezeMethodsNow().keys
    if (listed.isEmpty()) return null
    val decision = rules.decision(readRuleContext(app), listed)
    if (decision.sleep.isEmpty() && decision.wake.isEmpty()) return null
    val frozen = mutableSetOf<String>()
    val thawed = mutableSetOf<String>()
    for (key in decision.wake) {
        if (app.freezeManager.unfreezeKey(key) == FreezeOutcome.DONE) thawed += key
    }
    val foreground = if (decision.sleep.isNotEmpty()) app.freezeManager.foregroundPackage() else null
    for (key in decision.sleep) {
        // Work-profile and shortcut keys carry a suffix; the foreground check has to
        // compare packages, not raw keys.
        if (FreezeTargetKey.packageName(key) == foreground) continue
        if (app.freezeManager.freezeKey(key) == FreezeOutcome.DONE) frozen += key
    }
    if (frozen.isEmpty() && thawed.isEmpty()) return null
    val log = FreezeRunLog(System.currentTimeMillis(), source, frozen, thawed)
    app.prefs.setFreezeRun(log)
    return log
}

/** What the rules ask about, read the same way the home screen's favorite rules read it. */
private fun readRuleContext(context: Context): RuleContext {
    val wifi = runCatching {
        @Suppress("DEPRECATION")
        val ssid = context.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid?.trim('"')
        ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }.getOrNull()
    val headset = runCatching {
        context.getSystemService(AudioManager::class.java)
            ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.any { device ->
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET
            } == true
    }.getOrDefault(false)
    val now = java.util.Calendar.getInstance()
    val battery = context.getSystemService(BatteryManager::class.java)
    val percent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 } ?: 100
    return RuleContext(
        wifiSsid = wifi,
        headset = headset,
        minuteOfDay = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE),
        charging = battery?.isCharging == true,
        batteryPercent = percent,
    )
}
