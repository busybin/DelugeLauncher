// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import androidx.core.text.BidiFormatter

/**
 * Joins localized fragments with a middle dot. Each fragment is isolated first, so an RTL
 * layout keeps the order the caller wrote instead of letting the neutral dot reorder them.
 */
fun joinMiddleDot(parts: List<String>): String =
    parts.joinToString(" \u00B7 ") { BidiFormatter.getInstance().unicodeWrap(it) }
