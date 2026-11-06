// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.json.JSONArray
import org.json.JSONObject

/** What a sleep rule does while its conditions hold. */
enum class SleepAction { FREEZE, THAW }

/**
 * A rule that puts apps to sleep, or wakes them, while the phone looks a certain way.
 *
 * The conditions are the ones favorite rules already use — Wi-Fi, headphones, a time window —
 * plus charging and a battery level, because "at night" and "on the road" are the moments a
 * freeze is worth having. An empty [tokens] means the whole freeze list, which is the common
 * case: nothing on the home screen, everything that can sleep sleeps.
 */
data class SleepRule(
    val id: String,
    val name: String = "",
    val enabled: Boolean = true,
    val action: SleepAction = SleepAction.FREEZE,
    /** Freeze-list keys: `package/activity`, or a profile-suffixed key. Empty means all of them. */
    val tokens: Set<String> = emptySet(),
    val wifi: FavoriteRuleWifi = FavoriteRuleWifi.ANY,
    val wifiNames: Set<String> = emptySet(),
    val headset: FavoriteRuleHeadset = FavoriteRuleHeadset.ANY,
    /** Minutes after midnight; both -1 for no time window. */
    val startMinute: Int = -1,
    val endMinute: Int = -1,
    /** True only while charging, false only while not; null is no condition. */
    val charging: Boolean? = null,
    /** Freeze at or below this percentage; zero is no condition. */
    val batteryBelow: Int = 0,
) {
    /** The conditions this rule actually carries, for the "N conditions" line in the editor. */
    val conditions: Int
        get() = listOf(
            // An empty name list is "any network", which is a condition in its own right.
            wifi != FavoriteRuleWifi.ANY,
            headset != FavoriteRuleHeadset.ANY,
            // Equal start and end is not a 24-hour window; it used to be saved as one that
            // matched always. Counting it here keeps Save disabled until it means something.
            startMinute >= 0 && endMinute >= 0 && startMinute != endMinute,
            charging != null,
            batteryBelow > 0,
        ).count { it }

    fun matches(context: RuleContext): Boolean {
        if (!RuleConditions.match(wifi, wifiNames, headset, startMinute, endMinute, context)) {
            return false
        }
        if (charging != null && charging != context.charging) return false
        if (batteryBelow > 0 && context.batteryPercent > batteryBelow) return false
        return true
    }
}

/** The apps a set of rules puts to sleep and wakes right now. */
data class SleepDecision(val sleep: Set<String>, val wake: Set<String>)

/**
 * What [rules] ask for at this moment, given [context] and the current freeze list.
 *
 * A rule that names no tokens means the whole list; that is resolved here so the caller does
 * not have to know which rules were written as "everything". An explicit wake beats a freeze
 * on the same key: the owner asked for that app now, and a broader rule should not undo it.
 * Keys no running rule names are left alone — the launcher only changes what a rule decided.
 */
fun List<SleepRule>.decision(context: RuleContext, listed: Set<String>): SleepDecision {
    val asleep = mutableSetOf<String>()
    val awake = mutableSetOf<String>()
    for (rule in this) {
        if (!rule.enabled || !rule.matches(context)) continue
        val targets = if (rule.tokens.isEmpty()) listed else rule.tokens
        if (rule.action == SleepAction.FREEZE) asleep += targets else awake += targets
    }
    return SleepDecision(sleep = asleep - awake, wake = awake)
}

/** Whether [key] is a valid freeze-list key for a sleep rule. */
fun isSleepToken(key: String): Boolean =
    key.isNotBlank() && key.length <= 256 && key.none { it.isISOControl() }

/** Limits on what a stored sleep rule may carry. */
internal const val MAX_SLEEP_RULES = 24
private const val MAX_SLEEP_TOKENS = 128
private const val MAX_SLEEP_NAME = 40
private const val MAX_SLEEP_WIFI_NAMES = 16
private const val MAX_SLEEP_SSID = 64

internal fun sleepRulesToJson(rules: List<SleepRule>): String {
    val array = JSONArray()
    rules.take(MAX_SLEEP_RULES).forEach { rule ->
        array.put(
            JSONObject().apply {
                put("id", rule.id)
                if (rule.name.isNotBlank()) put("name", rule.name)
                put("enabled", rule.enabled)
                put("action", rule.action.name)
                if (rule.tokens.isNotEmpty()) {
                    put("tokens", JSONArray().apply { rule.tokens.sorted().forEach { put(it) } })
                }
                if (rule.wifi != FavoriteRuleWifi.ANY) {
                    put("wifi", rule.wifi.name)
                    if (rule.wifiNames.isNotEmpty()) {
                        put("wifiNames", JSONArray().apply { rule.wifiNames.sorted().forEach { put(it) } })
                    }
                }
                if (rule.headset != FavoriteRuleHeadset.ANY) put("headset", rule.headset.name)
                if (rule.startMinute in 0..1439) put("start", rule.startMinute)
                if (rule.endMinute in 0..1439) put("end", rule.endMinute)
                rule.charging?.let { put("charging", it) }
                if (rule.batteryBelow > 0) put("batteryBelow", rule.batteryBelow)
            }
        )
    }
    return array.toString()
}

internal fun sleepRulesFromJson(json: String?): List<SleepRule> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val id = obj.optString("id")
            if (id.isBlank() || id.length > 32 || id.any { !it.isLetterOrDigit() && it != '-' && it != '_' }) {
                return@mapNotNull null
            }
            val tokensArray = obj.optJSONArray("tokens") ?: JSONArray()
            val tokens = (0 until tokensArray.length())
                .mapNotNull { i -> tokensArray.optString(i).takeIf { isSleepToken(it) } }
                .take(MAX_SLEEP_TOKENS)
                .toSet()
            val namesArray = obj.optJSONArray("wifiNames") ?: JSONArray()
            val names = (0 until namesArray.length())
                .mapNotNull { i ->
                    namesArray.optString(i).takeIf { it.isNotBlank() && it.length <= MAX_SLEEP_SSID }
                }
                .take(MAX_SLEEP_WIFI_NAMES)
                .toSet()
            val start = obj.optInt("start", -1)
            val end = obj.optInt("end", -1)
            SleepRule(
                id = id,
                name = obj.optString("name").take(MAX_SLEEP_NAME),
                enabled = obj.optBoolean("enabled", true),
                action = runCatching { SleepAction.valueOf(obj.optString("action")) }
                    .getOrDefault(SleepAction.FREEZE),
                tokens = tokens,
                wifi = runCatching { FavoriteRuleWifi.valueOf(obj.optString("wifi")) }
                    .getOrDefault(FavoriteRuleWifi.ANY),
                wifiNames = names,
                headset = runCatching { FavoriteRuleHeadset.valueOf(obj.optString("headset")) }
                    .getOrDefault(FavoriteRuleHeadset.ANY),
                startMinute = if (start in 0..1439) start else -1,
                endMinute = if (end in 0..1439) end else -1,
                charging = if (obj.has("charging")) obj.optBoolean("charging") else null,
                batteryBelow = obj.optInt("batteryBelow", 0).coerceIn(0, 100),
            )
        }.take(MAX_SLEEP_RULES)
    }.getOrDefault(emptyList())
}
