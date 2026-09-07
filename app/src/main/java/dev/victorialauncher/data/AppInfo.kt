// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.content.ComponentName
import androidx.compose.runtime.Immutable

/**
 * A ComponentName is immutable, but it comes from the platform with no stability information,
 * so Compose infers this whole class as unstable and stops every row that takes one from ever
 * skipping recomposition. The annotation states what is already true.
 */
@Immutable
data class AppInfo(
    val componentName: ComponentName,
    val label: String,
) {
    // Held rather than derived: this is the map key for overrides, favorites and list item
    // keys, so it is asked for several times per visible row per frame while scrubbing, and
    // flattenToString() builds a new string every time.
    val key: String = componentName.flattenToString()
    val packageName: String get() = componentName.packageName
}