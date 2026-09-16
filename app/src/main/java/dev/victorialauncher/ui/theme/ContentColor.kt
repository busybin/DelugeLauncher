// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.theme

import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.victorialauncher.data.TextColorMode

private val LightText = Color(0xFFFFFFFF)
private val DarkText = Color(0xFF10161C)

/**
 * Text color for anything drawn over the wallpaper. AUTO asks the system for the
 * wallpaper's own colors and picks whichever reads against it, re-checking when the
 * wallpaper changes.
 */
/**
 * Whether the wallpaper is light enough that dark text reads better on it.
 *
 * Re-checked when the wallpaper changes, so anything derived from it follows along.
 */
@Composable
fun rememberWallpaperIsLight(): Boolean {
    val context = LocalContext.current
    var wallpaperIsLight by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return@DisposableEffect onDispose { }
        }
        val manager = WallpaperManager.getInstance(context)

        fun refresh() {
            val colors = runCatching {
                manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            }.getOrNull()
            val primary = colors?.primaryColor
            wallpaperIsLight = primary != null && primary.luminance() > 0.5f
        }
        refresh()

        val listener = WallpaperManager.OnColorsChangedListener { _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) refresh()
        }
        runCatching { manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { manager.removeOnColorsChangedListener(listener) } }
    }

    return wallpaperIsLight
}

/**
 * The Material palette matched to the wallpaper rather than to the system's dark mode.
 *
 * Anything drawn over the wallpaper has to answer to the wallpaper, not to whether Settings is
 * currently dark — so the swatches offered for a color and the color MATERIAL picks come from
 * one scheme instead of two that quietly disagree.
 */
@Composable
fun rememberWallpaperPalette(): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    val light = rememberWallpaperIsLight()
    return if (light) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)
}

@Composable
fun rememberContentColor(mode: TextColorMode, customArgb: Int = 0xFFFFFFFF.toInt()): Color {
    // MATERIAL needs the same reading as AUTO: which of the palette's two extremes to take
    // depends on what the wallpaper is doing behind the text.
    val wallpaperIsLight = rememberWallpaperIsLight()
    val palette = rememberWallpaperPalette()

    return when (mode) {
        TextColorMode.LIGHT -> LightText
        TextColorMode.DARK -> DarkText
        TextColorMode.AUTO -> if (wallpaperIsLight) DarkText else LightText
        TextColorMode.CUSTOM -> Color(customArgb)
        // The accent, taken from whichever scheme matches the wallpaper — a pale tint over a
        // dark one, a deep one over a light one, each designed to read on that surface. The
        // neutral roles were tried first and came out grey enough to be indistinguishable from
        // plain white, which left the whole option with nothing to offer over Custom.
        TextColorMode.MATERIAL ->
            palette?.primary ?: if (wallpaperIsLight) DarkText else LightText
    }
}