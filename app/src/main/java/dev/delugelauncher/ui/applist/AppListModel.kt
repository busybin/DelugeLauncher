// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

import androidx.compose.runtime.Immutable
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.EntryKind
import dev.delugelauncher.data.Folder
import dev.delugelauncher.data.folderToken
import dev.delugelauncher.data.Pinyin
import android.content.Context
import android.content.res.Configuration
import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.RequiresApi
import java.text.Normalizer
import java.util.Locale

/** Section tags, which only have to be distinct from each other and from no tag at all. */
internal const val PRIVATE_SECTION = "private"
internal const val RECENT_SECTION = "recent"

/** The row key a tag group's entries carry, so the same app under a tag does not clash. */
internal fun tagSectionToken(label: String) = "tag:$label"

/**
 * What a cell holds: an app, or a folder standing in the alphabet among them.
 *
 * Folders are only ever a full row of their own, but they pair like apps do when two columns
 * are on — pairing is a layout question, and a folder is as tall as an app.
 */
@Immutable
sealed interface ListItem {
    val key: String
    val section: String?

    data class AppItem(val entry: AppListRow.Entry) : ListItem {
        override val key: String get() = entry.app.key
        override val section: String? get() = entry.section
    }

    data class FolderItem(val row: AppListRow.FolderRow) : ListItem {
        override val key: String get() = folderToken(row.folder.id)
        override val section: String? get() = row.section
    }
}

/**
 * One laid-out row of the list. Portrait holds one item per row; sideways two share one,
 * so the two columns stay level instead of one running long.
 */
@Immutable
sealed interface CellRow {
    data class Header(val text: String) : CellRow
    data class One(val item: ListItem) : CellRow
    data class Two(val left: ListItem, val right: ListItem?) : CellRow

    /** Only the apps, for the callers that launch one or collect tags. */
    fun entries(): List<AppListRow.Entry> = when (this) {
        is Header -> emptyList()
        is One -> listOfNotNull((item as? ListItem.AppItem)?.entry)
        is Two -> listOfNotNull(
            (left as? ListItem.AppItem)?.entry,
            (right as? ListItem.AppItem)?.entry,
        )
    }
}

/**
 * The model as laid out: same shape as [AppListModel], so everything that reads letter
 * indices and row positions — scrub placement, spy, gaps — works off this untouched.
 */
@Immutable
data class CellModel(
    val rows: List<CellRow>,
    val letterIndex: List<Pair<String, Int>>,
    val alphabetStart: Int = 0,
) {
    val letters: List<String> = letterIndex.map { it.first }
}

/** A laid-out row as the cell that draws it. */
private fun itemAt(row: AppListRow): ListItem = when (row) {
    is AppListRow.Entry -> ListItem.AppItem(row)
    is AppListRow.FolderRow -> ListItem.FolderItem(row)
    is AppListRow.Header -> error("a heading is never an item")
}

/**
 * Pairs neighboring entries that belong together — same letter, same section — and leaves
 * everything else (headings, loners at a group's end) one to a row. Pairing across a group
 * boundary would sit strangers side by side and break the letter index.
 */
fun AppListModel.toCells(twoColumn: Boolean): CellModel {
    if (!twoColumn) {
        return CellModel(
            rows = rows.map { row ->
                when (row) {
                    is AppListRow.Header -> CellRow.Header(row.text)
                    is AppListRow.Entry -> CellRow.One(ListItem.AppItem(row))
                    is AppListRow.FolderRow -> CellRow.One(ListItem.FolderItem(row))
                }
            },
            letterIndex = letterIndex,
            alphabetStart = alphabetStart,
        )
    }
    // Row -> letter, walked once: the index is sorted by row, so a single cursor pass
    // files every row. groupOf/recordLetter below then read arrays instead of scanning.
    val rowLetter = Array(rows.size) { "" }
    var cursor = 0
    var letter = ""
    for (i in rows.indices) {
        while (cursor < letterIndex.size && letterIndex[cursor].second <= i) {
            letter = letterIndex[cursor].first
            cursor++
        }
        rowLetter[i] = letter
    }
    fun groupOf(index: Int): String {
        val row = rows[index]
        val section = when (row) {
            is AppListRow.Entry -> row.section
            is AppListRow.FolderRow -> row.section
            is AppListRow.Header -> null
        }
        if (section != null) return "s:$section"
        return "l:${rowLetter[index]}"
    }
    val atRow: Map<Int, List<String>> = letterIndex.groupBy({ it.second }, { it.first })
    val cellRows = mutableListOf<CellRow>()
    val cellIndex = mutableListOf<Pair<String, Int>>()
    var alphaStart = 0
    fun recordLetter(at: Int) {
        atRow[at]?.forEach { cellIndex += it to cellRows.size }
    }
    var i = 0
    while (i < rows.size) {
        if (i == alphabetStart) alphaStart = cellRows.size
        // Every row, heading or not: a letter is anchored at its heading, and only
        // recording entries dropped every one of them — an empty index, no strip, and a
        // highlight range that hid every row the moment a scrub began.
        recordLetter(i)
        when (val row = rows[i]) {
            is AppListRow.Header -> {
                cellRows += CellRow.Header(row.text)
                i++
            }
            is AppListRow.Entry, is AppListRow.FolderRow -> {
                val next = rows.getOrNull(i + 1)
                val pairs = next != null && next !is AppListRow.Header &&
                    groupOf(i + 1) == groupOf(i)
                if (pairs) {
                    recordLetter(i + 1)
                    cellRows += CellRow.Two(itemAt(row), itemAt(next!!))
                    i += 2
                } else {
                    cellRows += CellRow.One(itemAt(row))
                    i++
                }
            }
        }
    }
    return CellModel(cellRows, cellIndex, alphaStart)
}

fun CellModel.alphabetEnd(): Int {
    // From where the alphabet begins: tag groups sit above it, and scanning from row zero
    // would stop at the first tag and leave the whole strip unlit.
    for (i in alphabetStart until rows.size) {
        when (val row = rows[i]) {
            is CellRow.Header -> if (row.text.length != 1) return i
            is CellRow.One -> if (row.item.section != null) return i
            is CellRow.Two ->
                if (row.left.section != null || row.right?.section != null) return i
        }
    }
    return rows.size
}

/** A tag's stand-in for the strip; the heading shows the whole tag. */
internal fun tagStripLabel(label: String): String =
    label.trim().take(STRIP_LABEL_CHARS)

/** How many characters of a tag the strip shows. Three reads, one does not. */
internal const val STRIP_LABEL_CHARS = 3

/** The auto-tagger's word in every shipped language, read from `tag_frequent`. */
internal fun autoTagWords(context: Context): Set<String> {
    val base = context.resources.configuration
    return AUTO_TAG_LOCALES.mapNotNull { tag ->
        runCatching {
            val config = Configuration(base).apply { setLocale(Locale.forLanguageTag(tag)) }
            context.createConfigurationContext(config).getString(R.string.tag_frequent)
        }.getOrNull()
    }.toSet()
}

private val AUTO_TAG_LOCALES = listOf("en", "zh-CN", "es", "fr", "ar", "pt-BR", "ru", "ja")

@Immutable
sealed interface AppListRow {
    data class Header(val text: String) : AppListRow

    /**
     * One app, and which part of the list it is in.
     *
     * [section] is null throughout the alphabet and names the section otherwise. An app shown
     * again under "Recently installed" is the same app in two places, and a list keyed by the
     * app alone then has the same key twice — which Compose refuses outright, taking the home
     * screen down with it.
     */
    data class Entry(val app: AppInfo, val section: String? = null) : AppListRow

    /**
     * A folder standing in the alphabet by its own name.
     *
     * Only present when the list is set to show folders. A folder never leaves an app out of
     * the alphabet: the apps inside it are still filed under their own letters.
     */
    data class FolderRow(val folder: Folder, val section: String? = null) : AppListRow
}

/**
 * Marked immutable so Compose treats it as a stable parameter. It is only ever replaced
 * wholesale, never mutated, but the `List` fields on their own have it inferred as unstable,
 * which costs the whole app list a recomposition every time anything above it changes.
 */
@Immutable
data class AppListModel(
    val rows: List<AppListRow>,
    /** First row index for each strip entry, in scrubber order: tag groups first, then A-Z. */
    val letterIndex: List<Pair<String, Int>>,
    /** The row the A-Z alphabet begins at; tag groups sit above it. */
    val alphabetStart: Int = 0,
) {
    /** The scrubber's labels, derived once here rather than at each place that draws it. */
    val letters: List<String> = letterIndex.map { it.first }
}

/**
 * First row index past the A-Z alphabet: the private and recent sections belong to no
 * letter and must never light one up. A letter's heading is its own single character;
 * a section heading is a word, and an appended app carries its section along.
 *
 * Only the cell layout is ever drawn, so only it is ever asked; this stays for the tests.
 */
fun AppListModel.alphabetEnd(): Int {
    // From where the alphabet begins: tag groups sit above it, and scanning from row zero would
    // stop at the first tag and leave the whole strip unlit.
    for (i in alphabetStart until rows.size) {
        val row = rows[i]
        when {
            row is AppListRow.Entry && row.section != null -> return i
            row is AppListRow.FolderRow && row.section != null -> return i
            row is AppListRow.Header && row.text.length != 1 -> return i
        }
    }
    return rows.size
}

/**
 * The letter a name is filed under, or '#'.
 *
 * Only A-Z get one of their own. Char.isLetter is true of every kanji and every hangul
 * syllable, so filing by it gave a strip of thousands of entries on a device with CJK app
 * names — one per character, which is no index at all.
 *
 * An alphabet that can be carried over letter for letter is, so Калькулятор files under K
 * rather than joining everything else in '#'. That keeps one strip for a phone whose app
 * names are half Latin, which is the usual case.
 *
 * Accents and ligatures are folded first, so Ärger files under A and Œuvre under O, rather to '#'
 * than falling to '#' with the scripts that have no place on an A-Z strip. '#' sorts above A,
 * being the lower codepoint.
 */
/**
 * The letter an app files under, given the name being shown for it.
 *
 * Separate from [indexLetter] because the interesting part is not which letter a string starts
 * with but what to do when it starts with none. [english] is asked for only then, and only for
 * a name that came from the app: a name somebody typed is the answer even when it files under
 * '#'. Renaming an app to 4PDA and finding it still under F was this fallback handing back the
 * very name that had just been replaced.
 */
internal fun filingLetter(
    shown: String,
    renamed: Boolean,
    pinyin: Boolean = false,
    japanese: Boolean = false,
    english: () -> String?,
): Char {
    val own = indexLetter(shown, pinyin)
    if (own != '#') return own
    // A Japanese reader looks for 天気 among the kana and kanji, not under W.
    if (japanese) return japaneseHeading(shown) ?: '#'
    if (renamed) return '#'
    return english()?.let { indexLetter(it, pinyin) } ?: '#'
}

/**
 * The kana row a Japanese name files under, in the order JIS X 4061 gives them after A-Z, or
 * [KANJI_HEADING] for a name that starts with a kanji.
 *
 * Kanji are not given a row: which one depends on how the word is read, and nothing on the
 * phone says that for an app's name.
 */
internal fun japaneseHeading(name: String): Char? {
    val first = name.firstOrNull() ?: return null
    if (runCatching { Character.UnicodeScript.of(first.code) }.getOrNull() == Character.UnicodeScript.HAN) {
        return KANJI_HEADING
    }
    // Half-width katakana widened and voicing marks split off, so が files with か.
    var kana = Normalizer.normalize(first.toString(), Normalizer.Form.NFKD).firstOrNull() ?: return null
    if (kana in '\u30A1'..'\u30F6') kana -= 0x60
    kana = SMALL_KANA[kana] ?: kana
    if (kana !in '\u3041'..'\u3096') return null
    return KANA_ROWS.last { it <= kana }
}

internal const val KANJI_HEADING = '漢'

private val KANA_ROWS = listOf('あ', 'か', 'さ', 'た', 'な', 'は', 'ま', 'や', 'ら', 'わ')

/** Small kana that sort below their row's first letter, mapped to their full-size form. */
private val SMALL_KANA = mapOf(
    'ぁ' to 'あ', 'ゃ' to 'や', 'ゅ' to 'ゆ', 'ょ' to 'よ', 'ゎ' to 'わ', 'ゕ' to 'か', 'ゖ' to 'け',
    // ゔ is う with a voicing mark, so JIS X 4061 puts it in the あ row, not at the end of わ.
    'ゔ' to 'う',
)

/**
 * The letter a name files under, with [pinyin] for a list whose language is Chinese.
 *
 * With [pinyin] the Han characters are read as pinyin, so a Chinese name files under W and
 * Alipay's under Z — the name on the screen rather than the English one the app stores, which
 * puts Alipay under A. Without it a Han name still has no A-Z letter of its own.
 */
internal fun indexLetter(name: String, pinyin: Boolean = false): Char {
    val first = name.firstOrNull() ?: return '#'
    val folded = Normalizer.normalize(first.toString(), Normalizer.Form.NFKD)
        .firstOrNull()
        ?.uppercaseChar()
        ?: return '#'
    if (folded in 'A'..'Z') return folded
    LATIN_STANDALONE[folded]?.let { return it }
    romanize(folded)?.let { return it }
    // Only Han comes back from here, and only where ICU answers: kana and hangul read
    // differently from the same characters in Chinese, so they stay '#' and keep the English
    // fallback that filingLetter applies to them.
    if (pinyin) {
        Pinyin.latin(name).firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.let {
            return it
        }
    }
    return '#'
}

/**
 * Scripts an alphabet can be carried over to A-Z without inventing anything.
 *
 * Each of these has a letter-for-letter romanization, so К lands on K the way a reader of the
 * script would expect. Han and kana do not: romanizing those needs to know the word, and the
 * same character reads differently in Japanese and Chinese — so they keep '#' unless the list
 * asks for pinyin (see [indexLetter]).
 */
private val ROMANIZABLE = setOf(
    Character.UnicodeScript.CYRILLIC,
    Character.UnicodeScript.GREEK,
    Character.UnicodeScript.ARMENIAN,
    Character.UnicodeScript.GEORGIAN,
)

/**
 * ICU does this properly and ships with the platform, so the table below is only what stands
 * in for it on Android 9 and older, where the transliterator is not public API. The two agree
 * on Cyrillic and Greek; Armenian and Georgian fall back to '#' there rather than carry a
 * third alphabet by hand.
 */
private val romanizer: Transliterator? by lazy {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        null
    } else {
        runCatching { Transliterator.getInstance("Any-Latin; Latin-ASCII") }.getOrNull()
    }
}

private fun romanize(upper: Char): Char? {
    val script = runCatching { Character.UnicodeScript.of(upper.code) }.getOrNull()
    if (script !in ROMANIZABLE) return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        icuRomanize(upper)?.let { return it }
    }
    return FALLBACK_ROMAN[upper]
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun icuRomanize(upper: Char): Char? =
    romanizer?.transliterate(upper.toString())
        ?.firstOrNull()
        ?.uppercaseChar()
        ?.takeIf { it in 'A'..'Z' }

/** Matched to what ICU's Any-Latin produces, so both paths file a name the same way. */
private val FALLBACK_ROMAN = mapOf(
    // Cyrillic
    'А' to 'A', 'Б' to 'B', 'В' to 'V', 'Г' to 'G', 'Д' to 'D', 'Е' to 'E', 'Ё' to 'E',
    'Ж' to 'Z', 'З' to 'Z', 'И' to 'I', 'Й' to 'J', 'К' to 'K', 'Л' to 'L', 'М' to 'M',
    'Н' to 'N', 'О' to 'O', 'П' to 'P', 'Р' to 'R', 'С' to 'S', 'Т' to 'T', 'У' to 'U',
    'Ф' to 'F', 'Х' to 'H', 'Ц' to 'C', 'Ч' to 'C', 'Ш' to 'S', 'Щ' to 'S', 'Ы' to 'Y',
    'Э' to 'E', 'Ю' to 'U', 'Я' to 'A',
    // Ukrainian, Belarusian, Serbian and Macedonian letters Russian does not use
    'Ґ' to 'G', 'Є' to 'E', 'І' to 'I', 'Ї' to 'I', 'Ў' to 'U',
    'Ђ' to 'D', 'Ј' to 'J', 'Љ' to 'L', 'Њ' to 'N', 'Ћ' to 'C', 'Џ' to 'D',
    // Greek
    'Α' to 'A', 'Β' to 'B', 'Γ' to 'G', 'Δ' to 'D', 'Ε' to 'E', 'Ζ' to 'Z', 'Η' to 'E',
    'Θ' to 'T', 'Ι' to 'I', 'Κ' to 'K', 'Λ' to 'L', 'Μ' to 'M', 'Ν' to 'N', 'Ξ' to 'X',
    'Ο' to 'O', 'Π' to 'P', 'Ρ' to 'R', 'Σ' to 'S', 'Τ' to 'T', 'Υ' to 'Y', 'Φ' to 'P',
    'Χ' to 'C', 'Ψ' to 'P', 'Ω' to 'O',
)

/**
 * Latin letters Unicode holds as characters in their own right rather than as an accented A-Z
 * one, so no amount of normalizing reaches the letter underneath. Without these, names in
 * Danish, Norwegian, Polish or Icelandic fall to '#' the same as a script that genuinely has
 * no A-Z letter to file under.
 */
private val LATIN_STANDALONE = mapOf(
    'Æ' to 'A', 'Ø' to 'O', 'Œ' to 'O', 'Ł' to 'L', 'Đ' to 'D', 'Ð' to 'D',
    'Þ' to 'T', 'Ħ' to 'H', 'Ŧ' to 'T', 'Ŋ' to 'N', 'Ə' to 'E', 'ẞ' to 'S', 'ß' to 'S',
)

/** One alphabet entry before it becomes a row: an app or a folder, sharing a sort. */
private class AlphabetItem(
    val app: AppInfo?,
    val folder: Folder? = null,
    val count: Int,
    val order: String,
    val key: String,
)

/**
 * [launchCounts] empty keeps every section alphabetical; otherwise the apps inside each letter
 * are ordered by how often they were opened from here. The letters themselves never move —
 * an app is still filed under its own name, or the scrubber would be pointing at nothing.
 */
fun buildAppListModel(
    apps: List<AppInfo>,
    hidden: Set<String>,
    displayName: (AppInfo) -> String,
    launchCounts: Map<String, Int> = emptyMap(),
    /**
     * The app's name in English, asked for only when its own name has no letter to file under.
     *
     * A Japanese phone names an app in katakana, which lands in '#' along with everything else
     * that is not A-Z — so scrubbing to B, where its English name would put it, finds nothing.
     */
    englishName: (AppInfo) -> String? = { null },
    /**
     * Whether the list is in Chinese, which reads Han names as pinyin: a Chinese name is filed
     * under its pinyin letter and ordered by it, where the English name would put it under A
     * among latin names. The English fallback still applies to what pinyin cannot read.
     */
    pinyin: Boolean = false,
    /**
     * Whether each letter gets a heading row of its own. Without them the letters are still
     * indexed — at the first app of each instead of at its heading — so the strip still knows
     * where to send the list.
     */
    showHeaders: Boolean = true,
    /**
     * Whether this app's name was chosen rather than read from the app.
     *
     * Only consulted for a name that has no letter to file under, where it decides between
     * respecting the choice and falling back to [englishName].
     */
    renamed: (AppInfo) -> Boolean = { false },
    /** Heading for a section of the private space's own apps, or null to leave them in the A-Z. */
    privateSectionLabel: String? = null,
    /** Folders to file among the apps, each under the letter of its own name. */
    folders: List<Folder> = emptyList(),
    /** Whether folders stand in the alphabet. Off, the list is apps only. */
    showFolders: Boolean = false,
    /** Heading and how many, for a section of what was installed most recently. */
    recentSection: Pair<String, Int>? = null,
    /** Whether names that are not A-Z file under kana rows and kanji, as a Japanese phone sorts. */
    japanese: Boolean = false,
    /** Apps gathered under a tag, each group with its own heading, across the alphabet. */
    tagSections: List<Pair<String, List<AppInfo>>> = emptyList(),
): AppListModel {
    val all = apps.filter { it.key !in hidden }
    // The row that locks and unlocks the space goes at the very end rather than under P, where
    // nobody looking for the space thinks to look.
    val padlock = all.filter { it.kind == EntryKind.PRIVATE_SPACE }
    // Lifted out of the alphabet entirely rather than listed twice. A private app and its main
    // profile twin have the same name, which is the whole reason for asking.
    val visible = all.filterNot {
        it.kind == EntryKind.PRIVATE_SPACE || (privateSectionLabel != null && it.privateProfile)
    }
    val rows = mutableListOf<AppListRow>()
    val letterIndex = mutableListOf<Pair<String, Int>>()

    // What the apps inside a letter are ordered by: the name as the user reads it, which for a
    // Chinese list is its pinyin. Otherwise weibo would sort behind weixin, whose codepoints
    // come first. A latin name reads as itself either way.
    val orderKey: (AppInfo) -> String = { app ->
        val name = displayName(app)
        if (pinyin) Pinyin.latin(name) else name.lowercase(java.util.Locale.ROOT)
    }

    // Tag groups first, above the alphabet. A tag is a heading of its own and the apps under it
    // are the same apps that are in the alphabet — the point being to find an app by what it is
    // for rather than by its first letter. The strip shows each tag by its first three
    // characters, centred on its own row like every other entry; the heading shows it in full.
    // Two tags sharing the first three lengthen until they differ, or the second is unreachable.
    val usedStripLabels = mutableSetOf<String>()
    tagSections.forEach { (label, group) ->
        val shown = group.filter { it.key !in hidden }
        if (shown.isNotEmpty()) {
            val trimmed = label.trim()
            var width = STRIP_LABEL_CHARS
            var strip = trimmed.take(width)
            while ((strip in usedStripLabels || strip.isEmpty()) && width < trimmed.length) {
                width++
                strip = trimmed.take(width)
            }
            // Never split a surrogate pair across the cut.
            if (strip.isNotEmpty() && Character.isHighSurrogate(strip.last())) {
                strip = strip.dropLast(1)
            }
            if (strip.isNotEmpty() && strip !in usedStripLabels) {
                letterIndex += strip to rows.size
                usedStripLabels += strip
            }
            rows += AppListRow.Header(label)
            shown.sortedWith(
                compareBy(orderKey, { it.key })
            ).forEach { rows += AppListRow.Entry(it, section = tagSectionToken(label)) }
        }
    }
    val alphabetStart = rows.size

    val byLetter = visible.groupBy { app ->
        filingLetter(displayName(app), renamed(app), pinyin, japanese) { englishName(app) }
    }
    // A folder files under the first letter of its own name, and sorts among the apps rather
    // than in a section of its own: the point of having it in the list is to reach it where
    // its name says it is.
    val foldersByLetter = if (!showFolders) {
        emptyMap()
    } else {
        folders.groupBy {
            filingLetter(it.name, renamed = true, pinyin = pinyin, japanese = japanese) { null }
        }
    }

    (byLetter.keys + foldersByLetter.keys).toSortedSet().forEach { letter ->
        letterIndex += letter.toString() to rows.size
        if (showHeaders) rows += AppListRow.Header(letter.toString())
        val items = mutableListOf<AlphabetItem>()
        byLetter[letter]?.forEach { app ->
            items += AlphabetItem(
                app = app,
                count = launchCounts[app.key] ?: 0,
                order = orderKey(app),
                key = app.key,
            )
        }
        foldersByLetter[letter]?.forEach { folder ->
            items += AlphabetItem(
                app = null,
                folder = folder,
                count = 0,
                order = folder.name.lowercase(java.util.Locale.ROOT),
                key = folderToken(folder.id),
            )
        }
        items.sortedWith(
            compareByDescending<AlphabetItem> { it.count }
                .thenBy { it.order }
                .thenBy { it.key }
        ).forEach { item ->
            rows += if (item.folder != null) AppListRow.FolderRow(item.folder)
            else AppListRow.Entry(item.app!!)
        }
    }

    // Appended after the alphabet, each under its own heading. Not given a letter of their
    // own: the strip is the alphabet, and a section that is not a letter has no business
    // claiming a place in it.
    recentSection?.let { (label, limit) ->
        val recent = visible.filter { it.installedAt > 0L }
            .sortedByDescending { it.installedAt }
            .take(limit)
        if (recent.isNotEmpty()) {
            rows += AppListRow.Header(label)
            recent.forEach { rows += AppListRow.Entry(it, section = RECENT_SECTION) }
        }
    }

    // Last, so the padlock and the apps it lets out sit together at the bottom rather than
    // under P, where nobody looking for the space thinks to look.
    val privateApps = if (privateSectionLabel == null) emptyList() else all.filter { it.privateProfile }
    if (privateApps.isNotEmpty()) rows += AppListRow.Header(privateSectionLabel!!)
    padlock.forEach { rows += AppListRow.Entry(it) }
    privateApps.sortedWith(
        compareBy(orderKey, { it.key })
    ).forEach { rows += AppListRow.Entry(it, section = PRIVATE_SECTION) }

    return AppListModel(rows, letterIndex, alphabetStart)
}

/**
 * The same list with only the entries [match] accepts, and only the headers still holding
 * something. Rebuilt rather than filtered in place: letterIndex stores row indices, so
 * removing any row invalidates every index after it.
 */
fun AppListModel.filtered(match: (AppInfo) -> Boolean): AppListModel {
    val kept = mutableListOf<AppListRow>()
    val newIndex = mutableListOf<Pair<String, Int>>()
    var pendingHeader: AppListRow.Header? = null

    // Which letter the row being looked at belongs to. Read from the index this model already
    // carries rather than from the heading above it, because a list drawn without headings has
    // no heading to read — and the letters still have to come out of it.
    var cursor = 0
    var letter: String? = null
    var letterKept = false
    // Where the alphabet begins in the kept rows: everything before it is tag groups, and the
    // spy must start past them or the strip goes dark.
    var keptAlphabetStart = 0
    var alphabetPassed = alphabetStart <= 0

    rows.forEachIndexed { index, row ->
        if (!alphabetPassed && index >= alphabetStart) {
            keptAlphabetStart = kept.size
            alphabetPassed = true
        }
        while (cursor < letterIndex.size && letterIndex[cursor].second <= index) {
            letter = letterIndex[cursor].first
            letterKept = false
            cursor++
        }
        when (row) {
            is AppListRow.Header -> pendingHeader = row
            // A query searches apps by name; a folder's members are still found by their own
            // names under their own letters.
            is AppListRow.FolderRow -> Unit
            is AppListRow.Entry -> if (match(row.app)) {
                // Recorded before the heading is added, so the letter points at the heading
                // where there is one and at its first app where there is not.
                if (!letterKept) {
                    letter?.let { newIndex += it to kept.size }
                    letterKept = true
                }
                pendingHeader?.let { kept += it; pendingHeader = null }
                kept += row
            }
        }
    }
    // Nothing matched: no rows, no start past them.
    if (kept.isEmpty()) keptAlphabetStart = 0
    return AppListModel(kept, newIndex, keptAlphabetStart.coerceAtMost(kept.size))
}

/**
 * The same list reordered by [rank], keeping the order it already had within each band and
 * rebuilding the letter index, which is row positions and cannot survive a reorder.
 *
 * Filed like the list itself, not by the bare shown name: a CJK name that files under its
 * English word keeps that letter here too, so scrubbing a search result lands where the
 * list would have put it.
 */
fun AppListModel.rankedBy(
    rank: (AppInfo) -> Int,
    displayName: (AppInfo) -> String = { it.label },
    renamed: (AppInfo) -> Boolean = { false },
    englishName: (AppInfo) -> String? = { null },
    pinyin: Boolean = false,
    japanese: Boolean = false,
): AppListModel {
    val entries = rows.filterIsInstance<AppListRow.Entry>()
    val sorted = entries.sortedBy { rank(it.app) }
    if (sorted == entries) return this
    // Headings would be wrong the moment the rows stop being in letter order, and a search
    // result is not a letter's worth of apps anyway. Indexed by the shown name, not the
    // package label: a rename files the row where it is drawn.
    val letterIndex = sorted.mapIndexedNotNull { index, row ->
        val letter = filingLetter(displayName(row.app), renamed(row.app), pinyin, japanese) {
            englishName(row.app)
        }
        letter.takeIf {
            index == 0 || it != filingLetter(
                displayName(sorted[index - 1].app),
                renamed(sorted[index - 1].app),
                pinyin,
                japanese,
            ) { englishName(sorted[index - 1].app) }
        }?.let { it.toString() to index }
    }
    return AppListModel(sorted, letterIndex)
}
