// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher

/**
 * Which way the theme is forced, or null to leave the system's own choice alone.
 *
 * Always-dark wins over the night schedule: the two switches are independent, and the one that
 * cannot be wrong at the wrong hour should be the one that decides when both are on.
 */
internal fun themeForceDark(alwaysDark: Boolean, autoDark: Boolean, nightNow: Boolean): Boolean? =
    when {
        alwaysDark -> true
        autoDark -> nightNow
        else -> null
    }
