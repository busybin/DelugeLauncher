// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The theme decision: a switch that must not be wrong at the wrong hour.
 *
 * Leaving it null is what lets the system decide, so every non-null case here is a promise to
 * the user that their choice overrides the phone's.
 */
class ThemeModeTest {
    @Test
    fun alwaysDarkWinsAtAnyHour() {
        assertEquals(true, themeForceDark(alwaysDark = true, autoDark = false, nightNow = false))
        assertEquals(true, themeForceDark(alwaysDark = true, autoDark = true, nightNow = false))
    }

    @Test
    fun nightScheduleOnlyDarkensInsideItsWindow() {
        assertEquals(true, themeForceDark(alwaysDark = false, autoDark = true, nightNow = true))
        assertEquals(false, themeForceDark(alwaysDark = false, autoDark = true, nightNow = false))
    }

    @Test
    fun neitherSwitchLeavesItToTheSystem() {
        assertEquals(null, themeForceDark(alwaysDark = false, autoDark = false, nightNow = true))
    }
}
