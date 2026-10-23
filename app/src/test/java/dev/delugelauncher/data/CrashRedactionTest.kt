// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashRedactionTest {

    private fun redact(trace: String, vararg foreign: String) =
        CrashRedaction.redact(trace) { it in foreign }

    @Test
    fun `a package from another profile is replaced wherever it appears`() {
        val trace = """
            java.lang.RuntimeException: boom
                at com.secret.app.Widget.onUpdate(Widget.kt:12)
                at android.appwidget.AppWidgetHostView.updateAppWidget(AppWidgetHostView.java:1)
            ComponentInfo{com.secret.app/.Widget}
        """.trimIndent()
        val out = redact(trace, "com.secret.app")
        assertFalse(out.contains("com.secret.app"))
        assertTrue(out.contains("[private]"))
        // Ordinary framework names stay readable.
        assertTrue(out.contains("android.appwidget.AppWidgetHostView"))
    }

    @Test
    fun `the launcher's own package is left alone`() {
        val trace = "at dev.delugelauncher.ui.home.HomeRoute.launch(HomeRoute.kt:1)"
        assertEquals(trace, redact(trace, "com.secret.app"))
    }

    @Test
    fun `a package is not replaced inside a longer name`() {
        val trace = "com.foo.Bar and com.foobar.Baz"
        val out = redact(trace, "com.foo")
        assertFalse(out.contains("com.foo.Bar"))
        assertTrue(out.contains("com.foobar.Baz"))
    }

    @Test
    fun `a second profile suffix is taken out`() {
        val out = redact("pkg/.Main|u12", "pkg")
        assertFalse(out.contains("|u12"))
        assertTrue(out.contains("|u[profile]"))
    }

    @Test
    fun `the main profile suffix is kept`() {
        assertEquals("pkg/.Main|u0", redact("pkg/.Main|u0", "pkg"))
    }

    @Test
    fun `a trace with nothing foreign is returned unchanged`() {
        val trace = "java.lang.IllegalStateException\n    at dev.delugelauncher.MainActivity.onCreate(MainActivity.kt:80)"
        assertEquals(trace, redact(trace, "com.secret.app"))
    }
}
