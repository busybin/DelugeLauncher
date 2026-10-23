// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.delugelauncher.ui.theme.DEFAULT_SHADOW_STRENGTH
import dev.delugelauncher.ui.theme.SHADOW_STRENGTH_RANGE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

enum class EdgeSide { LEFT, RIGHT, BOTH }

/**
 * Which way an app row is swiped to reach the shortcuts it publishes.
 *
 * A direction and not a switch because only one of the two does anything, and which one should
 * be the useful one is a question about the hand holding the phone.
 */
enum class ShortcutSwipe { OFF, RIGHT, LEFT }

/** Which edge favorites and app rows line up against. */
enum class HomeAlignment { LEFT, CENTER, RIGHT }

/** Which side of an app's name its icon sits on. */
enum class IconSide { LEFT, RIGHT }

/** The palette the launcher draws itself in. Deluge blue until told otherwise. */
enum class ColorSchemeChoice {
    DELUGE_BLUE,
    DYNAMIC,
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    TEAL,
    PURPLE,
    PINK,
}

/** The two swipe directions under the favorites that can launch an app. */
enum class QuickLaunchSlot { LEFT, RIGHT }

/** Which edge an app is carried in from when it is opened by a sideways swipe. */

/**
 * Where the favorites section gets its apps.
 *
 * FREQUENT is the same section drawn from what has actually been opened, so everything that
 * renders a favorite renders one of these too — it is the list that is computed, not the row.
 */
enum class FavoritesSource { MANUAL, FREQUENT }

/** Enough to be a shortlist, not so many that the section becomes the app list again. */
val FREQUENT_RANGE = 3..12

/**
 * How far down the viewport a scrubbed letter may be parked.
 *
 * Nearly the whole screen. The top is the top, and the only reason to stop short of the
 * bottom is that past this the letter's own row is half off the screen and there is nothing
 * under it to read — which is a broken state rather than a taste anyone holds.
 */
val SECTION_TOP_RANGE = 0..90

/** Rows a second a held letter walks the list at. */
val HOLD_SCROLL_RANGE = 1..12

/** Past about this the strip is being pushed off the edge it belongs to. */
val EDGE_ZONE_INSET_RANGE = 0..32

/** Enough to be a list of what is new, short of being a second app list. */
val RECENT_SECTION_RANGE = 3..15
enum class AppFont { SYSTEM, SANS_SERIF, SERIF, MONOSPACE, CUSTOM }

/** AUTO picks light or dark text from the wallpaper's own colors. */
enum class TextColorMode { AUTO, LIGHT, DARK, MATERIAL, CUSTOM }

/**
 * The mask an icon is drawn through.
 *
 * SYSTEM leaves the drawable alone, which means whatever shape the device already applies.
 * The rest re-mask an adaptive icon's own layers; an icon that is not adaptive has no safe
 * zone to cut into, so it is left as it is whatever this says.
 */
enum class IconShape { SYSTEM, CIRCLE, ROUNDED, SQUARE }

/** Which of the four two-column switches is meant. */
enum class TwoColumnSlot { HOME_PORTRAIT, HOME_LANDSCAPE, LIST_PORTRAIT, LIST_LANDSCAPE }

/** What a slot does: the corner button's tap, its slide up, either swipe under the favorites. */
enum class CornerAction {
    /** Opens the app chosen for that slot. */
    OPEN_APP,
    /** Opens the shortcut chosen for that slot. */
    OPEN_SHORTCUT,
    APP_LIST,
    SEARCH,
    CAMERA,
    FLASHLIGHT,
    SHADE,
    LOCK,
    /** Puts every app in the freeze list back under its chosen method. */
    FREEZE_ALL,
    /** Pauses everything in the freeze list; the list itself is left alone. */
    UNFREEZE_ALL,
    /** Nothing at all. */
    DO_NOTHING,
}

/** Which gesture a picker is binding a target for. */
enum class SlotTarget { CORNER_TAP, CORNER_SWIPE, QUICK_LEFT, QUICK_RIGHT }

/** What kind of target a picker lists. */
enum class SlotKind { APP, SHORTCUT }

/** Whether picking this action needs a target first — nothing is stored until one is picked. */
fun CornerAction.targetKind(): SlotKind? = when (this) {
    CornerAction.OPEN_APP -> SlotKind.APP
    CornerAction.OPEN_SHORTCUT -> SlotKind.SHORTCUT
    else -> null
}

/** When the A-Z strip shows on the home screen itself. */
enum class AzStripVisibility { NEVER, LANDSCAPE, ALWAYS }

/** How hard the launcher taps back: silent, or three strengths. */
enum class HapticFeedback { OFF, LIGHT, MEDIUM, STRONG }

/** Whether the freeze screen is showing user apps or system apps. */
enum class FreezeSourceFilter { USER, SYSTEM }

/** The second freeze-screen filter: everything, or only what is frozen, or only what is not. */
enum class FreezeStateFilter { ALL, FROZEN, UNFROZEN }

/** How the freeze screen orders what it shows. */
enum class FreezeSort {
    NAME_ASC,
    NAME_DESC,
    /** Newest install first. */
    INSTALLED,
    /** Most recently updated first. */
    UPDATED,
}

/** Whether this order needs each package's install and update times, which are read in one pass. */
fun FreezeSort.needsTimes(): Boolean = this == FreezeSort.INSTALLED || this == FreezeSort.UPDATED

private val Context.dataStore by preferencesDataStore(name = "deluge_prefs")

/** Bumped only if the shape of an exported file changes, so an old one can be refused. */
private const val EXPORT_FORMAT = 1

/** A minute of screen-off before the automatic freeze runs, until the user says otherwise. */
private const val DEFAULT_FREEZE_DELAY_SECONDS = 60

/** The backup's language entry, which lives outside the store — see exportJson. */
private const val PREF_LANGUAGE = "language"

/** Store keys that are modes, not settings, and must never travel in a backup. */
private val NEVER_EXPORT = setOf(
    "screenshot_mode",
    // Records of what this device has already asked the release page and been told. A setting
    // moved to another phone says nothing about that phone's build, so restoring them there
    // would suppress an update prompt that is still owed.
    "last_update_check",
    "last_update_check_failed",
    "update_known_version",
    "update_prompted_version",
    // Which apps this device has frozen is a fact about this device, not a preference:
    // restored elsewhere it would grey out apps that were never touched.
    "freeze_methods",
    // Which of them are frozen right now is even more of a device fact.
    "freeze_applied",
)

/** [json] to write out, and whether anything belonging to the private space was left out of it. */
data class ExportResult(val json: String, val omittedPrivateSpace: Boolean)

class Prefs(private val context: Context) {

    // The settings whose values are app keys take their names from PrivateSpaceExport, which is
    // what has to recognise every one of them by name to keep a locked space out of a backup. A
    // name spelled out in both places is a name that can be changed in one of them.
    private object Keys {
        val HIDDEN_APPS = stringSetPreferencesKey(PREF_HIDDEN_APPS)
        val FREQUENT_EXCLUDED = stringSetPreferencesKey(PREF_FREQUENT_EXCLUDED)
        val FAVORITES = stringPreferencesKey(PREF_FAVORITES)
        val FOLDERS = stringPreferencesKey(PREF_FOLDERS)
        val NAME_OVERRIDES = stringPreferencesKey(PREF_NAME_OVERRIDES)
        val ICON_OVERRIDES = stringPreferencesKey(PREF_ICON_OVERRIDES)
        val ICON_SIZE_DP = intPreferencesKey("icon_size_dp")
        val LABEL_SIZE_SP = intPreferencesKey("label_size_sp")
        val ITEM_SPACING_DP = intPreferencesKey("item_spacing_dp")
        val SIDE_PADDING_DP = intPreferencesKey("side_padding_dp")
        val NOW_PLAYING_HEIGHT_DP = intPreferencesKey("now_playing_height_dp")
        val NOW_PLAYING_PAD_TOP = intPreferencesKey("now_playing_pad_top")
        val NOW_PLAYING_PAD_BOTTOM = intPreferencesKey("now_playing_pad_bottom")
        val WIDGET_PAD_TOP = intPreferencesKey("widget_pad_top")
        val WIDGET_PAD_BOTTOM = intPreferencesKey("widget_pad_bottom")
        val FAVORITES_PAD_TOP = intPreferencesKey("favorites_pad_top")
        val FAVORITES_PAD_BOTTOM = intPreferencesKey("favorites_pad_bottom")
        val FONT = stringPreferencesKey("font")
        val COLOR_SCHEME = stringPreferencesKey("color_scheme")
        val FONT_FILE = stringPreferencesKey("font_file")
        val TEXT_COLOR_CUSTOM = intPreferencesKey("text_color_custom")
        val DIM_COLOR = intPreferencesKey("dim_color")
        val ALLOW_ROTATION = booleanPreferencesKey("allow_rotation")
        val SWIPE_FOR_SHORTCUTS = booleanPreferencesKey("swipe_for_shortcuts")
        val SHORTCUT_SWIPE = stringPreferencesKey("shortcut_swipe")
        val THEMED_ICONS = booleanPreferencesKey("themed_icons")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val HIDE_STATUS_BAR = booleanPreferencesKey("hide_status_bar")
        val HIDE_STATUS_BAR_APPLIST = booleanPreferencesKey("hide_status_bar_applist")
        val DIM_WALLPAPER_ALPHA = floatPreferencesKey("dim_wallpaper_alpha")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val DIM_HOME_ALPHA = floatPreferencesKey("dim_home_alpha")
        val WALLPAPER_BLUR_DP = intPreferencesKey("wallpaper_blur_dp")
        val SHOW_FAVORITE_LABELS = booleanPreferencesKey("show_favorite_labels")
        val TEXT_COLOR_MODE = stringPreferencesKey("text_color_mode")
        val DOUBLE_TAP_TO_LOCK = booleanPreferencesKey("double_tap_to_lock")
        val EDGE_SIDE = stringPreferencesKey("edge_side")
        val ALWAYS_SHOW_AZ = booleanPreferencesKey("always_show_az")
        val AZ_STRIP_VISIBILITY = stringPreferencesKey("az_strip_visibility")
        val HAPTIC_FEEDBACK = stringPreferencesKey("haptic_feedback")
        val HOME_TWO_COLUMN_PORTRAIT = booleanPreferencesKey("home_two_column_portrait")
        val HOME_TWO_COLUMN_LANDSCAPE = booleanPreferencesKey("home_two_column_landscape")
        val LIST_TWO_COLUMN_PORTRAIT = booleanPreferencesKey("list_two_column_portrait")
        val LIST_TWO_COLUMN_LANDSCAPE = booleanPreferencesKey("list_two_column_landscape")
        val SHOW_ALPHABET = booleanPreferencesKey("show_alphabet")
        val ALIGN_RIGHT = booleanPreferencesKey("align_right")
        val ICON_PACK_PACKAGE = stringPreferencesKey("icon_pack_package")
        val NOW_PLAYING_ENABLED = booleanPreferencesKey("now_playing_enabled")
        val WIDGET_ID = intPreferencesKey("widget_id")
        val WIDGET_POSITION = intPreferencesKey("widget_position")
        val WIDGET_HEIGHT_DP = intPreferencesKey("widget_height_dp")
        val DEFAULT_PROMPT_DISMISSED = booleanPreferencesKey("default_prompt_dismissed")
        val WIDGET_IDS = stringPreferencesKey("widget_ids")
        val WIDGET_PROVIDERS = stringPreferencesKey("widget_providers")
        val WIDGET_SIDE_PADDING_DP = intPreferencesKey("widget_side_padding_dp")
        val WIDGET_OFFSET_X_DP = intPreferencesKey("widget_offset_x_dp")
        val SWIPE_UP_OPENS_LIST = booleanPreferencesKey("swipe_up_opens_list")
        val APPLIST_SEARCH_ENABLED = booleanPreferencesKey("applist_search_enabled")
        val APPLIST_SEARCH_BOTTOM = booleanPreferencesKey("applist_search_bottom")
        val APPLIST_SEARCH_HIDDEN = booleanPreferencesKey("applist_search_hidden")
        val SORT_BY_USAGE = booleanPreferencesKey("sort_by_usage")
        val LAUNCH_COUNTS = stringPreferencesKey(PREF_LAUNCH_COUNTS)
        val EDGE_ZONE_WIDTH_DP = intPreferencesKey("edge_zone_width_dp")
        val EDGE_ZONE_INSET_DP = intPreferencesKey("edge_zone_inset_dp")
        val EDGE_ZONE_INSET_AUTO = booleanPreferencesKey("edge_zone_inset_auto")
        val QUICK_LAUNCH_LEFT = stringPreferencesKey(PREF_QUICK_LAUNCH_LEFT)
        val QUICK_LAUNCH_RIGHT = stringPreferencesKey(PREF_QUICK_LAUNCH_RIGHT)
        val QUICK_LAUNCH_LEFT_ACTION = stringPreferencesKey("quick_launch_left_action")
        val QUICK_LAUNCH_RIGHT_ACTION = stringPreferencesKey("quick_launch_right_action")
        val LAYOUT_DEFAULTS_VERSION = intPreferencesKey("layout_defaults_version")
        val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
        val DIM_OTHERS_SCRUBBING = booleanPreferencesKey("dim_others_scrubbing")
        val MARK_WORK_APPS = booleanPreferencesKey("mark_work_apps")
        val TEXT_SHADOW = booleanPreferencesKey("text_shadow")
        val TEXT_SHADOW_STRENGTH = intPreferencesKey("text_shadow_strength")
        val COMPACT_TAP_AREA = booleanPreferencesKey("compact_tap_area")
        val STAY_AFTER_PRIVATE_UNLOCK = booleanPreferencesKey("stay_after_private_unlock")
        val PRIVATE_SPACE_SECTION = booleanPreferencesKey("private_space_section")
        val RECENT_SECTION = booleanPreferencesKey("recent_section")
        val RECENT_SECTION_COUNT = intPreferencesKey("recent_section_count")
        val SHOW_APP_ICONS = booleanPreferencesKey("show_app_icons")
        val SHOW_FAVORITE_ICONS = booleanPreferencesKey("show_favorite_icons")
        val FAVORITES_SOURCE = stringPreferencesKey("favorites_source")
        val FREQUENT_COUNT = intPreferencesKey("frequent_count")
        val SHOW_LIST_HEADERS = booleanPreferencesKey("show_list_headers")
        val NOTIFICATION_BADGES = booleanPreferencesKey("notification_badges")
        val NOTIFICATION_PREVIEW = booleanPreferencesKey("notification_preview")
        val DIM_PAUSED_APPS = booleanPreferencesKey("dim_paused_apps")
        val SCREENSHOT_MODE = booleanPreferencesKey("screenshot_mode")
        val AUTO_DARK = booleanPreferencesKey("auto_dark")
        val NIGHT_START_HOUR = intPreferencesKey("night_start_hour")
        val NIGHT_END_HOUR = intPreferencesKey("night_end_hour")
        val PINCH_TO_RESIZE = booleanPreferencesKey("pinch_to_resize")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val AUTO_FREQUENT_TAG = booleanPreferencesKey("auto_frequent_tag")
        val AUTO_FREQUENT_WORDS = stringSetPreferencesKey("auto_frequent_words")
        val LOCKED_APPS = stringSetPreferencesKey(PREF_LOCKED_APPS)
        val APP_TAGS = stringPreferencesKey(PREF_APP_TAGS)
        val SECTION_TOP_PERCENT = intPreferencesKey("section_top_percent")
        val CLOSE_FOLDER_ON_LAUNCH = booleanPreferencesKey("close_folder_on_launch")
        val SHOW_SETTINGS_ROW = booleanPreferencesKey("show_settings_row")
        val CORNER_BUTTON_KEY = stringPreferencesKey(PREF_CORNER_BUTTON_KEY)
        val CORNER_SWIPE_KEY = stringPreferencesKey(PREF_CORNER_SWIPE_KEY)
        val CORNER_X_PORTRAIT = floatPreferencesKey("corner_x_portrait")
        val CORNER_Y_PORTRAIT = floatPreferencesKey("corner_y_portrait")
        val CORNER_X_LANDSCAPE = floatPreferencesKey("corner_x_landscape")
        val CORNER_Y_LANDSCAPE = floatPreferencesKey("corner_y_landscape")
        val CORNER_TAP_ACTION = stringPreferencesKey("corner_tap_action")
        val CORNER_SWIPE_ACTION = stringPreferencesKey("corner_swipe_action")
        val AUTO_KEYBOARD = booleanPreferencesKey("auto_keyboard")
        val LAST_LETTER_TO_LINE = booleanPreferencesKey("last_letter_to_line")
        val FAVORITES_EVER_SET = booleanPreferencesKey("favorites_ever_set")
        val EDGE_ZONE_BAND_ONLY = booleanPreferencesKey("edge_zone_band_only")
        val HOLD_SCROLL = booleanPreferencesKey("hold_scroll")
        val WEB_SEARCH_FALLBACK = booleanPreferencesKey("web_search_fallback")
        val SWIPE_UP_OPENS_SEARCH = booleanPreferencesKey("swipe_up_opens_search")
        val HOLD_SCROLL_SPEED = intPreferencesKey("hold_scroll_speed")
        val ALIGNMENT = stringPreferencesKey("alignment")
        val APPLIST_ALIGNMENT = stringPreferencesKey("applist_alignment")
        val ICON_SIDE = stringPreferencesKey("icon_side")
        val STATUS_BAR_PEEK_SECONDS = intPreferencesKey("status_bar_peek_seconds")
        val AZ_BAND_TOP_FRACTION = floatPreferencesKey("az_band_top_fraction")
        val AZ_BAND_HEIGHT_FRACTION = floatPreferencesKey("az_band_height_fraction")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val LAST_UPDATE_CHECK_FAILED = booleanPreferencesKey("last_update_check_failed")
        val UPDATE_KNOWN_VERSION = intPreferencesKey("update_known_version")
        val UPDATE_PROMPTED_VERSION = intPreferencesKey("update_prompted_version")
        val UPDATE_AUTO_CHECK = booleanPreferencesKey("update_auto_check")
        val FREEZE_ENABLED = booleanPreferencesKey("freeze_enabled")
        val FREEZE_METHODS = stringPreferencesKey("freeze_methods")
        val FREEZE_APPLIED = stringSetPreferencesKey("freeze_applied")
        val FREEZE_DIALOG_READY = booleanPreferencesKey("freeze_dialog_ready")
        val FREEZE_AUTO_ENABLED = booleanPreferencesKey("freeze_auto_enabled")
        val FREEZE_AUTO_DELAY_SECONDS = intPreferencesKey("freeze_auto_delay_seconds")
        val FREEZE_UNFREEZE_ON_UNLOCK = booleanPreferencesKey("freeze_unfreeze_on_unlock")
        val FREEZE_SYSTEM_APPS = booleanPreferencesKey("freeze_system_apps")
        val FREEZE_AUTO_SKIP_CHARGING = booleanPreferencesKey("freeze_auto_skip_charging")
        val FREEZE_AUTO_SKIP_FOREGROUND = booleanPreferencesKey("freeze_auto_skip_foreground")
        val FREEZE_AUTO_SKIP_NOTIFICATIONS = booleanPreferencesKey("freeze_auto_skip_notifications")
        val FREEZE_SOURCE_FILTER = stringPreferencesKey("freeze_source_filter")
        val FREEZE_STATE_FILTER = stringPreferencesKey("freeze_state_filter")
        val FREEZE_SORT = stringPreferencesKey("freeze_sort")
        val FREEZE_QUERY = stringPreferencesKey("freeze_query")
    }

    /** Every padding a user can set by hand; their presence is what retires the first-run layout. */
    private val padKeys = listOf(
        Keys.NOW_PLAYING_PAD_TOP,
        Keys.NOW_PLAYING_PAD_BOTTOM,
        Keys.WIDGET_PAD_TOP,
        Keys.WIDGET_PAD_BOTTOM,
        Keys.FAVORITES_PAD_TOP,
        Keys.FAVORITES_PAD_BOTTOM,
    )

    private val data get() = context.dataStore.data

    /**
     * Every stored setting as JSON, for moving a set-up to another phone or keeping it.
     *
     * Written from the store itself rather than a list of keys kept alongside it, because a
     * list like that is only right until the next setting is added and nobody remembers it.
     * The type travels with each value, since JSON cannot tell 1 from 1L from 1.0f and the
     * store very much can.
     *
     * A custom font is a path into this app's own storage, so it points at nothing on another
     * phone; the font falls back to the default there until one is picked again.
     *
     * [privateSerial] is the private space's profile serial, when there is one, so its apps —
     * and how often they were opened — never leave the device in a file the user can save
     * anywhere. Passed in rather than looked up here because resolving it needs Android types
     * this class otherwise avoids; the caller already has it from the same read that decided
     * what to show on screen.
     */
    suspend fun exportJson(stripOtherProfiles: Boolean): ExportResult {
        val stored = data.first()
        val values = JSONObject()
        var omittedPrivateSpace = false
        stored.asMap().forEach { (key, rawValue) ->
            // Modes, not settings: a screenshot disguise must never travel in a backup.
            if (key.name in NEVER_EXPORT) return@forEach
            val value = stripPrivateSpaceFromExport(key.name, rawValue, stripOtherProfiles)
            if (value == null) {
                omittedPrivateSpace = true
                return@forEach
            }
            if (value != rawValue) omittedPrivateSpace = true
            // A malformed JSON we could not parse comes back as-is rather than crashing — but
            // "as-is" may still hold another profile's keys, so the backup must say it omitted.
            if (value == rawValue && value is String && stripOtherProfiles &&
                (key.name == PREF_FOLDERS || key.name == PREF_NAME_OVERRIDES ||
                    key.name == PREF_ICON_OVERRIDES || key.name == PREF_LAUNCH_COUNTS ||
                    key.name == PREF_APP_TAGS) &&
                ((value.startsWith("{") && runCatching {
                    JSONObject(value)
                }.isFailure) || (value.startsWith("[") && runCatching {
                    JSONArray(value)
                }.isFailure))
            ) {
                omittedPrivateSpace = true
            }
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("type", "boolean").put("value", value)
                is Int -> entry.put("type", "int").put("value", value)
                is Long -> entry.put("type", "long").put("value", value)
                is Float -> entry.put("type", "float").put("value", value.toDouble())
                is String -> entry.put("type", "string").put("value", value)
                is Set<*> -> entry.put("type", "stringSet")
                    .put("value", JSONArray().apply { value.forEach { put(it.toString()) } })
                else -> return@forEach
            }
            values.put(key.name, entry)
        }
        // The language lives outside the store (it must read before anything can suspend), so
        // it travels as its own entry rather than as a key. Blank means follow the system.
        values.put(
            PREF_LANGUAGE,
            JSONObject().put("type", "string").put("value", LanguagePrefs.read(context).orEmpty()),
        )
        val json = JSONObject()
            .put("format", EXPORT_FORMAT)
            .put("app", "Deluge Launcher")
            .put("values", values)
            .toString(2)
        return ExportResult(json, omittedPrivateSpace)
    }

    /**
     * Replaces every setting with the ones in [text]. Returns false if it is not ours.
     *
     * Parsed fully before anything is written: a half-read file that had already cleared the
     * store would leave someone with neither their old set-up nor the one they were restoring.
     */
    suspend fun importJson(text: String): Boolean {
        // The picker's reader already stops at this size; this is the backstop for any other
        // caller, including a test or a future one that hands over a string directly.
        if (text.length > ImportLimits.MAX_BYTES) return false
        var language: String? = null
        var languagePresent = false
        val parsed = runCatching {
            val root = JSONObject(text)
            // Unknown future formats are read entry by entry rather than refused whole:
            // entries we understand restore, the rest are skipped below.
            if (root.optInt("format") < EXPORT_FORMAT) return false
            val values = root.getJSONObject("values")
            buildList {
                values.keys().forEach { name ->
                    val entry = runCatching { values.getJSONObject(name) }.getOrNull() ?: return@forEach
                    // The language is not a store key; it goes back to its own file below.
                    // Absent in old backups, which simply keep the language they have.
                    // Present-but-blank means follow the system — tracked separately so it
                    // is not mistaken for absent.
                    if (name == PREF_LANGUAGE && entry.optString("type") == "string") {
                        language = entry.optString("value")
                        languagePresent = true
                        return@forEach
                    }
                    // Modes, not settings: a hand-edited backup must not smuggle back what
                    // export refuses to carry (screenshot_mode and friends).
                    if (name in NEVER_EXPORT) return@forEach
                    // Pair(...) rather than `to`: DataStore has an infix `to` of its own on
                    // Key, which builds a Preferences.Pair and not the one wanted here.
                    // One bad entry skips itself rather than rejecting the whole backup.
                    val pair: Pair<Preferences.Key<*>, Any> = runCatching {
                        when (entry.getString("type")) {
                            "boolean" -> Pair(booleanPreferencesKey(name), entry.getBoolean("value"))
                            "int" -> Pair(intPreferencesKey(name), entry.getInt("value"))
                            "long" -> Pair(longPreferencesKey(name), entry.getLong("value"))
                            "float" -> Pair(floatPreferencesKey(name), entry.getDouble("value").toFloat())
                            "string" -> Pair(stringPreferencesKey(name), entry.getString("value"))
                            "stringSet" -> {
                                val array = entry.getJSONArray("value")
                                if (array.length() > ImportLimits.MAX_SET_ITEMS) return@forEach
                                Pair(
                                    stringSetPreferencesKey(name),
                                    (0 until array.length()).map { array.getString(it) }.toSet(),
                                )
                            }
                            else -> return@forEach
                        }
                    }.getOrNull() ?: return@forEach
                    val safe = when (val value = pair.second) {
                        is Int -> ImportLimits.int(name, value) ?: return@forEach
                        is Float -> ImportLimits.float(name, value) ?: return@forEach
                        is String -> ImportLimits.string(value) ?: return@forEach
                        is Set<*> -> ImportLimits.stringSet(value.filterIsInstance<String>().toSet())
                            ?: return@forEach
                        else -> value
                    }
                    // A hand-edited backup can smuggle a shortcut id carrying a newline, which
                    // would corrupt the whole favorites list on the way back in. The pin flow
                    // refuses such ids; import does the same rather than storing them.
                    if (name == PREF_FAVORITES && safe is String) {
                        val lines = safe.split("\n").filter { line ->
                            if (!EntryKeys.isShortcut(line)) true
                            else EntryKeys.parseShortcut(line)?.let {
                                EntryKeys.isStorableShortcutId(it.shortcutId)
                            } == true
                        }
                        add(Pair(pair.first, lines.joinToString("\n")))
                    } else {
                        add(Pair(pair.first, safe))
                    }
                }
            }
        }.getOrNull() ?: return false

        context.dataStore.edit { store ->
            // Device facts and modes are not carried in a backup, but they are also not
            // something a restore should wipe: which apps are frozen, which version this
            // build last checked, whether the screenshot disguise is on. They are read out
            // here and put back, so restoring settings does not strand a frozen app with no
            // record of it or pretend a fresh install on a phone that has been running.
            val kept = store.asMap().entries
                .filter { (key, _) -> key.name in NEVER_EXPORT }
                .map { (key, value) -> Pair(key as Preferences.Key<Any>, value) }
            store.clear()
            kept.forEach { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                store[key as Preferences.Key<Any>] = value
            }
            parsed.forEach { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                store[key as Preferences.Key<Any>] = value
            }
        }
        // A cleared store reads as a fresh install; stamp it as an established one so the
        // first-run defaults (welcome, auto clock widget) do not fire on imported data.
        // Language kept only when the backup carried it — see above.
        ensureInstallMarker()
        // Applied on the next recreate, like a language picked by hand. Absent means the
        // backup predates the language entry: keep whatever language is set now. Blank
        // means follow the system, which LanguagePrefs.write maps back to removing the key.
        if (languagePresent) LanguagePrefs.write(context, language?.takeIf { it.isNotBlank() })
        return true
    }

    /** Favorites are stored as one newline-joined string; these are the only two readers. */
    private fun readFavorites(pref: Preferences): List<String> =
        pref[Keys.FAVORITES]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    private fun MutablePreferences.writeFavorites(list: List<String>) {
        this[Keys.FAVORITES] = list.joinToString("\n")
    }

    /**
     * Absent means this install predates multiple widgets, so the lone [Keys.WIDGET_ID] is
     * promoted. An empty string is different: it means every widget was removed, and must not
     * resurrect that old key.
     */
    private fun readWidgetIds(pref: Preferences): List<Int> {
        val raw = pref[Keys.WIDGET_IDS]
            ?: return listOfNotNull(pref[Keys.WIDGET_ID]?.takeIf { it > 0 })
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
    }

    private fun MutablePreferences.writeWidgetIds(ids: List<Int>) {
        this[Keys.WIDGET_IDS] = ids.joinToString(",")
        // Mirrored so a downgrade to a single-widget build still finds one.
        this[Keys.WIDGET_ID] = ids.firstOrNull() ?: -1
    }

    val hiddenApps: Flow<Set<String>> =
        data.map { it[Keys.HIDDEN_APPS] ?: emptySet() }.distinctUntilChanged()

    /** Package name to the method chosen for it. Empty when the list is empty. */
    /**
     * Whether app freezing is offered at all.
     *
     * Turning it off is not just a hidden switch: the manager lifts every frozen app and
     * empties the list first, so no app is left disabled with no screen to bring it back.
     * On by default for installations that already have the feature.
     */
    val freezeEnabled: Flow<Boolean> =
        data.map { it[Keys.FREEZE_ENABLED] ?: true }.distinctUntilChanged()

    suspend fun freezeEnabledNow(): Boolean = freezeEnabled.first()

    val freezeMethods: Flow<Map<String, FreezeMethod>> = data.map { pref ->
        jsonToMap(pref[Keys.FREEZE_METHODS]).mapNotNull { (packageName, name) ->
            val method = runCatching { FreezeMethod.valueOf(name) }.getOrNull()
                ?: return@mapNotNull null
            packageName to method
        }.toMap()
    }.distinctUntilChanged()

    suspend fun freezeMethodsNow(): Map<String, FreezeMethod> = freezeMethods.first()

    /**
     * Packages from the list that are frozen at the moment.
     *
     * Choosing an app and freezing it are two steps: the list is what the user wants, this is
     * what has actually been applied, and a trigger — Freeze all, the corner button, the
     * automatic freeze — moves the whole list into it. Unfreezing takes an app back out and
     * leaves it in the list, which is what makes the next trigger put it back.
     */
    val freezeApplied: Flow<Set<String>> =
        data.map { it[Keys.FREEZE_APPLIED] ?: emptySet() }.distinctUntilChanged()

    suspend fun freezeAppliedNow(): Set<String> = freezeApplied.first()

    /** Whether the apps paused before the pause dialog existed have been given it. */
    val freezeDialogReady: Flow<Boolean> =
        data.map { it[Keys.FREEZE_DIALOG_READY] ?: false }.distinctUntilChanged()

    suspend fun freezeDialogReadyNow(): Boolean = freezeDialogReady.first()

    val freezeAutoEnabled: Flow<Boolean> =
        data.map { it[Keys.FREEZE_AUTO_ENABLED] ?: false }.distinctUntilChanged()

    /** Seconds the screen must stay off before the automatic freeze runs. */
    val freezeAutoDelaySeconds: Flow<Int> =
        data.map { it[Keys.FREEZE_AUTO_DELAY_SECONDS] ?: DEFAULT_FREEZE_DELAY_SECONDS }
            .distinctUntilChanged()

    // On by default: an app frozen for the lock screen has to be usable again the moment the
    // screen is back, or freezing looks like the launcher broke it.
    val freezeUnfreezeOnUnlock: Flow<Boolean> =
        data.map { it[Keys.FREEZE_UNFREEZE_ON_UNLOCK] ?: true }.distinctUntilChanged()

    val freezeSystemApps: Flow<Boolean> =
        data.map { it[Keys.FREEZE_SYSTEM_APPS] ?: false }.distinctUntilChanged()

    suspend fun freezeSystemAppsNow(): Boolean = freezeSystemApps.first()

    val freezeAutoSkipCharging: Flow<Boolean> =
        data.map { it[Keys.FREEZE_AUTO_SKIP_CHARGING] ?: true }.distinctUntilChanged()

    suspend fun freezeAutoSkipChargingNow(): Boolean = freezeAutoSkipCharging.first()

    val freezeAutoSkipForeground: Flow<Boolean> =
        data.map { it[Keys.FREEZE_AUTO_SKIP_FOREGROUND] ?: true }.distinctUntilChanged()

    suspend fun freezeAutoSkipForegroundNow(): Boolean = freezeAutoSkipForeground.first()

    val freezeAutoSkipNotifications: Flow<Boolean> =
        data.map { it[Keys.FREEZE_AUTO_SKIP_NOTIFICATIONS] ?: true }.distinctUntilChanged()

    suspend fun freezeAutoSkipNotificationsNow(): Boolean = freezeAutoSkipNotifications.first()

    /** Filters, order and query of the freeze screen: view state, but backed up like a setting. */
    val freezeSourceFilter: Flow<FreezeSourceFilter> =
        readEnum(Keys.FREEZE_SOURCE_FILTER, FreezeSourceFilter.USER)

    val freezeStateFilter: Flow<FreezeStateFilter> =
        readEnum(Keys.FREEZE_STATE_FILTER, FreezeStateFilter.ALL)

    val freezeSort: Flow<FreezeSort> = readEnum(Keys.FREEZE_SORT, FreezeSort.NAME_ASC)

    val freezeQuery: Flow<String> =
        data.map { it[Keys.FREEZE_QUERY] ?: "" }.distinctUntilChanged()

    val favorites: Flow<List<String>> =
        data.map { pref -> readFavorites(pref) }.distinctUntilChanged()

    val folders: Flow<List<Folder>> =
        data.map { pref -> foldersFromJson(pref[Keys.FOLDERS]) }.distinctUntilChanged()

    val nameOverrides: Flow<Map<String, String>> =
        data.map { pref -> jsonToMap(pref[Keys.NAME_OVERRIDES]) }.distinctUntilChanged()

    val iconOverrides: Flow<Map<String, String>> =
        data.map { pref -> jsonToMap(pref[Keys.ICON_OVERRIDES]) }.distinctUntilChanged()

    val iconSizeDp: Flow<Int> = data.map { it[Keys.ICON_SIZE_DP] ?: 56 }.distinctUntilChanged()

    val labelSizeSp: Flow<Int> = data.map { it[Keys.LABEL_SIZE_SP] ?: 16 }.distinctUntilChanged()

    /** Vertical gap between favorite rows. */
    val itemSpacingDp: Flow<Int> = data.map { it[Keys.ITEM_SPACING_DP] ?: 10 }.distinctUntilChanged()

    /** Left/right inset applied to every element on the home screen, so they stay in line. */
    val sidePaddingDp: Flow<Int> = data.map { it[Keys.SIDE_PADDING_DP] ?: 20 }.distinctUntilChanged()

    val nowPlayingHeightDp: Flow<Int> = data.map { it[Keys.NOW_PLAYING_HEIGHT_DP] ?: 64 }.distinctUntilChanged()

    /** Draggable top/bottom padding for each home block, set in edit mode. */
    val homePaddings: Flow<HomePaddings> = data.map {
        HomePaddings(
            nowPlayingTop = it[Keys.NOW_PLAYING_PAD_TOP] ?: 8,
            nowPlayingBottom = it[Keys.NOW_PLAYING_PAD_BOTTOM] ?: 8,
            widgetTop = it[Keys.WIDGET_PAD_TOP] ?: 8,
            widgetBottom = it[Keys.WIDGET_PAD_BOTTOM] ?: 8,
            favoritesTop = it[Keys.FAVORITES_PAD_TOP] ?: 8,
            favoritesBottom = it[Keys.FAVORITES_PAD_BOTTOM] ?: 24,
        )
    }.distinctUntilChanged()

    /** Absolute path of the typeface the user supplied, once it has been copied in. */
    val fontFile: Flow<String?> = data.map { it[Keys.FONT_FILE] }.distinctUntilChanged()

    /**
     * Whether swiping a row sideways offers the shortcuts its app publishes.
     *
     * On by default, since it is the quick way to reach them and the only one — they are not
     * in the long-press menu, which is for setting a row up rather than using it. Off is for
     * anyone who would rather their rows answered only to a tap.
     */
    /**
     * Falls back to the switch this replaced, so anyone who had turned it off stays off and
     * everyone else keeps the direction it always had.
     */
    val shortcutSwipe: Flow<ShortcutSwipe> =
        data.map { prefs ->
            prefs[Keys.SHORTCUT_SWIPE]
                ?.let { name -> runCatching { ShortcutSwipe.valueOf(name) }.getOrNull() }
                ?: if (prefs[Keys.SWIPE_FOR_SHORTCUTS] == false) ShortcutSwipe.OFF else ShortcutSwipe.RIGHT
        }.distinctUntilChanged()

    /**
     * Whether the launcher turns with the device.
     *
     * Null until it is set, which leaves the decision to the screen: a tablet has the height
     * for this sideways and a phone does not. Someone who wants it anyway — a phone in a car
     * mount is the case that came up — can say so.
     */
    val allowRotation: Flow<Boolean?> = data.map { it[Keys.ALLOW_ROTATION] }.distinctUntilChanged()

    /**
     * What the wallpaper is dimmed with, as an opaque RGB; the dim sliders set how much of it
     * lands. Black until the user picks otherwise, which is what it always was.
     */
    val dimColor: Flow<Int> =
        data.map { it[Keys.DIM_COLOR] ?: 0xFF000000.toInt() }.distinctUntilChanged()

    /** Used when the text color mode is CUSTOM. Opaque white until the user picks something. */
    val textColorCustom: Flow<Int> =
        data.map { it[Keys.TEXT_COLOR_CUSTOM] ?: 0xFFFFFFFF.toInt() }.distinctUntilChanged()

    /**
     * Draws the monochrome layer Android 13 added to adaptive icons, tinted to the system
     * palette, instead of the app's own colors. Apps that ship no such layer keep their
     * ordinary icon — there is nothing to derive one from.
     */
    val themedIcons: Flow<Boolean> = data.map { it[Keys.THEMED_ICONS] ?: false }.distinctUntilChanged()

    val iconShape: Flow<IconShape> = data.map { pref ->
        pref[Keys.ICON_SHAPE]?.let { runCatching { IconShape.valueOf(it) }.getOrNull() } ?: IconShape.SYSTEM
    }.distinctUntilChanged()

    val font: Flow<AppFont> = data.map {
        runCatching { AppFont.valueOf(it[Keys.FONT] ?: AppFont.SYSTEM.name) }.getOrDefault(AppFont.SYSTEM)
    }.distinctUntilChanged()

    /** Deluge blue until told otherwise; DYNAMIC follows the wallpaper on Android 12+. */
    val colorSchemeChoice: Flow<ColorSchemeChoice> = data.map {
        runCatching {
            ColorSchemeChoice.valueOf(it[Keys.COLOR_SCHEME] ?: ColorSchemeChoice.DELUGE_BLUE.name)
        }.getOrDefault(ColorSchemeChoice.DELUGE_BLUE)
    }.distinctUntilChanged()

    val hideStatusBar: Flow<Boolean> = data.map { it[Keys.HIDE_STATUS_BAR] ?: false }.distinctUntilChanged()

    /**
     * The same choice for the app list, which used to follow the home screen's.
     *
     * Falls back to it until set, so nobody's status bar appears where it did not before.
     */
    val hideStatusBarAppList: Flow<Boolean> =
        data.map { it[Keys.HIDE_STATUS_BAR_APPLIST] ?: it[Keys.HIDE_STATUS_BAR] ?: false }
            .distinctUntilChanged()

    val dimWallpaperAlpha: Flow<Float> = data.map { it[Keys.DIM_WALLPAPER_ALPHA] ?: 0.35f }.distinctUntilChanged()

    /** How far the wallpaper behind the window is blurred, in dp. Zero is off. */
    val wallpaperBlurDp: Flow<Int> =
        data.map { (it[Keys.WALLPAPER_BLUR_DP] ?: 0).coerceIn(0, 80) }.distinctUntilChanged()

    /**
     * How hard the launcher taps back. Reads the old on/off answer when this has not been
     * set, so nobody's buzz appears or disappears on update.
     */
    val hapticFeedback: Flow<HapticFeedback> = data.map { pref ->
        pref[Keys.HAPTIC_FEEDBACK]?.let { runCatching { HapticFeedback.valueOf(it) }.getOrNull() }
            ?: if (pref[Keys.HAPTICS_ENABLED] == false) HapticFeedback.OFF else HapticFeedback.MEDIUM
    }.distinctUntilChanged()

    val dimHomeAlpha: Flow<Float> = data.map { it[Keys.DIM_HOME_ALPHA] ?: 0f }.distinctUntilChanged()

    val showFavoriteLabels: Flow<Boolean> =
        data.map { it[Keys.SHOW_FAVORITE_LABELS] ?: true }.distinctUntilChanged()

    val textColorMode: Flow<TextColorMode> = data.map {
        runCatching { TextColorMode.valueOf(it[Keys.TEXT_COLOR_MODE] ?: TextColorMode.AUTO.name) }
            .getOrDefault(TextColorMode.AUTO)
    }.distinctUntilChanged()

    val doubleTapToLock: Flow<Boolean> =
        data.map { it[Keys.DOUBLE_TAP_TO_LOCK] ?: false }.distinctUntilChanged()

    val edgeSide: Flow<EdgeSide> = data.map {
        runCatching { EdgeSide.valueOf(it[Keys.EDGE_SIDE] ?: EdgeSide.BOTH.name) }.getOrDefault(EdgeSide.BOTH)
    }.distinctUntilChanged()

    /** Keep the A-Z strip on screen even when the app list is closed. */
    /**
     * When the strip sits on the home screen with no app list open.
     *
     * Reads the old on/off answer when this has not been set, so nobody's strip appears or
     * disappears on update. LANDSCAPE is for a phone in a car mount, where the strip earns
     * the room it takes and upright it does not.
     */
    val azStripVisibility: Flow<AzStripVisibility> = data.map { pref ->
        pref[Keys.AZ_STRIP_VISIBILITY]?.let { runCatching { AzStripVisibility.valueOf(it) }.getOrNull() }
            ?: if (pref[Keys.ALWAYS_SHOW_AZ] == true) AzStripVisibility.ALWAYS else AzStripVisibility.NEVER
    }.distinctUntilChanged()

    /** The A-Z strip inside the app list; the edge gesture still works without it. */
    val showAlphabet: Flow<Boolean> = data.map { it[Keys.SHOW_ALPHABET] ?: true }.distinctUntilChanged()

    /** Two favorites per home row, upright and sideways separately. */
    val homeTwoColumnPortrait: Flow<Boolean> =
        data.map { it[Keys.HOME_TWO_COLUMN_PORTRAIT] ?: false }.distinctUntilChanged()
    val homeTwoColumnLandscape: Flow<Boolean> =
        data.map { it[Keys.HOME_TWO_COLUMN_LANDSCAPE] ?: true }.distinctUntilChanged()

    /** Two apps per list row, upright and sideways separately. */
    val listTwoColumnPortrait: Flow<Boolean> =
        data.map { it[Keys.LIST_TWO_COLUMN_PORTRAIT] ?: false }.distinctUntilChanged()
    val listTwoColumnLandscape: Flow<Boolean> =
        data.map { it[Keys.LIST_TWO_COLUMN_LANDSCAPE] ?: true }.distinctUntilChanged()

    /** Lay icons and labels out from the right edge instead of the left. */
    val alignRight: Flow<Boolean> = data.map { it[Keys.ALIGN_RIGHT] ?: false }.distinctUntilChanged()

    val iconPackPackage: Flow<String?> = data.map { it[Keys.ICON_PACK_PACKAGE] }.distinctUntilChanged()

    val nowPlayingEnabled: Flow<Boolean> = data.map { it[Keys.NOW_PLAYING_ENABLED] ?: false }.distinctUntilChanged()

    val widgetId: Flow<Int> = data.map { it[Keys.WIDGET_ID] ?: -1 }.distinctUntilChanged()
    /** Index into the merged (favorites + widget) home list where the widget sits. 0 = top. */
    val widgetPosition: Flow<Int> = data.map { it[Keys.WIDGET_POSITION] ?: 0 }.distinctUntilChanged()
    val widgetHeightDp: Flow<Int> = data.map { it[Keys.WIDGET_HEIGHT_DP] ?: 180 }.distinctUntilChanged()

    val widgetIds: Flow<List<Int>> = data.map { readWidgetIds(it) }.distinctUntilChanged()

    /**
     * Whether the launcher's own "not the default home" prompt has been dismissed.
     *
     * Never cleared by anything: the prompt exists to tell someone who does not know, and
     * someone who has said "skip" knows. Asking again on every launch is the behaviour this
     * flag is here to avoid.
     */
    val defaultPromptDismissed: Flow<Boolean> =
        data.map { it[Keys.DEFAULT_PROMPT_DISMISSED] ?: false }.distinctUntilChanged()

    suspend fun dismissDefaultPrompt() {
        context.dataStore.edit { it[Keys.DEFAULT_PROMPT_DISMISSED] = true }
    }

    /** When the release page was last asked, or 0 before this device has ever asked. */
    val lastUpdateCheck: Flow<Long> =
        data.map { it[Keys.LAST_UPDATE_CHECK] ?: 0L }.distinctUntilChanged()

    /**
     * Whether that last ask came back with nothing.
     *
     * What the interval is measured against: a check that answered is not worth repeating for
     * hours, one that did not is worth repeating the next time the launcher is opened.
     */
    val lastUpdateCheckFailed: Flow<Boolean> =
        data.map { it[Keys.LAST_UPDATE_CHECK_FAILED] ?: false }.distinctUntilChanged()

    /**
     * The newest release any check has seen, or 0 while none has answered.
     *
     * Kept rather than only held in memory: a launcher is restarted often, and a check is
     * throttled, so without this the knowledge that an update exists would be forgotten
     * between launches and the row would go back to saying nothing.
     */
    val updateKnownVersion: Flow<Int> =
        data.map { it[Keys.UPDATE_KNOWN_VERSION] ?: 0 }.distinctUntilChanged()

    /** The newest release the user has already been told about, so it is only said once. */
    val updatePromptedVersion: Flow<Int> =
        data.map { it[Keys.UPDATE_PROMPTED_VERSION] ?: 0 }.distinctUntilChanged()

    /**
     * Whether the release page may be asked on its own, without being asked first.
     *
     * **Off by default.** The manual "check for updates" in Settings always works; this only
     * decides whether the launcher may make that request by itself at startup. A home screen
     * that reads a network page unasked is one more thing its user has to trust, and a version
     * check nobody opted into is what F-Droid labels as tracking — so the request happens only
     * after someone turns this on. The check itself reads the public release page and sends
     * nothing else; see [UpdateChecker].
     */
    val updateAutoCheck: Flow<Boolean> =
        data.map { it[Keys.UPDATE_AUTO_CHECK] ?: false }.distinctUntilChanged()

    suspend fun setUpdateAutoCheck(value: Boolean) {
        context.dataStore.edit { it[Keys.UPDATE_AUTO_CHECK] = value }
    }

    /**
     * Records that a check ran just now, and what it saw.
     *
     * The timestamp is written whether or not the check answered, so an unreachable release
     * page is not retried on every one of the many times a launcher is opened in a day; [failed]
     * is what shortens the wait to the next one, so a phone that was offline or blocked for a
     * minute is not stuck saying so for the rest of the day. The version only ever moves
     * forward: a check that saw an older one — the answer to a comparison that is still "up to
     * date" — must not erase what a previous one found.
     */
    suspend fun recordUpdateCheck(knownVersion: Int, nowMillis: Long, failed: Boolean) {
        context.dataStore.edit { pref ->
            pref[Keys.LAST_UPDATE_CHECK] = nowMillis
            pref[Keys.LAST_UPDATE_CHECK_FAILED] = failed
            val seen = maxOf(pref[Keys.UPDATE_KNOWN_VERSION] ?: 0, knownVersion)
            if (seen > 0) pref[Keys.UPDATE_KNOWN_VERSION] = seen
        }
    }

    suspend fun markUpdatePrompted(version: Int) {
        context.dataStore.edit { it[Keys.UPDATE_PROMPTED_VERSION] = version }
    }

    /**
     * The widget's own left/right inset. Absent means it has never been set apart, so it
     * follows the favorites and nothing moves the first time this key appears.
     */
    val widgetSidePaddingDp: Flow<Int> =
        data.map { it[Keys.WIDGET_SIDE_PADDING_DP] ?: it[Keys.SIDE_PADDING_DP] ?: 20 }.distinctUntilChanged()

    /**
     * How far the widget is shifted sideways, negative left and positive right.
     *
     * A third-party widget lays out its own contents and most clocks center theirs, which
     * nothing out here can reach inside. Moving the whole widget is what moves them. Side
     * padding already sets how wide it is; this is only where that width sits.
     */
    val widgetOffsetXDp: Flow<Int> =
        data.map { (it[Keys.WIDGET_OFFSET_X_DP] ?: 0).coerceIn(-200, 200) }.distinctUntilChanged()

    /** Opening the app list by swiping up from the home screen. */
    val swipeUpOpensList: Flow<Boolean> =
        data.map { it[Keys.SWIPE_UP_OPENS_LIST] ?: false }.distinctUntilChanged()

    val appListSearchEnabled: Flow<Boolean> =
        data.map { it[Keys.APPLIST_SEARCH_ENABLED] ?: false }.distinctUntilChanged()

    /** Within thumb reach on a tall phone, rather than up by the status bar. */
    val appListSearchBottom: Flow<Boolean> =
        data.map { it[Keys.APPLIST_SEARCH_BOTTOM] ?: false }.distinctUntilChanged()

    /**
     * Lets a search reach apps hidden from the list.
     *
     * Off by default: hidden means hidden, and someone who hid an app did not ask for it back.
     */
    val appListSearchHidden: Flow<Boolean> =
        data.map { it[Keys.APPLIST_SEARCH_HIDDEN] ?: false }.distinctUntilChanged()

    /** Orders each letter's apps by how often they were opened from here, rather than by name. */
    val sortByUsage: Flow<Boolean> = data.map { it[Keys.SORT_BY_USAGE] ?: false }.distinctUntilChanged()

    val launchCounts: Flow<Map<String, Int>> = data.map { pref ->
        jsonToMap(pref[Keys.LAUNCH_COUNTS]).mapValues { (_, v) -> v.toIntOrNull() ?: 0 }
    }.distinctUntilChanged()

    /** Width of the invisible strip at each screen edge that opens the app list. */
    val edgeZoneWidthDp: Flow<Int> = data.map { it[Keys.EDGE_ZONE_WIDTH_DP] ?: 40 }.distinctUntilChanged()

    /**
     * How much of the very edge is left to the system rather than taken by the strip.
     *
     * Android's back gesture starts at the screen edge and so does this strip, so on a phone
     * with gesture navigation the two are reaching for the same pixels. Giving the outermost
     * few back means a swipe that starts right at the edge goes back and one that starts just
     * inside it scrubs.
     *
     * Zero by default: on a phone with buttons there is nothing to share with, and anyone the
     * conflict bothers can say so.
     */
    /**
     * Whether the room left for the back gesture follows the system's own gesture area.
     *
     * Android says exactly how far in from each edge its back gesture reaches, and says zero
     * when navigation is by buttons — so following it gives a phone with gesture navigation
     * the room it needs and changes nothing for one without. On unless someone has already
     * set the gap by hand, in which case their number stands.
     */
    val edgeZoneInsetAuto: Flow<Boolean> =
        data.map { it[Keys.EDGE_ZONE_INSET_AUTO] ?: (it[Keys.EDGE_ZONE_INSET_DP] == null) }
            .distinctUntilChanged()

    val edgeZoneInsetDp: Flow<Int> =
        data.map { (it[Keys.EDGE_ZONE_INSET_DP] ?: 0).coerceIn(EDGE_ZONE_INSET_RANGE) }
            .distinctUntilChanged()

    val quickLaunchLeft: Flow<String?> = data.map { it[Keys.QUICK_LAUNCH_LEFT] }.distinctUntilChanged()

    val quickLaunchRight: Flow<String?> = data.map { it[Keys.QUICK_LAUNCH_RIGHT] }.distinctUntilChanged()

    /** What a swipe under the favorites does, on each side. Same list the button offers. */
    val quickLaunchLeftAction: Flow<CornerAction> = readCornerAction(
        Keys.QUICK_LAUNCH_LEFT_ACTION, CornerAction.OPEN_APP,
    )

    val quickLaunchRightAction: Flow<CornerAction> = readCornerAction(
        Keys.QUICK_LAUNCH_RIGHT_ACTION, CornerAction.OPEN_APP,
    )

    /** Drawing icons at all; off leaves text-only rows everywhere. */
    val showAppIcons: Flow<Boolean> = data.map { it[Keys.SHOW_APP_ICONS] ?: true }.distinctUntilChanged()

    /**
     * Falls back to the one switch these two were split out of, so anyone who had turned icons
     * off keeps them off in both places rather than having half of them come back.
     */
    val showFavoriteIcons: Flow<Boolean> =
        data.map { it[Keys.SHOW_FAVORITE_ICONS] ?: it[Keys.SHOW_APP_ICONS] ?: true }
            .distinctUntilChanged()

    /**
     * Apps kept out of the computed favorites, while still in the A-Z list.
     *
     * Hiding an app takes it out of everywhere, which is the wrong tool for an app you open
     * often and would rather not have a tap away. Only consulted while the favorites are the
     * computed ones; a manual list already says what is on it.
     */
    val frequentExcluded: Flow<Set<String>> =
        data.map { it[Keys.FREQUENT_EXCLUDED] ?: emptySet() }.distinctUntilChanged()

    val favoritesSource: Flow<FavoritesSource> =
        data.map { prefs ->
            prefs[Keys.FAVORITES_SOURCE]
                ?.let { name -> runCatching { FavoritesSource.valueOf(name) }.getOrNull() }
                ?: FavoritesSource.MANUAL
        }.distinctUntilChanged()

    /** How many of the most-opened apps the favorites section shows when it is computed. */
    val frequentCount: Flow<Int> =
        data.map { (it[Keys.FREQUENT_COUNT] ?: 6).coerceIn(FREQUENT_RANGE) }.distinctUntilChanged()

    val showListHeaders: Flow<Boolean> =
        data.map { it[Keys.SHOW_LIST_HEADERS] ?: true }.distinctUntilChanged()

    val notificationBadges: Flow<Boolean> =
        data.map { it[Keys.NOTIFICATION_BADGES] ?: false }.distinctUntilChanged()

    /**
     * Whether the newest notification's words are shown under an app's name.
     *
     * Off by default, and deliberately not tied to the badge: a count says something is waiting,
     * the text says what it is, and those are different amounts of the user's business.
     */
    val notificationPreview: Flow<Boolean> =
        data.map { it[Keys.NOTIFICATION_PREVIEW] ?: false }.distinctUntilChanged()

    /** Whether a paused (suspended) app is shown greyed out so it reads as set aside. */
    val dimPausedApps: Flow<Boolean> =
        data.map { it[Keys.DIM_PAUSED_APPS] ?: true }.distinctUntilChanged()

    /**
     * Screenshot disguise: mainstream names and borrowed icons everywhere, status bar hidden.
     * Computed, never stored per app — switching it off restores everything by itself.
     * A mode, not a setting: backups never carry it (see NEVER_EXPORT).
     */
    val screenshotMode: Flow<Boolean> =
        data.map { it[Keys.SCREENSHOT_MODE] ?: false }.distinctUntilChanged()

    /** Dark theme on a nightly schedule instead of following the system. */
    val autoDark: Flow<Boolean> =
        data.map { it[Keys.AUTO_DARK] ?: false }.distinctUntilChanged()

    /** Night starts at this hour for the auto-dark schedule. */
    val nightStartHour: Flow<Int> =
        data.map { (it[Keys.NIGHT_START_HOUR] ?: 19).coerceIn(0, 23) }.distinctUntilChanged()

    /** Night ends at this hour for the auto-dark schedule. */
    val nightEndHour: Flow<Int> =
        data.map { (it[Keys.NIGHT_END_HOUR] ?: 7).coerceIn(0, 23) }.distinctUntilChanged()

    /** Two-finger pinch on the home screen resizes the icons. */
    val pinchToResize: Flow<Boolean> =
        data.map { it[Keys.PINCH_TO_RESIZE] ?: true }.distinctUntilChanged()

    /** Skip the launcher's own transitions, for anyone who finds them distracting. */
    val reduceMotion: Flow<Boolean> =
        data.map { it[Keys.REDUCE_MOTION] ?: false }.distinctUntilChanged()

    /** Whether the top-used apps carry the Frequent tag on their own, following the counts. */
    val autoFrequentTag: Flow<Boolean> =
        data.map { it[Keys.AUTO_FREQUENT_TAG] ?: false }.distinctUntilChanged()

    /**
     * Every word the auto-tagger has ever added, in whatever language was current. The static
     * reserved set covers the shipped languages; this covers any language added later, so its
     * word is refused by hand and taken back with the switch exactly like the others.
     */
    val autoFrequentWords: Flow<Set<String>> =
        data.map { it[Keys.AUTO_FREQUENT_WORDS] ?: emptySet() }.distinctUntilChanged()

    /** Apps that ask for the device's own lock before they open. */
    val lockedApps: Flow<Set<String>> =
        data.map { it[Keys.LOCKED_APPS] ?: emptySet() }.distinctUntilChanged()

    /** Free-form tags per app, so an app can be gathered under several headings at once. */
    val appTags: Flow<Map<String, Set<String>>> = data.map { pref ->
        val raw = pref[Keys.APP_TAGS] ?: return@map emptyMap()
        runCatching {
            val obj = org.json.JSONObject(raw)
            buildMap {
                obj.keys().forEach { k ->
                    when (val v = obj.opt(k)) {
                        is org.json.JSONArray -> {
                            val set = buildSet {
                                for (i in 0 until v.length()) {
                                    v.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                                }
                            }
                            if (set.isNotEmpty()) put(k, set)
                        }
                        // The first cut stored one tag as a plain string; read it as a set of one.
                        is String -> v.trim().takeIf { it.isNotEmpty() }?.let { put(k, setOf(it)) }
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }.distinctUntilChanged()

    /** How far down the screen a scrubbed letter is parked, as a percentage of the viewport. */
    val sectionTopPercent: Flow<Int> =
        data.map { (it[Keys.SECTION_TOP_PERCENT] ?: 26).coerceIn(SECTION_TOP_RANGE) }
            .distinctUntilChanged()

    /**
     * The app a button in the bottom corner opens, or null for no button at all.
     *
     * Null rather than a flag beside a key: there is nothing to draw without an app to draw it
     * for, so the two cannot disagree.
     */
    val cornerButtonKey: Flow<String?> = data.map { it[Keys.CORNER_BUTTON_KEY] }.distinctUntilChanged()

    /**
     * Whether opening the app list puts the cursor in the search box and the keyboard up.
     *
     * Off by default: the keyboard covers half of what was just opened, and most openings are
     * to look rather than to type.
     */
    val autoKeyboard: Flow<Boolean> =
        data.map { it[Keys.AUTO_KEYBOARD] ?: false }.distinctUntilChanged()

    /**
     * Whether the letters at the end of the list reach the same line as the rest.
     *
     * Off by default because the room they need is real: a screen's worth of empty space under
     * the last app, which is there whenever the list is open rather than only when it is used.
     */
    val lastLetterToLine: Flow<Boolean> =
        data.map { it[Keys.LAST_LETTER_TO_LINE] ?: true }.distinctUntilChanged()

    /**
     * Whether there has ever been a favorite on the home screen.
     *
     * The empty-favorites prompt is for someone who does not yet know how, and it had no way of
     * telling them from someone who knows exactly how and wants none — so a bare home screen by
     * choice carried the hint for good.
     */
    val favoritesEverSet: Flow<Boolean> =
        data.map { it[Keys.FAVORITES_EVER_SET] ?: false }.distinctUntilChanged()

    /**
     * Whether the edge that opens the app list is only as tall as the letters it shows.
     *
     * Off by default. The whole edge has always been live, and quietly taking that away is a
     * louder complaint than the one it answers — but on a home screen with a widget in reach of
     * the edge, a strip down the whole side is in the way of the widget.
     */
    val edgeZoneBandOnly: Flow<Boolean> =
        data.map { it[Keys.EDGE_ZONE_BAND_ONLY] ?: false }.distinctUntilChanged()

    /**
     * Whether a search that matches no app offers to hand the words to the browser.
     *
     * Off by default, and it is a row to tap rather than something that happens: typing in the
     * launcher does not reach a search engine because you stopped typing.
     */
    val webSearchFallback: Flow<Boolean> =
        data.map { it[Keys.WEB_SEARCH_FALLBACK] ?: false }.distinctUntilChanged()

    /** Whether swiping up puts the cursor in the search box rather than only opening the list. */
    val swipeUpOpensSearch: Flow<Boolean> =
        data.map { it[Keys.SWIPE_UP_OPENS_SEARCH] ?: false }.distinctUntilChanged()

    /**
     * Whether the private space's apps get a section of their own below the alphabet.
     *
     * Off by default. An app installed in both profiles has the same name in both, and only
     * someone who keeps a private space has any idea which of the two they are looking at.
     */
    val privateSpaceSection: Flow<Boolean> =
        data.map { it[Keys.PRIVATE_SPACE_SECTION] ?: false }.distinctUntilChanged()

    /** Whether the list ends with what was installed most recently. */
    val recentSection: Flow<Boolean> =
        data.map { it[Keys.RECENT_SECTION] ?: false }.distinctUntilChanged()

    /** How many of them. */
    val recentSectionCount: Flow<Int> =
        data.map { (it[Keys.RECENT_SECTION_COUNT] ?: 5).coerceIn(RECENT_SECTION_RANGE) }
            .distinctUntilChanged()

    /**
     * Whether unlocking the private space leaves the app list open.
     *
     * Off by default, as it always behaved: the list closes and the home screen comes back.
     * On, the list stays where the space's apps are about to appear.
     */
    val stayAfterPrivateUnlock: Flow<Boolean> =
        data.map { it[Keys.STAY_AFTER_PRIVATE_UNLOCK] ?: false }.distinctUntilChanged()

    /**
     * Whether a favorite answers a tap only on its icon and name.
     *
     * Off by default, which is how it always behaved: the whole line is the button. On, the
     * rest of the line is home screen again, for anyone opening apps they never meant to by
     * tapping beside a name.
     */
    val compactTapArea: Flow<Boolean> =
        data.map { it[Keys.COMPACT_TAP_AREA] ?: false }.distinctUntilChanged()

    /**
     * Whether launcher text carries a shadow, for reading it over a busy wallpaper.
     *
     * Only ever behind light text. A shadow under dark text on a light wallpaper muddies it
     * rather than separating it, so the setting asks for contrast and this decides where
     * contrast is actually gained.
     */
    val textShadow: Flow<Boolean> =
        data.map { it[Keys.TEXT_SHADOW] ?: false }.distinctUntilChanged()

    /** How strong that shadow is; the default is the look it shipped with. */
    val textShadowStrength: Flow<Int> =
        data.map { (it[Keys.TEXT_SHADOW_STRENGTH] ?: DEFAULT_SHADOW_STRENGTH).coerceIn(SHADOW_STRENGTH_RANGE) }
            .distinctUntilChanged()

    /**
     * Whether a work profile's apps say so after their name.
     *
     * On, since anyone with a work profile has two of several apps and no way to tell them
     * apart in a list of names. Nothing to see for anyone who has no second profile.
     */
    val markWorkApps: Flow<Boolean> =
        data.map { it[Keys.MARK_WORK_APPS] ?: true }.distinctUntilChanged()

    /**
     * Whether the rest of the list fades while a finger travels the alphabet.
     *
     * On, as it has always been — the fade is what makes the letter you are on findable. Off
     * for anyone who would rather keep sight of what is around it.
     */
    val dimOthersScrubbing: Flow<Boolean> =
        data.map { it[Keys.DIM_OTHERS_SCRUBBING] ?: true }.distinctUntilChanged()

    /** Whether resting a finger on a letter walks the list on through that letter's apps. */
    val holdScroll: Flow<Boolean> =
        data.map { it[Keys.HOLD_SCROLL] ?: false }.distinctUntilChanged()

    /** Rows a second while it does. */
    val holdScrollSpeed: Flow<Int> =
        data.map { (it[Keys.HOLD_SCROLL_SPEED] ?: 4).coerceIn(HOLD_SCROLL_RANGE) }
            .distinctUntilChanged()

    val showSettingsRow: Flow<Boolean> =
        data.map { it[Keys.SHOW_SETTINGS_ROW] ?: true }.distinctUntilChanged()

    val closeFolderOnLaunch: Flow<Boolean> =
        data.map { it[Keys.CLOSE_FOLDER_ON_LAUNCH] ?: false }.distinctUntilChanged()

    /**
     * Reads [key], falling back to the shared alignment and then to the old right-handed
     * boolean, so an upgrade keeps whichever side the user had chosen and splitting the two
     * lists apart starts them both where they already were.
     */
    private fun readAlignment(pref: Preferences, key: Preferences.Key<String>): HomeAlignment {
        val stored = pref[key] ?: pref[Keys.ALIGNMENT]
        return when {
            stored != null -> runCatching { HomeAlignment.valueOf(stored) }.getOrDefault(HomeAlignment.LEFT)
            pref[Keys.ALIGN_RIGHT] == true -> HomeAlignment.RIGHT
            else -> HomeAlignment.LEFT
        }
    }

    /** How the favorites on the home screen line up. */
    val alignment: Flow<HomeAlignment> =
        data.map { readAlignment(it, Keys.ALIGNMENT) }.distinctUntilChanged()

    /** How the A-Z list lines up, which people want to differ from the home screen. */
    val appListAlignment: Flow<HomeAlignment> =
        data.map { readAlignment(it, Keys.APPLIST_ALIGNMENT) }.distinctUntilChanged()

    val iconSide: Flow<IconSide> = data.map {
        runCatching { IconSide.valueOf(it[Keys.ICON_SIDE] ?: IconSide.LEFT.name) }
            .getOrDefault(IconSide.LEFT)
    }.distinctUntilChanged()

    /** How long a pull-down keeps the status bar on screen before it fades away again. */
    val statusBarPeekSeconds: Flow<Int> =
        data.map { it[Keys.STATUS_BAR_PEEK_SECONDS] ?: 5 }.distinctUntilChanged()

    /** Null while the A-Z strip follows the favorites; a set range is a fraction of the viewport. */
    val scrubBand: Flow<Pair<Float, Float>?> = data.map { pref ->
        val top = pref[Keys.AZ_BAND_TOP_FRACTION]
        val height = pref[Keys.AZ_BAND_HEIGHT_FRACTION]
        if (top != null && height != null) top to height else null
    }.distinctUntilChanged()

    /** 0 = installed before this scheme, 1 = a genuine first run. Absent until [ensureInstallMarker]. */
    val layoutDefaultsVersion: Flow<Int?> =
        data.map { it[Keys.LAYOUT_DEFAULTS_VERSION] }.distinctUntilChanged()

    val welcomeSeen: Flow<Boolean> = data.map { it[Keys.WELCOME_SEEN] ?: false }.distinctUntilChanged()

    /** True once any padding has been set by hand, which retires the computed first-run layout. */
    val hasCustomLayout: Flow<Boolean> =
        data.map { pref -> padKeys.any { pref.contains(it) } }.distinctUntilChanged()

    /**
     * Whether the favorites' own top gap has been set by hand.
     *
     * What retires the computed centred placement, rather than any layout padding at all.
     * Adjusting the gap *below* the favorites used to retire it too, which meant the centred
     * top had to be written out first to keep them where they were — and getting that wrong
     * sent them to the top of the screen with no obvious way back.
     */
    val hasCustomFavoritesTop: Flow<Boolean> =
        data.map { pref -> pref.contains(Keys.FAVORITES_PAD_TOP) }.distinctUntilChanged()

    suspend fun setHidden(componentKey: String, hidden: Boolean) {
        context.dataStore.edit { pref ->
            val current = pref[Keys.HIDDEN_APPS] ?: emptySet()
            pref[Keys.HIDDEN_APPS] = if (hidden) current + componentKey else current - componentKey
        }
    }

    /** Adds or moves an app in the freeze list, or drops it when [method] is null. */
    suspend fun setFreezeMethod(packageName: String, method: FreezeMethod?) {
        context.dataStore.edit { pref ->
            val current = jsonToMap(pref[Keys.FREEZE_METHODS]).toMutableMap()
            if (method == null) current.remove(packageName) else current[packageName] = method.name
            pref[Keys.FREEZE_METHODS] = mapToJson(current)
        }
    }

    /** Adds or moves a whole batch in one write, so Select all is one store pass. */
    suspend fun setFreezeMethods(entries: Map<String, FreezeMethod>) {
        context.dataStore.edit { pref ->
            val current = jsonToMap(pref[Keys.FREEZE_METHODS]).toMutableMap()
            entries.forEach { (packageName, method) -> current[packageName] = method.name }
            pref[Keys.FREEZE_METHODS] = mapToJson(current)
        }
    }

    /** Drops an app from the list entirely, applied state included. */
    suspend fun removeFreezeTarget(packageName: String) {
        context.dataStore.edit { pref ->
            val current = jsonToMap(pref[Keys.FREEZE_METHODS]).toMutableMap()
            current.remove(packageName)
            pref[Keys.FREEZE_METHODS] = mapToJson(current)
            pref[Keys.FREEZE_APPLIED] = (pref[Keys.FREEZE_APPLIED] ?: emptySet()) - packageName
        }
    }

    /** Drops a whole batch from the list and from the applied set in one write. */
    suspend fun removeFreezeTargets(packages: Collection<String>) {
        if (packages.isEmpty()) return
        context.dataStore.edit { pref ->
            val current = jsonToMap(pref[Keys.FREEZE_METHODS]).toMutableMap()
            packages.forEach { current.remove(it) }
            pref[Keys.FREEZE_METHODS] = mapToJson(current)
            pref[Keys.FREEZE_APPLIED] = (pref[Keys.FREEZE_APPLIED] ?: emptySet()) - packages.toSet()
        }
    }

    /** Empties the freeze list and its applied set; used when the feature is turned off. */
    suspend fun clearFreezePlan() {
        context.dataStore.edit { pref ->
            pref.remove(Keys.FREEZE_METHODS)
            pref.remove(Keys.FREEZE_APPLIED)
            pref[Keys.FREEZE_AUTO_ENABLED] = false
            pref[Keys.FREEZE_SYSTEM_APPS] = false
        }
    }

    suspend fun setFreezeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FREEZE_ENABLED] = enabled }
    }

    /**
     * Turns off the two gesture actions that only exist for freezing.
     *
     * Leaving one selected while its option is gone would keep freezing apps from a button
     * the user can no longer explain.
     */
    suspend fun clearFreezeActionBindings() {
        val freezeActions = setOf(CornerAction.FREEZE_ALL.name, CornerAction.UNFREEZE_ALL.name)
        context.dataStore.edit { pref ->
            listOf(
                Keys.QUICK_LAUNCH_LEFT_ACTION,
                Keys.QUICK_LAUNCH_RIGHT_ACTION,
                Keys.CORNER_TAP_ACTION,
                Keys.CORNER_SWIPE_ACTION,
            ).forEach { key ->
                if (pref[key] in freezeActions) pref[key] = CornerAction.DO_NOTHING.name
            }
        }
    }

    /** Marks packages as actually frozen, or no longer, with [applied] saying which. */
    suspend fun setFreezeApplied(packages: Set<String>, applied: Boolean) {
        context.dataStore.edit { pref ->
            val current = pref[Keys.FREEZE_APPLIED] ?: emptySet()
            pref[Keys.FREEZE_APPLIED] = if (applied) current + packages else current - packages
        }
    }

    suspend fun setFreezeDialogReady() {
        context.dataStore.edit { it[Keys.FREEZE_DIALOG_READY] = true }
    }

    /**
     * One-time carry-over from the build that recorded the apps *let out* rather than the ones
     * frozen. The list is unchanged and the other set is its complement, so an app that was
     * frozen stays frozen and one that was lifted stays lifted.
     *
     * Returns whether it wrote anything, so the caller only reloads when the answer changed.
     */
    suspend fun migrateFreezeApplied(): Boolean {
        var wrote = false
        context.dataStore.edit { pref ->
            if (pref.contains(Keys.FREEZE_APPLIED)) return@edit
            val plan = jsonToMap(pref[Keys.FREEZE_METHODS]).keys
            val lifted = pref[stringSetPreferencesKey("freeze_lifted")] ?: emptySet()
            pref[Keys.FREEZE_APPLIED] = plan - lifted
            wrote = plan.isNotEmpty()
        }
        return wrote
    }

    suspend fun setFreezeAutoEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FREEZE_AUTO_ENABLED] = enabled }
    }

    suspend fun setFreezeAutoDelaySeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.FREEZE_AUTO_DELAY_SECONDS] = seconds }
    }

    suspend fun setFreezeUnfreezeOnUnlock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FREEZE_UNFREEZE_ON_UNLOCK] = enabled }
    }

    suspend fun setFreezeSystemApps(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FREEZE_SYSTEM_APPS] = enabled }
    }

    suspend fun setFreezeAutoSkipCharging(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FREEZE_AUTO_SKIP_CHARGING] = enabled }
    }

    suspend fun setFreezeAutoSkipForeground(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FREEZE_AUTO_SKIP_FOREGROUND] = enabled }
    }

    suspend fun setFreezeAutoSkipNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FREEZE_AUTO_SKIP_NOTIFICATIONS] = enabled }
    }

    suspend fun setFreezeSourceFilter(filter: FreezeSourceFilter) {
        context.dataStore.edit { it[Keys.FREEZE_SOURCE_FILTER] = filter.name }
    }

    suspend fun setFreezeStateFilter(filter: FreezeStateFilter) {
        context.dataStore.edit { it[Keys.FREEZE_STATE_FILTER] = filter.name }
    }

    suspend fun setFreezeSort(sort: FreezeSort) {
        context.dataStore.edit { it[Keys.FREEZE_SORT] = sort.name }
    }

    suspend fun setFreezeQuery(query: String) {
        context.dataStore.edit {
            if (query.isEmpty()) it.remove(Keys.FREEZE_QUERY) else it[Keys.FREEZE_QUERY] = query
        }
    }

    suspend fun setFavorites(list: List<String>) {
        context.dataStore.edit { it.writeFavorites(list) }
    }

    suspend fun addFavorite(componentKey: String) {
        context.dataStore.edit { pref ->
            val current = readFavorites(pref)
            if (componentKey !in current) pref.writeFavorites(current + componentKey)
        }
    }

    suspend fun removeFavorite(componentKey: String) {
        context.dataStore.edit { pref ->
            pref.writeFavorites(readFavorites(pref) - componentKey)
        }
    }

    /**
     * Everything stored about one row, dropped in a single write. Only for a row that is gone
     * for good — a shortcut its publisher withdrew, which [AppRepository.unpin] calls once the
     * system itself has unpinned it. Never for a row that might merely be unreachable right
     * now: an app in a locked private space or a paused work profile also drops out of the
     * list, but its name, icon and place are still waiting when the profile comes back, so
     * unticking it in Manage Favorites only removes the favorite ([removeFavorite]), not this.
     *
     * A shortcut that has been unpinned does not come back, so what is kept under its key is
     * only clutter nothing can reach, and it would be restored onto a new phone by the settings
     * backup as well.
     *
     * One transaction rather than seven, so a row cannot end up half forgotten.
     */
    suspend fun forgetEntry(componentKey: String) {
        context.dataStore.edit { pref ->
            val favorites = readFavorites(pref)
            if (componentKey in favorites) pref.writeFavorites(favorites - componentKey)

            val folders = foldersFromJson(pref[Keys.FOLDERS])
            if (folders.any { componentKey in it.apps }) {
                pref[Keys.FOLDERS] = foldersToJson(folders.map { it.copy(apps = it.apps - componentKey) })
            }

            val names = jsonToMap(pref[Keys.NAME_OVERRIDES])
            if (componentKey in names) pref[Keys.NAME_OVERRIDES] = mapToJson(names - componentKey)

            val icons = jsonToMap(pref[Keys.ICON_OVERRIDES])
            if (componentKey in icons) pref[Keys.ICON_OVERRIDES] = mapToJson(icons - componentKey)

            val hidden = pref[Keys.HIDDEN_APPS] ?: emptySet()
            if (componentKey in hidden) pref[Keys.HIDDEN_APPS] = hidden - componentKey

            val counts = jsonToMap(pref[Keys.LAUNCH_COUNTS])
            if (componentKey in counts) pref[Keys.LAUNCH_COUNTS] = mapToJson(counts - componentKey)

            // Per-app sets keyed the same way: an unpinned app must not keep its lock, its
            // tags, or its opt-out of the frequent list behind, or a backup carries them
            // back in for an app that is gone.
            val excluded = pref[Keys.FREQUENT_EXCLUDED] ?: emptySet()
            if (componentKey in excluded) pref[Keys.FREQUENT_EXCLUDED] = excluded - componentKey

            val locked = pref[Keys.LOCKED_APPS] ?: emptySet()
            if (componentKey in locked) pref[Keys.LOCKED_APPS] = locked - componentKey

            runCatching {
                val tags = org.json.JSONObject(pref[Keys.APP_TAGS] ?: "{}")
                if (tags.has(componentKey)) {
                    tags.remove(componentKey)
                    pref[Keys.APP_TAGS] = tags.toString()
                }
            }

            // A quick-launch slot that still points at this key would otherwise swipe to
            // nothing once the row is gone for good. The corner's tap and slide-up keep
            // their own targets, so both are cleared here.
            if (pref[Keys.CORNER_BUTTON_KEY] == componentKey) pref.remove(Keys.CORNER_BUTTON_KEY)
            if (pref[Keys.CORNER_SWIPE_KEY] == componentKey) pref.remove(Keys.CORNER_SWIPE_KEY)
            if (pref[Keys.QUICK_LAUNCH_LEFT] == componentKey) pref.remove(Keys.QUICK_LAUNCH_LEFT)
            if (pref[Keys.QUICK_LAUNCH_RIGHT] == componentKey) pref.remove(Keys.QUICK_LAUNCH_RIGHT)
        }
    }

    /** Creates the folder if [id] is new, otherwise replaces it. */
    suspend fun upsertFolder(folder: Folder) {
        context.dataStore.edit { pref ->
            val existing = foldersFromJson(pref[Keys.FOLDERS])
            val updated = if (existing.any { it.id == folder.id }) {
                existing.map { if (it.id == folder.id) folder else it }
            } else {
                existing + folder
            }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    /** Removes the folder and its row; the apps themselves are untouched. */
    suspend fun deleteFolder(id: String) {
        context.dataStore.edit { pref ->
            pref[Keys.FOLDERS] = foldersToJson(foldersFromJson(pref[Keys.FOLDERS]).filterNot { it.id == id })
            pref.writeFavorites(readFavorites(pref).filterNot { it == folderToken(id) })
        }
    }

    suspend fun setFolderIcon(id: String, icon: String?) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS])
                .map { if (it.id == id) it.copy(icon = icon) else it }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    suspend fun setFolderApps(id: String, apps: List<String>) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS])
                .map { if (it.id == id) it.copy(apps = apps) else it }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    /**
     * Puts [componentKey] in the folder. An app that was a top-level favorite moves into it
     * rather than being duplicated in both places.
     */
    suspend fun addAppToFolder(folderId: String, componentKey: String) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS]).map { folder ->
                if (folder.id == folderId && componentKey !in folder.apps) {
                    folder.copy(apps = folder.apps + componentKey)
                } else {
                    folder
                }
            }
            pref[Keys.FOLDERS] = foldersToJson(updated)
            pref.writeFavorites(readFavorites(pref).filterNot { it == componentKey })
        }
    }

    suspend fun removeAppFromFolder(folderId: String, componentKey: String) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS]).map { folder ->
                if (folder.id == folderId) folder.copy(apps = folder.apps - componentKey) else folder
            }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    suspend fun setNameOverride(componentKey: String, name: String?) {
        context.dataStore.edit { pref ->
            val map = jsonToMap(pref[Keys.NAME_OVERRIDES]).toMutableMap()
            if (name.isNullOrBlank()) map.remove(componentKey) else map[componentKey] = name
            pref[Keys.NAME_OVERRIDES] = mapToJson(map)
        }
    }

    suspend fun setIconOverride(componentKey: String, uri: String?) {
        context.dataStore.edit { pref ->
            val map = jsonToMap(pref[Keys.ICON_OVERRIDES]).toMutableMap()
            if (uri.isNullOrBlank()) map.remove(componentKey) else map[componentKey] = uri
            pref[Keys.ICON_OVERRIDES] = mapToJson(map)
        }
    }

    suspend fun setIconSizeDp(v: Int) {
        context.dataStore.edit { it[Keys.ICON_SIZE_DP] = v }
    }

    suspend fun setLabelSizeSp(v: Int) {
        context.dataStore.edit { it[Keys.LABEL_SIZE_SP] = v }
    }

    suspend fun setSidePaddingDp(v: Int) {
        context.dataStore.edit { it[Keys.SIDE_PADDING_DP] = v }
    }

    suspend fun setItemSpacingDp(v: Int) {
        context.dataStore.edit { it[Keys.ITEM_SPACING_DP] = v }
    }

    suspend fun setNowPlayingHeightDp(v: Int) {
        context.dataStore.edit { it[Keys.NOW_PLAYING_HEIGHT_DP] = v }
    }

    suspend fun setHomePadding(slot: PaddingSlot, v: Int) {
        val key = when (slot) {
            PaddingSlot.NOW_PLAYING_TOP -> Keys.NOW_PLAYING_PAD_TOP
            PaddingSlot.NOW_PLAYING_BOTTOM -> Keys.NOW_PLAYING_PAD_BOTTOM
            PaddingSlot.WIDGET_TOP -> Keys.WIDGET_PAD_TOP
            PaddingSlot.WIDGET_BOTTOM -> Keys.WIDGET_PAD_BOTTOM
            PaddingSlot.FAVORITES_TOP -> Keys.FAVORITES_PAD_TOP
            PaddingSlot.FAVORITES_BOTTOM -> Keys.FAVORITES_PAD_BOTTOM
        }
        context.dataStore.edit { it[key] = v }
    }

    suspend fun setFontFile(path: String?) {
        context.dataStore.edit { pref ->
            if (path == null) pref.remove(Keys.FONT_FILE) else pref[Keys.FONT_FILE] = path
        }
    }

    suspend fun setShortcutSwipe(v: ShortcutSwipe) {
        context.dataStore.edit {
            it[Keys.SHORTCUT_SWIPE] = v.name
            // Mirrored, so a downgrade to the version that only had a switch reads it right.
            it[Keys.SWIPE_FOR_SHORTCUTS] = v != ShortcutSwipe.OFF
        }
    }

    suspend fun setAllowRotation(v: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_ROTATION] = v }
    }

    suspend fun setDimColor(argb: Int) {
        context.dataStore.edit { it[Keys.DIM_COLOR] = argb }
    }

    suspend fun setTextColorCustom(argb: Int) {
        context.dataStore.edit { it[Keys.TEXT_COLOR_CUSTOM] = argb }
    }

    suspend fun setThemedIcons(v: Boolean) {
        context.dataStore.edit { it[Keys.THEMED_ICONS] = v }
    }

    suspend fun setIconShape(v: IconShape) {
        context.dataStore.edit { it[Keys.ICON_SHAPE] = v.name }
    }

    suspend fun setFont(f: AppFont) {
        context.dataStore.edit { it[Keys.FONT] = f.name }
    }

    suspend fun setColorSchemeChoice(c: ColorSchemeChoice) {
        context.dataStore.edit { it[Keys.COLOR_SCHEME] = c.name }
    }

    suspend fun setHideStatusBarAppList(v: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_STATUS_BAR_APPLIST] = v }
    }

    suspend fun setHideStatusBar(v: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_STATUS_BAR] = v }
    }

    suspend fun setDimWallpaperAlpha(v: Float) {
        context.dataStore.edit { it[Keys.DIM_WALLPAPER_ALPHA] = v }
    }

    suspend fun setWallpaperBlurDp(v: Int) {
        context.dataStore.edit { it[Keys.WALLPAPER_BLUR_DP] = v.coerceIn(0, 80) }
    }

    suspend fun setDimHomeAlpha(v: Float) {
        context.dataStore.edit { it[Keys.DIM_HOME_ALPHA] = v }
    }

    suspend fun setShowFavoriteLabels(v: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_FAVORITE_LABELS] = v }
    }

    suspend fun setTextColorMode(v: TextColorMode) {
        context.dataStore.edit { it[Keys.TEXT_COLOR_MODE] = v.name }
    }

    suspend fun setDoubleTapToLock(v: Boolean) {
        context.dataStore.edit { it[Keys.DOUBLE_TAP_TO_LOCK] = v }
    }

    suspend fun setHapticFeedback(v: HapticFeedback) {
        context.dataStore.edit {
            it[Keys.HAPTIC_FEEDBACK] = v.name
            // The old switch follows, so a downgrade back reads the same answer.
            it[Keys.HAPTICS_ENABLED] = v != HapticFeedback.OFF
        }
    }

    suspend fun setShowAlphabet(v: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_ALPHABET] = v }
    }

    suspend fun setTwoColumn(which: TwoColumnSlot, v: Boolean) {
        context.dataStore.edit {
            it[when (which) {
                TwoColumnSlot.HOME_PORTRAIT -> Keys.HOME_TWO_COLUMN_PORTRAIT
                TwoColumnSlot.HOME_LANDSCAPE -> Keys.HOME_TWO_COLUMN_LANDSCAPE
                TwoColumnSlot.LIST_PORTRAIT -> Keys.LIST_TWO_COLUMN_PORTRAIT
                TwoColumnSlot.LIST_LANDSCAPE -> Keys.LIST_TWO_COLUMN_LANDSCAPE
            }] = v
        }
    }

    suspend fun setAlignRight(v: Boolean) {
        context.dataStore.edit { it[Keys.ALIGN_RIGHT] = v }
    }

    suspend fun setAzStripVisibility(v: AzStripVisibility) {
        context.dataStore.edit {
            it[Keys.AZ_STRIP_VISIBILITY] = v.name
            // Kept in step so an older build reads something sensible if one is installed.
            it[Keys.ALWAYS_SHOW_AZ] = v == AzStripVisibility.ALWAYS
        }
    }

    suspend fun setEdgeSide(v: EdgeSide) {
        context.dataStore.edit { it[Keys.EDGE_SIDE] = v.name }
    }

    suspend fun setIconPackPackage(pkg: String?) {
        context.dataStore.edit { pref ->
            if (pkg.isNullOrBlank()) pref.remove(Keys.ICON_PACK_PACKAGE) else pref[Keys.ICON_PACK_PACKAGE] = pkg
        }
    }

    suspend fun setWidgetId(id: Int) {
        context.dataStore.edit { it[Keys.WIDGET_ID] = id }
    }

    suspend fun setNowPlayingEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.NOW_PLAYING_ENABLED] = v }
    }

    suspend fun setWidgetPosition(v: Int) {
        context.dataStore.edit { it[Keys.WIDGET_POSITION] = v }
    }

    suspend fun setWidgetHeightDp(v: Int) {
        context.dataStore.edit { it[Keys.WIDGET_HEIGHT_DP] = v }
    }

    /**
     * Whether the widget height was ever set by hand. Absent means it follows the
     * installed widgets (see the auto height in the nav host); clearing it hands the
     * decision back.
     */
    val hasCustomWidgetHeight: Flow<Boolean> =
        data.map { it.contains(Keys.WIDGET_HEIGHT_DP) }.distinctUntilChanged()

    suspend fun clearWidgetHeightDp() {
        context.dataStore.edit { it.remove(Keys.WIDGET_HEIGHT_DP) }
    }

    suspend fun addWidgetId(id: Int) {
        context.dataStore.edit { pref ->
            val current = readWidgetIds(pref)
            if (id !in current) pref.writeWidgetIds(current + id)
        }
    }

    /**
     * Which package provides each widget id, so a dead one can be told apart.
     *
     * A null provider info means two different things: the package is gone (uninstall — the
     * id should go) or it is still installed but frozen or suspended (temporary — the id must
     * stay, or unfreezing finds the widget deleted). The info itself names no package when it
     * is null, so the mapping is written at bind time, when the provider is known.
     */
    val widgetProviders: Flow<Map<Int, String>> = data.map { pref ->
        val raw = pref[Keys.WIDGET_PROVIDERS] ?: return@map emptyMap()
        runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { k ->
                    k.toIntOrNull()?.let { id ->
                        obj.optString(k).takeIf { it.isNotEmpty() }?.let { put(id, it) }
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }.distinctUntilChanged()

    suspend fun setWidgetProvider(id: Int, packageName: String) {
        context.dataStore.edit { pref ->
            val obj = runCatching { JSONObject(pref[Keys.WIDGET_PROVIDERS] ?: "{}") }
                .getOrDefault(JSONObject())
            obj.put(id.toString(), packageName)
            pref[Keys.WIDGET_PROVIDERS] = obj.toString()
        }
    }

    /**
     * Keeps only the widget ids [keep] accepts, for the ids whose provider has gone.
     *
     * A widget whose app was uninstalled leaves a dead page that says so and has to be
     * removed by hand; an id restored from a backup onto a device that never had that widget
     * is the same page on first sight. Both are answered here.
     */
    suspend fun keepWidgetIds(keep: (Int) -> Boolean) {
        context.dataStore.edit { pref ->
            val current = readWidgetIds(pref)
            val kept = current.filter(keep)
            if (kept.size != current.size) {
                pref.writeWidgetIds(kept)
                // The provider map would otherwise keep rows for widgets that no longer
                // exist, like removeWidgetId already avoids doing one at a time.
                runCatching {
                    val obj = JSONObject(pref[Keys.WIDGET_PROVIDERS] ?: "{}")
                    val keptStrings = kept.map { it.toString() }.toSet()
                    obj.keys().forEach { id -> if (id !in keptStrings) obj.remove(id) }
                    pref[Keys.WIDGET_PROVIDERS] = obj.toString()
                }
            }
        }
    }

    suspend fun removeWidgetId(id: Int) {
        context.dataStore.edit { pref ->
            pref.writeWidgetIds(readWidgetIds(pref) - id)
            runCatching {
                val obj = JSONObject(pref[Keys.WIDGET_PROVIDERS] ?: "{}")
                obj.remove(id.toString())
                pref[Keys.WIDGET_PROVIDERS] = obj.toString()
            }
        }
    }

    suspend fun setWidgetSidePaddingDp(v: Int) {
        context.dataStore.edit { it[Keys.WIDGET_SIDE_PADDING_DP] = v }
    }

    suspend fun setWidgetOffsetXDp(v: Int) {
        context.dataStore.edit { it[Keys.WIDGET_OFFSET_X_DP] = v.coerceIn(-200, 200) }
    }

    suspend fun setSwipeUpOpensList(v: Boolean) {
        context.dataStore.edit { it[Keys.SWIPE_UP_OPENS_LIST] = v }
    }

    suspend fun setAppListSearchEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.APPLIST_SEARCH_ENABLED] = v }
    }

    suspend fun setAppListSearchBottom(v: Boolean) {
        context.dataStore.edit { it[Keys.APPLIST_SEARCH_BOTTOM] = v }
    }

    suspend fun setAppListSearchHidden(v: Boolean) {
        context.dataStore.edit { it[Keys.APPLIST_SEARCH_HIDDEN] = v }
    }

    suspend fun setSortByUsage(v: Boolean) {
        context.dataStore.edit { it[Keys.SORT_BY_USAGE] = v }
    }

    /**
     * Counted for every launch, not just while the usage sort is on, so turning the sort on
     * later has a history to order by rather than starting from nothing.
     */
    suspend fun incrementLaunchCount(componentKey: String) {
        context.dataStore.edit { pref ->
            val map = jsonToMap(pref[Keys.LAUNCH_COUNTS]).toMutableMap()
            map[componentKey] = ((map[componentKey]?.toIntOrNull() ?: 0) + 1).toString()
            pref[Keys.LAUNCH_COUNTS] = mapToJson(map)
        }
    }

    suspend fun setEdgeZoneWidthDp(v: Int) {
        context.dataStore.edit { it[Keys.EDGE_ZONE_WIDTH_DP] = v }
    }

    suspend fun setQuickLaunch(slot: QuickLaunchSlot, componentKey: String?) {
        val key = when (slot) {
            QuickLaunchSlot.LEFT -> Keys.QUICK_LAUNCH_LEFT
            QuickLaunchSlot.RIGHT -> Keys.QUICK_LAUNCH_RIGHT
        }
        context.dataStore.edit { pref ->
            if (componentKey.isNullOrBlank()) pref.remove(key) else pref[key] = componentKey
        }
    }

    suspend fun setQuickLaunchAction(slot: QuickLaunchSlot, action: CornerAction) {
        context.dataStore.edit { pref ->
            pref[when (slot) {
                QuickLaunchSlot.LEFT -> Keys.QUICK_LAUNCH_LEFT_ACTION
                QuickLaunchSlot.RIGHT -> Keys.QUICK_LAUNCH_RIGHT_ACTION
            }] = action.name
        }
    }

    suspend fun setShowAppIcons(v: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_APP_ICONS] = v }
    }

    suspend fun setShowFavoriteIcons(v: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_FAVORITE_ICONS] = v }
    }

    suspend fun setFavoritesSource(v: FavoritesSource) {
        context.dataStore.edit { it[Keys.FAVORITES_SOURCE] = v.name }
    }

    suspend fun setFrequentCount(v: Int) {
        context.dataStore.edit { it[Keys.FREQUENT_COUNT] = v.coerceIn(FREQUENT_RANGE) }
    }

    suspend fun setShowListHeaders(v: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_LIST_HEADERS] = v }
    }

    suspend fun setNotificationBadges(v: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATION_BADGES] = v }
    }

    suspend fun setNotificationPreview(v: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATION_PREVIEW] = v }
    }

    suspend fun setDimPausedApps(v: Boolean) {
        context.dataStore.edit { it[Keys.DIM_PAUSED_APPS] = v }
    }

    suspend fun setScreenshotMode(v: Boolean) {
        context.dataStore.edit { it[Keys.SCREENSHOT_MODE] = v }
    }

    suspend fun setAutoDark(v: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DARK] = v }
    }

    suspend fun setNightStartHour(v: Int) {
        context.dataStore.edit { it[Keys.NIGHT_START_HOUR] = v.coerceIn(0, 23) }
    }

    suspend fun setNightEndHour(v: Int) {
        context.dataStore.edit { it[Keys.NIGHT_END_HOUR] = v.coerceIn(0, 23) }
    }

    suspend fun setPinchToResize(v: Boolean) {
        context.dataStore.edit { it[Keys.PINCH_TO_RESIZE] = v }
    }

    suspend fun setReduceMotion(v: Boolean) {
        context.dataStore.edit { it[Keys.REDUCE_MOTION] = v }
    }

    suspend fun setAutoFrequentTag(v: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_FREQUENT_TAG] = v }
    }

    /** Remembers a word the auto-tagger added, so later languages' words stay reserved too. */
    suspend fun recordAutoFrequentWord(word: String) {
        val clean = word.trim()
        if (clean.isEmpty()) return
        context.dataStore.edit { pref ->
            pref[Keys.AUTO_FREQUENT_WORDS] = (pref[Keys.AUTO_FREQUENT_WORDS] ?: emptySet()) + clean
        }
    }

    suspend fun setAppLocked(key: String, locked: Boolean) {
        context.dataStore.edit { pref ->
            val current = pref[Keys.LOCKED_APPS] ?: emptySet()
            pref[Keys.LOCKED_APPS] = EntryKeys.withAppLocked(current, key, locked)
        }
    }

    suspend fun setAppTags(key: String, tags: Set<String>) {
        context.dataStore.edit { pref ->
            val obj = runCatching { org.json.JSONObject(pref[Keys.APP_TAGS] ?: "{}") }
                .getOrDefault(org.json.JSONObject())
            val clean = tags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            if (clean.isEmpty()) obj.remove(key) else obj.put(key, org.json.JSONArray(clean.toList()))
            pref[Keys.APP_TAGS] = obj.toString()
        }
    }

    /** Writes the whole tag map at once: N apps retagged cost one edit, not N. */
    suspend fun setAllAppTags(tags: Map<String, Set<String>>) {
        context.dataStore.edit { pref ->
            val obj = org.json.JSONObject()
            tags.forEach { (key, set) ->
                val clean = set.map { it.trim() }.filter { it.isNotEmpty() }
                if (clean.isNotEmpty()) obj.put(key, org.json.JSONArray(clean))
            }
            pref[Keys.APP_TAGS] = obj.toString()
        }
    }

    suspend fun setSectionTopPercent(v: Int) {
        context.dataStore.edit { it[Keys.SECTION_TOP_PERCENT] = v.coerceIn(SECTION_TOP_RANGE) }
    }

    suspend fun setCornerButton(componentKey: String?) {
        context.dataStore.edit { pref ->
            if (componentKey.isNullOrBlank()) pref.remove(Keys.CORNER_BUTTON_KEY)
            else pref[Keys.CORNER_BUTTON_KEY] = componentKey
        }
    }

    /** The slide-up's own target: the tap keeps the button key above, never shared. */
    val cornerSwipeKey: Flow<String?> = data.map { it[Keys.CORNER_SWIPE_KEY] }.distinctUntilChanged()

    suspend fun setCornerSwipeKey(componentKey: String?) {
        context.dataStore.edit { pref ->
            if (componentKey.isNullOrBlank()) pref.remove(Keys.CORNER_SWIPE_KEY)
            else pref[Keys.CORNER_SWIPE_KEY] = componentKey
        }
    }

    /**
     * Where the home button sits, as fractions of its middle over the viewport — [Pair.x]
     * across, [Pair.y] down, each 0..1. Portrait and landscape are stored apart and never
     * share. Null until the user drags it there; until then the default (bottom right,
     * clear of the strip) is computed, not stored.
     *
     * Plain floats, so a backup carries them like any other number — and clamps them on
     * the way back in, so a hand-edited file cannot park the button off screen for good.
     */
    val cornerPosPortrait: Flow<Pair<Float, Float>?> = data.map { pref ->
        cornerPair(pref, Keys.CORNER_X_PORTRAIT, Keys.CORNER_Y_PORTRAIT)
    }.distinctUntilChanged()

    val cornerPosLandscape: Flow<Pair<Float, Float>?> = data.map { pref ->
        cornerPair(pref, Keys.CORNER_X_LANDSCAPE, Keys.CORNER_Y_LANDSCAPE)
    }.distinctUntilChanged()

    private fun cornerPair(
        pref: Preferences,
        xKey: Preferences.Key<Float>,
        yKey: Preferences.Key<Float>,
    ): Pair<Float, Float>? {
        val x = pref[xKey] ?: return null
        val y = pref[yKey] ?: return null
        if (!x.isFinite() || !y.isFinite()) return null
        return x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)
    }

    suspend fun setCornerPos(portrait: Boolean, x: Float, y: Float) {
        context.dataStore.edit { pref ->
            if (portrait) {
                pref[Keys.CORNER_X_PORTRAIT] = x.coerceIn(0f, 1f)
                pref[Keys.CORNER_Y_PORTRAIT] = y.coerceIn(0f, 1f)
            } else {
                pref[Keys.CORNER_X_LANDSCAPE] = x.coerceIn(0f, 1f)
                pref[Keys.CORNER_Y_LANDSCAPE] = y.coerceIn(0f, 1f)
            }
        }
    }

    /** A stored enum by name, with a default for anything unrecognised. */
    private inline fun <reified T : Enum<T>> readEnum(
        key: Preferences.Key<String>,
        default: T,
    ): Flow<T> = data.map { pref ->
        pref[key]?.let { stored -> runCatching { enumValueOf<T>(stored) }.getOrNull() } ?: default
    }.distinctUntilChanged()

    private fun readCornerAction(key: Preferences.Key<String>, default: CornerAction): Flow<CornerAction> =        data.map { pref ->
            pref[key]?.let { stored ->
                // The combined option was split in two: an old binding opens whatever it
                // points at, which is what opening an app does.
                if (stored == "APP_OR_SHORTCUT") CornerAction.OPEN_APP
                else runCatching { CornerAction.valueOf(stored) }.getOrNull()
            } ?: default
        }.distinctUntilChanged()

    /** What a tap on the corner button does. */
    val cornerTapAction: Flow<CornerAction> =
        readCornerAction(Keys.CORNER_TAP_ACTION, CornerAction.OPEN_APP)

    /** What hold-slide-up on the corner button does. */
    val cornerSwipeAction: Flow<CornerAction> =
        readCornerAction(Keys.CORNER_SWIPE_ACTION, CornerAction.APP_LIST)

    suspend fun setCornerTapAction(action: CornerAction) {
        context.dataStore.edit { it[Keys.CORNER_TAP_ACTION] = action.name }
    }

    suspend fun setCornerSwipeAction(action: CornerAction) {
        context.dataStore.edit { it[Keys.CORNER_SWIPE_ACTION] = action.name }
    }

    suspend fun setLastLetterToLine(v: Boolean) {
        context.dataStore.edit { it[Keys.LAST_LETTER_TO_LINE] = v }
    }

    suspend fun setAutoKeyboard(v: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_KEYBOARD] = v }
    }

    suspend fun setWebSearchFallback(v: Boolean) {
        context.dataStore.edit { it[Keys.WEB_SEARCH_FALLBACK] = v }
    }

    suspend fun setSwipeUpOpensSearch(v: Boolean) {
        context.dataStore.edit { it[Keys.SWIPE_UP_OPENS_SEARCH] = v }
    }

    /**
     * Settings a fresh install should start with, which an existing one must not be given.
     *
     * Some defaults are only right for someone who has not used this before. Confining the live
     * edge to the letters is the better first experience; changing it under someone who has
     * been swiping the whole side for months is not an improvement, it is their launcher
     * behaving differently one morning. So it is written once, when the welcome is dismissed —
     * a screen only a new install ever sees — and only where nothing has been chosen already.
     */
    suspend fun applyNewInstallDefaults() {
        context.dataStore.edit {
            if (it[Keys.EDGE_ZONE_BAND_ONLY] == null) it[Keys.EDGE_ZONE_BAND_ONLY] = true
        }
    }

    suspend fun setEdgeZoneInsetAuto(v: Boolean) {
        context.dataStore.edit { it[Keys.EDGE_ZONE_INSET_AUTO] = v }
    }

    suspend fun setEdgeZoneInsetDp(v: Int) {
        context.dataStore.edit { it[Keys.EDGE_ZONE_INSET_DP] = v.coerceIn(EDGE_ZONE_INSET_RANGE) }
    }

    suspend fun setFrequentExcluded(key: String, excluded: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FREQUENT_EXCLUDED] ?: emptySet()
            prefs[Keys.FREQUENT_EXCLUDED] =
                if (excluded) current + key else current - key
        }
    }

    suspend fun setPrivateSpaceSection(v: Boolean) {
        context.dataStore.edit { it[Keys.PRIVATE_SPACE_SECTION] = v }
    }

    suspend fun setRecentSection(v: Boolean) {
        context.dataStore.edit { it[Keys.RECENT_SECTION] = v }
    }

    suspend fun setRecentSectionCount(v: Int) {
        context.dataStore.edit { it[Keys.RECENT_SECTION_COUNT] = v.coerceIn(RECENT_SECTION_RANGE) }
    }

    suspend fun setStayAfterPrivateUnlock(v: Boolean) {
        context.dataStore.edit { it[Keys.STAY_AFTER_PRIVATE_UNLOCK] = v }
    }

    suspend fun setCompactTapArea(v: Boolean) {
        context.dataStore.edit { it[Keys.COMPACT_TAP_AREA] = v }
    }

    suspend fun setTextShadowStrength(v: Int) {
        context.dataStore.edit { it[Keys.TEXT_SHADOW_STRENGTH] = v.coerceIn(SHADOW_STRENGTH_RANGE) }
    }

    suspend fun setTextShadow(v: Boolean) {
        context.dataStore.edit { it[Keys.TEXT_SHADOW] = v }
    }

    suspend fun setMarkWorkApps(v: Boolean) {
        context.dataStore.edit { it[Keys.MARK_WORK_APPS] = v }
    }

    suspend fun setDimOthersScrubbing(v: Boolean) {
        context.dataStore.edit { it[Keys.DIM_OTHERS_SCRUBBING] = v }
    }

    suspend fun setHoldScroll(v: Boolean) {
        context.dataStore.edit { it[Keys.HOLD_SCROLL] = v }
    }

    suspend fun setHoldScrollSpeed(v: Int) {
        context.dataStore.edit { it[Keys.HOLD_SCROLL_SPEED] = v.coerceIn(HOLD_SCROLL_RANGE) }
    }

    suspend fun setEdgeZoneBandOnly(v: Boolean) {
        context.dataStore.edit { it[Keys.EDGE_ZONE_BAND_ONLY] = v }
    }

    suspend fun markFavoritesEverSet() {
        context.dataStore.edit {
            if (it[Keys.FAVORITES_EVER_SET] != true) it[Keys.FAVORITES_EVER_SET] = true
        }
    }

    suspend fun setShowSettingsRow(v: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_SETTINGS_ROW] = v }
    }

    suspend fun setCloseFolderOnLaunch(v: Boolean) {
        context.dataStore.edit { it[Keys.CLOSE_FOLDER_ON_LAUNCH] = v }
    }

    suspend fun setAlignment(v: HomeAlignment) {
        context.dataStore.edit {
            it[Keys.ALIGNMENT] = v.name
            // Kept in step so a downgrade still lands on the side the user picked.
            it[Keys.ALIGN_RIGHT] = v == HomeAlignment.RIGHT
        }
    }

    suspend fun setAppListAlignment(v: HomeAlignment) {
        context.dataStore.edit { it[Keys.APPLIST_ALIGNMENT] = v.name }
    }

    suspend fun setIconSide(v: IconSide) {
        context.dataStore.edit { it[Keys.ICON_SIDE] = v.name }
    }

    suspend fun setStatusBarPeekSeconds(v: Int) {
        context.dataStore.edit { it[Keys.STATUS_BAR_PEEK_SECONDS] = v }
    }

    suspend fun setScrubBand(topFraction: Float, heightFraction: Float) {
        context.dataStore.edit {
            it[Keys.AZ_BAND_TOP_FRACTION] = topFraction
            it[Keys.AZ_BAND_HEIGHT_FRACTION] = heightFraction
        }
    }

    /** Hands the strip back to following the favorites list. */
    suspend fun clearScrubBand() {
        context.dataStore.edit {
            it.remove(Keys.AZ_BAND_TOP_FRACTION)
            it.remove(Keys.AZ_BAND_HEIGHT_FRACTION)
        }
    }

    suspend fun setWelcomeSeen(v: Boolean) {
        context.dataStore.edit { it[Keys.WELCOME_SEEN] = v }
    }

    /**
     * Stamps whether this install is new, once. Nothing writes to the store before the user
     * changes something, so an empty store is the one reliable signal of a first run — which
     * is why this has to run before any other setter can muddy it.
     */
    suspend fun ensureInstallMarker() {
        context.dataStore.edit { pref ->
            if (pref[Keys.LAYOUT_DEFAULTS_VERSION] == null) {
                pref[Keys.LAYOUT_DEFAULTS_VERSION] = if (pref.asMap().isEmpty()) 1 else 0
            }
        }
    }

    private fun jsonToMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { k -> map[k] = obj.getString(k) }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun mapToJson(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }
}