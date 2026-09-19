// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
fun AppShortcutItems(app: AppInfo, expanded: Boolean, onStarted: () -> Unit) {
    val context = LocalContext.current
    val victoriaApp = context.applicationContext as VictoriaApp
    val density = LocalConfiguration.current.densityDpi
    var shortcuts by remember(app.key) { mutableStateOf<List<ShortcutInfo>>(emptyList()) }

    LaunchedEffect(app.key, expanded) {
        if (!expanded) return@LaunchedEffect
        shortcuts = withContext(Dispatchers.IO) {
            victoriaApp.appRepository.appShortcuts(app).take(MAX_SHORTCUTS)
        }
    }

    if (shortcuts.isEmpty()) return

    shortcuts.forEach { shortcut ->
        val label = (shortcut.shortLabel ?: shortcut.longLabel)?.toString().orEmpty()
        if (label.isBlank()) return@forEach
        val icon = remember(shortcut.id, density) {
            runCatching {
                victoriaApp.appRepository.shortcutIcon(shortcut, density)
                    ?.toBitmap(ICON_PX, ICON_PX)
                    ?.asImageBitmap()
            }.getOrNull()
        }
        DropdownMenuItem(
            text = { Text(label) },
            leadingIcon = icon?.let {
                { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(24.dp)) }
            },
            onClick = {
                victoriaApp.appRepository.startAppShortcut(shortcut)
                onStarted()
            },
        )
    }
    HorizontalDivider()
}

/** Rasterised at menu-icon size; the drawable itself is whatever density the publisher had. */
private const val ICON_PX = 72
