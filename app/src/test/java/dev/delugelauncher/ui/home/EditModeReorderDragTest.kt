package dev.delugelauncher.ui.home

import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Does the reorder drag on a row's grab handle still start when the stack that holds it is a
 * verticalScroll (edit mode) rather than the plain dragged stack?
 *
 * Same modifier chain the home screen builds for a handle, row for row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditModeReorderDragTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun handleDragInsideEditModeStack() {
        var started = false
        var moved = 0f
        compose.setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                repeat(6) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("row$index")
                    ) {
                        Text("row $index")
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .then(
                                    Modifier.pointerInput("row$index") {
                                        detectDragGestures(
                                            onDragStart = { started = true },
                                            onDragEnd = {},
                                            onDragCancel = {},
                                        ) { _, amount -> moved += amount.y }
                                    }
                                )
                                .size(36.dp)
                                .padding(6.dp)
                                .testTag("handle$index")
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("handle2").performTouchInput {
            down(center)
            moveBy(Offset(0f, 200f))
            moveBy(Offset(0f, 300f))
            up()
        }
        compose.waitForIdle()
        assertTrue("drag must start (started=$started moved=$moved)", started && moved > 0f)
    }
}
