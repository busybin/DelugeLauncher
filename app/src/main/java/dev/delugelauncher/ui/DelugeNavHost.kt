// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui

import android.app.Activity.RESULT_OK
import android.app.role.RoleManager
import android.os.Build
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.delugelauncher.TypedKey
import dev.delugelauncher.BuildConfig
import dev.delugelauncher.DelugeApp
import android.widget.Toast
import dev.delugelauncher.data.IconShape
import dev.delugelauncher.data.IconPackRepository
import dev.delugelauncher.data.AppFont
import dev.delugelauncher.data.ColorSchemeChoice
import dev.delugelauncher.data.CornerAction
import dev.delugelauncher.data.SlotKind
import dev.delugelauncher.data.SlotTarget
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.EntryKind
import dev.delugelauncher.data.PrivateSpace
import dev.delugelauncher.data.AzStripVisibility
import dev.delugelauncher.data.EdgeSide
import dev.delugelauncher.data.EntryKeys
import dev.delugelauncher.data.FavoritesSource
import dev.delugelauncher.ui.applist.RESERVED_TAGS
import dev.delugelauncher.ui.common.LocalScreenshotNames
import dev.delugelauncher.ui.common.ScreenshotDisguise
import dev.delugelauncher.data.ShortcutSwipe
import dev.delugelauncher.data.UpdateChecker
import dev.delugelauncher.data.UpdateResult
import dev.delugelauncher.data.HomeAlignment
import dev.delugelauncher.data.IconSide
import dev.delugelauncher.data.HomePaddings
import dev.delugelauncher.data.QuickLaunchSlot
import dev.delugelauncher.data.TextColorMode
import dev.delugelauncher.data.HapticFeedback
import androidx.compose.ui.res.stringResource
import dev.delugelauncher.R
import dev.delugelauncher.data.folderIdFromToken
import dev.delugelauncher.data.stripsOtherProfiles
import dev.delugelauncher.media.isListenerEnabled
import dev.delugelauncher.service.SystemUi
import dev.delugelauncher.ui.common.IconPickerScreen
import dev.delugelauncher.ui.common.IconStyle
import dev.delugelauncher.ui.common.LocalIconConfig
import dev.delugelauncher.ui.common.clearIconCache
import dev.delugelauncher.ui.common.clearIconCacheForPackage
import dev.delugelauncher.ui.common.clearIconDiskCache
import dev.delugelauncher.ui.common.encodePackOverride
import dev.delugelauncher.ui.common.warmIconCache
import dev.delugelauncher.ui.home.FavoriteEntry
import dev.delugelauncher.ui.home.HomeRoute
import dev.delugelauncher.ui.home.HomeSettings
import dev.delugelauncher.ui.settings.AppPickerScreen
import dev.delugelauncher.ui.settings.FolderAppsScreen
import dev.delugelauncher.ui.settings.HiddenAppsScreen
import dev.delugelauncher.ui.settings.ChangelogScreen
import dev.delugelauncher.ui.settings.LanguageScreen
import dev.delugelauncher.ui.theme.DEFAULT_SHADOW_STRENGTH
import dev.delugelauncher.ui.settings.ManageFavoritesScreen
import dev.delugelauncher.ui.settings.SettingsScreen
import dev.delugelauncher.ui.settings.SettingsScreenModel
import dev.delugelauncher.ui.settings.SettingsActionsA
import dev.delugelauncher.ui.settings.SettingsActionsB
import dev.delugelauncher.ui.theme.rememberContentColor
import dev.delugelauncher.widget.ClockWidgetProvider
import dev.delugelauncher.widget.WidgetPickerActivity
import dev.delugelauncher.widget.WidgetSlotActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Collects the stored settings once and hosts the navigation graph.
 *
 * Everything the destinations need is read here rather than in each screen, so the DataStore
 * is collected once per key instead of once per consumer.
 */
private const val FREQUENT_TAG_COUNT = 5

/**
 * How long between silent looks at the release page.
 *
 * Six hours: a release is noticed the same day without a request riding on every HOME press,
 * and the row in About is always there to ask again on the spot.
 */
private const val UPDATE_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

/**
 * How long after a *failed* look before another.
 *
 * Fifteen minutes: long enough that an offline phone is not asking on every HOME press, short
 * enough that whatever was wrong has a chance to have been fixed — being blocked by the phone
 * itself, plugged back into the network — before anyone opens settings to wonder about it.
 */
private const val UPDATE_RETRY_INTERVAL_MS = 15L * 60L * 1000L

@Composable
fun DelugeNavHost(
    app: DelugeApp,
    homeIntentTick: Int,
    typedToSearch: List<TypedKey>,
    onTypedToSearchHandled: (List<TypedKey>) -> Unit,
    font: AppFont,
    hideStatusBar: Boolean,
    hideStatusBarAppList: Boolean,
    iconPackPackage: String?,
    iconOverrides: Map<String, String>,
    onPeekStatusBar: () -> Unit,
    /** Reported up so the status bar can stay put while the overlay is showing. */
    onAppListVisibleChange: (Boolean) -> Unit,
) {
    val navController = rememberSaveable(saver = RouterSaver) { Router("home") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Bumped on every package event, so the widget pages re-read their provider: a frozen
    // provider reads null and shows the dead page, and only a fresh read brings it back when
    // it unfreezes. The ids do not change either way, so nothing else would recompose them.
    // Declared up here because refresh() below is a local fun that must see it.
    var widgetTick by remember { mutableIntStateOf(0) }

    // Enumerating every installed app costs a PackageManager round trip per app; doing it in
    // the first composition is what stalled the cold start. Load it off the main thread and
    // let the home screen render against an empty list for the first frame.
    // Seeded from the last list this process handed out, when there is one: a
    // recreated screen then draws at once instead of standing an empty home
    // in front of the user while the fresh enumeration below runs behind it.
    // Null on a true cold start, where there is nothing to seed from yet.
    var appsAndSpace by remember {
        val cached = app.appRepository.cachedApps()
        mutableStateOf(
            if (cached != null) AppsAndSpace(cached.first, cached.second)
            else AppsAndSpace(PrivateSpace.Absent, emptyList()),
        )
    }
    val allApps = appsAndSpace.apps
    // Kept beside the list because the settings screens need it for keys whose rows are
    // deliberately absent: a favorite inside a locked private space has nothing to look up.
    val privateSpace = appsAndSpace.privateSpace
    // Reloads are started from half a dozen independent places and overlap; this is what keeps
    // the most recently read answer the one on screen. See [ReloadOrder].
    val reloadOrder = remember { ReloadOrder() }

    // The version check: one read of the release page, now and then, plus whatever the last
    // one left in the store. Held in memory as well so the settings row can show what a tap
    // just found without the store having to be asked for it.
    var updateChecking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    val updateKnownVersion by app.prefs.updateKnownVersion.collectAsState(initial = 0)
    val updatePromptedVersion by app.prefs.updatePromptedVersion.collectAsState(initial = 0)
    val updateAutoCheck by app.prefs.updateAutoCheck.collectAsState(initial = false)

    fun openReleasePage() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASES_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun checkForUpdates() {
        if (updateChecking) return
        scope.launch {
            updateChecking = true
            val result = UpdateChecker.check(
                context = context,
                currentVersion = BuildConfig.VERSION_CODE,
                userAgent = "DelugeLauncher/${BuildConfig.VERSION_NAME}",
            )
            updateChecking = false
            updateResult = result
            app.prefs.recordUpdateCheck(
                knownVersion = when (result) {
                    is UpdateResult.Available -> result.version
                    is UpdateResult.UpToDate -> result.version
                    is UpdateResult.Failed -> 0
                },
                nowMillis = System.currentTimeMillis(),
                failed = result is UpdateResult.Failed,
            )
        }
    }

    /**
     * [known] is the state to enumerate against when the caller already has one it trusts more
     * than a fresh read would be — a lock it has just been granted, which the system has not
     * finished applying and would still describe as an open space.
     *
     * [pass] is the number this reload took from [reloadOrder] before it read anything.
     */
    suspend fun reloadApps(known: PrivateSpace?, pass: Long) {
        val (state, apps) = withContext(Dispatchers.Default) {
            // Resolved once and handed on, so the list and what the settings screens conceal
            // can never disagree about whether the space was open when it was read.
            val state = known ?: app.appRepository.privateSpace()
            // First-install times are read only while some screen can show them: the pass
            // that gathers them is the slowest call in here. Asked of the store rather
            // than taken from the caller's flows, which are collected further down and
            // may still be at their initial value on the first reload of a cold start.
            val withTimes = app.prefs.recentSection.first()
            state to app.appRepository.queryAllApps(state, withInstallTimes = withTimes)
        }
        // Overtaken while it ran: this list was enumerated against a state that is no longer
        // the newest thing known about the device, and publishing it would put a locked
        // space's apps back under an open padlock. Nothing is lost by dropping it — the pass
        // that overtook this one publishes a list of its own.
        if (reloadOrder.mayPublish(pass)) {
            appsAndSpace = AppsAndSpace(state, apps)
            app.appRepository.notePublished(state, apps)
        }
    }

    // A shortcut pinned through the confirm screen, or unpinned from a menu, changes what
    // there is to list without any package changing — so nothing here would otherwise notice.
    val shortcutChanges by app.appRepository.shortcutChanges.collectAsState()
    LaunchedEffect(shortcutChanges) { reloadApps(known = null, pass = reloadOrder.begin()) }

    /**
     * Takes a state that has just been resolved and acts on what it conceals at once, on the
     * list already in hand, before the reload that will take a moment.
     *
     * The reload is a full enumeration: every profile, every activity, an icon cache thrown
     * away and rebuilt. That is long enough to read the names off a home screen, and a lock
     * that only takes effect at the end of it has left them there for exactly that long.
     *
     * Publishes nothing once [pass] has been superseded, for the same reason the reload does
     * not: this state was read before a newer one, and the direction a stale state goes wrong
     * in is the one that un-conceals.
     */
    fun adoptPrivateSpace(state: PrivateSpace, pass: Long) {
        if (!reloadOrder.mayPublish(pass)) return
        val kept = appsAndSpace.apps.filterNot { it.kind == EntryKind.PRIVATE_SPACE || state.conceals(it.key) } +
            app.appRepository.privateSpaceRow(state)
        appsAndSpace = AppsAndSpace(state, kept)
        app.appRepository.notePublished(state, kept)
    }
    // No separate LaunchedEffect(Unit) reload: the shortcutChanges effect above
    // fires on first composition as well, so another one here would enumerate
    // every installed app twice on each cold start for the same answer.

    // Before anything the user does can write to the store, so "the store is empty" still
    // means "this is a first run" when it is read.
    LaunchedEffect(Unit) { app.prefs.ensureInstallMarker() }

    // A silent look at the release page now and then. A launcher is opened many times a day,
    // so the interval is what keeps this from being a request per launch; the row in About is
    // how to ask again sooner. A check that failed is worth repeating sooner than one that
    // answered — a phone that was offline, or whose system was refusing this app a moment ago,
    // must not be left saying so until tomorrow. After the install marker and asking for it
    // again: the check writes to the store, and that marker reads "the store is empty" to tell
    // a first run from an upgrade — a write of ours landing first would make a new install
    // look like an old one.
    LaunchedEffect(Unit) {
        app.prefs.ensureInstallMarker()
        if (!app.prefs.updateAutoCheck.first()) return@LaunchedEffect
        val failed = app.prefs.lastUpdateCheckFailed.first()
        val since = System.currentTimeMillis() - app.prefs.lastUpdateCheck.first()
        if (since < if (failed) UPDATE_RETRY_INTERVAL_MS else UPDATE_CHECK_INTERVAL_MS) {
            return@LaunchedEffect
        }
        checkForUpdates()
    }

    DisposableEffect(Unit) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        fun refresh(packageName: String? = null, clearIcons: Boolean = true) {
            // Before the reload, so the widget pages re-read their provider even when the
            // enumeration below finds the same apps: freeze and unfreeze change no id.
            widgetTick++
            scope.launch {
                // Taken before the read, so anything already enumerating is superseded by the
                // moment this pass looked rather than by the moment it finished.
                val pass = reloadOrder.begin()
                // The private space first and on its own: whatever it conceals leaves the list
                // that is on screen now, rather than when the enumeration below comes back.
                val state = withContext(Dispatchers.Default) { app.appRepository.privateSpace() }
                adoptPrivateSpace(state, pass)
                // An app that ships a new icon in an update changes none of the cache
                // key's components, so nothing else would invalidate the stale bitmap.
                // Scoped to the package that changed: one update must not empty the cache
                // for the other three hundred apps. Pausing an app changes no icon at
                // all, so those passes skip the invalidation entirely rather than
                // paying the whole cold path for it.
                if (!clearIcons) {
                    // Nothing to invalidate: the pictures did not change.
                } else if (packageName != null) clearIconCacheForPackage(context, packageName)
                else clearIconDiskCache(context)
                reloadApps(state, pass)
            }
        }

        // LauncherApps reports package changes across every profile. The PACKAGE_* broadcasts
        // only ever describe the profile we run in, so an app installed into a work profile or
        // a private space would never reach the list.
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) =
                refresh(packageName)
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) =
                refresh(packageName)
            override fun onPackageChanged(packageName: String?, user: UserHandle?) =
                refresh(packageName)
            override fun onPackagesAvailable(names: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
            override fun onPackagesUnavailable(names: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
            // Pausing or resuming an app changes no package, so no package callback fires for
            // it. Without these a paused app only went grey the next time something else
            // happened to reload. No icon invalidation: the picture did not change.
            override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) = refresh(clearIcons = false)
            override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: UserHandle?) = refresh(clearIcons = false)
            // A shortcut can be added, renamed, re-iconed or switched off while we are
            // showing it, none of which is a package change. The enumeration itself comes
            // from the shortcutChanges flow below, which fires for the same event — so this
            // only drops the one package's icons instead of enumerating a second time.
            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<ShortcutInfo>,
                user: UserHandle,
            ) = clearIconCacheForPackage(context, packageName)
        }
        runCatching { launcherApps.registerCallback(callback) }

        // Locking or unlocking a private space changes no package, so no package callback
        // ever fires for it — it arrives as one of these instead. Android 15 sends PROFILE_-
        // UNAVAILABLE then PROFILE_INACCESSIBLE on locking and the matching pair on
        // unlocking, including when the screen going off re-locks the space on its own.
        // Written as strings because the Intent constants are newer than this app's minimum.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction("android.intent.action.PROFILE_AVAILABLE")
            addAction("android.intent.action.PROFILE_UNAVAILABLE")
            addAction("android.intent.action.PROFILE_ACCESSIBLE")
            addAction("android.intent.action.PROFILE_INACCESSIBLE")
            addAction("android.intent.action.PROFILE_ADDED")
            addAction("android.intent.action.PROFILE_REMOVED")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = refresh()
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        onDispose {
            runCatching { launcherApps.unregisterCallback(callback) }
            context.unregisterReceiver(receiver)
        }
    }

    // A broadcast is the only other thing that ever says the space has locked, and it is one
    // thing: it is only heard while this is composed, the system re-locks the space by itself
    // whenever the screen goes off, and a lock that was missed stays missed until something
    // else happens to reload. Coming back to the launcher is the moment that matters, so the
    // state is read again there — a handful of binder calls, with the enumeration behind it
    // only when the answer actually changed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_START) return@LifecycleEventObserver
            scope.launch {
                val probe = withContext(Dispatchers.Default) { app.appRepository.privateSpace() }
                if (probe == appsAndSpace.privateSpace) {
                    // Android's "hide when locked" is set in Settings while the space stays
                    // locked, so the state reads the same and only the padlock row differs.
                    val row = withContext(Dispatchers.Default) { app.appRepository.privateSpaceRow(probe) }
                    val shown = appsAndSpace.apps.filter { it.kind == EntryKind.PRIVATE_SPACE }
                    if (row.map { it.componentName } == shown.map { it.componentName }) return@launch
                    val pass = reloadOrder.begin()
                    val state = withContext(Dispatchers.Default) { app.appRepository.privateSpace() }
                    adoptPrivateSpace(state, pass)
                    return@launch
                }
                // Only once there is something to do: a number taken to decide nothing changed
                // would supersede a reload already running and leave the list as it was. But
                // the read above was made before the number, so something newer may have
                // published in between — a lock, say — and acting on it now would outrank that.
                // So the number is taken first and the space is read again under it.
                val pass = reloadOrder.begin()
                val state = withContext(Dispatchers.Default) { app.appRepository.privateSpace() }
                adoptPrivateSpace(state, pass)
                clearIconDiskCache(context)
                reloadApps(state, pass)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hiddenApps by app.prefs.hiddenApps.collectAsState(initial = emptySet())
    val favoriteKeys by app.prefs.favorites.collectAsState(initial = emptyList())
    val frequentExcluded by app.prefs.frequentExcluded.collectAsState(initial = emptySet())
    val favoritesEverSet by app.prefs.favoritesEverSet.collectAsState(initial = true)
    val edgeZoneBandOnly by app.prefs.edgeZoneBandOnly.collectAsState(initial = false)
    val holdScroll by app.prefs.holdScroll.collectAsState(initial = false)
    val dimOthersScrubbing by app.prefs.dimOthersScrubbing.collectAsState(initial = true)
    val markWorkApps by app.prefs.markWorkApps.collectAsState(initial = true)
    val textShadow by app.prefs.textShadow.collectAsState(initial = false)
    val textShadowStrength by app.prefs.textShadowStrength.collectAsState(initial = DEFAULT_SHADOW_STRENGTH)
    val compactTapArea by app.prefs.compactTapArea.collectAsState(initial = false)
    val stayAfterPrivateUnlock by app.prefs.stayAfterPrivateUnlock.collectAsState(initial = false)
    val privateSpaceSection by app.prefs.privateSpaceSection.collectAsState(initial = false)
    val recentSection by app.prefs.recentSection.collectAsState(initial = false)
    val recentSectionCount by app.prefs.recentSectionCount.collectAsState(initial = 5)
    val edgeZoneInsetDp by app.prefs.edgeZoneInsetDp.collectAsState(initial = 0)
    val edgeZoneInsetAuto by app.prefs.edgeZoneInsetAuto.collectAsState(initial = true)
    val webSearchFallback by app.prefs.webSearchFallback.collectAsState(initial = false)
    val swipeUpOpensSearch by app.prefs.swipeUpOpensSearch.collectAsState(initial = false)
    val holdScrollSpeed by app.prefs.holdScrollSpeed.collectAsState(initial = 4)
    // Noted the first time there is one, and never unset. Whether the hint is still wanted is a
    // question about whether someone has done this before, not about the list being empty now.
    LaunchedEffect(favoriteKeys.isNotEmpty()) {
        if (favoriteKeys.isNotEmpty()) app.prefs.markFavoritesEverSet()
    }
    val nameOverrides by app.prefs.nameOverrides.collectAsState(initial = emptyMap())
    val iconSizeDp by app.prefs.iconSizeDp.collectAsState(initial = 56)
    val labelSizeSp by app.prefs.labelSizeSp.collectAsState(initial = 16)
    val itemSpacingDp by app.prefs.itemSpacingDp.collectAsState(initial = 10)
    val sidePaddingDp by app.prefs.sidePaddingDp.collectAsState(initial = 20)
    val nowPlayingHeightDp by app.prefs.nowPlayingHeightDp.collectAsState(initial = 64)
    val homePaddings by app.prefs.homePaddings.collectAsState(initial = HomePaddings.Default)
    val edgeSide by app.prefs.edgeSide.collectAsState(initial = EdgeSide.BOTH)
    val azStripVisibility by app.prefs.azStripVisibility.collectAsState(initial = AzStripVisibility.NEVER)
    val showAlphabet by app.prefs.showAlphabet.collectAsState(initial = true)
    val alignRight by app.prefs.alignRight.collectAsState(initial = false)
    val dimWallpaperAlpha by app.prefs.dimWallpaperAlpha.collectAsState(initial = 0.35f)
    val wallpaperBlurDp by app.prefs.wallpaperBlurDp.collectAsState(initial = 0)
    val hapticFeedback by app.prefs.hapticFeedback.collectAsState(initial = HapticFeedback.MEDIUM)
    val dimHomeAlpha by app.prefs.dimHomeAlpha.collectAsState(initial = 0f)
    val showFavoriteLabels by app.prefs.showFavoriteLabels.collectAsState(initial = true)
    val textColorMode by app.prefs.textColorMode.collectAsState(initial = TextColorMode.AUTO)
    val doubleTapToLock by app.prefs.doubleTapToLock.collectAsState(initial = false)
    val widgetId by app.prefs.widgetId.collectAsState(initial = -1)
    val widgetPosition by app.prefs.widgetPosition.collectAsState(initial = 0)
    val widgetHeightDp by app.prefs.widgetHeightDp.collectAsState(initial = 180)
    val hasCustomWidgetHeight by app.prefs.hasCustomWidgetHeight.collectAsState(initial = true)
    val defaultPromptDismissed by app.prefs.defaultPromptDismissed.collectAsState(initial = false)
    val nowPlayingEnabled by app.prefs.nowPlayingEnabled.collectAsState(initial = false)
    val folders by app.prefs.folders.collectAsState(initial = emptyList())
    val widgetIds by app.prefs.widgetIds.collectAsState(initial = emptyList())
    val widgetProviders by app.prefs.widgetProviders.collectAsState(initial = emptyMap())
    // A widget whose provider is gone — its app uninstalled, or an id restored from another
    // device — is dropped once, rather than left as a page that says it is unavailable. But a
    // frozen or suspended app reads null too, and that is temporary: only an id whose package
    // is actually gone goes, while one whose package is still installed stays for the unfreeze.
    // Ids from before the provider mapping existed name no package and keep the old answer.
    LaunchedEffect(widgetIds, widgetProviders) {
        if (widgetIds.isEmpty()) return@LaunchedEffect
        // The provider map arrives on its own flow: cleaning while it is still empty would
        // mistake "mapping not here yet" for "package uninstalled" and delete live widgets.
        if (widgetIds.any { !widgetProviders.containsKey(it) }) return@LaunchedEffect
        val manager = AppWidgetManager.getInstance(context)
        val pm = context.packageManager
        fun packageGone(packageName: String): Boolean =
            runCatching { pm.getPackageInfo(packageName, 0); false }.getOrDefault(true)
        app.prefs.keepWidgetIds { id ->
            if (runCatching { manager.getAppWidgetInfo(id) }.getOrNull() != null) return@keepWidgetIds true
            val pkg = widgetProviders[id] ?: return@keepWidgetIds false
            !packageGone(pkg)
        }
    }
    /**
     * What the widgets ask for themselves, when nobody has said otherwise by hand.
     *
     * Every page shares one height so swiping never moves the favorites underneath, so
     * this is the tallest minimum among the installed widgets rather than one height
     * each. A provider that names no minimum does not vote. Nothing here is stored:
     * the stored number, once set by the stepper, wins over this for good, until the
     * widget menu hands the decision back.
     */
    var autoWidgetHeightResolved by remember(widgetIds) { mutableStateOf<Int?>(null) }
    // Binder per widget id: measured off the composition thread, applied when it lands.
    LaunchedEffect(widgetIds) {
        autoWidgetHeightResolved = withContext(Dispatchers.Default) {
            val manager = AppWidgetManager.getInstance(context)
            widgetIds.mapNotNull { id ->
                runCatching { manager.getAppWidgetInfo(id)?.minHeight?.takeIf { it > 0 } }.getOrNull()
            }.maxOrNull()?.coerceIn(80, 900)
        }
    }
    val effectiveWidgetHeightDp =
        if (hasCustomWidgetHeight) widgetHeightDp else (autoWidgetHeightResolved ?: widgetHeightDp)
    val widgetSidePaddingDp by app.prefs.widgetSidePaddingDp.collectAsState(initial = sidePaddingDp)
    val widgetOffsetXDp by app.prefs.widgetOffsetXDp.collectAsState(initial = 0)
    val swipeUpOpensList by app.prefs.swipeUpOpensList.collectAsState(initial = false)
    val appListSearchEnabled by app.prefs.appListSearchEnabled.collectAsState(initial = false)
    val appListSearchBottom by app.prefs.appListSearchBottom.collectAsState(initial = false)
    val sectionTopPercent by app.prefs.sectionTopPercent.collectAsState(initial = 26)
    val closeFolderOnLaunch by app.prefs.closeFolderOnLaunch.collectAsState(initial = false)
    val showListHeaders by app.prefs.showListHeaders.collectAsState(initial = true)
    val showSettingsRow by app.prefs.showSettingsRow.collectAsState(initial = true)
    val cornerButtonKey by app.prefs.cornerButtonKey.collectAsState(initial = null)
    val cornerTapAction by app.prefs.cornerTapAction.collectAsState(initial = CornerAction.OPEN_APP)
    val cornerSwipeAction by app.prefs.cornerSwipeAction.collectAsState(initial = CornerAction.APP_LIST)
    val cornerSwipeKey by app.prefs.cornerSwipeKey.collectAsState(initial = null)
    val autoKeyboard by app.prefs.autoKeyboard.collectAsState(initial = false)
    val lastLetterToLine by app.prefs.lastLetterToLine.collectAsState(initial = false)
    val showFavoriteIcons by app.prefs.showFavoriteIcons.collectAsState(initial = true)
    val favoritesSource by app.prefs.favoritesSource.collectAsState(initial = FavoritesSource.MANUAL)
    val frequentCount by app.prefs.frequentCount.collectAsState(initial = 6)
    val appListSearchHidden by app.prefs.appListSearchHidden.collectAsState(initial = false)
    val sortByUsage by app.prefs.sortByUsage.collectAsState(initial = false)
    val launchCounts by app.prefs.launchCounts.collectAsState(initial = emptyMap())
    val edgeZoneWidthDp by app.prefs.edgeZoneWidthDp.collectAsState(initial = 56)
    val quickLaunchLeftKey by app.prefs.quickLaunchLeft.collectAsState(initial = null)
    val quickLaunchRightKey by app.prefs.quickLaunchRight.collectAsState(initial = null)
    val quickLaunchLeftAction by app.prefs.quickLaunchLeftAction
        .collectAsState(initial = CornerAction.OPEN_APP)
    val quickLaunchRightAction by app.prefs.quickLaunchRightAction
        .collectAsState(initial = CornerAction.OPEN_APP)
    val showAppIcons by app.prefs.showAppIcons.collectAsState(initial = true)
    val notificationBadges by app.prefs.notificationBadges.collectAsState(initial = false)
    val dimPausedApps by app.prefs.dimPausedApps.collectAsState(initial = true)
    val autoDark by app.prefs.autoDark.collectAsState(initial = false)
    val nightStartHour by app.prefs.nightStartHour.collectAsState(initial = 19)
    val nightEndHour by app.prefs.nightEndHour.collectAsState(initial = 7)
    val pinchToResize by app.prefs.pinchToResize.collectAsState(initial = true)
    val reduceMotion by app.prefs.reduceMotion.collectAsState(initial = false)
    val homeTwoColumnPortrait by app.prefs.homeTwoColumnPortrait.collectAsState(initial = false)
    val homeTwoColumnLandscape by app.prefs.homeTwoColumnLandscape.collectAsState(initial = true)
    val listTwoColumnPortrait by app.prefs.listTwoColumnPortrait.collectAsState(initial = false)
    val listTwoColumnLandscape by app.prefs.listTwoColumnLandscape.collectAsState(initial = true)
    val screenshotMode by app.prefs.screenshotMode.collectAsState(initial = false)
    // Built fresh each time the mode turns on and dropped when it turns off: nothing stored,
    // so switching off restores every name and icon by itself. Keyed on the mode alone, so
    // an unrelated reload while the mode is off costs nothing at all.
    val disguise = if (!screenshotMode) {
        null
    } else {
        remember(allApps) { ScreenshotDisguise.build(allApps) }
    }
    val fontFile by app.prefs.fontFile.collectAsState(initial = null)
    val colorSchemeChoice by app.prefs.colorSchemeChoice.collectAsState(
        initial = ColorSchemeChoice.DELUGE_BLUE
    )
    val textColorCustom by app.prefs.textColorCustom.collectAsState(initial = 0xFFFFFFFF.toInt())
    val dimColor by app.prefs.dimColor.collectAsState(initial = 0xFF000000.toInt())
    val rotatesByDefault = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val allowRotationPref by app.prefs.allowRotation.collectAsState(initial = null)
    val shortcutSwipe by app.prefs.shortcutSwipe.collectAsState(initial = ShortcutSwipe.RIGHT)
    val allowRotation = allowRotationPref ?: rotatesByDefault
    val iconShape by app.prefs.iconShape.collectAsState(initial = IconShape.SYSTEM)
    val themedIcons by app.prefs.themedIcons.collectAsState(initial = false)
    val alignment by app.prefs.alignment.collectAsState(initial = HomeAlignment.LEFT)
    val appListAlignment by app.prefs.appListAlignment.collectAsState(initial = HomeAlignment.LEFT)
    val iconSide by app.prefs.iconSide.collectAsState(initial = IconSide.LEFT)
    val statusBarPeekSeconds by app.prefs.statusBarPeekSeconds.collectAsState(initial = 5)
    val scrubBand by app.prefs.scrubBand.collectAsState(initial = null)
    val layoutDefaultsVersion by app.prefs.layoutDefaultsVersion.collectAsState(initial = null)

    // A brand new launcher gets its own clock, so the home screen is not simply empty on first
    // sight. Only ever on an install that has never been set up — the marker is written once,
    // before anything the user does — and only while there is no widget at all, so nobody's
    // arrangement is added to and a widget they removed does not come back.
    LaunchedEffect(layoutDefaultsVersion, widgetIds) {
        if (layoutDefaultsVersion != 1 || widgetIds.isNotEmpty()) return@LaunchedEffect
        val manager = AppWidgetManager.getInstance(context)
        val id = app.widgetHost.allocateAppWidgetId()
        val bound = runCatching {
            manager.bindAppWidgetIdIfAllowed(id, ClockWidgetProvider.componentName(context))
        }.getOrDefault(false)
        if (!bound) {
            // Binding is refused unless this launcher holds the home role, which it may not
            // yet on the very first run. The id is given back rather than left stranded.
            runCatching { app.widgetHost.deleteAppWidgetId(id) }
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) { ClockWidgetProvider.render(context, manager, id) }
        app.prefs.addWidgetId(id)
        // The provider is known here, at bind time — see widgetProviders on why it is written.
        app.prefs.setWidgetProvider(id, ClockWidgetProvider.componentName(context).packageName)
    }
    val welcomeSeen by app.prefs.welcomeSeen.collectAsState(initial = true)
    val hasCustomLayout by app.prefs.hasCustomLayout.collectAsState(initial = true)
    val hasCustomFavoritesTop by app.prefs.hasCustomFavoritesTop.collectAsState(initial = true)
    var settingsTick by remember { mutableIntStateOf(0) }
    // Bumped on every package event, so the widget pages re-read their provider: a frozen
    // provider reads null and shows the dead page, and only a fresh read brings it back when
    // it unfreezes. The ids do not change either way, so nothing else would recompose them.
    val contentColor = rememberContentColor(textColorMode, textColorCustom)

    val appsByKey = remember(allApps) { allApps.associateBy { it.key } }
    val appTags by app.prefs.appTags.collectAsState(initial = emptyMap())
    val autoFrequentWords by app.prefs.autoFrequentWords.collectAsState(initial = emptySet())
    val autoFrequentTag by app.prefs.autoFrequentTag.collectAsState(initial = false)
    // The name of the auto tag in this language. Stored as-is: tags are user data, and one
    // named in this language stays put when the language changes.
    val frequentTag = stringResource(R.string.tag_frequent)

    fun topFrequentKeys(): Set<String> = appsByKey.values
        .filter { it.kind == EntryKind.APP && it.key !in favoriteKeys && (launchCounts[it.key] ?: 0) > 0 }
        .sortedByDescending { launchCounts[it.key] ?: 0 }
        .take(FREQUENT_TAG_COUNT)
        .map { it.key }
        .toSet()

    // Follows the counts the way the usage sort does: whoever newly enters the top five gains
    // the tag, whoever falls out loses it, other tags untouched. Keyed on everything that
    // decides membership but not on the tags themselves, so writing them does not loop.
    // A language switch changes the tag's own word: every other language's word is stripped
    // here (the picker refuses them), so sweeping them all is safe. The recorded words cover
    // languages added after the static set was written.
    LaunchedEffect(autoFrequentTag, launchCounts, favoriteKeys, frequentTag) {
        if (!autoFrequentTag) return@LaunchedEffect
        app.prefs.recordAutoFrequentWord(frequentTag)
        val top = topFrequentKeys()
        // One edit for the whole pass: per-app writes fanned out a DataStore write per app on
        // every launch count change, each waking every collector on this screen.
        val current = appTags
        val reserved = RESERVED_TAGS + autoFrequentWords
        var changed = false
        val next = HashMap<String, Set<String>>(current)
        appsByKey.values.forEach { a ->
            if (a.kind != EntryKind.APP) return@forEach
            val have = current[a.key] ?: emptySet()
            val wantBase = have - reserved
            val want = if (a.key in top) wantBase + frequentTag else wantBase
            if (want != have) {
                changed = true
                if (want.isEmpty()) next.remove(a.key) else next[a.key] = want
            }
        }
        if (changed) app.prefs.setAllAppTags(next)
    }
    // The number the hidden-apps screen itself arrives at, rather than the size of the stored
    // set: that screen lists rows, and a hidden app inside a locked private space has no row.
    // Counting the stored set in the subtitle says out loud how many apps are in there.
    val hiddenShownCount = remember(allApps, hiddenApps) { allApps.count { it.key in hiddenApps } }
    val foldersById = remember(folders) { folders.associateBy { it.id } }

    // A favorites row is an app or a folder; both come out of the same ordered token list.
    //
    // Or out of what has actually been opened, when the section is set to keep itself. The rows
    // are the same rows either way — it is the list that is computed, not what is drawn from it
    // — so nothing downstream knows or cares which of the two it was handed.
    //
    // Folders are not in the computed list: a folder is something someone made, and there is no
    // count of openings that would put one together.
    val favoriteEntries = remember(
        favoritesSource,
        favoriteKeys,
        appsByKey,
        foldersById,
        hiddenApps,
        privateSpace,
        if (favoritesSource == FavoritesSource.FREQUENT) launchCounts else emptyMap(),
        frequentCount,
        frequentExcluded,
    ) {
        if (favoritesSource == FavoritesSource.FREQUENT) {
            launchCounts.asSequence()
                .filter { it.value > 0 }
                // Before take, so the next most-used app moves up into the slot rather than
                // the row simply going missing.
                .filter { it.key !in frequentExcluded }
                .mapNotNull { (key, count) -> appsByKey[key]?.let { it to count } }
                // Hidden or concealed rows must not surface here: hiding an app hides it
                // everywhere, and a locked space's apps have no business on the home screen.
                .filter { (app, _) -> app.key !in hiddenApps && !privateSpace.conceals(app.key) }
                .sortedWith(
                    compareByDescending<Pair<AppInfo, Int>> { it.second }
                        .thenBy { it.first.label.lowercase(java.util.Locale.ROOT) }
                        .thenBy { it.first.key }
                )
                .take(frequentCount)
                .map { FavoriteEntry.App(it.first) }
                .toList()
        } else {
            favoriteKeys.mapNotNull { token ->
                if (token in hiddenApps) return@mapNotNull null
                val folderId = folderIdFromToken(token)
                if (folderId != null) {
                    foldersById[folderId]?.let { FavoriteEntry.FolderRef(it) }
                } else {
                    appsByKey[token]?.let { FavoriteEntry.App(it) }
                }
            }
        }
    }

    // Rasterise icons in the background so opening the A-Z list doesn't have to. Favorites and
    // folder members go first: they are what the home screen needs before anything else.
    val listIconPx = with(LocalDensity.current) { iconSizeDp.dp.roundToPx() }
    val priorityKeys = remember(favoriteKeys, folders) {
        favoriteKeys.toSet() + folders.flatMap { it.apps }
    }
    // Same style the rows will ask for, or the warm pass fills the cache under keys nothing
    // then looks up, and every icon is rasterised twice.
    val iconCfg = LocalIconConfig.current
    val scheme = MaterialTheme.colorScheme
    val iconStyle = remember(iconCfg, scheme) {
        IconStyle(
            shape = iconCfg.shape,
            themed = iconCfg.themed,
            background = scheme.primaryContainer.toArgb(),
            foreground = scheme.onPrimaryContainer.toArgb(),
        )
    }
    LaunchedEffect(allApps, iconPackPackage, iconOverrides, listIconPx, priorityKeys, iconStyle) {
        warmIconCache(context, allApps, iconPackPackage, iconOverrides, listIconPx, iconStyle, priorityKeys)
    }

    // Written through the document picker rather than to a path of our own: the file is the
    // user's to keep, and this way it lands wherever they keep things without the app asking
    // for storage it otherwise never needs.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Every state but Absent strips, including the ones with no serial to strip by:
            // an unreadable space is the case most in need of it, and it is the case a strip
            // keyed on the serial does nothing in.
            val stripOtherProfiles = privateSpace.stripsOtherProfiles
            val omittedPrivateSpace = withContext(Dispatchers.IO) {
                runCatching {
                    val result = app.prefs.exportJson(stripOtherProfiles)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(result.json.toByteArray()) }
                        ?: return@runCatching null
                    result.omittedPrivateSpace
                }.getOrNull()
            }
            Toast.makeText(
                context,
                when (omittedPrivateSpace) {
                    null -> R.string.settings_backup_failed
                    true -> R.string.settings_export_done_private_omitted
                    false -> R.string.settings_export_done
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            val ok = text != null && app.prefs.importJson(text)
            // A shortcut lives on the launcher's behalf rather than in the file, so a
            // restored favorite naming one comes back as a row that resolves to nothing
            // until the system is asked for it again. Done here, right after the file is
            // in place, and before the reload the shortcut change triggers.
            if (ok) {
                val restored = withContext(Dispatchers.IO) {
                    runCatching { app.prefs.favorites.first() }.getOrDefault(emptyList())
                }
                withContext(Dispatchers.Default) { app.appRepository.repinShortcuts(restored) }
            }
            Toast.makeText(
                context,
                if (ok) R.string.settings_import_done else R.string.settings_backup_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val settings = HomeSettings(
        iconSizeDp = iconSizeDp,
        labelSizeSp = labelSizeSp,
        itemSpacingDp = itemSpacingDp,
        sidePaddingDp = sidePaddingDp,
        nowPlayingHeightDp = nowPlayingHeightDp,
        nowPlayingEnabled = nowPlayingEnabled,
        edgeSide = edgeSide,
        azStripVisibility = azStripVisibility,
        shortcutSwipe = shortcutSwipe,
        showAlphabet = showAlphabet,
        alignment = alignment,
        appListAlignment = appListAlignment,
        iconSide = iconSide,
        widgetSidePaddingDp = widgetSidePaddingDp,
        widgetOffsetXDp = widgetOffsetXDp,
        edgeZoneWidthDp = edgeZoneWidthDp,
        swipeUpOpensAppList = swipeUpOpensList,
        appListSearch = appListSearchEnabled,
        appListSearchBottom = appListSearchBottom,
        sectionTopPercent = sectionTopPercent,
        closeFolderOnLaunch = closeFolderOnLaunch,
        showListHeaders = showListHeaders,
        showSettingsRow = showSettingsRow,
        showFavoriteIcons = showFavoriteIcons,
        favoritesEverSet = favoritesEverSet,
        edgeZoneBandOnly = edgeZoneBandOnly,
        holdScroll = holdScroll,
        dimOthersScrubbing = dimOthersScrubbing,
        markWorkApps = markWorkApps,
        textShadow = textShadow,
        textShadowStrength = textShadowStrength,
        compactTapArea = compactTapArea,
        stayAfterPrivateUnlock = stayAfterPrivateUnlock,
        privateSpaceSection = privateSpaceSection,
        recentSection = recentSection,
        recentSectionCount = recentSectionCount,
        edgeZoneInsetDp = edgeZoneInsetDp,
        edgeZoneInsetAuto = edgeZoneInsetAuto,
        webSearchFallback = webSearchFallback,
        swipeUpOpensSearch = swipeUpOpensSearch,
        holdScrollSpeed = holdScrollSpeed,
        favoritesSource = favoritesSource,
        appListSearchHidden = appListSearchHidden,
        // Screenshot disguise always hides the status bar, whatever the setting says.
        hideStatusBarAppList = hideStatusBarAppList || screenshotMode,
        hideStatusBar = hideStatusBar || screenshotMode,
        sortByUsage = sortByUsage,
        quickLaunchLeft = quickLaunchLeftKey?.let { appsByKey[it] },
        quickLaunchRight = quickLaunchRightKey?.let { appsByKey[it] },
        quickLaunchLeftAction = quickLaunchLeftAction,
        quickLaunchRightAction = quickLaunchRightAction,
        // Both flows start null/true so nothing is centered or offered before the stored
        // answer arrives; a legacy install is stamped 0 and never enters either path.
        centerFavorites = layoutDefaultsVersion == 1 && !hasCustomFavoritesTop,
        dimWallpaperAlpha = dimWallpaperAlpha,
        dimHomeAlpha = dimHomeAlpha,
        dimColor = dimColor,
        hapticFeedback = hapticFeedback,
        showFavoriteLabels = showFavoriteLabels,
        doubleTapToLock = doubleTapToLock,
        contentColor = contentColor,
        dimPausedApps = dimPausedApps,
        pinchToResize = pinchToResize,
        reduceMotion = reduceMotion,
        homeTwoColumnPortrait = homeTwoColumnPortrait,
        homeTwoColumnLandscape = homeTwoColumnLandscape,
        listTwoColumnPortrait = listTwoColumnPortrait,
        listTwoColumnLandscape = listTwoColumnLandscape,
    )

    var pendingIconTarget by remember { mutableStateOf<String?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val target = pendingIconTarget
        if (uri != null && target != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val folderId = folderIdFromToken(target)
            scope.launch {
                if (folderId != null) {
                    app.prefs.setFolderIcon(folderId, uri.toString())
                } else {
                    app.prefs.setIconOverride(target, uri.toString())
                }
            }
        }
        pendingIconTarget = null
    }

    val widgetPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            // Appended rather than replacing: picking a widget now adds a page.
            if (id != -1) scope.launch {
                app.prefs.addWidgetId(id)
                // The provider is known here, at bind time — see widgetProviders.
                AppWidgetManager.getInstance(context).getAppWidgetInfo(id)
                    ?.let { app.prefs.setWidgetProvider(id, it.provider.packageName) }
            }
        }
    }

    val widgetActions = remember {
        WidgetSlotActions(
            onAddWidget = { widgetPickerLauncher.launch(Intent(context, WidgetPickerActivity::class.java)) },
            onRemoveWidget = { id ->
                scope.launch {
                    // Releasing the host id matters: left allocated, its provider keeps
                    // broadcasting updates to a widget nobody can see.
                    if (id > 0) app.widgetHost.deleteAppWidgetId(id)
                    app.prefs.removeWidgetId(id)
                }
            },
            onWidgetSettings = { id ->
                val configure = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.configure
                if (configure != null) {
                    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                        component = configure
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    }
                    runCatching { context.startActivity(intent) }
                }
            },
            onAppInfo = { id ->
                AppWidgetManager.getInstance(context).getAppWidgetInfo(id)
                    ?.let { app.appRepository.openAppInfo(it.provider.packageName) }
            },
            onResize = { newHeight -> scope.launch { app.prefs.setWidgetHeightDp(newHeight) } },
            // Null while the height is automatic, so the menu offers no reset for it.
            onResetHeight = if (hasCustomWidgetHeight) {
                { scope.launch { app.prefs.clearWidgetHeightDp() } }
            } else {
                null
            },
            onOpenSettings = { navController.navigate("settings") },
        )
    }

    // HOME has to unwind the whole stack. The equivalent effect inside the home destination
    // can't do this: that destination isn't composed while Settings is on screen.
    // Drained to the last screen rather than popped to a named one: the stack can hold
    // "home" twice (home -> settings -> home), and popping to the name keeps the middle.
    LaunchedEffect(homeIntentTick) {
        if (homeIntentTick > 0 && navController.currentDestination?.route != "home") {
            while (navController.popBackStack()) { /* unwind fully */ }
        }
    }

    // System BACK walks the stack, one screen at a time. Without this it reached the Activity
    // and finished it, dropping straight to the home screen from any settings screen — the
    // screens above home are the launcher's own, so back has to mean back.
    BackHandler(enabled = navController.stack.size > 1) { navController.popBackStack() }

    RouterHost(router = navController) {
        composable("home") {
            // Fake names for the screenshot disguise, or nothing. Scoped to home and its
            // list: the management screens keep real names and icons.
            CompositionLocalProvider(LocalScreenshotNames provides disguise) {
            HomeRoute(
                cornerButtonApp = cornerButtonKey?.takeIf { it != EntryKeys.SEARCH }?.let { appsByKey[it] },
                cornerButtonIsSearch = cornerButtonKey == EntryKeys.SEARCH,
                autoKeyboard = autoKeyboard,
                lastLetterToLine = lastLetterToLine,
                app = app,
                homeIntentTick = homeIntentTick,
                typedToSearch = typedToSearch,
                onTypedToSearchHandled = onTypedToSearchHandled,
                settings = settings,
                homePaddings = homePaddings,
                favorites = favoriteEntries,
                appsByKey = appsByKey,
                folders = folders,
                favoriteKeys = favoriteKeys,
                hiddenApps = hiddenApps,
                nameOverrides = nameOverrides,
                widgetIds = widgetIds,
                widgetPosition = widgetPosition,
                widgetHeightDp = effectiveWidgetHeightDp,
                widgetActions = widgetActions,
                widgetTick = widgetTick,
                launchCounts = launchCounts,
                // Both flows start at a value that shows nothing, so the dialog can't flash
                // before the stored answer arrives. A legacy install is stamped 0 and never
                // qualifies.
                showWelcome = layoutDefaultsVersion == 1 && !welcomeSeen,
                // Asked again every time HOME is pressed, since changing it is done outside
                // this app and the answer moves while the card is on screen. isRoleHeld is
                // a binder call, so it is keyed to the HOME tick rather than run per frame.
                showDefaultPrompt = !defaultPromptDismissed &&
                    !remember(homeIntentTick) { isDefaultHome(context) },
                onDismissDefaultPrompt = { scope.launch { app.prefs.dismissDefaultPrompt() } },
                onOpenHomeSettings = {
                    settingsTick++
                    val opened = runCatching {
                        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                        true
                    }.getOrDefault(false)
                    if (!opened) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    }
                },
                onWelcomeDismissed = {
                    scope.launch {
                        app.prefs.applyNewInstallDefaults()
                        app.prefs.setWelcomeSeen(true)
                    }
                },
                scrubBandFractions = scrubBand,
                onSetScrubBand = { top, height -> scope.launch { app.prefs.setScrubBand(top, height) } },
                onClearScrubBand = { scope.launch { app.prefs.clearScrubBand() } },
                onPeekStatusBar = onPeekStatusBar,
                onAppListVisibleChange = onAppListVisibleChange,
                onTogglePrivateSpace = {
                    scope.launch {
                        val locked = app.appRepository.togglePrivateSpace()
                        // Taken after the system has answered, not before: on a phone with a
                        // screen lock the authentication in front of an unlock takes as long
                        // as the person does, and a pass held open across it would supersede
                        // every reload started while the dialog was up.
                        val pass = reloadOrder.begin()
                        // Granted means locked, and locked is concealed here and now: the
                        // broadcast that says so arrives well after the profile has stopped.
                        if (locked != null) adoptPrivateSpace(locked, pass)
                        clearIconDiskCache(context)
                        // Enumerated against the lock rather than against what the system says
                        // this instant, which for the next moment is still an open space.
                        reloadApps(locked, pass)
                    }
                },
                onNavigate = { route -> navController.navigate(route) },
                disguiseNames = disguise ?: emptyMap(),
            )
            }
        }

        composable("settings") {
            // Package-manager query, five of them: off the composition thread or the settings
            // screen drops frames on its way in.
            var iconPacks by remember { mutableStateOf(emptyList<IconPackRepository.IconPackInfo>()) }
            LaunchedEffect(Unit) {
                iconPacks = withContext(Dispatchers.Default) {
                    app.iconPackRepository.getInstalledIconPacks()
                }
            }
            // Same grant, same staleness as on home: re-check on resume, since the user
            // grants it in system Settings and comes back with BACK, not HOME.
            val settingsLifecycle = LocalLifecycleOwner.current
            var settingsResumeTick by remember { mutableStateOf(0) }
            DisposableEffect(settingsLifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        settingsResumeTick++
                        // Same heal-or-nudge as home: the grant screen is reached from
                        // here, and coming back is a resume, not a HOME intent.
                        SystemUi.refreshAccessibilityBinding(context)
                    }
                }
                settingsLifecycle.lifecycle.addObserver(observer)
                onDispose { settingsLifecycle.lifecycle.removeObserver(observer) }
            }
            val listenerEnabled = remember(homeIntentTick, settingsResumeTick) { isListenerEnabled(context) }
            // The Settings app rather than whatever happens to sort first: a stable,
            // recognizable icon to judge a size against on every device. Resolved off the
            // composition thread: resolveActivity is a binder call.
            var previewApp by remember { mutableStateOf<AppInfo?>(null) }
            LaunchedEffect(allApps) {
                previewApp = withContext(Dispatchers.Default) {
                    val settingsPkg = runCatching {
                        @Suppress("DEPRECATION")
                        context.packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)
                            ?.activityInfo?.packageName
                    }.getOrNull()
                    allApps.firstOrNull { it.packageName == settingsPkg }
                        ?: allApps.firstOrNull { it.packageName == "com.android.settings" }
                        ?: allApps.firstOrNull()
                }
            }
            // Asked again whenever the launcher is returned to, since changing it is the
            // whole point of the row and the answer changes outside this app. Resolved off
            // the composition thread: resolveActivity is a binder call.
            var isDefaultLauncher by remember { mutableStateOf(false) }
            LaunchedEffect(homeIntentTick, settingsTick) {
                isDefaultLauncher = withContext(Dispatchers.Default) {
                    runCatching {
                        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        context.packageManager
                            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                            ?.activityInfo?.packageName == context.packageName
                    }.getOrDefault(false)
                }
            }
            @Composable
            fun buildActionsA() = SettingsActionsA(
                    onSetShowFavoriteIcons = { scope.launch { app.prefs.setShowFavoriteIcons(it) } },
                    onSetTextShadowStrength = { scope.launch { app.prefs.setTextShadowStrength(it) } },
                    onSetStayAfterPrivateUnlock = { scope.launch { app.prefs.setStayAfterPrivateUnlock(it) } },
                    onSetCompactTapArea = { scope.launch { app.prefs.setCompactTapArea(it) } },
                    onSetPrivateSpaceSection = { scope.launch { app.prefs.setPrivateSpaceSection(it) } },
                    onSetRecentSection = { scope.launch { app.prefs.setRecentSection(it) } },
                    onSetRecentSectionCount = { scope.launch { app.prefs.setRecentSectionCount(it) } },
                    onSetTextShadow = { scope.launch { app.prefs.setTextShadow(it) } },
                    onSetEdgeZoneInsetAuto = { scope.launch { app.prefs.setEdgeZoneInsetAuto(it) } },
                    onSetEdgeZoneInsetDp = { scope.launch { app.prefs.setEdgeZoneInsetDp(it) } },
                    onSetMarkWorkApps = { scope.launch { app.prefs.setMarkWorkApps(it) } },
                    onSetDimOthersScrubbing = { scope.launch { app.prefs.setDimOthersScrubbing(it) } },
                    onSetWebSearchFallback = { scope.launch { app.prefs.setWebSearchFallback(it) } },
                    onSetSwipeUpOpensSearch = { scope.launch { app.prefs.setSwipeUpOpensSearch(it) } },
                    onSetHoldScroll = { scope.launch { app.prefs.setHoldScroll(it) } },
                    onSetHoldScrollSpeed = { scope.launch { app.prefs.setHoldScrollSpeed(it) } },
                    onSetEdgeZoneBandOnly = { scope.launch { app.prefs.setEdgeZoneBandOnly(it) } },
                    onSetShowSettingsRow = { scope.launch { app.prefs.setShowSettingsRow(it) } },
                    onSetShowListHeaders = { scope.launch { app.prefs.setShowListHeaders(it) } },
                    onSetNotificationBadges = { scope.launch { app.prefs.setNotificationBadges(it) } },
                    onSetDimPausedApps = { scope.launch { app.prefs.setDimPausedApps(it) } },
                    onSetAutoDark = { scope.launch { app.prefs.setAutoDark(it) } },
                    onSetCornerTapAction = { scope.launch { app.prefs.setCornerTapAction(it) } },
                    onSetCornerSwipeAction = { scope.launch { app.prefs.setCornerSwipeAction(it) } },
                    onSetNightStartHour = { scope.launch { app.prefs.setNightStartHour(it) } },
                    onSetNightEndHour = { scope.launch { app.prefs.setNightEndHour(it) } },
                    onSetPinchToResize = { scope.launch { app.prefs.setPinchToResize(it) } },
                    onSetReduceMotion = { scope.launch { app.prefs.setReduceMotion(it) } },
                    onSetSectionTopPercent = { scope.launch { app.prefs.setSectionTopPercent(it) } },
                    onSetCloseFolderOnLaunch = { scope.launch { app.prefs.setCloseFolderOnLaunch(it) } },
                    onSetFavoritesSource = { scope.launch { app.prefs.setFavoritesSource(it) } },
                    onSetFrequentCount = { scope.launch { app.prefs.setFrequentCount(it) } },
                    onSetScreenshotMode = { scope.launch { app.prefs.setScreenshotMode(it) } },
                    onSetIconPack = { scope.launch { app.prefs.setIconPackPackage(it) } },
                    onSetShowAppIcons = { scope.launch { app.prefs.setShowAppIcons(it) } },
                    onSetIconSize = { scope.launch { app.prefs.setIconSizeDp(it) } },
                    onSetLabelSize = { scope.launch { app.prefs.setLabelSizeSp(it) } },
                    onSetItemSpacing = { scope.launch { app.prefs.setItemSpacingDp(it) } },
                    onSetFont = { scope.launch { app.prefs.setFont(it) } },
                    onSetColorScheme = { scope.launch { app.prefs.setColorSchemeChoice(it) } },
                    onSetHideStatusBar = { scope.launch { app.prefs.setHideStatusBar(it) } },
                    onSetHideStatusBarAppList = { scope.launch { app.prefs.setHideStatusBarAppList(it) } },
                    onSetStatusBarPeekSeconds = { scope.launch { app.prefs.setStatusBarPeekSeconds(it) } },
                    onSetDimWallpaper = { scope.launch { app.prefs.setDimWallpaperAlpha(it) } },
                    onSetWallpaperBlur = { scope.launch { app.prefs.setWallpaperBlurDp(it) } },
            )

            @Composable
            fun buildActionsB() = SettingsActionsB(
                    onSetHapticFeedback = { scope.launch { app.prefs.setHapticFeedback(it) } },
                    onSetDimHome = { scope.launch { app.prefs.setDimHomeAlpha(it) } },
                    onSetShowFavoriteLabels = { scope.launch { app.prefs.setShowFavoriteLabels(it) } },
                    onSetTextColorMode = { scope.launch { app.prefs.setTextColorMode(it) } },
                    onSetTextColorCustom = { scope.launch { app.prefs.setTextColorCustom(it) } },
                    onSetDimColor = { scope.launch { app.prefs.setDimColor(it) } },
                    onSetAllowRotation = { scope.launch { app.prefs.setAllowRotation(it) } },
                    onSetShortcutSwipe = { scope.launch { app.prefs.setShortcutSwipe(it) } },
                            onExportSettings = { exportLauncher.launch("deluge-launcher-settings.json") },
                    onImportSettings = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onSetIconShape = { scope.launch { app.prefs.setIconShape(it); clearIconCache() } },
                    onSetThemedIcons = { scope.launch { app.prefs.setThemedIcons(it); clearIconCache() } },
                    // Copied in rather than referenced: a document URI is only as durable as the
                    // file behind it, and a font picked from Downloads would break the moment it
                    // was moved or cleaned up.
                    //
                    // A new name every time: the font on screen is mapped from its file, so
                    // writing the next one over it garbled the text until a restart (#110).
                    onPickFontFile = { uri ->
                        scope.launch {
                            val path = withContext(Dispatchers.IO) {
                                runCatching {
                                    val out = java.io.File(context.filesDir, "custom_font_${System.currentTimeMillis()}")
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        out.outputStream().use { input.copyTo(it) }
                                    } ?: return@runCatching null
                                    // Proves it parses before anything starts drawing with it.
                                    runCatching { android.graphics.Typeface.createFromFile(out) }
                                        .onFailure { out.delete() }.getOrThrow()
                                    context.filesDir.listFiles()
                                        ?.filter { it.name.startsWith("custom_font") && it != out }
                                        ?.forEach { it.delete() }
                                    out.absolutePath
                                }.getOrNull()
                            }
                            if (path == null) {
                                Toast.makeText(context, R.string.font_custom_failed, Toast.LENGTH_SHORT).show()
                            } else {
                                app.prefs.setFontFile(path)
                                app.prefs.setFont(AppFont.CUSTOM)
                            }
                        }
                    },
                    onSetDoubleTapToLock = { scope.launch { app.prefs.setDoubleTapToLock(it) } },
                    onSetEdgeSide = { scope.launch { app.prefs.setEdgeSide(it) } },
                    onSetEdgeZoneWidth = { scope.launch { app.prefs.setEdgeZoneWidthDp(it) } },
                    onSetAzStripVisibility = { scope.launch { app.prefs.setAzStripVisibility(it) } },
                    onSetShowAlphabet = { scope.launch { app.prefs.setShowAlphabet(it) } },
                    onSetSortByUsage = { scope.launch { app.prefs.setSortByUsage(it) } },
                    onSetTwoColumn = { slot, v -> scope.launch { app.prefs.setTwoColumn(slot, v) } },
                    onSetAutoFrequentTag = {
                        scope.launch {
                            // Turning it off takes its tags back with it: they were its doing,
                            // and leaving them would freeze a ranking that no longer updates.
                            // Every language's word, in case the language moved since — plus
                            // every word ever recorded, for languages added after the set.
                            if (!it) {
                                val reserved = RESERVED_TAGS + autoFrequentWords
                                appsByKey.values.forEach { a ->
                                    val current = appTags[a.key] ?: emptySet()
                                    val stripped = current - reserved
                                    if (stripped != current) app.prefs.setAppTags(a.key, stripped)
                                }
                            }
                            app.prefs.setAutoFrequentTag(it)
                        }
                    },
                    onSetAppListSearch = { scope.launch { app.prefs.setAppListSearchEnabled(it) } },
                    onSetAppListSearchBottom = { scope.launch { app.prefs.setAppListSearchBottom(it) } },
                    onSetAppListSearchHidden = { scope.launch { app.prefs.setAppListSearchHidden(it) } },
                    onSetSwipeUpOpensList = { scope.launch { app.prefs.setSwipeUpOpensList(it) } },
                    onOpenSlotPicker = { slot, kind ->
                        navController.navigate("slotpicker/" + slot.name + "/" + kind.name)
                    },
                    onSetQuickLaunchLeftAction = { scope.launch { app.prefs.setQuickLaunchAction(QuickLaunchSlot.LEFT, it) } },
                    onSetQuickLaunchRightAction = { scope.launch { app.prefs.setQuickLaunchAction(QuickLaunchSlot.RIGHT, it) } },
                    onSetLastLetterToLine = { scope.launch { app.prefs.setLastLetterToLine(it) } },
                    onSetAutoKeyboard = { scope.launch { app.prefs.setAutoKeyboard(it) } },
                    onSetAlignment = { scope.launch { app.prefs.setAlignment(it) } },
                    onSetAppListAlignment = { scope.launch { app.prefs.setAppListAlignment(it) } },
                    onSetIconSide = { scope.launch { app.prefs.setIconSide(it) } },
                    onSetNowPlayingEnabled = { scope.launch { app.prefs.setNowPlayingEnabled(it) } },
                    onOpenAccessibilitySettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onOpenAppInfo = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onOpenHiddenApps = { navController.navigate("settings/hidden") },
                    onOpenHomeSettings = {
                        settingsTick++
                        // The system screen, never a silent switch: which launcher runs is the
                        // user's to say and Android asks them properly.
                        val opened = runCatching {
                            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                            true
                        }.getOrDefault(false)
                        if (!opened) {
                            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                        }
                    },
                    onOpenFrequentExcluded = { navController.navigate("settings/frequentexcluded") },
                    onOpenFavorites = { navController.navigate("favorites") },
                    onOpenLanguage = { navController.navigate("settings/language") },
                    onOpenChangelog = { navController.navigate("settings/changelog") },
                    onCheckForUpdates = { checkForUpdates() },
                    onOpenReleasePage = { openReleasePage() },
                    onOpenNotificationSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onBack = { navController.popBackStack() },
            )

            val actionsA = buildActionsA()
            val actionsB = buildActionsB()
            SettingsScreen(SettingsScreenModel(
                hiddenCount = hiddenShownCount,
                iconPacks = iconPacks,
                iconPackPackage = iconPackPackage,
                showAppIcons = showAppIcons,
                showFavoriteIcons = showFavoriteIcons,
                onSetShowFavoriteIcons = actionsA.onSetShowFavoriteIcons,
                showListHeaders = showListHeaders,
                showSettingsRow = showSettingsRow,
                edgeZoneBandOnly = edgeZoneBandOnly,
                holdScroll = holdScroll,
                dimOthersScrubbing = dimOthersScrubbing,
                markWorkApps = markWorkApps,
                textShadow = textShadow,
                textShadowStrength = textShadowStrength,
                onSetTextShadowStrength = actionsA.onSetTextShadowStrength,
                compactTapArea = compactTapArea,
                stayAfterPrivateUnlock = stayAfterPrivateUnlock,
                onSetStayAfterPrivateUnlock = actionsA.onSetStayAfterPrivateUnlock,
                onSetCompactTapArea = actionsA.onSetCompactTapArea,
                privateSpaceSection = privateSpaceSection,
                onSetPrivateSpaceSection = actionsA.onSetPrivateSpaceSection,
                recentSection = recentSection,
                onSetRecentSection = actionsA.onSetRecentSection,
                recentSectionCount = recentSectionCount,
                onSetRecentSectionCount = actionsA.onSetRecentSectionCount,
                onSetTextShadow = actionsA.onSetTextShadow,
                edgeZoneInsetDp = edgeZoneInsetDp,
                edgeZoneInsetAuto = edgeZoneInsetAuto,
                onSetEdgeZoneInsetAuto = actionsA.onSetEdgeZoneInsetAuto,
                onSetEdgeZoneInsetDp = actionsA.onSetEdgeZoneInsetDp,
                onSetMarkWorkApps = actionsA.onSetMarkWorkApps,
                onSetDimOthersScrubbing = actionsA.onSetDimOthersScrubbing,
                webSearchFallback = webSearchFallback,
                onSetWebSearchFallback = actionsA.onSetWebSearchFallback,
                swipeUpOpensSearch = swipeUpOpensSearch,
                onSetSwipeUpOpensSearch = actionsA.onSetSwipeUpOpensSearch,
                onSetHoldScroll = actionsA.onSetHoldScroll,
                holdScrollSpeed = holdScrollSpeed,
                onSetHoldScrollSpeed = actionsA.onSetHoldScrollSpeed,
                onSetEdgeZoneBandOnly = actionsA.onSetEdgeZoneBandOnly,
                onSetShowSettingsRow = actionsA.onSetShowSettingsRow,
                onSetShowListHeaders = actionsA.onSetShowListHeaders,
                notificationBadges = notificationBadges,
                onSetNotificationBadges = actionsA.onSetNotificationBadges,
                dimPausedApps = dimPausedApps,
                onSetDimPausedApps = actionsA.onSetDimPausedApps,
                autoDark = autoDark,
                onSetAutoDark = actionsA.onSetAutoDark,
                cornerTapAction = cornerTapAction,
                onSetCornerTapAction = actionsA.onSetCornerTapAction,
                cornerSwipeAction = cornerSwipeAction,
                onSetCornerSwipeAction = actionsA.onSetCornerSwipeAction,
                nightStartHour = nightStartHour,
                onSetNightStartHour = actionsA.onSetNightStartHour,
                nightEndHour = nightEndHour,
                onSetNightEndHour = actionsA.onSetNightEndHour,
                pinchToResize = pinchToResize,
                onSetPinchToResize = actionsA.onSetPinchToResize,
                reduceMotion = reduceMotion,
                onSetReduceMotion = actionsA.onSetReduceMotion,
                sectionTopPercent = sectionTopPercent,
                onSetSectionTopPercent = actionsA.onSetSectionTopPercent,
                closeFolderOnLaunch = closeFolderOnLaunch,
                onSetCloseFolderOnLaunch = actionsA.onSetCloseFolderOnLaunch,
                favoritesSource = favoritesSource,
                onSetFavoritesSource = actionsA.onSetFavoritesSource,
                frequentCount = frequentCount,
                onSetFrequentCount = actionsA.onSetFrequentCount,
                previewApp = previewApp,
                iconSizeDp = iconSizeDp,
                labelSizeSp = labelSizeSp,
                itemSpacingDp = itemSpacingDp,
                font = font,
                colorScheme = colorSchemeChoice,
                hideStatusBar = hideStatusBar,
                hideStatusBarAppList = hideStatusBarAppList,
                statusBarPeekSeconds = statusBarPeekSeconds,
                dimWallpaperAlpha = dimWallpaperAlpha,
                hapticFeedback = hapticFeedback,
                dimHomeAlpha = dimHomeAlpha,
                showFavoriteLabels = showFavoriteLabels,
                screenshotMode = screenshotMode,
                onSetScreenshotMode = actionsA.onSetScreenshotMode,
                textColorMode = textColorMode,
                textColorCustom = textColorCustom,
                dimColor = dimColor,
                allowRotation = allowRotation,
                shortcutSwipe = shortcutSwipe,
                fontFile = fontFile,
                iconShape = iconShape,
                themedIcons = themedIcons,
                doubleTapToLock = doubleTapToLock,
                edgeSide = edgeSide,
                edgeZoneWidthDp = edgeZoneWidthDp,
                azStripVisibility = azStripVisibility,
                showAlphabet = showAlphabet,
                sortByUsage = sortByUsage,
                appListSearch = appListSearchEnabled,
                appListSearchBottom = appListSearchBottom,
                appListSearchHidden = appListSearchHidden,
                swipeUpOpensList = swipeUpOpensList,
                alignment = alignment,
                appListAlignment = appListAlignment,
                iconSide = iconSide,
                nowPlayingEnabled = nowPlayingEnabled,
                nowPlayingListenerEnabled = listenerEnabled,
                onSetIconPack = actionsA.onSetIconPack,
                onSetShowAppIcons = actionsA.onSetShowAppIcons,
                onSetIconSize = actionsA.onSetIconSize,
                onSetLabelSize = actionsA.onSetLabelSize,
                onSetItemSpacing = actionsA.onSetItemSpacing,
                onSetFont = actionsA.onSetFont,
                onSetColorScheme = actionsA.onSetColorScheme,
                onSetHideStatusBar = actionsA.onSetHideStatusBar,
                onSetHideStatusBarAppList = actionsA.onSetHideStatusBarAppList,
                onSetStatusBarPeekSeconds = actionsA.onSetStatusBarPeekSeconds,
                onSetDimWallpaper = actionsA.onSetDimWallpaper,
                wallpaperBlurDp = wallpaperBlurDp,
                onSetWallpaperBlur = actionsA.onSetWallpaperBlur,
                onSetHapticFeedback = actionsB.onSetHapticFeedback,
                onSetDimHome = actionsB.onSetDimHome,
                onSetShowFavoriteLabels = actionsB.onSetShowFavoriteLabels,
                onSetTextColorMode = actionsB.onSetTextColorMode,
                onSetTextColorCustom = actionsB.onSetTextColorCustom,
                onSetDimColor = actionsB.onSetDimColor,
                onSetAllowRotation = actionsB.onSetAllowRotation,
                onSetShortcutSwipe = actionsB.onSetShortcutSwipe,
                onExportSettings = actionsB.onExportSettings,
                onImportSettings = actionsB.onImportSettings,
                onSetIconShape = actionsB.onSetIconShape,
                onSetThemedIcons = actionsB.onSetThemedIcons,
                onPickFontFile = actionsB.onPickFontFile,
                onSetDoubleTapToLock = actionsB.onSetDoubleTapToLock,
                onSetEdgeSide = actionsB.onSetEdgeSide,
                onSetEdgeZoneWidth = actionsB.onSetEdgeZoneWidth,
                onSetAzStripVisibility = actionsB.onSetAzStripVisibility,
                onSetShowAlphabet = actionsB.onSetShowAlphabet,
                onSetSortByUsage = actionsB.onSetSortByUsage,
                autoFrequentTag = autoFrequentTag,
                homeTwoColumnPortrait = homeTwoColumnPortrait,
                homeTwoColumnLandscape = homeTwoColumnLandscape,
                listTwoColumnPortrait = listTwoColumnPortrait,
                listTwoColumnLandscape = listTwoColumnLandscape,
                onSetTwoColumn = actionsB.onSetTwoColumn,
                onSetAutoFrequentTag = actionsB.onSetAutoFrequentTag,
                onSetAppListSearch = actionsB.onSetAppListSearch,
                onSetAppListSearchBottom = actionsB.onSetAppListSearchBottom,
                onSetAppListSearchHidden = actionsB.onSetAppListSearchHidden,
                onSetSwipeUpOpensList = actionsB.onSetSwipeUpOpensList,
                quickLaunchLeftLabel = quickLaunchLeftKey?.let { key ->
                    appsByKey[key]?.let { nameOverrides[it.key] ?: it.label }
                },
                quickLaunchRightLabel = quickLaunchRightKey?.let { key ->
                    appsByKey[key]?.let { nameOverrides[it.key] ?: it.label }
                },
                onOpenSlotPicker = actionsB.onOpenSlotPicker,
                quickLaunchLeftAction = quickLaunchLeftAction,
                onSetQuickLaunchLeftAction = actionsB.onSetQuickLaunchLeftAction,
                quickLaunchRightAction = quickLaunchRightAction,
                onSetQuickLaunchRightAction = actionsB.onSetQuickLaunchRightAction,
                cornerButtonLabel = when (cornerButtonKey) {
                    null -> null
                    EntryKeys.SEARCH -> stringResource(R.string.settings_corner_search)
                    else -> appsByKey[cornerButtonKey]?.let { nameOverrides[it.key] ?: it.label }
                },
                cornerSwipeLabel = when (cornerSwipeKey) {
                    null -> null
                    EntryKeys.SEARCH -> stringResource(R.string.settings_corner_search)
                    else -> appsByKey[cornerSwipeKey]?.let { nameOverrides[it.key] ?: it.label }
                },
                autoKeyboard = autoKeyboard,
                lastLetterToLine = lastLetterToLine,
                onSetLastLetterToLine = actionsB.onSetLastLetterToLine,
                onSetAutoKeyboard = actionsB.onSetAutoKeyboard,
                onSetAlignment = actionsB.onSetAlignment,
                onSetAppListAlignment = actionsB.onSetAppListAlignment,
                onSetIconSide = actionsB.onSetIconSide,
                onSetNowPlayingEnabled = actionsB.onSetNowPlayingEnabled,
                shadeGestureReady = remember(homeIntentTick, settingsResumeTick) { SystemUi.canExpandShade() },
                lockGestureReady = remember(homeIntentTick, settingsResumeTick) { SystemUi.canLockScreen() },
                onOpenAccessibilitySettings = actionsB.onOpenAccessibilitySettings,
                onOpenAppInfo = actionsB.onOpenAppInfo,
                onOpenHiddenApps = actionsB.onOpenHiddenApps,
                isDefaultLauncher = isDefaultLauncher,
                onOpenHomeSettings = actionsB.onOpenHomeSettings,
                frequentExcludedCount = frequentExcluded.size,
                frequentExcludedVisible = favoritesSource == FavoritesSource.FREQUENT,
                onOpenFrequentExcluded = actionsB.onOpenFrequentExcluded,
                onOpenFavorites = actionsB.onOpenFavorites,
                onOpenLanguage = actionsB.onOpenLanguage,
                onOpenChangelog = actionsB.onOpenChangelog,
                updateChecking = updateChecking,
                updateResult = updateResult,
                updateKnownVersion = updateKnownVersion,
                updateAutoCheck = updateAutoCheck,
                onSetUpdateAutoCheck = { enabled ->
                    scope.launch { app.prefs.setUpdateAutoCheck(enabled) }
                    if (enabled) checkForUpdates()
                },
                onCheckForUpdates = actionsB.onCheckForUpdates,
                onOpenReleasePage = actionsB.onOpenReleasePage,
                onOpenNotificationSettings = actionsB.onOpenNotificationSettings,
                onBack = actionsB.onBack,
            ))
        }

        composable("favorites") {
            ManageFavoritesScreen(
                allApps = allApps,
                favoriteKeys = favoriteKeys,
                folders = folders,
                privateSpace = privateSpace,
                nameOverrides = nameOverrides,
                iconSizeDp = iconSizeDp,
                hapticFeedback = hapticFeedback,
                onReorder = { keys -> scope.launch { app.prefs.setFavorites(keys) } },
                onSetFavorite = { appInfo, add ->
                    scope.launch {
                        if (add) app.prefs.addFavorite(appInfo.key) else app.prefs.removeFavorite(appInfo.key)
                    }
                },
                // Missing here can just mean a locked private space or a paused work profile,
                // both of which come back — so this only drops the favorite, the same as
                // unticking a row that IS resolved. forgetEntry's wider wipe (rename, icon,
                // folder, hidden, launch count) is for a shortcut that is actually gone for
                // good, which AppRepository.unpin already calls on its own.
                onForget = { key -> scope.launch { app.prefs.removeFavorite(key) } },
                onBack = { navController.popBackStack() },
            )
        }

        composableArgs("folder/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            FolderAppsScreen(
                folder = foldersById[id],
                allApps = allApps,
                privateSpace = privateSpace,
                nameOverrides = nameOverrides,
                iconSizeDp = iconSizeDp,
                hapticFeedback = hapticFeedback,
                onSetInFolder = { appInfo, inFolder ->
                    scope.launch {
                        if (inFolder) {
                            app.prefs.addAppToFolder(id, appInfo.key)
                        } else {
                            app.prefs.removeAppFromFolder(id, appInfo.key)
                        }
                    }
                },
                onReorder = { keys -> scope.launch { app.prefs.setFolderApps(id, keys) } },
                onForget = { key -> scope.launch { app.prefs.removeAppFromFolder(id, key) } },
                onBack = { navController.popBackStack() },
            )
        }

        composableArgs("slotpicker/{slot}/{kind}") { entry ->
            val slot = runCatching {
                SlotTarget.valueOf(entry.arguments?.getString("slot").orEmpty())
            }.getOrDefault(SlotTarget.QUICK_LEFT)
            val kind = runCatching {
                SlotKind.valueOf(entry.arguments?.getString("kind").orEmpty())
            }.getOrDefault(SlotKind.APP)
            // Apps open apps, shortcuts open shortcuts: each picker lists only its own
            // kind, so a binding can never point at the wrong sort of thing.
            val wantShortcuts = kind == SlotKind.SHORTCUT
            val listed = remember(allApps, wantShortcuts) {
                if (wantShortcuts) allApps.filter { it.kind == EntryKind.SHORTCUT }
                else allApps.filter { it.kind == EntryKind.APP }
            }
            val currentKey: String? = when (slot) {
                SlotTarget.CORNER_TAP -> cornerButtonKey
                SlotTarget.CORNER_SWIPE -> cornerSwipeKey
                SlotTarget.QUICK_LEFT -> quickLaunchLeftKey
                SlotTarget.QUICK_RIGHT -> quickLaunchRightKey
            }
            // The action this pick binds: opening an app or opening a shortcut.
            val boundAction =
                if (wantShortcuts) CornerAction.OPEN_SHORTCUT else CornerAction.OPEN_APP
            AppPickerScreen(
                title = stringResource(
                    when (slot) {
                        SlotTarget.CORNER_TAP -> R.string.settings_corner_tap
                        SlotTarget.CORNER_SWIPE -> R.string.settings_corner_swipe
                        SlotTarget.QUICK_LEFT -> R.string.settings_quick_launch_left
                        SlotTarget.QUICK_RIGHT -> R.string.settings_quick_launch_right
                    }
                ),
                allApps = listed,
                selectedKey = currentKey,
                nameOverrides = nameOverrides,
                iconSizeDp = iconSizeDp,
                emptyText = if (wantShortcuts) stringResource(R.string.slot_no_shortcuts) else null,
                onPick = { picked ->
                    scope.launch {
                        // Saved together or not at all: backing out (onBack) stores
                        // nothing, so a slot never ends up opening something with nothing
                        // bound. Picking "none" clears the target and parks the slot on
                        // doing nothing, which is the only honest reading of it.
                        val action =
                            if (picked == null) CornerAction.DO_NOTHING else boundAction
                        when (slot) {
                            SlotTarget.CORNER_TAP -> {
                                app.prefs.setCornerButton(picked?.key)
                                app.prefs.setCornerTapAction(action)
                            }
                            SlotTarget.CORNER_SWIPE -> {
                                app.prefs.setCornerSwipeKey(picked?.key)
                                app.prefs.setCornerSwipeAction(action)
                            }
                            SlotTarget.QUICK_LEFT -> {
                                app.prefs.setQuickLaunch(QuickLaunchSlot.LEFT, picked?.key)
                                app.prefs.setQuickLaunchAction(QuickLaunchSlot.LEFT, action)
                            }
                            SlotTarget.QUICK_RIGHT -> {
                                app.prefs.setQuickLaunch(QuickLaunchSlot.RIGHT, picked?.key)
                                app.prefs.setQuickLaunchAction(QuickLaunchSlot.RIGHT, action)
                            }
                        }
                    }
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/language") {
            LanguageScreen(onBack = { navController.popBackStack() })
        }

        composable("settings/changelog") {
            ChangelogScreen(onBack = { navController.popBackStack() })
        }

        composable("settings/hidden") {            HiddenAppsScreen(
                allApps = allApps,
                hiddenApps = hiddenApps,
                nameOverrides = nameOverrides,
                iconSizeDp = iconSizeDp,
                onToggleHidden = { appInfo, hidden ->
                    scope.launch { app.prefs.setHidden(appInfo.key, hidden) }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/frequentexcluded") {
            HiddenAppsScreen(
                allApps = allApps,
                hiddenApps = frequentExcluded,
                nameOverrides = nameOverrides,
                iconSizeDp = iconSizeDp,
                onToggleHidden = { appInfo, excluded ->
                    scope.launch { app.prefs.setFrequentExcluded(appInfo.key, excluded) }
                },
                onBack = { navController.popBackStack() },
                titleRes = R.string.settings_frequent_excluded,
                countRes = R.string.frequent_excluded_count,
                addRes = R.string.frequent_excluded_add,
            )
        }

        composableArgs("iconpicker/{key}") { entry ->
            // Decoded once already by the router: decoding again turns a literal %25
            // in a shortcut id into % and then into whatever follows it.
            val key = entry.arguments?.getString("key").orEmpty()
            val folderId = folderIdFromToken(key)
            val targetApp = allApps.find { it.key == key }
            val label = when {
                folderId != null -> foldersById[folderId]?.name.orEmpty()
                else -> nameOverrides[key] ?: targetApp?.label.orEmpty()
            }

            fun applyIcon(value: String?) {
                scope.launch {
                    if (folderId != null) {
                        app.prefs.setFolderIcon(folderId, value)
                    } else {
                        app.prefs.setIconOverride(key, value)
                    }
                }
            }

            IconPickerScreen(
                appLabel = label,
                onPickPackIcon = { packPkg, drawableName ->
                    applyIcon(encodePackOverride(packPkg, drawableName))
                    navController.popBackStack()
                },
                onPickFromGallery = {
                    pendingIconTarget = key
                    pickImageLauncher.launch(arrayOf("image/*"))
                },
                onResetIcon = {
                    applyIcon(null)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }

    // Only once per release: a launcher that greeted every HOME press with the same news would
    // be a reason to turn the check off. "Later" is remembered as firmly as opening the page —
    // the row in About still says an update is there, so nothing is lost but the interruption.
    val updateToOffer = updateKnownVersion
        .takeIf { updateAutoCheck && it > BuildConfig.VERSION_CODE && it > updatePromptedVersion }
    if (updateToOffer != null) {
        val offeredVersion = "$updateToOffer"
        fun dismissOffer() {
            scope.launch { app.prefs.markUpdatePrompted(updateToOffer) }
        }
        AlertDialog(
            onDismissRequest = { dismissOffer() },
            title = { Text(stringResource(R.string.update_dialog_title)) },
            text = { Text(stringResource(R.string.update_dialog_message, offeredVersion)) },
            confirmButton = {
                TextButton(onClick = {
                    openReleasePage()
                    dismissOffer()
                }) { Text(stringResource(R.string.update_dialog_open)) }
            },
            dismissButton = {
                TextButton(onClick = { dismissOffer() }) {
                    Text(stringResource(R.string.update_dialog_later))
                }
            },
        )
    }
}

/**
 * The app list and the private-space state it was enumerated against, held as one value.
 *
 * Two pieces of state would let a composition see a list from one read beside a state from
 * another, and every consumer needs them to agree: the settings screens decide what to conceal
 * from the state and then look the rest up in the list, and the home screen draws the padlock
 * from one and the rows from the other. Published together, there is no such moment.
 */
private data class AppsAndSpace(val privateSpace: PrivateSpace, val apps: List<AppInfo>)

/**
 * Whether this app is the device's home app right now.
 *
 * Asked of the role on Android 10+, which is the answer the system itself acts on; the
 * resolved-activity fallback below that is what the settings row used before. Both wrapped:
 * a launcher that cannot tell must behave as it always did rather than fail.
 */
private fun isDefaultHome(context: Context): Boolean = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
    } else {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    }
}.getOrDefault(false)