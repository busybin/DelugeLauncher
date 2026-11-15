// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Both doors have to end at the broadcast receiver: a finished activity is a cached process,
 * and the system freezes it before a multi-pass freeze or lift can return. The link door used
 * to do the work itself, which is why `deluge://unfreeze` swallowed most of its lifts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FreezeDoorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the link door hands its work to the receiver`() {
        val intent = FreezeIntents.receiverIntent(
            context,
            FreezeIntents.ACTION_UNFREEZE,
            "org.fossify.notes",
            "secret",
        )
        assertEquals(FreezeActionReceiver::class.java.name, intent.component?.className)
        assertEquals(FreezeIntents.ACTION_UNFREEZE, intent.action)
        assertEquals("org.fossify.notes", intent.getStringExtra(FreezeIntents.EXTRA_PACKAGE))
        assertEquals("secret", intent.getStringExtra(FreezeIntents.EXTRA_TOKEN))
    }

    @Test
    fun `a deluge link maps onto the same actions`() {
        assertEquals(
            FreezeIntents.ACTION_FREEZE,
            FreezeIntents.actionOf(Uri.parse("deluge://freeze?package=com.example")),
        )
        assertEquals(
            FreezeIntents.ACTION_UNFREEZE_ALL,
            FreezeIntents.actionOf(Uri.parse("deluge://unfreeze_all")),
        )
        assertEquals(null, FreezeIntents.actionOf(Uri.parse("deluge://something_else")))
    }

    @Test
    fun `only this app or the token may toggle a per-app shortcut`() {
        // The home screen starts its own shortcut: no token needed.
        assertTrue(FreezeIntents.toggleAllowed(null, null, "dev.delugelauncher", "dev.delugelauncher"))
        // A stranger's launch comes from its own package and carries no token.
        assertFalse(FreezeIntents.toggleAllowed(null, null, "com.example.evil", "dev.delugelauncher"))
        // A newer shortcut carries the token, so any launcher may start it.
        assertTrue(
            FreezeIntents.toggleAllowed("secret", "secret", "com.example.launcher", "dev.delugelauncher"),
        )
        assertFalse(
            FreezeIntents.toggleAllowed("secret", "guess", "com.example.launcher", "dev.delugelauncher"),
        )
        // No record of the caller is not a reason to trust it.
        assertFalse(FreezeIntents.toggleAllowed(null, null, null, "dev.delugelauncher"))
    }
}
