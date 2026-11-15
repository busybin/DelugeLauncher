// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

/**
 * The space a screen's top bar keeps clear: the status bar, plus the camera cutout.
 *
 * The bar's own inset follows "Hide status bar", so a hidden bar gives back its space; the
 * cutout is added on its own, because a title running under the camera is wrong either way.
 */
@OptIn(ExperimentalLayoutApi::class)
val topBarInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
