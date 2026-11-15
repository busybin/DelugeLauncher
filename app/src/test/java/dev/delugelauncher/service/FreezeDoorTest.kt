// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.delugelauncher.FreezeToggleActivity
import org.junit.Assert.assertEquals
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
    fun `a per-app shortcut carries the token its target checks`() {
        val intent = FreezeIntents.toggleIntent(context, "org.fossify.notes", "secret")
        assertEquals(FreezeToggleActivity.ACTION_TOGGLE, intent.action)
        assertEquals(FreezeToggleActivity::class.java.name, intent.component?.className)
        assertEquals(
            "org.fossify.notes",
            intent.getStringExtra(FreezeToggleActivity.EXTRA_KEY),
        )
        assertEquals("secret", intent.getStringExtra(FreezeIntents.EXTRA_TOKEN))
    }
}
