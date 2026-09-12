// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.content.ComponentName
import android.os.UserHandle
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
    /** Which profile owns this activity: work, private space, or the main one. */
    val user: UserHandle? = null,
    /** The profile's serial. Zero is the main profile, which is what every old key assumed. */
    val userSerial: Long = 0L,
) {
    // Held rather than derived: this is the map key for overrides, favorites and list item
    // keys, so it is asked for several times per visible row per frame while scrubbing, and
    // flattenToString() builds a new string every time.
    //
    // The main profile's key is byte-for-byte what it was before profiles existed, so every
    // stored favorite, rename, icon and hidden entry still matches. Only apps from a second
    // profile carry the suffix, and those could not have been stored before anyway.
    val key: String =
        if (userSerial == 0L) componentName.flattenToString()
        else componentName.flattenToString() + "|u" + userSerial

    val packageName: String get() = componentName.packageName
}