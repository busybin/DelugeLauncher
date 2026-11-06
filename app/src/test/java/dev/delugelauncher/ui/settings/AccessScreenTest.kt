// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.settings

import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.delugelauncher.data.FreezeAccess
import dev.delugelauncher.data.FreezeStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Dhizuku channel has three steps before it freezes anything: installed, permitted, and
 * delegated package access. The card has to name the step that is missing, or a user who
 * already granted permission is asked to grant it again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AccessScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(status: FreezeStatus, onEnableDhizuku: () -> Unit = {}) {
        compose.setContent {
            AccessScreen(
                status = status,
                onRefresh = {},
                onOpenShizuku = {},
                onGetShizuku = {},
                onRemoveDeviceOwner = {},
                onGrantDhizuku = {},
                onEnableDhizuku = onEnableDhizuku,
                onOpenDhizuku = {},
                onGetDhizuku = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `permitted Dhizuku offers package access, not the permission again`() {
        var enabled = 0
        show(
            FreezeStatus(
                checked = true,
                access = FreezeAccess.DHIZUKU,
                dhizukuInstalled = true,
                dhizukuPermission = true,
            ),
        ) { enabled++ }

        // The card is folded until it is asked for; the title is the handle. The capability
        // table now has a Dhizuku column too, so pick the node that can be clicked.
        compose.onAllNodesWithText("Dhizuku").filterToOne(hasClickAction()).performClick()
        // The card unfolds below the fold in the test's 891dp viewport, so the button has
        // to be scrolled into view before a click can land on it.
        compose.onNodeWithText("Allow package access").performScrollTo().performClick()
        assertEquals(1, enabled)
    }

    @Test
    fun `the capability table no longer lists uninstall`() {
        show(FreezeStatus(checked = true, access = FreezeAccess.ROOT, rootGranted = true))
        // Uninstall for one profile is gone from the freeze screen, so the table must not
        // keep advertising it.
        compose.onNodeWithText("Uninstall").assertDoesNotExist()
        compose.onNodeWithText("preinstalled only").assertDoesNotExist()
        // Dhizuku is a column of its own, not a footnote under the owner column.
        compose.onAllNodesWithText("Dhizuku").assertCountEquals(2)
    }

    @Test
    fun `removing the owner is offered without unfolding the card`() {
        show(
            FreezeStatus(
                checked = true,
                access = FreezeAccess.DEVICE_OWNER,
                deviceAdmin = true,
                deviceOwner = true,
            ),
        )
        // The way out used to hide behind the collapsed card and a scroll; it now sits
        // under the header, so this node exists with no click and no scroll.
        compose.onNodeWithText("Remove device owner").assertExists()
    }

    @Test
    fun `a delegated Dhizuku shows the channel as ready`() {
        show(
            FreezeStatus(
                checked = true,
                access = FreezeAccess.DHIZUKU,
                dhizukuInstalled = true,
                dhizukuPermission = true,
                dhizukuDelegated = true,
            ),
        )
        compose.onNodeWithText("Dhizuku is answering").assertExists()
    }
}
