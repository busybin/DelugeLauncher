// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R

/**
 * One release's notes.
 *
 * Written here rather than read from a file so that a release's notes travel with the code
 * that made them, and so nothing has to parse anything at run time. Kept in English: it is a
 * developer's log of what changed, and a translated changelog drifts from the code it is
 * describing. Everything the user actually reads on screen is translated.
 */
data class Release(val version: String, val notes: List<String>)

/** Newest first. Add an entry here only when a version is actually being cut. */
val CHANGELOG: List<Release> = listOf(
    Release(
        "V19",
        listOf(
            "Language can now be chosen in settings — 10 languages plus Traditional Chinese.",
            "The A-Z strip sits on the side the app list is not aligned to.",
            "The launcher keeps you where you were when the language changes.",
            "Home screen content no longer sits under the status bar when it is shown.",
            "Set as default: a card on the home screen offers to make Deluge your home app, and can be dismissed for good.",
        ),
    ),
    Release(
        "First fork releases",
        listOf(
            "Renamed and rebranded as Deluge Launcher, with a new blue theme and icon.",
            "App list: the strip lights up every letter on screen while scrolling, and the list runs edge to edge.",
            "Fixed the list refusing to scroll when a swipe started at an angle.",
            "Fixed the home screen taking seconds to draw after leaving an app.",
            "Fixed a two second blank screen when returning home from another app.",
            "App icons are cached on disk, so the second launch is fast.",
            "Folder rows show how many apps they hold, and whether they are open.",
            "Widget height follows the widget itself unless it is set by hand.",
            "Dead widgets left over from a backup are cleaned up on their own.",
            "Swipe for shortcuts only fires on a sideways swipe, so scrolling past a row is never stolen.",
            "Added a disk-backed icon cache, a lighter icon set, and the navigation library removed.",
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_changelog)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            items(CHANGELOG) { release ->
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            text = release.version,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        release.notes.forEach { note ->
                            Text(
                                text = "•  $note",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
