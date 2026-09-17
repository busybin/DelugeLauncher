// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Wraps an embedded AppWidgetHostView so a genuine long-press (finger held still) opens our
 * edit menu, while an ordinary tap or drag still reaches the widget underneath untouched.
 *
 * A plain Compose pointerInput long-press detector can't do this: once it starts tracking a
 * gesture it owns the whole touch stream, so the widget's own buttons (play/pause, a weather
 * tap-to-open) would stop working. This mirrors how scrollable containers arbitrate gestures
 * with their children: don't intercept on ACTION_DOWN, keep watching via onInterceptTouchEvent,
 * and only steal the stream once our own long-press timer actually fires.
 */
class LongPressFrameLayout(context: Context) : FrameLayout(context) {

    /** x/y are the press position in this view's local pixel coordinates. */
    var onLongPress: ((x: Float, y: Float) -> Unit)? = null

    private var longPressFired = false

    /** Where the current gesture began, so its direction can be judged as it moves. */
    private var downX = 0f
    private var downY = 0f
    private var claimedVertical = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                longPressFired = true
                onLongPress?.invoke(e.x, e.y)
            }
        },
    )

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressFired = false
                claimedVertical = false
                downX = ev.x
                downY = ev.y
            }

            MotionEvent.ACTION_MOVE -> if (!claimedVertical) {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                // Only a drag that is clearly up or down is taken, and only once it is clearly
                // a drag at all. A widget that scrolls has to receive those itself, or the home
                // screen's own vertical drag takes them and the notification shade comes down
                // instead of the list moving.
                //
                // Sideways is deliberately left alone: that is how the pager moves between
                // widgets, and claiming everything on the press meant a stack of them could no
                // longer be swiped through.
                if (dy > touchSlop && dy > dx) {
                    claimedVertical = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        gestureDetector.onTouchEvent(ev)
        if (longPressFired) {
            // Our own menu is taking over, so the hold on the parent is no longer wanted.
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return longPressFired
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            longPressFired = false
            claimedVertical = false
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}