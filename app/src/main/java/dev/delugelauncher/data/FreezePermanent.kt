// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

/**
 * Carries the old pin's hidden meaning over to the permanent flag, once.
 *
 * Before the permanent flag existed, a pin meant two things at once: there is a home-screen
 * shortcut, and Unfreeze all must leave the app frozen. Only the second became permanent.
 * The seed is the pin on a row that is still listed under a method that actually persists:
 * a stopped app is not frozen, so making it permanent would promise a state STOP cannot keep.
 */
object FreezePermanent {

    fun seed(
        pinned: Set<String>,
        methods: Map<String, FreezeMethod>,
        existing: Set<String> = emptySet(),
    ): Set<String> = existing + pinned.filter { key ->
        methods[key]?.let { it != FreezeMethod.STOP } == true
    }
}
