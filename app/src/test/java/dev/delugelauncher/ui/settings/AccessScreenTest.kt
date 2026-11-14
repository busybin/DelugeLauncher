// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import dev.delugelauncher.data.FreezeAccess
import dev.delugelauncher.data.FreezeStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The access page is a status board: which channel answers, and what each one can and cannot
 * do. These checks keep the table honest about what it lists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AccessScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(status: FreezeStatus) {
        compose.setContent {
            AccessScreen(
                status = status,
                networkSupported = false,
                onRefresh = {},
                onOpenShizuku = {},
                onGetShizuku = {},
                onRemoveDeviceOwner = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `the capability table no longer lists uninstall`() {
        show(FreezeStatus(checked = true, access = FreezeAccess.ROOT, rootGranted = true))
        // Uninstall for one profile is gone from the freeze screen, so the table must not
        // keep advertising it.
        compose.onNodeWithText("Uninstall").assertDoesNotExist()
        compose.onNodeWithText("preinstalled only").assertDoesNotExist()
        // Three channels: root, Shizuku and this app's own device owner.
        compose.onAllNodesWithText("Root").assertCountEquals(2)
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
}
