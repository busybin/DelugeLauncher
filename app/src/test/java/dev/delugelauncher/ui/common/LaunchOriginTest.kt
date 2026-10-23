// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import android.content.ComponentName
import android.graphics.Rect
import dev.delugelauncher.data.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchOriginTest {

    private fun app(serial: Long = 0L, pkg: String = "com.example") = AppInfo(
        componentName = ComponentName(pkg, "$pkg.Main"),
        label = "Example",
        userSerial = serial,
    )

    @Test
    fun `bounds are found by the row key`() {
        val row = app()
        LaunchOrigin.note(row, Rect(10, 20, 110, 120))
        assertEquals(Rect(10, 20, 110, 120), LaunchOrigin.of(row))
    }

    @Test
    fun `a second profile is a different row`() {
        val main = app()
        val second = app(serial = 12)
        LaunchOrigin.note(main, Rect(0, 0, 50, 50))
        LaunchOrigin.note(second, Rect(60, 60, 110, 110))
        assertEquals(Rect(60, 60, 110, 110), LaunchOrigin.of(second))
        assertEquals(
            Rect(60, 60, 110, 110),
            LaunchOrigin.of(second.componentName, 12),
        )
    }

    @Test
    fun `an empty rectangle is not recorded`() {
        val row = app(pkg = "com.empty")
        LaunchOrigin.note(row, Rect(5, 5, 5, 5))
        assertNull(LaunchOrigin.of(row))
    }
}
