// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

import android.icu.text.Transliterator
import android.os.Build
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Pinyin matching for search, so a phone full of Chinese app names can be reached with a
 * latin keyboard: 微信 answers to "wx" and to "weixin", not only to its own characters.
 *
 * The romanization is ICU's, which ships with the platform, so no dictionary is carried. It is
 * done one character at a time and cached, because a query is answered on every keystroke and
 * a scan of two hundred apps per letter would be felt. Below Android 10 the transliterator is
 * not available and matching quietly falls back to the name and package alone.
 */
internal object PinyinSearch {

    /** Not a match. Above every real rank, so `rank(...) == NO_MATCH` reads plainly. */
    const val NO_MATCH = Int.MAX_VALUE

    /**
     * A name in latin letters.
     *
     * [source] is the bridge back to the original: `full[i]` came from `label[source[i]]`, which
     * is what lets a hit in the pinyin underline the characters it actually came from.
     */
    private class Key(val full: String, val initials: String, val source: IntArray)

    private val keys = ConcurrentHashMap<String, Key>()
    private val chars = ConcurrentHashMap<Char, String>()

    private val transliterator: Transliterator? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            null
        } else {
            // Han-Latin is the specific one and reads a lone character best; Any-Latin covers it
            // on the builds that ship the general table instead. Whichever answers for 中 is the
            // one kept, so a device whose ICU lacks both simply has no pinyin rather than a
            // matcher that quietly returns nothing.
            listOf("Han-Latin; Latin-ASCII", "Any-Latin; Latin-ASCII").firstNotNullOfOrNull { id ->
                runCatching { Transliterator.getInstance(id) }
                    .getOrNull()
                    ?.takeIf { it.transliterate("中").any(Char::isLetter) }
            }
        }
    }

    private fun key(label: String): Key = keys.getOrPut(label) {
        // Unbounded would pin every rename and disguise string forever; the active set is a
        // few hundred, so shedding bulk past that costs one rebuild, not memory.
        if (keys.size > 2000) keys.clear()
        val full = StringBuilder()
        val initials = StringBuilder()
        val source = ArrayList<Int>(label.length)
        label.forEachIndexed { index, ch ->
            var first = true
            for (c in romanize(ch)) {
                full.append(c)
                source.add(index)
                if (first) {
                    initials.append(c)
                    first = false
                }
            }
        }
        Key(full.toString(), initials.toString(), source.toIntArray())
    }

    /** The latin letters one character answers to: its pinyin when it is Han, else itself. */
    private fun romanize(ch: Char): String {
        if (ch in 'a'..'z' || ch in 'A'..'Z') return ch.lowercaseChar().toString()
        if (ch in '0'..'9') return ch.toString()
        if (Character.UnicodeScript.of(ch.code) != Character.UnicodeScript.HAN) {
            // Accented latin folds to its base letter (Ä→a); anything else — a space, a dash, a
            // script with no pinyin — is just a separator.
            return Normalizer.normalize(ch.toString(), Normalizer.Form.NFKD)
                .filter { it in 'a'..'z' || it in 'A'..'Z' }
                .lowercase()
        }
        val t = transliterator ?: return ""
        return chars.getOrPut(ch) {
            runCatching { t.transliterate(ch.toString()) }
                .getOrDefault("")
                .filter { it in 'a'..'z' || it in 'A'..'Z' }
                .lowercase()
        }
    }

    /**
     * How well [name] answers [term], lower being better, or [NO_MATCH].
     *
     * The same bands the plain matcher used, with the pinyin folded in between: the start of the
     * initials ("wx") ranks as high as the start of a word, a hit inside the running pinyin
     * ("eixi") still beats the last resort of a package name, and everything else keeps the
     * order it had.
     */
    fun rank(name: String, packageName: String, term: String): Int {
        if (term.isEmpty()) return 0
        // Lowercase once up front with a fixed locale: Turkish dotted-I would otherwise move
        // hits around, and building the pinyin key before the cheap checks costs a full
        // transliteration scan of every app on the first keystroke.
        val folded = term.lowercase(java.util.Locale.ROOT)
        if (name.startsWith(folded, ignoreCase = true)) return 0
        if (name.split(' ', '-', '_', '.').any { it.startsWith(folded, ignoreCase = true) }) return 1
        if (name.contains(folded, ignoreCase = true)) return 2
        val key = key(name)
        return when {
            key.initials.startsWith(folded, ignoreCase = true) -> 1
            key.full.startsWith(folded, ignoreCase = true) -> 2
            key.full.contains(folded, ignoreCase = true) -> 3
            packageName.contains(folded, ignoreCase = true) -> 4
            else -> NO_MATCH
        }
    }

    /** The stretch of [label] a [term] hit covers, for marking what matched, or null if none. */
    fun highlight(label: String, term: String): IntRange? {
        if (term.isEmpty()) return null
        val direct = label.indexOf(term, ignoreCase = true)
        if (direct >= 0) return direct until direct + term.length
        val folded = term.lowercase(java.util.Locale.ROOT)
        val key = key(label)
        val at = key.full.indexOf(folded)
        if (at < 0 || key.source.isEmpty()) return null
        val from = key.source[at]
        val to = key.source[(at + folded.length - 1).coerceAtMost(key.source.lastIndex)]
        return from..to
    }
}
