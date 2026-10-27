// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteRuleTest {

    private val quiet = RuleContext(wifiSsid = null, headset = false, minuteOfDay = 10 * 60)

    private fun rule(
        id: String = "r1",
        tokens: Set<String> = setOf("a/A"),
        action: FavoriteRuleAction = FavoriteRuleAction.SHOW,
        wifi: FavoriteRuleWifi = FavoriteRuleWifi.ANY,
        names: Set<String> = emptySet(),
        headset: FavoriteRuleHeadset = FavoriteRuleHeadset.ANY,
        start: Int = -1,
        end: Int = -1,
        limit: Int = 0,
        enabled: Boolean = true,
    ) = FavoriteRule(
        id = id,
        tokens = tokens,
        action = action,
        wifi = wifi,
        wifiNames = names,
        headset = headset,
        startMinute = start,
        endMinute = end,
        dailyLimit = limit,
        enabled = enabled,
    )

    @Test
    fun `a favorite named by no rule always shows`() {
        val hidden = listOf(rule(tokens = setOf("b/B"))).hiddenTokens(quiet)
        assertFalse("a/A" in hidden)
    }

    @Test
    fun `a show rule hides its favorites until its conditions hold`() {
        val onHome = rule(wifi = FavoriteRuleWifi.ON, names = setOf("Home"))
        assertTrue("a/A" in listOf(onHome).hiddenTokens(quiet))
        assertTrue("a/A" in listOf(onHome).hiddenTokens(quiet.copy(wifiSsid = "Cafe")))
        assertFalse("a/A" in listOf(onHome).hiddenTokens(quiet.copy(wifiSsid = "Home")))
    }

    @Test
    fun `a hide rule hides only while its conditions hold`() {
        val away = rule(action = FavoriteRuleAction.HIDE, wifi = FavoriteRuleWifi.OFF, names = setOf("Home"))
        assertFalse("a/A" in listOf(away).hiddenTokens(quiet.copy(wifiSsid = "Home")))
        assertTrue("a/A" in listOf(away).hiddenTokens(quiet))
    }

    @Test
    fun `a matching hide rule beats a matching show rule`() {
        val rules = listOf(
            rule(id = "show", tokens = setOf("a/A")),
            rule(id = "hide", tokens = setOf("a/A"), action = FavoriteRuleAction.HIDE),
        )
        assertTrue("a/A" in rules.hiddenTokens(quiet))
    }

    @Test
    fun `several show rules keep a favorite while any of them holds`() {
        val rules = listOf(
            rule(id = "home", wifi = FavoriteRuleWifi.ON, names = setOf("Home")),
            rule(id = "headphones", headset = FavoriteRuleHeadset.CONNECTED),
        )
        assertTrue("a/A" in rules.hiddenTokens(quiet))
        assertFalse("a/A" in rules.hiddenTokens(quiet.copy(headset = true)))
        assertFalse("a/A" in rules.hiddenTokens(quiet.copy(wifiSsid = "Home")))
    }

    @Test
    fun `a time window holds inside it and wraps over midnight`() {
        val evening = rule(start = 22 * 60, end = 2 * 60)
        assertFalse("a/A" in listOf(evening).hiddenTokens(quiet.copy(minuteOfDay = 23 * 60)))
        assertFalse("a/A" in listOf(evening).hiddenTokens(quiet.copy(minuteOfDay = 60)))
        assertTrue("a/A" in listOf(evening).hiddenTokens(quiet.copy(minuteOfDay = 12 * 60)))
        val daytime = rule(start = 9 * 60, end = 17 * 60)
        assertFalse("a/A" in listOf(daytime).hiddenTokens(quiet.copy(minuteOfDay = 9 * 60)))
        assertTrue("a/A" in listOf(daytime).hiddenTokens(quiet.copy(minuteOfDay = 17 * 60)))
    }

    @Test
    fun `the daily limit hides a favorite once it has been opened that many times`() {
        val limited = rule(limit = 2)
        assertFalse("a/A" in listOf(limited).hiddenTokens(quiet.copy(opens = mapOf("a/A" to 1))))
        assertTrue("a/A" in listOf(limited).hiddenTokens(quiet.copy(opens = mapOf("a/A" to 2))))
    }

    @Test
    fun `a disabled rule is ignored entirely`() {
        val rules = listOf(rule(wifi = FavoriteRuleWifi.ON, names = setOf("Home"), enabled = false))
        assertFalse("a/A" in rules.hiddenTokens(quiet))
    }

    @Test
    fun `a Wi-Fi condition with no network named holds everywhere`() {
        val unnamed = rule(wifi = FavoriteRuleWifi.ON)
        assertEquals(0, unnamed.conditions)
        assertFalse("a/A" in listOf(unnamed).hiddenTokens(quiet))
    }

    @Test
    fun `a rule with no conditions holds everywhere`() {
        assertFalse("a/A" in listOf(rule()).hiddenTokens(quiet))
    }

    @Test
    fun `rules survive a JSON round-trip`() {
        val rules = listOf(
            FavoriteRule(
                id = "r1",
                name = "Home",
                tokens = setOf("a/A", "folder:f1"),
                wifi = FavoriteRuleWifi.ON,
                wifiNames = setOf("HomeNet"),
                headset = FavoriteRuleHeadset.CONNECTED,
                startMinute = 9 * 60,
                endMinute = 17 * 60,
                dailyLimit = 3,
            )
        )
        assertEquals(rules, favoriteRulesFromJson(favoriteRulesToJson(rules)))
    }

    @Test
    fun `malformed JSON yields no rules rather than throwing`() {
        assertTrue(favoriteRulesFromJson("{not json").isEmpty())
        assertTrue(favoriteRulesFromJson("").isEmpty())
        assertTrue(favoriteRulesFromJson(null).isEmpty())
    }

    @Test
    fun `a rule with an unusable id is dropped`() {
        val json = """[{"id":"bad id","tokens":["a/A"]},{"id":"ok","tokens":["a/A"]}]"""
        val restored = favoriteRulesFromJson(json)
        assertEquals(listOf("ok"), restored.map { it.id })
    }

    @Test
    fun `values outside their ranges are clamped rather than trusted`() {
        val json = """[{"id":"r","tokens":["a/A"],"start":-5,"dailyLimit":5000}]"""
        val rule = favoriteRulesFromJson(json).single()
        assertEquals(-1, rule.startMinute)
        assertEquals(99, rule.dailyLimit)
    }
}
