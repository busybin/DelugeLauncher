// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import dev.victorialauncher.data.IconShape

/**
 * How much larger an adaptive icon's layers are than the part of them that shows.
 *
 * An adaptive icon is drawn on a 108dp canvas of which the middle 72dp is guaranteed visible;
 * the rest is bleed for the system to mask into, and for parallax. Re-masking it by hand means
 * rendering the layers at full size and cropping back to that visible middle.
 */
private const val ADAPTIVE_BLEED = 108f / 72f

/**
 * Draws [drawable] into a square bitmap, applying the user's icon shape and, when asked, the
 * monochrome layer Android 13 added.
 *
 * Both only apply to adaptive icons. An older icon is a flat bitmap with no safe zone and no
 * monochrome layer, so there is nothing to cut into or tint — it is returned as it is rather
 * than cropped into.
 */
fun renderIcon(
    drawable: Drawable,
    px: Int,
    shape: IconShape,
    themed: Boolean,
    themedBackground: Int,
    themedForeground: Int,
): Bitmap {
    val adaptive = drawable as? AdaptiveIconDrawable

    val mono = if (themed && adaptive != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        adaptive.monochrome?.mutate()
    } else {
        null
    }

    if (adaptive == null) return drawable.toSquareBitmap(px)

    val layers = if (mono != null) {
        // The monochrome layer is a silhouette meant to be tinted and set on a filled shape,
        // the way the system draws it. Its own colors are not used.
        { canvas: Canvas, size: Int ->
            canvas.drawColor(themedBackground)
            mono.setTint(themedForeground)
            mono.setBounds(0, 0, size, size)
            mono.draw(canvas)
        }
    } else {
        { canvas: Canvas, size: Int ->
            adaptive.background?.apply { setBounds(0, 0, size, size); draw(canvas) }
            adaptive.foreground?.apply { setBounds(0, 0, size, size); draw(canvas) }
        }
    }

    // Nothing to do by hand: the drawable already masks itself to whatever the device uses.
    if (shape == IconShape.SYSTEM && mono == null) return drawable.toSquareBitmap(px)

    val full = (px * ADAPTIVE_BLEED).toInt().coerceAtLeast(px)
    val bleed = Bitmap.createBitmap(full, full, Bitmap.Config.ARGB_8888)
    layers(Canvas(bleed), full)

    val inset = (full - px) / 2
    val cropped = Bitmap.createBitmap(bleed, inset, inset, px, px)
    bleed.recycle()

    val path = shapePath(if (shape == IconShape.SYSTEM) IconShape.CIRCLE else shape, px)
    return cropped.maskedTo(path)
}

private fun Drawable.toSquareBitmap(px: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, px, px)
    draw(Canvas(bitmap))
    return bitmap
}

private fun shapePath(shape: IconShape, px: Int): Path {
    val size = px.toFloat()
    val rect = RectF(0f, 0f, size, size)
    return Path().apply {
        when (shape) {
            IconShape.CIRCLE, IconShape.SYSTEM -> addOval(rect, Path.Direction.CW)
            IconShape.ROUNDED -> addRoundRect(rect, size * 0.22f, size * 0.22f, Path.Direction.CW)
            IconShape.SQUARE -> addRect(rect, Path.Direction.CW)
        }
    }
}

/** Keeps only what falls inside [path], leaving the corners transparent rather than black. */
private fun Bitmap.maskedTo(path: Path): Bitmap {
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawPath(path, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(this, 0f, 0f, paint)
    recycle()
    return out
}
