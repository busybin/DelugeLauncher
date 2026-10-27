// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The lock-time flow's one decision: what to ask next. Every branch is a point where asking
 * the wrong thing either skips the protection the owner asked for or sends them to a screen
 * that has nothing left to do.
 */
class PrivateSpaceProtectionTest {

    @Test
    fun `no private space asks for one first`() {
        assertEquals(
            ProtectStep.CREATE,
            protectStepFor(
                PrivateSpaceKind.ABSENT,
                installedInSpace = null,
                installedInMain = true,
                shellAvailable = true,
            ),
        )
    }

    @Test
    fun `a locked space without a shell is opened before anything is decided`() {
        assertEquals(
            ProtectStep.UNLOCK,
            protectStepFor(
                PrivateSpaceKind.LOCKED,
                installedInSpace = null,
                installedInMain = true,
                shellAvailable = false,
            ),
        )
    }

    @Test
    fun `a locked space with a shell installs without opening it`() {
        assertEquals(
            ProtectStep.INSTALL_AUTO,
            protectStepFor(
                PrivateSpaceKind.LOCKED,
                installedInSpace = false,
                installedInMain = true,
                shellAvailable = true,
            ),
        )
    }

    @Test
    fun `an open space without the app asks for the install`() {
        assertEquals(
            ProtectStep.INSTALL_STORE,
            protectStepFor(
                PrivateSpaceKind.UNLOCKED,
                installedInSpace = false,
                installedInMain = true,
                shellAvailable = false,
            ),
        )
        assertEquals(
            ProtectStep.INSTALL_STORE,
            protectStepFor(
                PrivateSpaceKind.UNLOCKED,
                installedInSpace = null,
                installedInMain = true,
                shellAvailable = false,
            ),
        )
    }

    @Test
    fun `an open space with a shell installs without the store`() {
        assertEquals(
            ProtectStep.INSTALL_AUTO,
            protectStepFor(
                PrivateSpaceKind.UNLOCKED,
                installedInSpace = false,
                installedInMain = true,
                shellAvailable = true,
            ),
        )
    }

    @Test
    fun `a copy in both places asks for the outside one to be removed`() {
        assertEquals(
            ProtectStep.REMOVE_MAIN,
            protectStepFor(
                PrivateSpaceKind.UNLOCKED,
                installedInSpace = true,
                installedInMain = true,
                shellAvailable = false,
            ),
        )
    }

    @Test
    fun `only the private copy means there is nothing left to ask`() {
        assertEquals(
            ProtectStep.DONE,
            protectStepFor(
                PrivateSpaceKind.UNLOCKED,
                installedInSpace = true,
                installedInMain = false,
                shellAvailable = false,
            ),
        )
    }

    @Test
    fun `the removal step is finished without reading the space again`() {
        // The copy was seen in the space just before the uninstaller opened; the space may have
        // locked itself by the time the owner comes back, and an unlock prompt would be a
        // request to prove something that is already done.
        assertEquals(
            ProtectStep.DONE,
            protectStepFor(
                PrivateSpaceKind.LOCKED,
                installedInSpace = null,
                installedInMain = false,
                shellAvailable = false,
                afterRemoval = true,
            ),
        )
    }

    @Test
    fun `a cancelled uninstall leaves the removal question standing`() {
        assertEquals(
            ProtectStep.REMOVE_MAIN,
            protectStepFor(
                PrivateSpaceKind.UNLOCKED,
                installedInSpace = true,
                installedInMain = true,
                shellAvailable = false,
                afterRemoval = true,
            ),
        )
    }
}
