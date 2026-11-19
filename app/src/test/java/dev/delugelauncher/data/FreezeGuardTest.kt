// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.Context
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The guard's own switch. It is the only thing that decides which apps a freeze may refuse,
 * and the dialer is one of them rather than an exception to it: a phone whose SIM is data only
 * has no call to lose, and the switch says what turning it off releases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FreezeGuardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `with the guard off nothing is protected, the dialer included`() = runBlocking {
        val telecom = context.getSystemService(TelecomManager::class.java)
        shadowOf(telecom).setDefaultDialerPackage("com.example.dialer")
        assertEquals("com.example.dialer", FreezeProtection.dialer(context))

        val prefs = Prefs(context)
        prefs.setFreezeProtect(false)

        val manager = FreezeManager(context, prefs)

        assertEquals(emptySet<String>(), manager.protectedPackages())
    }
}
