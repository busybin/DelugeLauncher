// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

import dev.delugelauncher.data.EdgeSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sliding off the letters onto the list and letting go on an app (#175).
 *
 * The gesture itself needs a finger, but the two decisions behind it do not: when a finger
 * counts as being over the list, and what the holder says about what it is over.
 */
class ReleaseToLaunchTest {
    private val enter = 28f
    private val exit = 16f

    @Test
    fun `reaching for the list needs to pass the enter threshold`() {
        assertFalse(overApps(20f, alreadyOver = false, enter, exit))
        assertTrue(overApps(30f, alreadyOver = false, enter, exit))
    }

    @Test
    fun `coming back out keeps it over the list until the exit threshold`() {
        // Between the two, a finger that is already over the list stays over it: at a single
        // threshold this is where the strip flickered.
        assertTrue(overApps(20f, alreadyOver = true, enter, exit))
        assertFalse(overApps(10f, alreadyOver = true, enter, exit))
    }

    @Test
    fun `the hovered app is what the release opens`() {
        val state = ScrubState()
        state.begin(EdgeSide.RIGHT)
        state.updateOnApps(true)
        state.setHover("com.example.alpha/.Main")
        assertTrue(state.onApps)
        assertEquals("com.example.alpha/.Main", state.hoverKey)
    }

    @Test
    fun `leaving the list for the letters drops the hover`() {
        val state = ScrubState()
        state.begin(EdgeSide.RIGHT)
        state.updateOnApps(true)
        state.setHover("com.example.alpha/.Main")
        state.updateOnApps(false)
        assertFalse(state.onApps)
        assertNull(state.hoverKey)
    }

    @Test
    fun `a cancelled gesture forgets the app it was over`() {
        val state = ScrubState()
        state.begin(EdgeSide.RIGHT)
        state.updateOnApps(true)
        state.setHover("com.example.alpha/.Main")
        state.cancel()
        assertFalse(state.onApps)
        assertNull(state.hoverKey)
    }

    @Test
    fun `a new gesture starts on the letters`() {
        val state = ScrubState()
        state.begin(EdgeSide.RIGHT)
        state.updateOnApps(true)
        state.begin(EdgeSide.LEFT)
        assertFalse(state.onApps)
        assertNull(state.hoverKey)
    }

    @Test
    fun `letting go decides between opening, closing and nothing`() {
        assertEquals(ReleaseEffect.LAUNCH, releaseEffect(onApps = true, hoverKey = "k"))
        assertEquals(ReleaseEffect.DISMISS, releaseEffect(onApps = true, hoverKey = null))
        assertEquals(ReleaseEffect.PARK, releaseEffect(onApps = false, hoverKey = null))
        // Not on the list at all: a letter was being picked, which only parks the list.
        assertEquals(ReleaseEffect.PARK, releaseEffect(onApps = false, hoverKey = "k"))
    }

    @Test
    fun `the thresholds come from the strip and not from the test`() {
        assertTrue(overApps(ON_APPS_ENTER_DP + 1f, alreadyOver = false, ON_APPS_ENTER_DP, ON_APPS_EXIT_DP))
        assertFalse(overApps(ON_APPS_ENTER_DP - 1f, alreadyOver = false, ON_APPS_ENTER_DP, ON_APPS_EXIT_DP))
        assertTrue(overApps(ON_APPS_EXIT_DP + 1f, alreadyOver = true, ON_APPS_ENTER_DP, ON_APPS_EXIT_DP))
        assertFalse(overApps(ON_APPS_EXIT_DP - 1f, alreadyOver = true, ON_APPS_ENTER_DP, ON_APPS_EXIT_DP))
    }

    @Test
    fun `a two column row splits at the overlap, not the middle`() {
        // 1000px of content inset by 40, cells pulled together by 28: each is 514 wide, the
        // right one starts 28 inside the middle, so the boundary sits at 486 of content.
        assertTrue(isRightColumn(localX = 530f, contentLeft = 40f, contentWidth = 1000f, overlapPx = 28f))
        assertFalse(isRightColumn(localX = 500f, contentLeft = 40f, contentWidth = 1000f, overlapPx = 28f))
        // In the gutter before the content, which is no cell at all.
        assertFalse(isRightColumn(localX = 20f, contentLeft = 40f, contentWidth = 1000f, overlapPx = 28f))
    }

    @Test
    fun `the row under a still finger is asked again, not remembered`() {
        val state = ScrubState()
        state.begin(EdgeSide.RIGHT)
        var asked = 0
        state.appAt = { _, _ -> asked++; if (asked == 1) "com.stale/.Main" else "com.fresh/.Main" }
        state.updateOnApps(true)
        state.noteAppsFinger(100f, 300f)
        state.refreshHover()
        assertEquals("com.stale/.Main", state.hoverKey)
        // The list settled under the finger: the same point is a different row now.
        state.refreshHover()
        assertEquals("com.fresh/.Main", state.hoverKey)
        assertEquals(ReleaseEffect.LAUNCH, releaseEffect(state.onApps, state.hoverKey))
    }

    @Test
    fun `asking without a list leaves the hover alone`() {
        val state = ScrubState()
        state.begin(EdgeSide.RIGHT)
        state.updateOnApps(true)
        state.setHover("com.example.alpha/.Main")
        state.refreshHover()
        assertEquals("com.example.alpha/.Main", state.hoverKey)
    }

    @Test
    fun `letting go decisions come from the refreshed row`() {
        val state = ScrubState()
        state.begin(EdgeSide.RIGHT)
        var key: String? = "com.first/.Main"
        state.appAt = { _, _ -> key }
        state.updateOnApps(true)
        state.noteAppsFinger(0f, 0f)
        state.refreshHover()
        key = null // the row under the finger slid away into a heading
        state.refreshHover()
        assertEquals(ReleaseEffect.DISMISS, releaseEffect(state.onApps, state.hoverKey))
    }
}
