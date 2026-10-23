// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import android.content.ComponentName
import android.graphics.Rect
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.EntryKeys
import java.util.concurrent.ConcurrentHashMap

/**
 * Where each row's icon was last drawn, in window coordinates.
 *
 * The system grows a window out of the icon it was started from and shrinks it back on the
 * way home, provided it is told the rectangle. Rows record themselves as they are positioned;
 * a row that is off screen has no business being animated from, so the caller checks that the
 * rectangle is still on screen before handing it over.
 */
object LaunchOrigin {
    private val rects = ConcurrentHashMap<String, Rect>()

    fun note(app: AppInfo, rect: Rect) {
        if (rect.width() <= 0 || rect.height() <= 0) return
        rects[app.key] = Rect(rect)
    }

    fun of(app: AppInfo): Rect? = rects[app.key]?.let(::Rect)

    /** The contract names an activity and a profile, not one of our row keys. */
    fun of(component: ComponentName, userSerial: Long): Rect? =
        rects[EntryKeys.app(component.flattenToString(), userSerial)]?.let(::Rect)
}
