// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import android.app.Activity
import android.app.KeyguardManager
import android.content.pm.ShortcutInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.delugelauncher.DelugeApp
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.ShortcutSwipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Most an app is allowed to contribute, so a menu stays a menu rather than a list. */
private const val MAX_SHORTCUTS = 5

/**
 * The shortcuts an app publishes, as items at the top of its long-press menu.
 *
 * Read when the menu opens rather than with the app list: it is a call into the system per
 * app, and an app list is hundreds of apps of which one is ever asked about.
 *
 * Emits nothing at all when an app publishes none, so the menu is unchanged for most of them.
 */
@Composable
fun AppShortcutItems(
    app: AppInfo,
    expanded: Boolean,
    labelSizeSp: Int,
    onStarted: () -> Unit,
) {
    val context = LocalContext.current
    val delugeApp = context.applicationContext as DelugeApp
    val density = LocalConfiguration.current.densityDpi
    var shortcuts by remember(app.key) { mutableStateOf<List<ShortcutInfo>>(emptyList()) }

    // A shortcut points into its publisher's app, so a locked app asks for the device lock here
    // too. Null until the store answers: a tap while loading asks rather than waves through.
    val lockedApps by delugeApp.prefs.lockedApps.collectAsState(initial = null)
    val keyguard = remember(context) { context.getSystemService(KeyguardManager::class.java) }
    var pending by remember { mutableStateOf<Pair<AppInfo, ShortcutInfo>?>(null) }
    val confirmLock = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val waiting = pending
        pending = null
        if (waiting != null && result.resultCode == Activity.RESULT_OK) {
            delugeApp.appRepository.startAppShortcut(waiting.first, waiting.second, LaunchOrigin.of(waiting.first))
            onStarted()
        }
    }

    LaunchedEffect(app.key, expanded) {
        if (!expanded) return@LaunchedEffect
        shortcuts = withContext(Dispatchers.IO) {
            delugeApp.appRepository.appShortcuts(app).take(MAX_SHORTCUTS)
        }
    }

    if (shortcuts.isEmpty()) return

    shortcuts.forEach { shortcut ->
        val label = (shortcut.shortLabel ?: shortcut.longLabel)?.toString().orEmpty()
        if (label.isBlank()) return@forEach
        val iconDp = iconSizeFor(labelSizeSp)
        val icon = remember(shortcut.id, density) {
            runCatching {
                delugeApp.appRepository.shortcutIcon(shortcut, density)
                    ?.toBitmap(ICON_PX, ICON_PX)
                    ?.asImageBitmap()
            }.getOrNull()
        }
        DropdownMenuItem(
            text = { Text(label, fontSize = labelSizeSp.sp) },
            leadingIcon = icon?.let {
                { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(iconDp)) }
            },
            onClick = {
                val locked = lockedApps
                val prompt = if (locked == null || app.lockedBy(locked)) {
                    // No secure lock on the phone leaves nothing to ask for, and the shortcut
                    // opens as usual rather than becoming unreachable.
                    runCatching {
                        keyguard?.createConfirmDeviceCredentialIntent(
                            context.getString(R.string.app_lock_title),
                            context.getString(R.string.app_lock_prompt, app.label),
                        )
                    }.getOrNull()
                } else {
                    null
                }
                // One prompt at a time: a second answer would open the wrong shortcut.
                if (prompt == null) {
                    delugeApp.appRepository.startAppShortcut(app, shortcut, LaunchOrigin.of(app))
                    onStarted()
                } else if (pending == null) {
                    pending = app to shortcut
                    confirmLock.launch(prompt)
                }
            },
        )
    }
}

/** Rasterised at menu-icon size; the drawable itself is whatever density the publisher had. */
private const val ICON_PX = 72

/**
 * The icon beside a shortcut, sized from the label it sits next to.
 *
 * Tied to the launcher's own label size rather than given a setting of its own: this is the
 * same list of app names as everywhere else, and a panel that ignored the size set for those
 * was the thing worth fixing. Bounded so it stays an icon in a menu at either extreme.
 */
private fun iconSizeFor(labelSizeSp: Int): Dp =
    (labelSizeSp * 1.5f).dp.coerceIn(20.dp, 44.dp)

/**
 * Swipe a row sideways to reach what the app publishes.
 *
 * A separate gesture from the long press on purpose: shortcuts are used daily and the menu
 * behind a long press is set-up-and-done, so they do not belong in the same place.
 *
 * One direction, chosen in settings, so the other stays free to mean something later. It fires
 * once per drag rather than per pixel, and only past a distance no tap wanders.
 *
 * Nothing happens on a row whose app publishes none. There is no way to know that before
 * asking, and asking every row up front is a call into the system per app.
 */
fun Modifier.swipeForShortcuts(
    mode: ShortcutSwipe,
    enabled: Boolean,
    onSwipe: () -> Unit,
): Modifier =
    if (mode == ShortcutSwipe.OFF || !enabled) this else this.pointerInput(mode) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop
            var accX = 0f
            var accY = 0f
            var claimed = false
            var fired = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                // The list is already scrolling it: bow out without touching
                // anything, so the rest of the gesture stays smooth.
                if (change.isConsumed) break
                val dx = change.position.x - change.previousPosition.x
                val dy = change.position.y - change.previousPosition.y
                if (!claimed) {
                    accX += dx
                    accY += dy
                    // Mostly up-and-down: a scroll that started crooked, not a
                    // sideways swipe. Stop watching and consume nothing, so the
                    // list keeps the gesture it already has. This is what the
                    // stock horizontal-drag detector got wrong: it spent the
                    // touch slop the moment sideways won it, and a diagonal
                    // swipe that lost the race could never scroll again until
                    // the finger lifted.
                    if (abs(accY) > slop && abs(accY) > abs(accX)) break
                    // Clearly sideways, past slop, and heading the way this
                    // row answers to: only now is the gesture ours. A swipe
                    // the wrong way never claims it, so it can still become
                    // a scroll.
                    val heading = if (mode == ShortcutSwipe.RIGHT) accX > 0f else accX < 0f
                    if (heading && abs(accX) > slop && abs(accX) > SWIPE_DOMINANCE * abs(accY)) {
                        claimed = true
                    }
                } else {
                    accX += dx
                    change.consume()
                    // In slops, not pixels: touchSlop already scales with density, so the
                    // same flick fires on a low- and a high-density screen alike.
                    val far = when (mode) {
                        ShortcutSwipe.RIGHT -> accX > slop * SWIPE_THRESHOLD_SLOPS
                        ShortcutSwipe.LEFT -> accX < -slop * SWIPE_THRESHOLD_SLOPS
                        ShortcutSwipe.OFF -> false
                    }
                    if (!fired && far) {
                        fired = true
                        onSwipe()
                    }
                }
            }
        }
    }

/**
 * How many times further sideways than up-and-down a drag must be before the
 * row calls it a swipe. Past slop alone is not enough: a scroll that starts
 * at 45 degrees is past sideways slop too, and handing it to the row is what
 * used to wedge the list until the finger lifted.
 */
private const val SWIPE_DOMINANCE = 2f

/** Far enough not to fire on a tap that wandered, short enough to feel like a flick. */
private const val SWIPE_THRESHOLD_SLOPS = 4f

/**
 * The shortcuts on their own, for the swipe. Renders nothing when the app publishes none, so
 * a swipe on such a row opens an empty popup rather than a stray one.
 *
 * Anchored to the row and not to the finger, unlike the long-press menu. That one is opened
 * from somewhere on a row that may be as tall as a widget, so it has to find the finger; this
 * one is always a swipe across a row one line high, and following the finger only meant it
 * landed somewhere slightly different every time. Against the row it is in the same place at
 * the same size on every use, which is what makes it something you reach for rather than read.
 */
@Composable
fun AppShortcutMenu(app: AppInfo, expanded: Boolean, labelSizeSp: Int, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        AppShortcutItems(app, expanded, labelSizeSp) { onDismiss() }
    }
}
