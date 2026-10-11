// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.settings

import androidx.annotation.StringRes
import dev.delugelauncher.R
import dev.delugelauncher.data.AppFont
import dev.delugelauncher.data.AzStripVisibility
import dev.delugelauncher.data.ColorSchemeChoice
import dev.delugelauncher.data.EdgeSide
import dev.delugelauncher.data.FavoritesSource
import dev.delugelauncher.data.ShortcutSwipe
import dev.delugelauncher.data.HomeAlignment
import dev.delugelauncher.data.IconShape
import dev.delugelauncher.data.IconSide
import dev.delugelauncher.data.TextColorMode

/**
 * Display labels for the settings chips.
 *
 * These used to be rendered straight off the enum constant names, which made the whole
 * settings screen partly untranslatable — and tied user-visible text to identifiers that
 * exist for the code's benefit.
 */
@StringRes
fun AppFont.labelRes(): Int = when (this) {
    AppFont.SYSTEM -> R.string.font_system
    AppFont.SANS_SERIF -> R.string.font_sans_serif
    AppFont.SERIF -> R.string.font_serif
    AppFont.MONOSPACE -> R.string.font_monospace
    AppFont.CUSTOM -> R.string.font_custom
}

@StringRes
fun TextColorMode.labelRes(): Int = when (this) {
    TextColorMode.AUTO -> R.string.text_color_auto
    TextColorMode.LIGHT -> R.string.text_color_light
    TextColorMode.DARK -> R.string.text_color_dark
    TextColorMode.MATERIAL -> R.string.text_color_material
    TextColorMode.CUSTOM -> R.string.text_color_custom
}

@StringRes
fun AzStripVisibility.labelRes(): Int = when (this) {
    AzStripVisibility.NEVER -> R.string.az_visibility_never
    AzStripVisibility.LANDSCAPE -> R.string.az_visibility_landscape
    AzStripVisibility.ALWAYS -> R.string.az_visibility_always
}

@StringRes
fun EdgeSide.labelRes(): Int = when (this) {
    EdgeSide.LEFT -> R.string.edge_left
    EdgeSide.RIGHT -> R.string.edge_right
    EdgeSide.BOTH -> R.string.edge_both
}

@StringRes
fun FavoritesSource.labelRes(): Int = when (this) {
    FavoritesSource.MANUAL -> R.string.favorites_source_manual
    FavoritesSource.FREQUENT -> R.string.favorites_source_frequent
}

@StringRes
fun ShortcutSwipe.labelRes(): Int = when (this) {
    ShortcutSwipe.OFF -> R.string.shortcut_swipe_off
    ShortcutSwipe.RIGHT -> R.string.shortcut_swipe_right
    ShortcutSwipe.LEFT -> R.string.shortcut_swipe_left
}

@StringRes
fun HomeAlignment.labelRes(): Int = when (this) {
    HomeAlignment.LEFT -> R.string.alignment_left
    HomeAlignment.CENTER -> R.string.alignment_center
    HomeAlignment.RIGHT -> R.string.alignment_right
}

@StringRes
fun IconShape.labelRes(): Int = when (this) {
    IconShape.SYSTEM -> R.string.icon_shape_system
    IconShape.CIRCLE -> R.string.icon_shape_circle
    IconShape.ROUNDED -> R.string.icon_shape_rounded
    IconShape.SQUARE -> R.string.icon_shape_square
}

@StringRes
fun IconSide.labelRes(): Int = when (this) {
    IconSide.LEFT -> R.string.alignment_left
    IconSide.RIGHT -> R.string.alignment_right
}

@StringRes
fun ColorSchemeChoice.labelRes(): Int = when (this) {
    ColorSchemeChoice.DELUGE_BLUE -> R.string.scheme_deluge_blue
    ColorSchemeChoice.DYNAMIC -> R.string.scheme_dynamic
    ColorSchemeChoice.RED -> R.string.scheme_red
    ColorSchemeChoice.ORANGE -> R.string.scheme_orange
    ColorSchemeChoice.YELLOW -> R.string.scheme_yellow
    ColorSchemeChoice.GREEN -> R.string.scheme_green
    ColorSchemeChoice.TEAL -> R.string.scheme_teal
    ColorSchemeChoice.PURPLE -> R.string.scheme_purple
    ColorSchemeChoice.PINK -> R.string.scheme_pink
}
