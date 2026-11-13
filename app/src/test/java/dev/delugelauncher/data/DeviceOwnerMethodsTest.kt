// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOwnerMethodsTest {

    @Test
    fun `suspend and hide map to policy calls`() {
        assertEquals(PolicyOperation.SUSPEND, DeviceOwnerMethods.operation(FreezeMethod.SUSPEND))
        assertEquals(PolicyOperation.HIDE, DeviceOwnerMethods.operation(FreezeMethod.HIDE))
    }

    @Test
    fun `disable and stop have no policy call`() {
        assertNull(DeviceOwnerMethods.operation(FreezeMethod.DISABLE))
        assertNull(DeviceOwnerMethods.operation(FreezeMethod.STOP))
    }

    @Test
    fun `a device owner offers suspend and hide only`() {
        val status = FreezeStatus(
            checked = true,
            access = FreezeAccess.DEVICE_OWNER,
            deviceOwner = true,
        )
        assertTrue(status.supports(FreezeMethod.SUSPEND))
        assertTrue(status.supports(FreezeMethod.HIDE))
        assertFalse(status.supports(FreezeMethod.DISABLE))
        assertFalse(status.supports(FreezeMethod.STOP))
    }

    @Test
    fun `a device owner with root keeps every method`() {
        val status = FreezeStatus(
            checked = true,
            access = FreezeAccess.DEVICE_OWNER,
            rootGranted = true,
            deviceOwner = true,
        )
        FreezeMethod.entries.forEach { assertTrue(it.name, status.supports(it)) }
    }

    @Test
    fun `root alone still allows hiding`() {
        val status = FreezeStatus(checked = true, access = FreezeAccess.ROOT, rootGranted = true)
        FreezeMethod.entries.forEach { assertTrue(it.name, status.supports(it)) }
    }

    @Test
    fun `shizuku cannot hide but can disable and stop`() {
        val status = FreezeStatus(
            checked = true,
            access = FreezeAccess.SHIZUKU,
            shizukuAuthorized = true,
        )
        assertTrue(status.supports(FreezeMethod.SUSPEND))
        assertTrue(status.supports(FreezeMethod.DISABLE))
        assertTrue(status.supports(FreezeMethod.STOP))
        assertFalse(status.supports(FreezeMethod.HIDE))
    }

    @Test
    fun `no access supports nothing`() {
        val status = FreezeStatus(checked = true, access = FreezeAccess.NONE)
        FreezeMethod.entries.forEach { assertFalse(it.name, status.supports(it)) }
    }
}
