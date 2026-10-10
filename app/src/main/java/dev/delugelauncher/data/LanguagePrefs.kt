// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The launcher's own language, and how it is put into force.
 *
 * Held in a plain SharedPreferences file rather than in the settings store, and
 * deliberately: the choice has to be readable from `attachBaseContext`, before anything
 * asynchronous can run, so that the very first inflation of the home screen is already in
 * the chosen language. DataStore cannot be read without suspending, and suspending is not
 * something a base-context wrapper can do.
 *
 * This is the platform's own mechanism — a Configuration with a different locale handed to
 * every Context the app builds — rather than AppCompat's per-app locales. AppCompat applies
 * a stored language from the delegate of an `AppCompatActivity`, and this launcher is a
 * plain `ComponentActivity` over a wallpaper-showing theme of its own; adopting AppCompat's
 * theme to get the feature would trade the window setup for the translation.
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
     * The process default is set as well as the context's configuration: the two are read
     * by different things — resources from the context, `String.format`, date patterns and
     * `Locale.getDefault()` from the process — and a half-applied language shows up as one
     * screen disagreeing with the next.
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
