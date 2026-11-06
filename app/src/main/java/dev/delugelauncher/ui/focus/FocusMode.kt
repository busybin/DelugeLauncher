// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.focus

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.EntryKind
import dev.delugelauncher.data.FocusSession
import dev.delugelauncher.data.FreezeCommands
import dev.delugelauncher.ui.common.DelugeIcons
import dev.delugelauncher.ui.common.rememberAppLockRequest

/**
 * Starts a focus session for one app. Supplied by the launcher, null in previews and tests.
 */
val LocalFocusStarter = compositionLocalOf<((AppInfo) -> Unit)?> { null }

/**
 * The long-press action that narrows the launcher to one app.
 *
 * Only a main-profile app can be a target: a shortcut points at an app whose own row is the
 * thing that has to stay reachable, and another profile's app cannot be started from this
 * one's home screen without a profile switch.
 */
@Composable
fun FocusMenuItem(app: AppInfo, onDismiss: () -> Unit) {
    val start = LocalFocusStarter.current ?: return
    if (app.kind != EntryKind.APP || app.userSerial != 0L) return
    val context = LocalContext.current
    // An exit that cannot be authenticated is not an exit. Without a secure lock the item
    // stays visible but grey, so the feature is discoverable and the reason is on the row.
    val secure = remember(context) {
        runCatching { context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true }
            .getOrDefault(false)
    }
    DropdownMenuItem(
        text = {
            Column {
                Text(stringResource(R.string.focus_start))
                if (!secure) {
                    Text(
                        stringResource(R.string.focus_needs_lock),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        leadingIcon = { Icon(DelugeIcons.Visibility, contentDescription = null) },
        enabled = secure,
        onClick = { onDismiss(); start(app) },
    )
}

/**
 * The home screen while a focus session is running.
 *
 * It covers the launcher rather than changing any package state: the other apps are not
 * frozen, they are simply not offered here. The only two ways out are reopening the target
 * or confirming the device lock, which keeps a friend from wandering into the rest of the
 * phone without pretending this cannot be escaped.
 */
@Composable
fun FocusOverlay(session: FocusSession, onExit: () -> Unit) {
    val context = LocalContext.current
    val label = session.label.ifBlank { session.packageName }
    val requestUnlock = rememberAppLockRequest(
        R.string.focus_unlock_title,
        R.string.focus_unlock_prompt,
    )
    // If the target or the device lock has gone away there is no honest session left; leave
    // it immediately instead of trapping the phone on a screen with no working exit.
    LaunchedEffect(session.packageName) {
        while (true) {
            if (!isDeviceSecure(context) || !canOpen(context, session)) {
                onExit()
                break
            }
            delay(2_000)
        }
    }
    BackHandler(enabled = true) { /* home is the focus screen; back must not dismiss it */ }
    Surface(
        modifier = Modifier.fillMaxSize(),
        // `background` is transparent in this theme so the wallpaper shows through; a focus
        // screen must be opaque or the home screen behind it leaks through every gap.
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                DelugeIcons.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.focus_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                stringResource(R.string.focus_body, label),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Button(
                onClick = { if (!openTarget(context, session)) onExit() },
                modifier = Modifier.padding(top = 28.dp),
            ) {
                Text(stringResource(R.string.focus_open, label))
            }
            TextButton(
                onClick = { requestUnlock(label) { onExit() } },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.focus_unlock))
            }
        }
    }
}

private fun openTarget(context: Context, session: FocusSession): Boolean {
    if (!FreezeCommands.isPackageName(session.packageName)) {
        Toast.makeText(context, R.string.focus_gone, Toast.LENGTH_LONG).show()
        return false
    }
    val intent = context.packageManager.getLaunchIntentForPackage(session.packageName)
    if (intent == null) {
        Toast.makeText(context, R.string.focus_gone, Toast.LENGTH_LONG).show()
        return false
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}

private fun isDeviceSecure(context: Context): Boolean = runCatching {
    context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
}.getOrDefault(false)

/** The target still exists, is enabled, and has something to open. */
private fun canOpen(context: Context, session: FocusSession): Boolean {
    if (!FreezeCommands.isPackageName(session.packageName)) return false
    val info = runCatching {
        context.packageManager.getApplicationInfo(session.packageName, 0)
    }.getOrNull() ?: return false
    return info.enabled && context.packageManager.getLaunchIntentForPackage(session.packageName) != null
}
