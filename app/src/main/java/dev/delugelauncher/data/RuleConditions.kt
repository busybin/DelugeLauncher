// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

/**
 * The conditions a Wi-Fi, headset and time rule share.
 *
 * [FavoriteRule] and [SleepRule] ask the same question — is the phone in this state right now
 * — and answering it twice would let one of the two drift. The extra conditions sleep rules
 * carry (charging, battery) live in [SleepRule], because a favorite has no business hiding
 * itself for either.
 */
internal object RuleConditions {
    fun match(
        wifi: FavoriteRuleWifi,
        wifiNames: Set<String>,
        headset: FavoriteRuleHeadset,
        startMinute: Int,
        endMinute: Int,
        context: RuleContext,
    ): Boolean {
        when (wifi) {
            FavoriteRuleWifi.ANY -> Unit
            // No name yet is not a condition: hiding everywhere would look like the rule ate
            // the favorites, and there would be nothing on screen to explain it.
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
}
