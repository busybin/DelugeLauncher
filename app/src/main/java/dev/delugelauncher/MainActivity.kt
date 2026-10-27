// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher

import dev.delugelauncher.data.LanguagePrefs

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.UserHandle
import android.os.UserManager
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import dev.delugelauncher.data.AppFont
import dev.delugelauncher.data.ColorSchemeChoice
import dev.delugelauncher.data.IconShape
import dev.delugelauncher.media.NowPlayingListenerService
import dev.delugelauncher.service.StatusBarFader
import dev.delugelauncher.widget.ClockWidgetProvider
import dev.delugelauncher.ui.DelugeNavHost
import dev.delugelauncher.ui.common.IconConfig
import dev.delugelauncher.ui.theme.widgetTypeface
import dev.delugelauncher.ui.common.LaunchOrigin
import dev.delugelauncher.ui.common.FreezeToast
import dev.delugelauncher.ui.common.LocalIconConfig
import dev.delugelauncher.ui.theme.DelugeTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One keystroke taken on the home screen; a null character is a backspace. */
data class TypedKey(val seq: Long, val char: Char?)

/** Smallest width that counts as a tablet, which is the platform's own threshold. */
private const val TABLET_WIDTH_DP = 600

// The launcher ⇄ gesture navigation handshake, as defined by Launcher3.
private const val EXTRA_GESTURE_CONTRACT = "gesture_nav_contract_v1"
private const val EXTRA_CONTRACT_POSITION = "gesture_nav_contract_icon_position"
private const val EXTRA_CONTRACT_SURFACE = "gesture_nav_contract_surface_control"
private const val EXTRA_CONTRACT_CALLBACK = "android.intent.extra.REMOTE_CALLBACK"
private const val EXTRA_CONTRACT_FINISH = "gesture_nav_contract_finish_callback"
private const val EXTRA_CONTRACT_COMPONENT = Intent.EXTRA_COMPONENT_NAME
private const val EXTRA_CONTRACT_USER = Intent.EXTRA_USER

class MainActivity : ComponentActivity() {

    /**
     * The chosen language is put in force here rather than in `onCreate`, so the first
     * layout is already in the right one — a Context wrapped later would leave the window's
     * own resources, and the strings read during the first composition, in the old language.
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguagePrefs.wrap(newBase))
    }


    /** Bumped whenever HOME is pressed while we're already showing, so overlays can close. */
    private var homeIntentTick by mutableStateOf(0)

    /**
     * What the status bar was last asked to be, so it can be asked again if it stopped obeying.
     *
     * Fully expanding the notification shade hands the system bars to the system, which cancels
     * the control this app holds over them. Collapsing the shade does not hand them back, and
     * nothing here had changed its mind — so the bar stayed up with the app still believing it
     * was hidden, until some setting changed and the request was made afresh.
     */
    private var desiredStatusBarVisible: Boolean? = null

    /**
     * Keystrokes taken on the home screen, as a growing list so none is dropped.
     *
     * Each is stamped with a count rather than replacing the last: typing quickly produced two
     * characters before the first had been read, and the second overwrote it.
     */
    private var typedToSearch by mutableStateOf<List<TypedKey>>(emptyList())
    private var nextTypedSeq = 0L

    /**
     * Answers the system's return-to-home contract. It receives a message and has nothing to
     * do with it; the system only needs to know the animation may end.
     */
    private val gestureFinish = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) = Unit
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before the content: a shortcut that starts the app has the work already under way.
        if (!handleGestureContract(intent)) handleShortcut(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        allowDrawingBehindTheCutout()
        // Portrait until told otherwise on a phone, free on a tablet: this is a list down the
        // side of the screen, and a phone sideways has about a third of the height it needs.
        // The setting exists because that is a judgement about a screen rather than about a
        // person — a phone in a car mount wants it anyway.
        val rotatesByDefault = resources.configuration.smallestScreenWidthDp >= TABLET_WIDTH_DP
        val app = application as DelugeApp

        setContent {
            val hideStatusBar by app.prefs.hideStatusBar.collectAsState(initial = false)
            val hideStatusBarAppList by app.prefs.hideStatusBarAppList.collectAsState(initial = false)
            val screenshotMode by app.prefs.screenshotMode.collectAsState(initial = false)
            val peekSeconds by app.prefs.statusBarPeekSeconds.collectAsState(initial = 5)
            // A short pull-down peeks the status bar, then it slides away again.
            var statusBarPeek by remember { mutableStateOf(false) }
            // The two surfaces choose separately, so the overlay can keep the bar the home
            // screen hides.
            var appListOpen by remember { mutableStateOf(false) }
            LaunchedEffect(statusBarPeek, peekSeconds) {
                if (statusBarPeek) {
                    delay(peekSeconds * 1000L)
                    statusBarPeek = false
                }
            }
            val hideHere = if (appListOpen) hideStatusBarAppList else hideStatusBar
            // Screenshot disguise always hides the bar, whatever the setting says.
            val statusBarVisible = (!hideHere && !screenshotMode) || statusBarPeek
            // Keyed on the answer, not on what went into it. Re-asking for a state the bar is
            // already in restarts the fade, and a fade out begins by holding the bar fully
            // shown — so opening the list with both set to hide flashed it into view.
            LaunchedEffect(statusBarVisible) {
                desiredStatusBarVisible = statusBarVisible
                StatusBarFader.setVisible(window, visible = statusBarVisible)
            }

            val font by app.prefs.font.collectAsState(initial = AppFont.SYSTEM)
            val fontFile by app.prefs.fontFile.collectAsState(initial = null)
            LaunchedEffect(font, fontFile) {
                app.widgetHost.typeface = widgetTypeface(font, fontFile)
            }
            val colorSchemeChoice by app.prefs.colorSchemeChoice.collectAsState(
                initial = ColorSchemeChoice.DELUGE_BLUE
            )
            val allowRotation by app.prefs.allowRotation.collectAsState(initial = null)
            LaunchedEffect(allowRotation, rotatesByDefault) {
                requestedOrientation = if (allowRotation ?: rotatesByDefault) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
            // The wallpaper belongs to the system, so it cannot be blurred by drawing it — it is
            // the window's own background blur that does it, which needs Android 12.
            val wallpaperBlurDp by app.prefs.wallpaperBlurDp.collectAsState(initial = 0)
            LaunchedEffect(wallpaperBlurDp) {
                window.setBlurBehind(wallpaperBlurDp, resources.displayMetrics.density)
            }

            // Dark by the clock rather than by the system, when asked. Sleeps until the
            // next hour, since whole hours are all the schedule resolves to.
            val autoDark by app.prefs.autoDark.collectAsState(initial = false)
            val nightStartHour by app.prefs.nightStartHour.collectAsState(initial = 19)
            val nightEndHour by app.prefs.nightEndHour.collectAsState(initial = 7)
            var nightNow by remember { mutableStateOf(isNightHour(nightStartHour, nightEndHour)) }
            LaunchedEffect(autoDark, nightStartHour, nightEndHour) {
                nightNow = isNightHour(nightStartHour, nightEndHour)
                while (autoDark) {
                    delay(msToNextHour())
                    nightNow = isNightHour(nightStartHour, nightEndHour)
                }
            }
            val iconPackPackage by app.prefs.iconPackPackage.collectAsState(initial = null)

            val iconOverrides by app.prefs.iconOverrides.collectAsState(initial = emptyMap())
            val showAppIcons by app.prefs.showAppIcons.collectAsState(initial = true)
            val themedIcons by app.prefs.themedIcons.collectAsState(initial = false)
            val notificationBadges by app.prefs.notificationBadges.collectAsState(initial = false)
            val iconShape by app.prefs.iconShape.collectAsState(initial = IconShape.SYSTEM)
            val iconConfig = remember(
                iconPackPackage,
                iconOverrides,
                showAppIcons,
                themedIcons,
                notificationBadges,
                iconShape,
            ) {
                IconConfig(
                    pack = iconPackPackage,
                    overrides = iconOverrides,
                    showIcons = showAppIcons,
                    themed = themedIcons,
                    notificationBadges = notificationBadges,
                    shape = iconShape,
                )
            }

            DelugeTheme(
                font = font,
                fontFile = fontFile,
                scheme = colorSchemeChoice,
                forceDark = if (autoDark) nightNow else null,
            ) {
                CompositionLocalProvider(LocalIconConfig provides iconConfig) {
                    DelugeNavHost(
                        app = app,
                        homeIntentTick = homeIntentTick,
                        typedToSearch = typedToSearch,
                        onTypedToSearchHandled = { handled -> typedToSearch = typedToSearch - handled.toSet() },
                        font = font,
                        hideStatusBar = hideStatusBar,
                        hideStatusBarAppList = hideStatusBarAppList,
                        iconPackPackage = iconPackPackage,

                        iconOverrides = iconOverrides,
                        onPeekStatusBar = { statusBarPeek = true },
                        onAppListVisibleChange = { appListOpen = it },
                    )
                }
            }
        }
    }

    /**
     * Lets the window reach the whole screen, cutout included.
     *
     * Drawing edge to edge is not on its own enough to be allowed up there. The default rule is
     * that a window may extend into the cutout only while the cutout is covered by a system bar
     * — so with the status bar hidden the window is pushed below it instead, and the strip it
     * leaves behind belongs to nobody: the wallpaper shows through the dim on the home screen
     * and through the settings screen's own background.
     *
     * ALWAYS rather than SHORT_EDGES wherever there is one, because this rotates: sideways the
     * cutout is on a long edge, which SHORT_EDGES does not cover and which would letterbox the
     * window all over again. What the insets mean is unchanged — the screens that must keep
     * clear of the cutout already pad themselves by it.
     */
    private fun allowDrawingBehindTheCutout() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /**
     * Typing on a hardware keyboard opens the app list and starts searching.
     *
     * A phone with keys has nowhere else for a keystroke to go on a home screen, and reaching
     * for the search field first is the long way round to the thing you already started
     * spelling. Only printable characters count: the volume keys, the arrows and everything
     * else keep doing their own jobs.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            return super.onKeyDown(keyCode, event)
        }
        // Backspace once something has been typed, or the only way to fix a typo is to throw
        // the whole search away and start it again.
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            typedToSearch = typedToSearch + TypedKey(nextTypedSeq++, null)
            return true
        }
        val typed = event.unicodeChar.takeIf { it != 0 }?.toChar()
        if (typed == null || typed.isISOControl()) return super.onKeyDown(keyCode, event)
        // A space is a character in half the app names there are, but it is not something to
        // open a search with — on its own it would start a query that looks like nothing.
        if (typed == ' ' && typedToSearch.isEmpty()) return super.onKeyDown(keyCode, event)
        typedToSearch = typedToSearch + TypedKey(nextTypedSeq++, typed)
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Focus comes back when the shade closes, which is exactly when the bar may have been
        // left behind. Asked again only when it is actually wrong: asking for the state it is
        // already in restarts the fade, and a fade out begins by showing the bar in full.
        if (!hasFocus) return
        val want = desiredStatusBarVisible ?: return
        val actual = ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: return
        if (actual != want) StatusBarFader.setVisible(window, visible = want)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (handleGestureContract(intent)) return
        if (handleShortcut(intent)) return
        // Pressing HOME re-delivers the intent to us; treat it as "go back to the home screen".
        homeIntentTick++
    }

    /**
     * Replies with where the app's icon is, so the system can shrink the closing window into
     * it. Present only on a gesture back to home; without a matching icon rectangle there is
     * nothing useful to say and the contract is simply consumed.
     */
    private fun handleGestureContract(intent: Intent?): Boolean {
        val extras = intent?.getBundleExtra(EXTRA_GESTURE_CONTRACT) ?: return false
        intent.removeExtra(EXTRA_GESTURE_CONTRACT)
        @Suppress("DEPRECATION")
        val component = extras.getParcelable(EXTRA_CONTRACT_COMPONENT) as? ComponentName
        @Suppress("DEPRECATION")
        val user = extras.getParcelable(EXTRA_CONTRACT_USER) as? UserHandle
        @Suppress("DEPRECATION")
        val callback = extras.getParcelable(EXTRA_CONTRACT_CALLBACK) as? Message
        val systemReply = callback?.replyTo ?: return true
        if (component == null || user == null) return true
        val serial = runCatching {
            getSystemService(UserManager::class.java).getSerialNumberForUser(user)
        }.getOrDefault(if (user == android.os.Process.myUserHandle()) 0L else Long.MIN_VALUE)
        val bounds = LaunchOrigin.of(component, serial) ?: return true
        val data = Bundle().apply {
            putParcelable(EXTRA_CONTRACT_POSITION, RectF(bounds))
            putParcelable(EXTRA_CONTRACT_SURFACE, null)
            putParcelable(
                EXTRA_CONTRACT_FINISH,
                Message.obtain(null, 0).also { it.replyTo = gestureFinish },
            )
        }
        runCatching {
            val reply = Message.obtain().apply { copyFrom(callback) }
            reply.setData(data)
            systemReply.send(reply)
        }
        return true
    }

    /**
     * Runs one of the launcher's own icon shortcuts, or reports that this intent was not one.
     *
     * The freeze list belongs to the app, not to any screen, so the two verbs are handed
     * straight to the manager and answered with a toast — the shortcut may well be pressed
     * from another launcher, where nothing of ours is on screen at all.
     */
    private fun handleShortcut(intent: Intent?): Boolean {
        val freeze = when (intent?.action) {
            ACTION_FREEZE_ALL -> true
            ACTION_UNFREEZE_ALL -> false
            else -> return false
        }
        // Consumed here: a configuration change hands the same intent to onCreate again, and
        // freezing the whole list twice for one tap is not what the shortcut meant.
        intent.action = null
        val app = application as DelugeApp
        lifecycleScope.launch {
            // A stale shortcut pressed after the feature was switched off must do nothing.
            if (!app.prefs.freezeEnabledNow()) return@launch
            FreezeToast.progress(
                this@MainActivity,
                if (freeze) R.string.freeze_working else R.string.unfreeze_working,
            )
            val toast = if (freeze) {
                val result = app.freezeManager.freezeAll()
                if (result.noAccess) getString(R.string.freeze_needs_access)
                else getString(R.string.freeze_many_done, result.done)
            } else {
                val result = app.freezeManager.unfreezeAll()
                if (result.noAccess) getString(R.string.freeze_needs_access)
                else getString(R.string.freeze_all_done)
            }
            FreezeToast.result(this@MainActivity, toast)
        }
        return true
    }

    // One widget refresh in flight at most: rapid background/foreground hops used to queue
    // a render per resume, each a binder round trip measured in seconds on a cold system.
    private var widgetRefreshJob: Job? = null

    override fun onStart() {
        super.onStart()
        val app = application as DelugeApp
        // Binder into the widget service and a settings read: not on the resume path.
        app.offMain {
            runCatching { app.widgetHost.startListening() }
            runCatching { NowPlayingListenerService.rebindIfPermitted(this) }
        }
        // The clock widget's own rows go stale on their own — an event passes, an alarm is
        // set, "in 20 minutes" stops being true. Half an hour is the shortest the system will
        // wake a widget on a schedule, so the other half of keeping it honest is redrawing it
        // whenever the home screen is actually being looked at, which costs nothing when it
        // has not changed.
        //
        // Off the main thread, and it has to be: this is a binder round trip into the system
        // server on a system that has just had this process sitting in the background, and it
        // was measured taking two full seconds on the way back while everything around it
        // took five. Nothing here feeds the first frame — it is a clock, and a number that is
        // a beat out of date is invisible. Blocking the resume on it froze the whole window
        // on a blank screen for those same two seconds.
        // Debounced and single-flight: only the last resume in a quick burst redraws.
        widgetRefreshJob?.cancel()
        widgetRefreshJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(300)
            runCatching { ClockWidgetProvider.renderAll(this@MainActivity) }
        }
    }

    override fun onStop() {
        (application as DelugeApp).widgetHost.stopListening()
        // The fader holds a static controller for this window; don't outlive the Activity.
        StatusBarFader.release()
        super.onStop()
    }

    companion object {
        /** What the launcher's own dynamic shortcuts send back in; see FreezeShortcuts. */
        const val ACTION_FREEZE_ALL = "dev.delugelauncher.action.FREEZE_ALL"
        const val ACTION_UNFREEZE_ALL = "dev.delugelauncher.action.UNFREEZE_ALL"
    }
}

/**
 * Blurs what the window sits over — the wallpaper — instead of drawing the wallpaper ourselves
 * and blurring that, which is not ours to do and would not follow a live one. Android 12 added
 * the flag and the radius together; below that the wallpaper is left alone.
 */
private fun Window.setBlurBehind(radiusDp: Int, density: Float) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val params = attributes
    if (radiusDp > 0) {
        addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        params.blurBehindRadius = (radiusDp * density).toInt()
    } else {
        clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        params.blurBehindRadius = 0
    }
    attributes = params
}

/** Night for the auto-dark schedule: from [startHour] (inclusive) to [endHour] (exclusive). */
private fun isNightHour(startHour: Int = 19, endHour: Int = 7): Boolean {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    if (startHour == endHour) return false
    return if (startHour < endHour) hour in startHour until endHour
    else hour >= startHour || hour < endHour
}

/** How long until the next whole hour, when the schedule can next change its mind. */
private fun msToNextHour(): Long {
    val now = java.util.Calendar.getInstance()
    val next = (now.clone() as java.util.Calendar).apply {
        add(java.util.Calendar.HOUR_OF_DAY, 1)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return (next.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
}