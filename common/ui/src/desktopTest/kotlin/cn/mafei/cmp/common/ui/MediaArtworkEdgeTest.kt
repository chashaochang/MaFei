package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** No safety padding: a card on the viewport boundary must work just like one in the middle. */
@OptIn(ExperimentalTestApi::class)
class MediaArtworkEdgeTest {
    @get:org.junit.Rule val rule = createComposeRule()
    private lateinit var clearFocus: () -> Unit

    @Test fun shelfEndsKeepTheSameContourBeforeAndAfterScrolling() {
        lateinit var state: LazyListState
        rule.setContent {
            MaFeiTheme {
                val focus = LocalFocusManager.current
                clearFocus = { focus.clearFocus(force = true) }
                state = rememberLazyListState()
                LazyRow(Modifier.size(320.dp, 200.dp).clipToBounds()
                    .background(MaterialTheme.colorScheme.background), state = state) {
                    items(8, key = { it }) { Card(it) }
                }
            }
        }
        rule.runOnIdle { clearFocus() }
        assertStableContour(0)
        // Scroll while the first card is hovered, then exercise the last card flush to the right.
        rule.onNodeWithTag("card-0").performMouseInput { moveTo(center) }
        rule.runOnIdle { runBlocking { state.scrollToItem(7) } }
        rule.waitForIdle()
        assertStableContour(7)
        rule.runOnIdle { runBlocking { state.scrollToItem(0) } }
        rule.waitForIdle()
        assertStableContour(0)
    }

    @Test fun gridCornersDoNotGrowIntoNeighborsOrCaptions() {
        rule.setContent {
            MaFeiTheme {
                val focus = LocalFocusManager.current
                clearFocus = { focus.clearFocus(force = true) }
                LazyVerticalGrid(GridCells.Fixed(2), Modifier.size(320.dp, 400.dp)
                    .clipToBounds().background(MaterialTheme.colorScheme.background)) {
                    items(4, key = { it }) { Card(it) }
                }
            }
        }
        rule.runOnIdle { clearFocus() }
        for (index in 0..3) {
            val neighbors = (0..3).filter { it != index }.associateWith { pixels(artwork(it)) }
            assertStableContour(index) {
                neighbors.forEach { (neighbor, before) ->
                    assertTrue(before.contentEquals(pixels(artwork(neighbor))), "Hovering $index changed neighbor $neighbor")
                }
            }
        }
    }

    private fun assertStableContour(index: Int, checkNeighbors: () -> Unit = {}) {
        val card = rule.onNodeWithTag("card-$index")
        val artwork = artwork(index)
        val title = rule.onNodeWithTag("title-$index", useUnmergedTree = true)
        val bounds = artwork.fetchSemanticsNode().boundsInRoot
        val cardBounds = card.fetchSemanticsNode().boundsInRoot
        val titlePixels = pixels(title)
        val corners = cornerPixels(artwork)
        rule.mainClock.autoAdvance = false
        // Use the measured node's center; do not infer a hit point from an image.
        card.performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(400)
        assertEquals(bounds, artwork.fetchSemanticsNode().boundsInRoot, "Hover changed the contour of edge card $index")
        assertEquals(cardBounds, card.fetchSemanticsNode().boundsInRoot)
        assertTrue(corners.contentEquals(cornerPixels(artwork)), "Hover expanded the rounded contour into the viewport edge")
        assertTrue(titlePixels.contentEquals(pixels(title)), "Hover painted over the adjacent caption")
        checkNeighbors()
        card.performMouseInput { press() }
        rule.mainClock.advanceTimeBy(100)
        assertEquals(bounds, artwork.fetchSemanticsNode().boundsInRoot, "Press changed the contour of edge card $index")
        assertTrue(corners.contentEquals(cornerPixels(artwork)), "Press changed the fixed rounded contour")
        checkNeighbors()
        card.performMouseInput { cancel() }
        card.performMouseInput { moveTo(Offset(-10f, -10f) - cardBounds.topLeft) }
        rule.mainClock.advanceTimeBy(400)
        assertEquals(bounds, artwork.fetchSemanticsNode().boundsInRoot)
        rule.mainClock.autoAdvance = true
    }

    @Composable private fun Card(index: Int) {
        val interaction = rememberMediaCardInteraction()
        Column(Modifier.size(160.dp, 200.dp).testTag("card-$index")
            .mafeiMediaCardInteraction(interaction, {}, "card $index")) {
            Box(Modifier.size(160.dp).testTag("artwork-$index")
                .mafeiArtworkFeedback(interaction, RoundedCornerShape(10.dp))) {
                Canvas(Modifier.size(160.dp)) {
                    drawRect(Color(0xFF354C5C))
                    drawCircle(Color(0xFFC2D0BF), 30.dp.toPx(), Offset(size.width * .7f, size.height * .3f))
                }
            }
            Text("影片标题 $index", Modifier.testTag("title-$index"))
        }
    }

    private fun artwork(index: Int) = rule.onNodeWithTag("artwork-$index", useUnmergedTree = true)
    private fun cornerPixels(node: SemanticsNodeInteraction): IntArray {
        val image = node.captureToImage().toPixelMap()
        return listOf(0 to 4, 4 to 0, image.width - 1 to 4, image.width - 5 to 0,
            0 to image.height - 5, 4 to image.height - 1,
            image.width - 1 to image.height - 5, image.width - 5 to image.height - 1)
            .map { (x, y) -> image[x, y].hashCode() }.toIntArray()
    }
    private fun pixels(node: SemanticsNodeInteraction): IntArray {
        val image = node.captureToImage().toPixelMap()
        return IntArray(image.width * image.height) { image[it % image.width, it / image.width].hashCode() }
    }
}
