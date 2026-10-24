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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
        pending?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun schedule() {
        pending?.cancel()
        pending = scope.launch {
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
            if (app.prefs.freezeUnfreezeOnUnlock.first()) app.freezeManager.unfreezeAll()
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
