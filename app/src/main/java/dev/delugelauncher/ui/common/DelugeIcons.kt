// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The handful of icons this app draws that `material-icons-core` does not carry, as plain
 * path data — no `material-icons-extended` on the classpath.
 *
 * Why they are here and not handed to a library: that library ships several thousand
 * icons to deliver the ~15 used here, and the whole lot lands in the APK and in every
 * build's resource pass. It costs the release build nothing, since R8 strips what is
 * unreachable, but the debug build carries all of it and every build pays the merge.
 *
 * The paths are written out rather than borrowed, and each is simple geometry on the
 * Material 24dp grid with a 2-unit stroke where the original is a line: they are meant
 * to be obvious to read and to change, not to be exact copies. Nothing here is drawn
 * with anything fancier than a line, a circle and a polygon.
 *
 * Tint works on both fill and stroke, so an [androidx.compose.material3.Icon] wrapping
 * one of these tints like any other.
 */
object DelugeIcons {

    val MusicNote: ImageVector = vector("VicMusicNote") {
        stroke("M9.5 17V7M17.5 15.5V4.5M9.5 7l8 -2.5", 2f)
        fill("M6.5,17m-2.6,0a2.6,2.6 0,1 1,5.2 0a2.6,2.6 0,1 1,-5.2 0")
        fill("M14.5,15.5m-2.6,0a2.6,2.6 0,1 1,5.2 0a2.6,2.6 0,1 1,-5.2 0")
    }

    val SkipPrevious: ImageVector = vector("VicSkipPrevious") {
        fill("M6,6h2.4v12H6z")
        fill("M18,6v12L9.5,12z")
    }

    val SkipNext: ImageVector = vector("VicSkipNext") {
        fill("M15.6,6H18v12h-2.4z")
        fill("M6,6v12L14.5,12z")
    }

    val Pause: ImageVector = vector("VicPause") {
        fill("M7,5h3.5v14H7z")
        fill("M13.5,5H17v14h-3.5z")
    }

    val Remove: ImageVector = vector("VicRemove") {
        stroke("M5,12h14", 2.6f)
    }

    val Folder: ImageVector = vector("VicFolder") {
        fill("M10,4L4,4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2L22,8c0,-1.1 -0.9,-2 -2,-2h-8z")
    }

    val DragHandle: ImageVector = vector("VicDragHandle") {
        fill("M20,9L4,9v2h16z")
        fill("M4,15h16v-2L4,13z")
    }

    val Tune: ImageVector = vector("VicTune") {
        stroke("M3,7h18M3,12h18M3,17h18", 1.8f)
        fill("M20,7m-3,0a3,3 0,1 1,6 0a3,3 0,1 1,-6 0")
        fill("M6,12m-3,0a3,3 0,1 1,6 0a3,3 0,1 1,-6 0")
        fill("M20,17m-3,0a3,3 0,1 1,6 0a3,3 0,1 1,-6 0")
    }

    val Height: ImageVector = vector("VicHeight") {
        stroke("M12,4v16", 2f)
        stroke("M7,9l5,-5l5,6", 2f)
        stroke("M7,15l5,5l5,-5", 2f)
    }

    val LinkOff: ImageVector = vector("VicLinkOff") {
        stroke("M10,6L5,6a4,4 0,0 0,0 12h5M14,18h5a4,4 0,0 0,0 -12h-5", 2f)
        stroke("M4,20L20,4", 2f)
    }

    val Visibility: ImageVector = vector("VicVisibility") {
        stroke("M2,12s3.8,-6.5 10,-6.5S22,12 22,12s-3.8,6.5 -10,6.5S2,12 2,12z", 2f)
        fill("M12,12m-3,0a3,3 0,1 1,6 0a3,3 0,1 1,-6 0")
    }

    val VisibilityOff: ImageVector = vector("VicVisibilityOff") {
        stroke(
            "M2,12s3.8,-6.5 10,-6.5c1.4,0 2.7,0.3 3.8,0.8M14,5.8c0.6,0.1 1.2,0.4 1.7,0.6M12,18.5c-6.2,0 -10,-6.5 -10,-6.5",
            2f,
        )
        stroke("M3,21L21,3", 2f)
        fill("M12,12m-3,0a3,3 0,1 1,6 0a3,3 0,1 1,-6 0")
    }

    val Checklist: ImageVector = vector("VicChecklist") {
        stroke("M9,4h6M16,2L8,2v2L8,22h8L16,2", 2f)
        stroke("M8.8,12.5l2.4,2.4l4.4,-5", 2f)
    }

    val StarBorder: ImageVector = vector("VicStarBorder") {
        stroke("M12,3.5l2.7,5.5 6,0.9 -4.3,4.2 1,6 -5.4,-2.8 -5.4,2.8 1,-6 -4.3,-4.2 6,-0.9z", 2f)
    }

    val ArrowForwardIos: ImageVector = vector("VicArrowForwardIos") {
        fill("M7.6,4.6l1.4,-1.4l8.8,8.8l-8.8,8.8l-1.4,-1.4l7.4,-7.4z")
    }
}

/** The Material icon size, so an icon dropped in among `material-icons-core` ones matches. */
private const val GRID = 24f

/** One path of one icon, and whether it is a line (stroked) or a shape (filled). */
private class IconPath(val data: String, val strokeWidth: Float?)

private fun vector(name: String, build: MutableList<IconPath>.() -> Unit): ImageVector {
    val paths = mutableListOf<IconPath>()
    paths.build()
    return ImageVector.Builder(
        name = name,
        defaultWidth = GRID.dp,
        defaultHeight = GRID.dp,
        viewportWidth = GRID,
        viewportHeight = GRID,
    ).apply {
        paths.forEach { p ->
            if (p.strokeWidth == null) {
                addPath(pathData = addPathNodes(p.data))
            } else {
                addPath(
                    pathData = addPathNodes(p.data),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = p.strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
    }.build()
}

private fun MutableList<IconPath>.fill(data: String) {
    add(IconPath(data, null))
}

private fun MutableList<IconPath>.stroke(data: String, width: Float) {
    add(IconPath(data, width))
}
