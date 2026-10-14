package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.core.domain.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MediaArtworkEdgeTest {
    @get:org.junit.Rule val rule = createComposeRule()
    private lateinit var clearFocus: () -> Unit
    private val baseCard = mutableStateOf(false)

    @Test fun bothLegacyCardsLiftAtShelfEndsAndAfterScrolling() {
        lateinit var state: LazyListState
        rule.setContent {
            MaFeiTheme {
                val focus = LocalFocusManager.current
                clearFocus = { focus.clearFocus(force = true) }
                state = rememberLazyListState()
                val width = if (baseCard.value) 100.dp else 140.dp
                val inset = mediaArtworkHoverSideInset(width)
                Box(Modifier.size(420.dp, 350.dp).testTag("viewport").clipToBounds().padding(horizontal = 24.dp)) {
                    LazyRow(Modifier.fillMaxWidth().mafeiArtworkShelfBleed(inset), state = state,
                        contentPadding = PaddingValues(start = inset, end = inset, top = mediaArtworkHoverTopInset(width * 1.5f)),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(8, key = { it }) { Card(it) }
                    }
                }
            }
        }
        for (base in listOf(false, true)) {
            rule.runOnIdle { baseCard.value = base; clearFocus(); runBlocking { state.scrollToItem(0) } }
            assertLift(0)
            rule.runOnIdle { runBlocking { state.scrollToItem(7) } }
            assertLift(7)
        }
    }

    @Test fun legacyGridCornersLiftWithoutMovingNeighborsOrCaptions() {
        rule.setContent {
            MaFeiTheme {
                val focus = LocalFocusManager.current
                clearFocus = { focus.clearFocus(force = true) }
                val inset = mediaArtworkGridHoverInset(296.dp, 2, 16.dp)
                LazyVerticalGrid(GridCells.Fixed(2), Modifier.size(344.dp, 650.dp).testTag("viewport").clipToBounds(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = inset, bottom = inset),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(inset)) {
                    items(4) { Card(it) }
                }
            }
        }
        rule.runOnIdle { clearFocus() }
        repeat(4) { index ->
            val neighbors = (0..3).filter { it != index }.associateWith {
                rule.onNodeWithTag("card-$it").fetchSemanticsNode().boundsInRoot
            }
            assertLift(index)
            neighbors.forEach { (i, bounds) -> assertEquals(bounds, rule.onNodeWithTag("card-$i").fetchSemanticsNode().boundsInRoot) }
        }
    }

    private fun assertLift(index: Int) {
        val art = rule.onNodeWithTag("art-$index", useUnmergedTree = true)
        val title = rule.onNodeWithText("影片 $index", useUnmergedTree = true)
        val rest = art.fetchSemanticsNode().boundsInRoot
        val titleBounds = title.fetchSemanticsNode().boundsInRoot
        val card = rule.onNodeWithTag("card-$index")
        card.performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(400)
        val lifted = art.fetchSemanticsNode().boundsInRoot
        val viewport = rule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(rest.width * ArtworkLiftScale, lifted.width, .6f)
        assertEquals(rest.height * ArtworkLiftScale, lifted.height, .6f)
        assertTrue(lifted.left > viewport.left && lifted.right < viewport.right && lifted.top > viewport.top)
        assertTrue(lifted.bottom < titleBounds.top)
        assertEquals(titleBounds, title.fetchSemanticsNode().boundsInRoot)
        card.performMouseInput { moveTo(Offset(-80f, -80f)) }
        rule.mainClock.advanceTimeBy(400)
        assertEquals(rest, art.fetchSemanticsNode().boundsInRoot)
    }

    @Composable private fun Card(index: Int) {
        val item = MediaItem(MediaId("$index"), ServerId("server"), ProviderKind.Jellyfin,
            "影片 $index", mediaType = MediaType.Movie)
        val modifier = Modifier.width(if (baseCard.value) 100.dp else 140.dp).testTag("card-$index")
        val poster: @Composable (Modifier) -> Unit = { Box(it.testTag("art-$index").background(Color(0xFF75939D))) }
        if (baseCard.value) LegacyBaseItemCard(item, {}, modifier, poster)
        else LegacyVideoCard(item, {}, modifier, poster = poster)
    }
}
