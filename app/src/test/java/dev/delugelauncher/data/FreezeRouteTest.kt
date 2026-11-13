// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeRouteTest {

    private fun route(method: FreezeMethod, channel: FreezeChannel) =
        FreezeRoute.executors(method, channel)

    @Test
    fun `auto keeps the old order for every method`() {
        assertEquals(
            listOf(
                FreezeExecutor.POLICY,
                FreezeExecutor.BINDER,
                FreezeExecutor.SHELL,
            ),
            route(FreezeMethod.SUSPEND, FreezeChannel.AUTO),
        )
    }

    @Test
    fun `auto reaches root for hiding because a plain shell cannot hide`() {
        assertEquals(
            listOf(
                FreezeExecutor.POLICY,
                FreezeExecutor.BINDER,
                FreezeExecutor.ROOT_SHELL,
            ),
            route(FreezeMethod.HIDE, FreezeChannel.AUTO),
        )
    }

    @Test
    fun `an explicit channel never falls back to another one`() {
        assertEquals(listOf(FreezeExecutor.BINDER), route(FreezeMethod.SUSPEND, FreezeChannel.SHIZUKU))
        assertEquals(listOf(FreezeExecutor.ROOT_SHELL), route(FreezeMethod.SUSPEND, FreezeChannel.ROOT))
        assertEquals(
            listOf(FreezeExecutor.POLICY),
            route(FreezeMethod.SUSPEND, FreezeChannel.OWNER),
        )
    }

    @Test
    fun `every method has at least one executor on every channel`() {
        FreezeMethod.entries.forEach { method ->
            FreezeChannel.entries.forEach { channel ->
                assertTrue("$method/$channel", route(method, channel).isNotEmpty())
            }
        }
    }

    @Test
    fun `a plain shell is never asked to hide`() {
        FreezeMethod.entries.forEach { method ->
            FreezeChannel.entries.forEach { channel ->
                if (method == FreezeMethod.HIDE) {
                    assertFalse("$channel", FreezeExecutor.SHELL in route(method, channel))
                }
            }
        }
    }
}
