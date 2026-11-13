// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepRuleTest {

    private fun context(
        wifi: String? = null,
        headset: Boolean = false,
        minute: Int = 12 * 60,
        charging: Boolean = false,
        battery: Int = 80,
    ) = RuleContext(
        wifiSsid = wifi,
        headset = headset,
        minuteOfDay = minute,
        charging = charging,
        batteryPercent = battery,
    )

    private fun rule(
        id: String = "r1",
        action: SleepAction = SleepAction.FREEZE,
        tokens: Set<String> = emptySet(),
        wifi: FavoriteRuleWifi = FavoriteRuleWifi.ANY,
        names: Set<String> = emptySet(),
        headset: FavoriteRuleHeadset = FavoriteRuleHeadset.ANY,
        start: Int = -1,
        end: Int = -1,
        charging: Boolean? = null,
        batteryBelow: Int = 0,
        enabled: Boolean = true,
    ) = SleepRule(
        id = id,
        enabled = enabled,
        action = action,
        tokens = tokens,
        wifi = wifi,
        wifiNames = names,
        headset = headset,
        startMinute = start,
        endMinute = end,
        charging = charging,
        batteryBelow = batteryBelow,
    )

    @Test
    fun `an empty token set means the whole freeze list`() {
        val listed = setOf("com.a/com.a.Main", "com.b/com.b.Main")
        val decision = listOf(rule(start = 23 * 60, end = 7 * 60))
            .decision(context(minute = 2 * 60), listed)
        assertEquals(listed, decision.sleep)
        assertTrue(decision.wake.isEmpty())
    }

    @Test
    fun `a wifi rule only holds on the named network`() {
        val listed = setOf("com.a/com.a.Main")
        val rule = rule(wifi = FavoriteRuleWifi.ON, names = setOf("home"))
        assertEquals(listed, listOf(rule).decision(context(wifi = "home"), listed).sleep)
        assertTrue(listOf(rule).decision(context(wifi = "office"), listed).sleep.isEmpty())
    }

    @Test
    fun `a window over midnight holds on both sides`() {
        val listed = setOf("com.a/com.a.Main")
        val rule = rule(start = 23 * 60, end = 7 * 60)
        assertEquals(listed, listOf(rule).decision(context(minute = 23 * 60 + 30), listed).sleep)
        assertEquals(listed, listOf(rule).decision(context(minute = 6 * 60), listed).sleep)
        assertTrue(listOf(rule).decision(context(minute = 12 * 60), listed).sleep.isEmpty())
    }

    @Test
    fun `charging and battery are conditions of their own`() {
        val listed = setOf("com.a/com.a.Main")
        val charging = rule(charging = true)
        assertTrue(listOf(charging).decision(context(charging = false), listed).sleep.isEmpty())
        assertEquals(listed, listOf(charging).decision(context(charging = true), listed).sleep)

        val low = rule(batteryBelow = 20)
        assertTrue(listOf(low).decision(context(battery = 50), listed).sleep.isEmpty())
        assertEquals(listed, listOf(low).decision(context(battery = 15), listed).sleep)
    }

    @Test
    fun `an explicit wake beats a freeze on the same app`() {
        val listed = setOf("com.a/com.a.Main", "com.b/com.b.Main")
        val all = rule(id = "all")
        val keep = rule(
            id = "keep",
            action = SleepAction.THAW,
            tokens = setOf("com.b/com.b.Main"),
        )
        val decision = listOf(all, keep).decision(context(), listed)
        assertEquals(setOf("com.a/com.a.Main"), decision.sleep)
        assertEquals(setOf("com.b/com.b.Main"), decision.wake)
    }

    @Test
    fun `a disabled rule changes nothing`() {
        val listed = setOf("com.a/com.a.Main")
        assertTrue(listOf(rule(enabled = false)).decision(context(), listed).sleep.isEmpty())
    }

    @Test
    fun `conditions are counted for the editor`() {
        val rule = SleepRule(
            id = "r",
            wifi = FavoriteRuleWifi.ON,
            wifiNames = setOf("home"),
            headset = FavoriteRuleHeadset.CONNECTED,
            startMinute = 60,
            endMinute = 120,
            charging = true,
            batteryBelow = 30,
        )
        assertEquals(5, rule.conditions)
    }

    @Test
    fun `an equal start and end is not a time window`() {
        // It used to count as a condition and then match always; the editor now refuses it.
        val rule = SleepRule(id = "r", startMinute = 60, endMinute = 60)
        assertEquals(0, rule.conditions)
    }
}
