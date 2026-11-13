// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessProvidersTest {

    @Test
    fun `the shizuku family is recognised`() {
        assertEquals(AccessProvider.SHIZUKU, AccessProviders.known("moe.shizuku.privileged.api"))
        assertEquals(AccessProvider.SHIZUKU, AccessProviders.known("com.hamondev.shevery"))
    }

    @Test
    fun `root managers are recognised`() {
        assertEquals(AccessProvider.ROOT_MANAGER, AccessProviders.known("com.topjohnwu.magisk"))
        assertEquals(AccessProvider.ROOT_MANAGER, AccessProviders.known("me.weishu.kernelsu"))
    }

    @Test
    fun `an ordinary app is not an access provider`() {
        assertNull(AccessProviders.known("com.example.notes"))
    }
}
