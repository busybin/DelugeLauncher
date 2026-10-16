package dev.delugelauncher.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import dev.delugelauncher.R
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A slider drag used to hand the store one value per frame — a preferences write each time the
 * finger moved a pixel, and a rebuild of the whole screen behind every one of them. The row now
 * keeps the drag to itself and hands upstream a few values a second at most.
 *
 * Nothing here is about the phone: it is the row's own bookkeeping, which is where the write
 * storm came from. Gestures start mid-track because a drag beginning exactly on the thumb at the
 * very end of the track is dropped by the Material slider itself, ours or not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SliderRowCommitTest {

    @get:Rule
    val compose = createComposeRule()

    private val string = ApplicationProvider.getApplicationContext<Context>()

    private fun slider() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))

    /** [onCommit] stands in for the store; the hoisted value stands in for what it reads back. */
    private fun screen(onCommit: (Float) -> Unit) = compose.setContent {
        var value by remember { mutableFloatStateOf(20f) }
        Column {
            SliderRow(
                label = "size",
                value = value,
                range = 12f..96f,
                valueLabel = { "${it.roundToInt()}dp" },
                onValueChange = { value = it; onCommit(it) },
            )
            Button(onClick = { value = 96f }) { Text("reset") }
        }
    }

    @Test
    fun aDragHandsUpstreamAFractionOfTheTimesTheFingerMoves() {
        val commits = mutableListOf<Float>()
        screen { commits += it }

        // Thirty frames of drag, the cadence of a real finger — and 600px of travel, which the
        // old code turned into a store write at every one of those frames.
        slider().performTouchInput {
            down(center - Offset(300f, 0f))
            repeat(30) { moveBy(Offset(20f, 0f), delayMillis = 16) }
            up()
        }
        compose.waitForIdle()
        println("SliderRowCommitTest: thirty dragged frames wrote upstream ${commits.size} times")

        assertTrue("the drag has to reach the store at all", commits.isNotEmpty())
        assertTrue(
            "thirty dragged frames wrote upstream ${commits.size} times; a write per frame is " +
                "the storm this row exists to stop",
            commits.size <= 8,
        )
        // And the row must not be left showing a value the store never heard about.
        compose.onNodeWithText("${commits.last().roundToInt()}dp").assertExists()
    }

    @Test
    fun theValueTheFingerLandsOnIsTheOneThatIsStored() {
        val commits = mutableListOf<Float>()
        screen { commits += it }

        slider().performTouchInput {
            down(center)
            moveBy(Offset(300f, 0f), delayMillis = 300)
            up()
        }
        compose.waitForIdle()

        val landed = commits.last()
        assertTrue("the drag has to end well past where it started", landed > 40f)
        compose.onNodeWithText("${landed.roundToInt()}dp").assertExists()
    }

    @Test
    fun theStepperWritesAtOnce() {
        val commits = mutableListOf<Float>()
        screen { commits += it }

        compose.onNodeWithText("20dp").assertExists()
        compose.onNodeWithContentDescription(string.getString(R.string.settings_less)).performClick()
        compose.waitForIdle()

        assertEquals("one press is one write", listOf(19f), commits)
        compose.onNodeWithText("19dp").assertExists()
    }

    @Test
    fun aValueFromElsewhereIsAdopted() {
        val commits = mutableListOf<Float>()
        screen { commits += it }

        compose.onNodeWithText("20dp").assertExists()
        compose.onNodeWithText("reset").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("96dp").assertExists()
        assertEquals("a value we did not write is not a write", emptyList<Float>(), commits)
    }
}
