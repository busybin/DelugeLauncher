// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Bounds applied to a settings file on its way in.
 *
 * A backup is picked through the document picker, so it can be anything: another app's export,
 * a truncated copy, or a hand-edited file. Two kinds of damage matter here. A file larger than
 * any real backup can exhaust the heap before parsing starts, and a numeric setting outside the
 * range its slider offers can reach `padding()` or a layout constraint and crash the home screen.
 * Nothing in the app writes those values, so clamping on import loses nothing real.
 */
object ImportLimits {
    /** Several times the largest backup this app can produce. */
    const val MAX_BYTES = 1 * 1024 * 1024

    /** Longer than any label, path or key this app stores. */
    const val MAX_STRING_CHARS = 64 * 1024

    const val MAX_SET_ITEMS = 1024
    const val MAX_SET_ITEM_CHARS = 4096

    /** Ranges the UI itself offers; a value outside one of them did not come from here. */
    private val intRanges = mapOf(
        "icon_size_dp" to 12..96,
        "label_size_sp" to 10..28,
        "item_spacing_dp" to 0..40,
        "side_padding_dp" to 0..400,
        "now_playing_height_dp" to 0..400,
        "now_playing_pad_top" to 0..400,
        "now_playing_pad_bottom" to 0..400,
        "widget_pad_top" to 0..400,
        "widget_pad_bottom" to 0..400,
        "favorites_pad_top" to 0..400,
        "favorites_pad_bottom" to 0..400,
        "widget_height_dp" to 80..900,
        "widget_side_padding_dp" to 0..400,
        "widget_offset_x_dp" to -200..200,
        "edge_zone_width_dp" to 32..96,
        "widget_position" to 0..100,
        "status_bar_peek_seconds" to 1..30,
        "freeze_auto_delay_seconds" to 10..3600,
    )

    private val floatRanges = mapOf(
        "dim_wallpaper_alpha" to 0f..1f,
        "dim_home_alpha" to 0f..1f,
    )

    fun int(name: String, value: Int): Int? =
        intRanges[name]?.let { value.coerceIn(it) } ?: value

    /** NaN and infinity are not settings; a float in range is left alone. */
    fun float(name: String, value: Float): Float? {
        if (!value.isFinite()) return null
        return floatRanges[name]?.let { value.coerceIn(it) } ?: value
    }

    fun string(value: String): String? =
        value.takeIf { it.length <= MAX_STRING_CHARS }

    fun stringSet(values: Set<String>): Set<String>? {
        if (values.size > MAX_SET_ITEMS) return null
        if (values.any { it.length > MAX_SET_ITEM_CHARS }) return null
        return values
    }

    /**
     * Reads [stream] as UTF-8, but stops as soon as it is longer than [maxBytes].
     *
     * Null means the file is too large or unreadable. The caller closes the stream.
     */
    fun read(stream: InputStream, maxBytes: Int = MAX_BYTES): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
