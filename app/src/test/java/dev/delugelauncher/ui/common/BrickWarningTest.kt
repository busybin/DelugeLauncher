// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import android.content.ComponentName
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.delugelauncher.data.AppInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one dialog between a tap and a phone with nothing left to tap on.
 *
 * What matters: it asks for the packages that hold the phone together, it asks exactly once,
 * a cancel really cancels, and an ordinary app never sees it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrickWarningTest {

    @get:Rule
    val compose = createComposeRule()

    private fun app(packageName: String, label: String) =
        AppInfo(ComponentName(packageName, "$packageName.Main"), label)

    private val systemUi = app("com.android.systemui", "System UI")

    @Test
    fun `a package that holds the phone together asks first`() {
        val state = BrickWarningState(setOf(systemUi.packageName))
        var ran = false
        compose.setContent { BrickWarningDialog(state) }

        compose.runOnIdle { state.guard(listOf(systemUi)) { ran = true } }

        compose.onNodeWithText("This can leave the phone unusable").assertExists()
        compose.onNodeWithText("System UI", substring = true).assertExists()
        assertFalse(ran)

        compose.onNodeWithText("Freeze anyway").performClick()

        assertTrue(ran)
        compose.onNodeWithText("This can leave the phone unusable").assertDoesNotExist()
    }

    @Test
    fun `cancelling leaves the action undone`() {
        val state = BrickWarningState(setOf(systemUi.packageName))
        var ran = false
        compose.setContent { BrickWarningDialog(state) }

        compose.runOnIdle { state.guard(listOf(systemUi)) { ran = true } }
        compose.onNodeWithText("Cancel").performClick()

        assertFalse(ran)
        compose.onNodeWithText("This can leave the phone unusable").assertDoesNotExist()
    }

    @Test
    fun `an ordinary package runs without asking`() {
        val state = BrickWarningState(emptySet())
        var ran = false
        compose.setContent { BrickWarningDialog(state) }

        compose.runOnIdle { state.guard(listOf(app("com.example.notes", "Notes"))) { ran = true } }

        assertTrue(ran)
        compose.onNodeWithText("This can leave the phone unusable").assertDoesNotExist()
    }

    @Test
    fun `a freeze-list package without a launcher entry asks too`() {
        val state = BrickWarningState(setOf(systemUi.packageName))
        var ran = false
        compose.setContent { BrickWarningDialog(state) }

        compose.runOnIdle {
            state.guardPackages(listOf(systemUi.packageName), labelOf = { "System UI" }) {
                ran = true
            }
        }

        compose.onNodeWithText("This can leave the phone unusable").assertExists()
        compose.onNodeWithText("System UI", substring = true).assertExists()
        assertFalse(ran)

        compose.onNodeWithText("Freeze anyway").performClick()
        assertTrue(ran)
    }

    @Test
    fun `a profile key asks for the package underneath it`() {
        val state = BrickWarningState(setOf(systemUi.packageName))
        var ran = false
        compose.setContent { BrickWarningDialog(state) }

        compose.runOnIdle {
            state.guardPackages(
                listOf("${systemUi.packageName}|10"),
                labelOf = { "System UI" },
            ) { ran = true }
        }

        compose.onNodeWithText("This can leave the phone unusable").assertExists()
        assertFalse(ran)
    }
}
