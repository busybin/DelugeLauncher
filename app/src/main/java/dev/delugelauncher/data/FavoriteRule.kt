// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.json.JSONArray
import org.json.JSONObject

/** What a rule does to its favorites when its conditions hold. */
enum class FavoriteRuleAction { SHOW, HIDE }

/** Wi-Fi condition: any network, one of the listed ones, or anywhere but them. */
enum class FavoriteRuleWifi { ANY, ON, OFF }

/** Headset condition. */
enum class FavoriteRuleHeadset { ANY, CONNECTED, DISCONNECTED }

/**
 * A condition under which some favorites are shown or hidden.
 *
 * A favorite that appears in no rule is always shown. One that appears in a rule is shown only
 * while a rule that shows it holds — or, if every rule that names it hides it, while none of
 * those holds. [dailyLimit] hides it for the rest of the day once it has been opened that many
 * times, which is the one condition that counts something the launcher itself did.
 */
data class FavoriteRule(
    val id: String,
    val name: String = "",
    val enabled: Boolean = true,
    val action: FavoriteRuleAction = FavoriteRuleAction.SHOW,
    /** Favorite tokens: an app's key, or `folder:<id>`. */
    val tokens: Set<String> = emptySet(),
    val wifi: FavoriteRuleWifi = FavoriteRuleWifi.ANY,
    /** SSIDs the Wi-Fi condition names. Ignored while [wifi] is ANY. */
    val wifiNames: Set<String> = emptySet(),
    val headset: FavoriteRuleHeadset = FavoriteRuleHeadset.ANY,
    /** Minutes after midnight; both -1 for no time window. */
    val startMinute: Int = -1,
    val endMinute: Int = -1,
    /** Opens after which the favorites hide for the day. Zero leaves them alone. */
    val dailyLimit: Int = 0,
) {
    /** The conditions this rule actually carries, in the order the screen lists them. */
    val conditions: Int
        get() = listOf(
            // A Wi-Fi condition with no network named would hide its favorites everywhere,
            // which is never what someone meant. It counts as no condition until one is named.
            (wifi != FavoriteRuleWifi.ANY && wifiNames.isNotEmpty()),
            headset != FavoriteRuleHeadset.ANY,
            startMinute >= 0 && endMinute >= 0,
            dailyLimit > 0,
        ).count { it }
}

/** What the phone looks like at the moment a rule is evaluated. */
data class RuleContext(
    /** The connected Wi-Fi network's name, or null when not on Wi-Fi or not permitted. */
    val wifiSsid: String? = null,
    val headset: Boolean = false,
    /** Local time, minutes after midnight. */
    val minuteOfDay: Int = 0,
    /** How many times each favorite token has been opened today. */
    val opens: Map<String, Int> = emptyMap(),
)

/**
 * The favorite tokens the rules take off the home screen right now.
 *
 * Pure on purpose: which favorites are on screen is the kind of thing that is wrong quietly,
 * and this is the only part of it that can be answered without a phone.
 */
fun List<FavoriteRule>.hiddenTokens(context: RuleContext): Set<String> {
    val enabled = filter { it.enabled && it.tokens.isNotEmpty() }
    if (enabled.isEmpty()) return emptySet()
    val hidden = mutableSetOf<String>()
    enabled.flatMapTo(mutableSetOf()) { it.tokens }.forEach { token ->
        val rules = enabled.filter { token in it.tokens }
        val matched = rules.filter { it.matches(context) }
        val limited = rules.any {
            it.dailyLimit > 0 && (context.opens[token] ?: 0) >= it.dailyLimit
        }
        val anyHide = rules.any { it.action == FavoriteRuleAction.HIDE }
        val anyShow = rules.any { it.action == FavoriteRuleAction.SHOW }
        val visible = when {
            // A hide rule that holds wins over everything: it is the more specific request.
            matched.any { it.action == FavoriteRuleAction.HIDE } -> false
            anyShow -> matched.any { it.action == FavoriteRuleAction.SHOW }
            // Only hide rules name it: it is on screen until one of them holds.
            else -> true
        }
        if (!visible || limited) hidden += token
    }
    return hidden
}

private fun FavoriteRule.matches(context: RuleContext): Boolean {
    when (wifi) {
        FavoriteRuleWifi.ANY -> Unit
        // No name yet is not a condition: hiding everywhere would look like the rule ate the
        // favorites, and there would be nothing on screen to explain it.
        FavoriteRuleWifi.ON ->
            if (wifiNames.isNotEmpty() &&
                (context.wifiSsid == null || context.wifiSsid !in wifiNames)
            ) {
                return false
            }
        FavoriteRuleWifi.OFF ->
            if (wifiNames.isNotEmpty() && context.wifiSsid != null && context.wifiSsid in wifiNames) {
                return false
            }
    }
    when (headset) {
        FavoriteRuleHeadset.ANY -> Unit
        FavoriteRuleHeadset.CONNECTED -> if (!context.headset) return false
        FavoriteRuleHeadset.DISCONNECTED -> if (context.headset) return false
    }
    if (startMinute in 0..1439 && endMinute in 0..1439 && startMinute != endMinute) {
        val now = context.minuteOfDay
        val inside = if (startMinute < endMinute) {
            now >= startMinute && now < endMinute
        } else {
            // A window over midnight, which is the usual shape for "at night".
            now >= startMinute || now < endMinute
        }
        if (!inside) return false
    }
    return true
}

internal fun favoriteRulesToJson(rules: List<FavoriteRule>): String {
    val array = JSONArray()
    rules.forEach { rule ->
        array.put(
            JSONObject().apply {
                put("id", rule.id)
                if (rule.name.isNotBlank()) put("name", rule.name)
                put("enabled", rule.enabled)
                put("action", rule.action.name)
                put("tokens", JSONArray().apply { rule.tokens.sorted().forEach { put(it) } })
                put("wifi", rule.wifi.name)
                if (rule.wifiNames.isNotEmpty()) {
                    put("wifiNames", JSONArray().apply { rule.wifiNames.sorted().forEach { put(it) } })
                }
                put("headset", rule.headset.name)
                if (rule.startMinute >= 0) put("start", rule.startMinute)
                if (rule.endMinute >= 0) put("end", rule.endMinute)
                if (rule.dailyLimit > 0) put("dailyLimit", rule.dailyLimit)
            }
        )
    }
    return array.toString()
}

internal fun favoriteRulesFromJson(json: String?): List<FavoriteRule> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val id = obj.optString("id").takeIf { id ->
                id.isNotBlank() && id.length <= 32 &&
                    id.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' }
            } ?: return@mapNotNull null
            val tokensArray = obj.optJSONArray("tokens") ?: JSONArray()
            val tokens = (0 until tokensArray.length())
                .mapNotNull { tokensArray.optString(it).takeIf { s -> s.isNotBlank() && s.length <= 256 } }
                .take(MAX_RULE_TOKENS)
                .toSet()
            val namesArray = obj.optJSONArray("wifiNames") ?: JSONArray()
            val names = (0 until namesArray.length())
                .mapNotNull { namesArray.optString(it).takeIf { s -> s.isNotBlank() && s.length <= MAX_SSID } }
                .take(MAX_WIFI_NAMES)
                .toSet()
            FavoriteRule(
                id = id,
                name = obj.optString("name").take(MAX_RULE_NAME),
                enabled = obj.optBoolean("enabled", true),
                action = runCatching {
                    FavoriteRuleAction.valueOf(obj.optString("action", FavoriteRuleAction.SHOW.name))
                }.getOrDefault(FavoriteRuleAction.SHOW),
                tokens = tokens,
                wifi = runCatching {
                    FavoriteRuleWifi.valueOf(obj.optString("wifi", FavoriteRuleWifi.ANY.name))
                }.getOrDefault(FavoriteRuleWifi.ANY),
                wifiNames = names,
                headset = runCatching {
                    FavoriteRuleHeadset.valueOf(obj.optString("headset", FavoriteRuleHeadset.ANY.name))
                }.getOrDefault(FavoriteRuleHeadset.ANY),
                startMinute = obj.optInt("start", -1).takeIf { it in 0..1439 } ?: -1,
                endMinute = obj.optInt("end", -1).takeIf { it in 0..1439 } ?: -1,
                dailyLimit = obj.optInt("dailyLimit", 0).coerceIn(0, MAX_DAILY_LIMIT),
            )
        }.take(MAX_RULES)
    }.getOrDefault(emptyList())
}

internal const val MAX_RULES = 32
internal const val MAX_RULE_TOKENS = 200
internal const val MAX_WIFI_NAMES = 16
private const val MAX_SSID = 64
private const val MAX_RULE_NAME = 40
private const val MAX_DAILY_LIMIT = 99
