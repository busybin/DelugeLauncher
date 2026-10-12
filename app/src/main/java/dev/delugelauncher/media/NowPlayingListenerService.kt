// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.media

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.delugelauncher.widget.ClockWidgetProvider

class NowPlayingListenerService : NotificationListenerService() {

    companion object {
        /**
         * Asks the system to bind us again, when it is willing to and has not.
         *
         * Replacing the app kills the binding but leaves the permission switched on, so the
         * Now Playing card quietly stops updating and the setting still reads as granted —
         * there is nothing to turn back on, which makes it look like the feature broke. That
         * happens on every update, not only a sideload. requestRebind is the sanctioned way
         * back and does nothing if the permission was never given.
         */
        fun rebindIfPermitted(context: Context) {
            val component = ComponentName(context, NowPlayingListenerService::class.java)
            val enabled = runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners",
                )
            }.getOrNull().orEmpty()
            if (!enabled.contains(component.flattenToString()) &&
                !enabled.contains(component.packageName + "/" + component.className)
            ) {
                return
            }
            runCatching { requestRebind(component) }
        }

        // Binder plus disk plus a calendar query per render: never on the listener's thread.
        private val background: java.util.concurrent.ExecutorService by lazy {
            java.util.concurrent.Executors.newSingleThreadExecutor()
        }
    }

    private lateinit var sessionManager: MediaSessionManager
    private lateinit var componentName: ComponentName

    private var watched: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = republish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = republish()
        override fun onSessionDestroyed() {
            detach()
            republish()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        attachTo(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        publishCounts()
        sessionManager = getSystemService(MediaSessionManager::class.java)
        componentName = ComponentName(this, NowPlayingListenerService::class.java)
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, componentName)
            attachTo(sessionManager.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            NowPlayingBus.update(null)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        detach()
        NowPlayingBus.update(null)
        NotificationCountBus.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        if (::sessionManager.isInitialized) {
            runCatching { attachTo(sessionManager.getActiveSessions(componentName)) }
        }
        publishCounts()
    }

    /**
     * What is on the shade right now, counted by the app that put it there.
     *
     * Recounted from scratch rather than added to and subtracted from: a posted notification
     * can replace one already there without a removal ever arriving, and a count kept by
     * arithmetic drifts upward until something clears it.
     *
     * Ongoing notifications are left out — a music player or a running download is a state,
     * not something waiting to be read — and so are group summaries, which would count the
     * same messages a second time.
     */
    private fun publishCounts() {
        val active = runCatching { activeNotifications }.getOrNull()
        if (active == null) {
            NotificationCountBus.clear()
            persistCounts(emptyMap())
            renderAllAsync()
            return
        }
        val counts = HashMap<String, Int>()
        active.forEach { sbn ->
            if (!sbn.isClearable) return@forEach
            val notification = sbn.notification ?: return@forEach
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return@forEach
            counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
        }
        val before = NotificationCountBus.counts.value
        NotificationCountBus.update(counts)
        persistCounts(counts)
        // A widget is drawn only when something asks. The home screen follows the bus on its
        // own; a RemoteViews cannot, so a change in who has what — not just the total — has
        // to ask for it. One app gaining what another lost keeps the sum but not the rows.
        // Render carries a calendar query plus binder per widget: off this thread.
        if (counts != before) renderAllAsync()
    }

    /** Binder plus disk plus a calendar query: never on the listener's main thread. */
    private fun renderAllAsync() {
        background.execute { runCatching { ClockWidgetProvider.renderAll(this) } }
    }

    /**
     * The last published counts, for a widget drawn before the listener has published in
     * this process. The bus is memory-only, so a tick arriving after a restart would
     * otherwise read an empty map and hide the row; a live publish always wins over this.
     */
    private fun persistCounts(counts: Map<String, Int>) {
        runCatching {
            getSharedPreferences(NotificationCountBus.PERSIST_FILE, MODE_PRIVATE).edit()
                .putStringSet(
                    NotificationCountBus.PERSIST_KEY,
                    counts.map { (pkg, count) -> "$pkg\u0001$count" }.toSet(),
                )
                .apply()
        }
    }

    /** Follow whichever session is worth showing, and watch it for changes. */
    private fun attachTo(controllers: List<MediaController>?) {
        val next = controllers?.firstOrNull { isLive(it.playbackState) }
            ?: controllers?.firstOrNull { it.playbackState != null }

        if (next?.sessionToken != watched?.sessionToken) {
            detach()
            watched = next
            next?.registerCallback(controllerCallback)
        }
        republish()
    }

    private fun detach() {
        watched?.let { runCatching { it.unregisterCallback(controllerCallback) } }
        watched = null
    }

    private fun republish() {
        val controller = watched
        val state = controller?.playbackState
        if (controller == null || !isLive(state)) {
            NowPlayingBus.update(null)
            return
        }

        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        if (title.isBlank() && artist.isBlank()) {
            NowPlayingBus.update(null)
            return
        }

        NowPlayingBus.update(
            NowPlaying(
                title = title,
                artist = artist,
                isPlaying = state?.state == PlaybackState.STATE_PLAYING,
                art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART),
                controller = controller,
            )
        )
    }

    /** Playing, paused or buffering counts; stopped, errored or idle does not. */
    private fun isLive(state: PlaybackState?): Boolean = when (state?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_PAUSED,
        PlaybackState.STATE_BUFFERING,
        -> true

        else -> false
    }
}