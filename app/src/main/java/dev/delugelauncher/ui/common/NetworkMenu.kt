// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.delugelauncher.DelugeApp
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.EntryKind
import dev.delugelauncher.data.FreezeAccess
import dev.delugelauncher.data.NetworkAppState
import dev.delugelauncher.data.NetworkMode
import kotlinx.coroutines.launch

/**
 * The firewall answers a long-press menu offers for one app.
 *
 * Held as one object because the home screen and the app list both draw the same three
 * entries, and neither should have to know how the policy is stored.
 */
@Immutable
class NetworkActions(
    /** The stored answer for a package, or null when the firewall must not touch it. */
    val stateOf: (String) -> NetworkAppState?,
    val setState: (AppInfo, NetworkAppState) -> Unit,
    /** Opens the firewall page; the background menu offers it whether or not it is on. */
    val open: () -> Unit,
)

/**
 * The row-menu answers, provided once around the home screen.
 *
 * A composition local rather than two more parameters on [HomeRoute] and [HomeScreen]: both
 * are already at the JVM's method size limit, and another argument was enough to break the
 * build. The app list can read the same value when it grows the same menu.
 */
val LocalNetworkActions = staticCompositionLocalOf<NetworkActions?> { null }

/**
 * The single network entry a row menu offers: what the app does now, and a way in.
 *
 * The three answers live one level down in [NetworkSubmenu], so a long-press menu stays about
 * the whole row rather than growing three lines of firewall policy.
 */
@Composable
fun NetworkMenuItems(
    app: AppInfo,
    actions: NetworkActions?,
    onDismissMenu: () -> Unit,
    onOpenSubmenu: () -> Unit,
) {
    if (actions == null) return
    if (app.kind != EntryKind.APP) return
    val current = actions.stateOf(app.packageName) ?: return
    DropdownMenuItem(
        text = { Text(stringResource(R.string.network_title)) },
        leadingIcon = { Icon(DelugeIcons.LinkOff, contentDescription = null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(networkStateLabel(current)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Icon(
                    DelugeIcons.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(14.dp),
                )
            }
        },
        onClick = {
            onDismissMenu()
            onOpenSubmenu()
        },
    )
}

/** The three network answers, the current one marked with a check. */
@Composable
fun NetworkSubmenu(
    app: AppInfo,
    actions: NetworkActions?,
    onDismissMenu: () -> Unit,
) {
    if (actions == null) return
    if (app.kind != EntryKind.APP) return
    val current = actions.stateOf(app.packageName) ?: return
    NetworkStateItem(
        app = app,
        current = current,
        state = NetworkAppState.FOLLOW_FREEZE,
        label = R.string.network_state_follow,
        icon = DelugeIcons.Freeze,
        actions = actions,
        onDismissMenu = onDismissMenu,
    )
    NetworkStateItem(
        app = app,
        current = current,
        state = NetworkAppState.BLOCKED,
        label = R.string.network_state_blocked,
        icon = DelugeIcons.Block,
        actions = actions,
        onDismissMenu = onDismissMenu,
    )
    NetworkStateItem(
        app = app,
        current = current,
        state = NetworkAppState.UNRESTRICTED,
        label = R.string.network_state_unrestricted,
        icon = DelugeIcons.Language,
        actions = actions,
        onDismissMenu = onDismissMenu,
    )
}

@Composable
private fun NetworkStateItem(
    app: AppInfo,
    current: NetworkAppState,
    state: NetworkAppState,
    label: Int,
    icon: ImageVector,
    actions: NetworkActions,
    onDismissMenu: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = {
            if (current == state) {
                Icon(Icons.Filled.Check, contentDescription = null)
            }
        },
        onClick = {
            onDismissMenu()
            if (current != state) actions.setState(app, state)
        },
    )
}

private fun networkStateLabel(state: NetworkAppState): Int = when (state) {
    NetworkAppState.FOLLOW_FREEZE -> R.string.network_state_follow
    NetworkAppState.BLOCKED -> R.string.network_state_blocked
    NetworkAppState.UNRESTRICTED -> R.string.network_state_unrestricted
    NetworkAppState.PROTECTED -> R.string.network_state_protected
}

/**
 * Builds the row-menu answers from what the firewall stores.
 *
 * Null while the firewall is off: a menu that edits a policy nothing enforces is a lie. The
 * home screen's background entry still opens the page, which is where it is turned on.
 *
 * Null too when neither root nor Shizuku is available and nothing is blocked: the page can
 * only report the missing channel, and on a Device Owner phone it would be a dead end. A
 * firewall that is still on stays reachable so it can be turned off.
 *
 * Kept as one composable rather than inline in HomeRoute: that function is already at the
 * edge of the JVM's method size, and four more collectors pushed it over.
 */
@Composable
fun rememberNetworkActions(
    app: DelugeApp,
    open: () -> Unit,
): NetworkActions? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode by app.prefs.networkMode.collectAsState(initial = NetworkMode.OFF)
    val freezeStatus by app.freezeManager.status.collectAsState()
    var supported by remember { mutableStateOf(app.networkManager.supportedCached()) }
    LaunchedEffect(app) { supported = app.networkManager.supported() }
    // No channel, or a system without the switches: nothing this menu offers can run, so it
    // is not offered. Settings still shows the row and says why it cannot be used.
    if (!supported) return null
    if (freezeStatus.checked && freezeStatus.access == FreezeAccess.NONE) return null
    val blocked by app.prefs.networkBlocked.collectAsState(initial = emptySet())
    val unrestricted by app.prefs.networkUnrestricted.collectAsState(initial = emptySet())
    val protected = remember(app) { app.networkManager.protectedPackages() }
    return remember(mode, blocked, unrestricted, protected, open) {
        NetworkActions(
            stateOf = { packageName ->
                when {
                    !mode.enabled -> null
                    packageName in protected -> null
                    packageName in blocked -> NetworkAppState.BLOCKED
                    packageName in unrestricted -> NetworkAppState.UNRESTRICTED
                    else -> NetworkAppState.FOLLOW_FREEZE
                }
            },
            setState = { target, state ->
                scope.launch {
                    val result = app.networkManager.setAppState(target.packageName, state)
                    if (result.noAccess) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.network_needs_access),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
            open = open,
        )
    }
}
