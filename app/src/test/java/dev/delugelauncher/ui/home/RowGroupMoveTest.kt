package dev.delugelauncher.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * When the order changes, is a row's composition group moved, or disposed and built again?
 *
 * A rebuild takes everything the row was holding with it — a running drag included, which is
 * why the row snaps back the instant it should swap with its neighbour.
 *
 * The rule this pins down: a keyed group that sits inside a conditional cannot be moved, so
 * HomeScreen emits its one-column rows from a loop with the key in it and no branch over it
 * (edit layout is always one column). Keying the group is not enough on its own, and neither
 * is where the row's body lives — only the branch does the damage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RowGroupMoveTest {

    @get:Rule
    val compose = createComposeRule()

    private val rebuilds = mutableListOf<String>()

    @Composable
    private fun RowBody(item: String) {
        DisposableEffect(item) {
            onDispose { rebuilds += item }
        }
        Box(modifier = Modifier.fillMaxWidth().height(100.dp).testTag("row$item")) {
            Text(item)
        }
    }

    /** Upstream's shape: the row body is inline in the loop, `key` right there. */
    @Composable
    private fun InlineInLoop(order: List<String>) {
        Column(modifier = Modifier.fillMaxSize()) {
            order.forEach { item ->
                key(item) { RowBody(item) }
            }
        }
    }

    /** The fork's shape: a cell list, a row lambda, the loop keys the call. */
    @Composable
    private fun LambdaKeyedByLoop(order: List<String>) {
        val render: @Composable (String) -> Unit = { item -> RowBody(item) }
        Column(modifier = Modifier.fillMaxSize()) {
            val cells = remember(order) { order.mapIndexed { i, item -> item to i } }
            cells.forEach { (item, _) -> key(item) { render(item) } }
        }
    }

    /** The fork's shape, with the single/two-column branch split out of the keyed group. */
    @Composable
    private fun LambdaWithSplitLoops(order: List<String>, twoColumn: Boolean) {
        val render: @Composable (String) -> Unit = { item -> RowBody(item) }
        Column(modifier = Modifier.fillMaxSize()) {
            val cells = remember(order, twoColumn) {
                if (!twoColumn) order.mapIndexed { i, item -> listOf(item to i) }
                else order.chunked(2).map { chunk -> chunk.mapIndexed { i, item -> item to i } }
            }
            if (!twoColumn) {
                cells.forEach { cell -> key(cell[0].first) { render(cell[0].first) } }
            } else {
                cells.forEach { cell ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.weight(1f)) { key(cell[0].first) { render(cell[0].first) } }
                        if (cell.size > 1) {
                            Box(Modifier.weight(1f)) { key(cell[1].first) { render(cell[1].first) } }
                        }
                    }
                }
            }
        }
    }

    /** The fork's shape, with the branch moved inside the keyed group. */
    @Composable
    private fun LambdaWithBranchInsideKey(order: List<String>, twoColumn: Boolean) {
        val render: @Composable (String) -> Unit = { item -> RowBody(item) }
        Column(modifier = Modifier.fillMaxSize()) {
            val cells = remember(order, twoColumn) {
                if (!twoColumn) order.mapIndexed { i, item -> listOf(item to i) }
                else order.chunked(2).map { chunk -> chunk.mapIndexed { i, item -> item to i } }
            }
            cells.forEach { cell ->
                key(cell[0].first) {
                    if (cell.size == 1) {
                        render(cell[0].first)
                    } else {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.weight(1f)) { render(cell[0].first) }
                            Box(Modifier.weight(1f)) { key(cell[1].first) { render(cell[1].first) } }
                        }
                    }
                }
            }
        }
    }

    private fun scenario(content: @Composable (List<String>) -> Unit) {
        rebuilds.clear()
        val order = mutableStateOf(listOf("a", "b", "c", "d", "e"))
        compose.setContent { content(order.value) }
        compose.waitForIdle()
        rebuilds.clear()
        compose.runOnIdle { order.value = listOf("a", "c", "b", "d", "e") }
        compose.waitForIdle()
        assertEquals("rows rebuilt on the reorder: $rebuilds", emptyList<String>(), rebuilds)
    }

    @Test
    fun inlineInLoop() = scenario { InlineInLoop(it) }

    @Test
    fun lambdaKeyedByLoop() = scenario { LambdaKeyedByLoop(it) }

    @Test
    fun lambdaWithSplitLoops() = scenario { LambdaWithSplitLoops(it, twoColumn = false) }

    @Test
    fun lambdaWithBranchInsideKey() = scenario { LambdaWithBranchInsideKey(it, twoColumn = false) }
}
