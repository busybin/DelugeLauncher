// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import dev.delugelauncher.data.AppInfo
import kotlin.random.Random

/**
 * Screenshot disguise: every app answers to a mainstream name and wears an initial tile,
 * so a screenshot shows nothing of its own.
 *
 * Nothing here is stored. The mapping is built fresh each time the mode turns on and handed
 * down through composition; switching the mode off drops it and everything is itself again.
 * Names come from a fixed pool of well-known international apps. Icons are not borrowed from
 * anything installed — a borrowed icon is still somebody's real icon, recognisable on sight
 * and never matching the fake name beside it — but drawn as a plain initial tile instead.
 */
object ScreenshotDisguise {

    /** What an app can hide behind. International household names; never anything local. */
    val NAMES = listOf(
        "WhatsApp", "Telegram", "Instagram", "Facebook", "Messenger", "X", "TikTok",
        "YouTube", "Gmail", "Google Maps", "Google Drive", "Google Photos", "Chrome",
        "Spotify", "Netflix", "Disney+", "Prime Video", "Twitch", "Discord", "Reddit",
        "Pinterest", "LinkedIn", "Snapchat", "Skype", "Zoom", "Teams", "Slack", "Notion",
        "Dropbox", "OneDrive", "Google Keep", "Evernote", "Duolingo", "Strava", "Uber",
        "Lyft", "Airbnb", "Booking", "PayPal", "Revolut", "Shazam", "SoundCloud",
        "Google Pay", "Outlook",
    )

    /** Tile colors, picked by name hash so a name always wears the same one. */
    val TILE_COLORS = listOf(
        0xFF1A73E8, 0xFF188038, 0xFFD93025, 0xFFE8710A, 0xFF9334E6, 0xFF00796B,
        0xFFD81B60, 0xFF3949AB, 0xFF0097A7, 0xFF558B2F, 0xFF795548, 0xFF546E7A,
    )

    /** A fake name per app key. */
    fun build(apps: List<AppInfo>): Map<String, String> {
        if (apps.isEmpty()) return emptyMap()
        return buildMap {
            apps.shuffled(Random.Default).forEachIndexed { i, app ->
                // Never itself: a disguise that changes nothing is no disguise.
                var pick = NAMES[(i + app.key.hashCode().mod(NAMES.size)) % NAMES.size]
                if (pick == app.label) pick = NAMES[(NAMES.indexOf(pick) + 1) % NAMES.size]
                put(app.key, pick)
            }
        }
    }

    /** A folder's cover name, picked by its id so it does not wander. */
    fun folderName(folderId: String): String {
        val h = folderId.hashCode().let { if (it < 0) -it else it }
        return NAMES[h % NAMES.size]
    }

    /** Which tile color a fake name wears. */
    fun tileColor(name: String): Long =
        TILE_COLORS[name.hashCode().mod(TILE_COLORS.size)]
}

/** Fake names by app key, or null when nothing is disguised. Read wherever names draw. */
val LocalScreenshotNames = staticCompositionLocalOf<Map<String, String>?> { null }
