// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.settings

import dev.delugelauncher.ui.common.DelugeIcons

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.Folder
import dev.delugelauncher.data.HapticFeedback
import dev.delugelauncher.data.PrivateSpace
import dev.delugelauncher.data.concealsStored
import dev.delugelauncher.data.folderIdFromToken
import dev.delugelauncher.data.restoreConcealed
import dev.delugelauncher.ui.common.AppIcon
import dev.delugelauncher.R
import androidx.compose.ui.res.stringResource

/**
 * Pick favorites from the full app list with checkboxes, rather than relying on a
 * long-press in the A-Z list that nothing advertises.
 *
 * The ones already chosen are listed first, in the order they sit on the home screen, and can
 * be dragged into a different one — the same shape as a folder's members, and for the same
 * reason: that order is not alphabetical and cannot be edited in a list that is.
 */
@Composable
fun ManageFavoritesScreen(
    allApps: List<AppInfo>,
    favoriteKeys: List<String>,
    hiddenApps: Set<String>,
    folders: List<Folder>,
    /** So a favorite that lives in a locked private space can be left out of this screen. */
    privateSpace: PrivateSpace,
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    hapticFeedback: HapticFeedback,
    onSetFavorite: (AppInfo, Boolean) -> Unit,
    onToggleHidden: (AppInfo, Boolean) -> Unit,
    /**
     * Unticks a key nothing can resolve right now; see the row that offers it. Only ever drops
     * the favorite, not the row's rename/icon/folder/hidden/launch-count — a locked private
     * space or a paused work profile can make a real app or shortcut "missing" temporarily,
     * and those come back.
     */
    onForget: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val favorites = favoriteKeys.toSet()
    val appsByKey = remember(allApps) { allApps.associateBy { it.key } }
    val foldersById = remember(folders) { folders.associateBy { it.id } }
    // A favorite inside a locked private space is left out entirely, rather than shown as the
    // row that says an app is missing: that row would still say how many are in there. The
    // stored keys are untouched, so those favorites come back when the space is opened. When
    // the space itself could not be read, the same goes for any favorite that names nothing,
    // since there is then no serial to tell a private one from an uninstalled one.
    //
    // One predicate, and the reorder below puts rows back with this same one. Hiding by a wide
    // rule and restoring by a narrower one leaves the keys between the two in neither list,
    // and the restore reads a list that is shorter than it expected as rows the user deleted —
    // so the first drag would write that deletion to the store.
    val conceal: (String) -> Boolean = remember(privateSpace, appsByKey, foldersById) {
        { key ->
            val resolves = key in appsByKey || folderIdFromToken(key)?.let { it in foldersById } == true
            privateSpace.concealsStored(key, resolves)
        }
    }
    val shownKeys = remember(favoriteKeys, conceal) { favoriteKeys.filterNot(conceal) }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favorites_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.padding(padding),
        ) {
            item {
                ListSectionLabel(stringResource(R.string.favorites_count, shownKeys.size))
            }

            item {
                ReorderableRows(
                    keys = shownKeys,
                    hapticFeedback = hapticFeedback,
                    // What was left out goes back where it was, by the rule that left it
                    // out. The screen writes back the whole list it was given, so without this
                    // the first drag would delete every favorite this screen is hiding.
                    onReorder = { order -> onReorder(restoreConcealed(favoriteKeys, order, conceal)) },
                ) { key ->
                    val folder = folderIdFromToken(key)?.let { foldersById[it] }
                    val app = appsByKey[key]
                    when {
                        folder != null -> {
                            // A folder is one favorite like any other and moves among them,
                            // but it is not an app: what it holds is edited in its own screen,
                            // so there is nothing here to untick.
                            Icon(
                                DelugeIcons.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                folder.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        app != null -> {
                            var menuOpen by remember(app.key) { mutableStateOf(false) }
                            val hidden = app.key in hiddenApps
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { menuOpen = true },
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppIcon(app = app, sizeDp = minOf(iconSizeDp, 44))
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        nameOverrides[app.key] ?: app.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (hidden) {
                                        Icon(
                                            DelugeIcons.VisibilityOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Checkbox(checked = true, onCheckedChange = { onSetFavorite(app, false) })
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (hidden) R.string.applist_unhide
                                                    else R.string.applist_hide
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (hidden) DelugeIcons.Visibility
                                                else DelugeIcons.VisibilityOff,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            menuOpen = false
                                            onToggleHidden(app, !hidden)
                                        },
                                    )
                                }
                            }
                        }

                        // Whatever this key was is gone: an app uninstalled, or a shortcut
                        // its publisher withdrew. Shown rather than skipped so the count
                        // matches what is listed, and with the same untick as everything
                        // else, because otherwise it is a row that can never be got rid of.
                        else -> {
                            Text(
                                stringResource(R.string.folder_member_missing),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1f),
                            )
                            Checkbox(checked = true, onCheckedChange = { onForget(key) })
                        }
                    }
                }
            }

            item {
                ListSectionLabel(stringResource(R.string.folder_add_apps))
            }

            items(allApps.filterNot { it.key in favorites }, key = { it.key }) { app ->
                var menuOpen by remember(app.key) { mutableStateOf(false) }
                val hidden = app.key in hiddenApps
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onSetFavorite(app, true) },
                                onLongClick = { menuOpen = true },
                            )
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app = app, sizeDp = minOf(iconSizeDp, 44))
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(nameOverrides[app.key] ?: app.label, style = MaterialTheme.typography.bodyLarge)
                        }
                        if (hidden) {
                            Icon(
                                DelugeIcons.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Checkbox(checked = false, onCheckedChange = { onSetFavorite(app, true) })
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (hidden) R.string.applist_unhide else R.string.applist_hide
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (hidden) DelugeIcons.Visibility else DelugeIcons.VisibilityOff,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onToggleHidden(app, !hidden)
                            },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
        }
    }
}