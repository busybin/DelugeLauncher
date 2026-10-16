package dev.delugelauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bisects the real home-screen hierarchy down to whatever swallows the reorder drag.
 *
 * Each test switches on ONE more ancestor from HomeScreen's real tree:
 *  A: the Column with verticalScroll         (edit mode's stack)         — passes
 *  B: + the root Box's pinch detector        (pinchToResize)
 *  C: + the root Box's draggable, disabled  (the stack drag, off in edit mode)
 *  D: + the item wrapper's zIndex/graphicsLayer/onGloballyPositioned
 *
 * The handle keeps the exact modifier chain HomeScreen builds for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditModeDragBisectTest {

    @get:Rule
    val compose = createComposeRule()

    private var started = false
    private var moved = 0f

    private fun reset() {
        started = false
        moved = 0f
    }

    /** The handle, with HomeScreen's own modifier chain. */
    private fun Modifier.handleGesture(tag: String): Modifier =
        then(
            Modifier.pointerInput(tag) {
                detectDragGestures(
                    onDragStart = { started = true },
                    onDragEnd = {},
                    onDragCancel = {},
                ) { _, amount -> moved += amount.y }
            }
        )

    @androidx.compose.runtime.Composable
    private fun Stack(
        withPinchDetector: Boolean,
        withRootDraggable: Boolean,
        withItemLayer: Boolean,
    ) {
        var editMode by remember { mutableStateOf(true) }
        val livePaddings = remember { emptyMap<String, Int>() }
        val offsetY = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        Box(
            modifier = Modifier
                .fillMaxSize()
                // B: the pinch-to-resize detector, verbatim from HomeScreen's root Box.
                .then(
                    if (withPinchDetector) {
                        Modifier.pointerInput(48) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        event.changes.forEach { if (it.pressed) it.consume() }
                                    }
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        }
                    } else Modifier
                )
                // C: the stack drag, disabled the moment edit mode opens.
                .then(
                    if (withRootDraggable) {
                        Modifier.draggable(
                            orientation = Orientation.Vertical,
                            enabled = !editMode && livePaddings.isEmpty(),
                            onDragStarted = {},
                            state = rememberDraggableState { delta ->
                                scope.launch {
                                    offsetY.snapTo(
                                        (offsetY.value + delta).coerceIn(-100000f, 0f)
                                    )
                                }
                            },
                        )
                    } else Modifier
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                repeat(6) { index ->
                    val wrapper = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        // D: the item wrapper's own layer.
                        .then(
                            if (withItemLayer) {
                                Modifier
                                    .zIndex(0f)
                                    .graphicsLayer { }
                            } else Modifier
                        )
                    Box(modifier = wrapper.testTag("row$index")) {
                        Text(
                            text = "row $index",
                            modifier = Modifier.semantics { contentDescription = "content$index" },
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .handleGesture("row$index")
                                .size(36.dp)
                                .padding(6.dp)
                                .testTag("handle$index")
                        )
                    }
                }
            }
        }
    }

    private fun dragRowTwo() {
        compose.onNodeWithTag("handle2").performTouchInput {
            down(center)
            moveBy(Offset(0f, 200f))
            moveBy(Offset(0f, 300f))
            up()
        }
        compose.waitForIdle()
    }

    @Test
    fun a_scrollOnly() {
        reset()
        compose.setContent { Stack(withPinchDetector = false, withRootDraggable = false, withItemLayer = false) }
        dragRowTwo()
        assertTrue("A scroll only: started=$started moved=$moved", started && moved > 0f)
    }

    @Test
    fun b_plusPinchDetector() {
        reset()
        compose.setContent { Stack(withPinchDetector = true, withRootDraggable = false, withItemLayer = false) }
        dragRowTwo()
        assertTrue("B plus pinch detector: started=$started moved=$moved", started && moved > 0f)
    }

    @Test
    fun c_plusRootDraggableDisabled() {
        reset()
        compose.setContent { Stack(withPinchDetector = true, withRootDraggable = true, withItemLayer = false) }
        dragRowTwo()
        assertTrue("C plus disabled root draggable: started=$started moved=$moved", started && moved > 0f)
    }

    @Test
    fun d_plusItemLayer() {
        reset()
        compose.setContent { Stack(withPinchDetector = true, withRootDraggable = true, withItemLayer = true) }
        dragRowTwo()
        assertTrue("D plus item layer: started=$started moved=$moved", started && moved > 0f)
    }
}
