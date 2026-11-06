// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepRuleJsonTest {

    @Test
    fun `a rule survives a round trip`() {
        val rule = SleepRule(
            id = "night",
            name = "Night",
            action = SleepAction.THAW,
            tokens = setOf("com.example.notes/com.example.notes.Main"),
            wifi = FavoriteRuleWifi.ON,
            wifiNames = setOf("home"),
            headset = FavoriteRuleHeadset.DISCONNECTED,
            startMinute = 23 * 60,
            endMinute = 7 * 60,
            charging = false,
            batteryBelow = 20,
        )
        assertEquals(listOf(rule), sleepRulesFromJson(sleepRulesToJson(listOf(rule))))
    }

    @Test
    fun `a rule with no optional condition keeps its defaults`() {
        val rule = SleepRule(id = "all")
        val back = sleepRulesFromJson(sleepRulesToJson(listOf(rule))).single()
        assertEquals(SleepAction.FREEZE, back.action)
        assertNull(back.charging)
        assertEquals(0, back.batteryBelow)
        assertEquals(-1, back.startMinute)
    }

    @Test
    fun `junk and unsafe ids are dropped instead of crashing`() {
        assertTrue(sleepRulesFromJson("not json").isEmpty())
        assertTrue(sleepRulesFromJson("""[{"id":"has space"}]""").isEmpty())
        assertTrue(sleepRulesFromJson("""[{"id":"ok","start":99999}]""").single().startMinute == -1)
    }
}
