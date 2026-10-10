// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The launcher's own language, and how it is put into force.
 *
 * Kept in a plain SharedPreferences file rather than in the settings store because it has to
 * be readable from `attachBaseContext`, before anything asynchronous can run — the first
 * inflation of the home screen should already be in the chosen language, and DataStore
 * cannot be read without suspending.
 *
 * This is the platform's own mechanism: a Configuration with a different locale, handed to
 * every Context the app builds. AppCompat's per-app locales would mean becoming an
 * AppCompatActivity, and this launcher is a plain ComponentActivity over a wallpaper-showing
 * theme of its own; swapping the theme for the feature is a bad trade.
 */
object LanguagePrefs {

    private const val FILE = "deluge_language"
    private const val KEY = "tag"

    /** The chosen tag, or null while the system's own language is in use. */
    fun read(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.takeIf { it.isNotBlank() }

    fun write(context: Context, tag: String?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            if (tag.isNullOrBlank()) remove(KEY) else putString(KEY, tag)
        }.apply()
    }

    /**
     * [base] with the chosen language, for use from `attachBaseContext`.
     *
     * The process default is set along with the context's configuration because different
     * things read the two: resources from the context, and String.format, date patterns and
     * Locale.getDefault() from the process. Set only one and a screen disagrees with the
     * next.
     */
    fun wrap(base: Context): Context {
        val tag = read(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        if (locale.language.isEmpty()) return base
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }

    /** The language's own name for a tag, for the settings row. */
    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
    }
}
