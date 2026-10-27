// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.media

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.provider.Settings
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.delugelauncher.DelugeApp
import dev.delugelauncher.widget.ClockWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

        /** A row shows one line; anything longer is ellipsised there anyway. */
        private const val MAX_PREVIEW_CHARS = 200

        /** The panel wraps up to a few lines; past that it is not a notification any more. */
        private const val MAX_PANEL_CHARS = 600

        // Binder plus disk plus a calendar query per render: never on the listener's thread.
        private val background: java.util.concurrent.ExecutorService by lazy {
            java.util.concurrent.Executors.newSingleThreadExecutor()
        }
    }

    private lateinit var sessionManager: MediaSessionManager
    private lateinit var componentName: ComponentName

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Whether the setting that puts notification text on the home screen is on. */
    @Volatile
    private var previewWanted = false

    /** Whether the notification panel is on, which is what keeps whole notifications. */
    @Volatile
    private var panelWanted = false

    override fun onCreate() {
        super.onCreate()
        NotificationGateway.attach(this)
        val app = application as DelugeApp
        scope.launch {
            app.prefs.notificationPreview.collect { wanted ->
                previewWanted = wanted
                if (wanted) publishCounts() else NotificationPreviewBus.clear()
            }
        }
        scope.launch {
            app.prefs.notificationPanel.collect { wanted ->
                panelWanted = wanted
                if (wanted) publishCounts() else NotificationPanelBus.clear()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        NotificationGateway.detach(this)
        NotificationPanelBus.clear()
        super.onDestroy()
    }

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
        NotificationPreviewBus.clear()
        NotificationPanelBus.clear()
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
            NotificationPreviewBus.clear()
            NotificationPanelBus.clear()
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
        if (panelWanted || previewWanted) {
            val entries = active.mapNotNull { it.toPanelEntry() }.sortedByDescending { it.postTime }
            if (panelWanted) NotificationPanelBus.update(entries) else NotificationPanelBus.clear()
            if (previewWanted) {
                NotificationPreviewBus.update(entries)
            } else {
                NotificationPreviewBus.clear()
            }
        } else {
            NotificationPanelBus.clear()
            NotificationPreviewBus.clear()
        }
        persistCounts(counts)
        // A widget is drawn only when something asks. The home screen follows the bus on its
        // own; a RemoteViews cannot, so a change in who has what — not just the total — has
        // to ask for it. One app gaining what another lost keeps the sum but not the rows.
        // Render carries a calendar query plus binder per widget: off this thread.
        if (counts != before) renderAllAsync()
    }

    /**
     * One notification in full, for the panel.
     *
     * Group summaries are skipped because they duplicate their children's words. Ongoing ones
     * are kept: a running download or a media session belongs on a panel even though it is not
     * something waiting to be read. Text is flattened here so the screen only ever lays out.
     */
    private fun StatusBarNotification.toPanelEntry(): NotificationEntry? {
        val notification = notification ?: return null
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val extras = notification.extras
        val title = readLine(
            extras.getCharSequence(Notification.EXTRA_TITLE)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
            MAX_PANEL_CHARS,
        )
        val text = readLine(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT),
            MAX_PANEL_CHARS,
        )
        if (title.isEmpty() && text.isEmpty()) return null
        val serial = runCatching {
            getSystemService(UserManager::class.java).getSerialNumberForUser(user)
        }.getOrDefault(if (user == android.os.Process.myUserHandle()) 0L else Long.MIN_VALUE)
        val actions = notification.actions.orEmpty().mapNotNull { action ->
            val label = action.title?.toString()?.trim().orEmpty()
            val inputs: Array<android.app.RemoteInput> =
                action.remoteInputs?.map { it }?.toTypedArray() ?: emptyArray()
            val reply = inputs.firstOrNull { it.allowFreeFormInput }?.resultKey
            if (label.isEmpty()) null
            else NotificationAction(
                title = label,
                pendingIntent = action.actionIntent,
                inputs = inputs,
                replyKey = reply,
            )
        }
        return NotificationEntry(
            notificationKey = key,
            packageName = packageName,
            userSerial = serial,
            title = title,
            text = text,
            postTime = postTime,
            canDismiss = isClearable,
            actions = actions,
            contentIntent = notification.contentIntent,
        )
    }

    private fun readLine(value: CharSequence?, limit: Int = MAX_PREVIEW_CHARS): String =
        value?.toString().orEmpty()
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(limit)

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