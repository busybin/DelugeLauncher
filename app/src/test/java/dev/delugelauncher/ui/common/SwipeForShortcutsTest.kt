// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.delugelauncher.data.ShortcutSwipe
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A row that is also tappable must still answer a sideways swipe. The tap detector consumes
 * the gesture while it waits for a tap; the swipe must not take that as someone else winning.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SwipeForShortcutsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun content(swipes: MutableList<Unit>, taps: MutableList<Unit>) {
        compose.setContent {
            Box(
                Modifier
                    .size(200.dp)
                    .testTag("row")
                    .background(Color.Gray)
                    .swipeForShortcuts(ShortcutSwipe.RIGHT, true) { swipes += Unit }
                    .combinedClickable(
                        onClick = { taps += Unit },
                        onLongClick = { taps += Unit },
                    ),
            )
        }
    }

    /**
     * A tap detector that keeps consuming while the finger is down, the way a long press does.
     * The swipe must still see its own drag instead of bowing out on the first consumed move.
     */
    @Test
    fun swipeSurvivesAnotherHandlerConsumingTheDrag() {
        val swipes = mutableListOf<Unit>()
        compose.setContent {
            Box(
                Modifier
                    .size(200.dp)
                    .testTag("row")
                    .swipeForShortcuts(ShortcutSwipe.RIGHT, true) { swipes += Unit }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown().consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                change.consume()
                                if (!change.pressed) break
                            }
                        }
                    },
            )
        }

        compose.onNodeWithTag("row").performTouchInput {
            down(center)
            moveBy(Offset(80f, 0f))
            moveBy(Offset(80f, 0f))
            moveBy(Offset(80f, 0f))
            up()
        }
        compose.waitForIdle()

        assertEquals(1, swipes.size)
    }

    @Test
    fun swipeStartingOnTheTappableRowStillFires() {
        val swipes = mutableListOf<Unit>()
        val taps = mutableListOf<Unit>()
        content(swipes, taps)

        compose.onNodeWithTag("row").performTouchInput {
            down(center)
            moveBy(Offset(80f, 0f))
            moveBy(Offset(80f, 0f))
            moveBy(Offset(80f, 0f))
            up()
        }
        compose.waitForIdle()

        assertEquals(1, swipes.size)
        assertEquals(0, taps.size)
    }

    @Test
    fun verticalDragStaysATapGesture() {
        val swipes = mutableListOf<Unit>()
        val taps = mutableListOf<Unit>()
        content(swipes, taps)

        compose.onNodeWithTag("row").performTouchInput {
            down(center)
            moveBy(Offset(0f, 80f))
            moveBy(Offset(0f, 80f))
            up()
        }
        compose.waitForIdle()

        assertEquals(0, swipes.size)
    }
}
