// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A clock widget is drawn from a broadcast, not from the settings screen that wrote its rows,
 * so the gaps have to survive the trip through storage — and an old widget has to come back
 * with the rows where they used to be.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClockWidgetConfigGapTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a widget with no gaps stored keeps its rows where they were`() {
        val stored = ClockWidgetConfig.read(context, 7)
        assertEquals(0, stored.dateGapDp)
        assertEquals(0, stored.weatherGapDp)
        assertEquals(0, stored.notificationsGapDp)
        assertEquals(0, stored.agendaGapDp)
        assertEquals(0, stored.batteryGapDp)
    }

    @Test
    fun `each row keeps its own gap`() {
        val base = ClockWidgetConfig.read(context, 9)
        ClockWidgetConfig.write(
            context,
            9,
            base.copy(
                dateGapDp = 1,
                weatherGapDp = 2,
                notificationsGapDp = 3,
                agendaGapDp = 4,
                batteryGapDp = 5,
            ),
        )
        val read = ClockWidgetConfig.read(context, 9)
        assertEquals(1, read.dateGapDp)
        assertEquals(2, read.weatherGapDp)
        assertEquals(3, read.notificationsGapDp)
        assertEquals(4, read.agendaGapDp)
        assertEquals(5, read.batteryGapDp)
    }

    @Test
    fun `a gap outside the range is read back inside it`() {
        val base = ClockWidgetConfig.read(context, 11)
        ClockWidgetConfig.write(context, 11, base.copy(dateGapDp = 999, weatherGapDp = -4))
        val read = ClockWidgetConfig.read(context, 11)
        assertEquals(ClockWidgetConfig.GAP_RANGE.last, read.dateGapDp)
        assertEquals(ClockWidgetConfig.GAP_RANGE.first, read.weatherGapDp)
    }

    @Test
    fun `a removed widget does not leave its gaps for the next one`() {
        val base = ClockWidgetConfig.read(context, 13)
        ClockWidgetConfig.write(context, 13, base.copy(agendaGapDp = 20))
        ClockWidgetConfig.forget(context, 13)
        assertEquals(0, ClockWidgetConfig.read(context, 13).agendaGapDp)
    }
}
