package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MediaArtworkHoverRimTest {
    @get:org.junit.Rule val rule = createComposeRule()

    @Test fun contoursRunClockwiseOnPortraitLandscapeAndCircularArtwork() {
        for (shape in listOf(RectangleShape, RoundedCornerShape(10.dp), CircleShape)) {
            for (size in listOf(Size(160f, 240f), Size(240f, 135f), Size(96f, 96f))) {
                val rim = ArtworkHoverRim(shape.createOutline(size, LayoutDirection.Ltr, Density(1f)),
                    inset = 0f, density = 1f, accent = Color.Blue)
                val points = (0 until 16).map { rim.measure.getPosition(rim.measure.length * it / 16) }
                val winding = points.indices.sumOf { index ->
                    val next = points[(index + 1) % points.size]
                    (points[index].x * next.y - next.x * points[index].y).toDouble()
                }
                assertTrue(winding > 0, "With screen y pointing down, positive winding is clockwise: $shape/$size")
                assertEquals(rim.measure.getPosition(0f).x, rim.measure.getPosition(rim.measure.length).x, .01f)
                assertEquals(rim.measure.getPosition(0f).y, rim.measure.getPosition(rim.measure.length).y, .01f)
            }
        }
    }

    @Test fun onlyHoveredArtworkAnimatesWithoutRecomposingOrChangingCaptions() {
        var compositions = 0
        lateinit var clearFocus: () -> Unit
        val windowFocused = mutableStateOf(true)
        val windowInfo = object : WindowInfo { override val isWindowFocused get() = windowFocused.value }
        rule.setContent {
            CompositionLocalProvider(LocalWindowInfo provides windowInfo) {
                MaFeiTheme {
                    val focusManager = LocalFocusManager.current
                    clearFocus = { focusManager.clearFocus(force = true) }
                    Row(Modifier.size(450.dp, 380.dp).background(MaterialTheme.colorScheme.background).padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        repeat(2) { index ->
                            val interaction = rememberMediaCardInteraction()
                            SideEffect { compositions++ }
                            Column(Modifier.testTag("card-$index").mafeiMediaCardInteraction(interaction, {}, "card $index"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(160.dp, 240.dp).testTag("artwork-$index")
                                    .mafeiArtworkFeedback(interaction, RoundedCornerShape(10.dp))
                                    .background(Color(0xFF354C5C)))
                                Text("影片标题 $index", Modifier.testTag("title-$index"))
                                Text("2026 · 电影", Modifier.testTag("subtitle-$index"))
                            }
                        }
                    }
                }
            }
        }
        rule.runOnIdle { clearFocus() }
        val card = rule.onNodeWithTag("card-0")
        val title = rule.onNodeWithTag("title-0", useUnmergedTree = true)
        val subtitle = rule.onNodeWithTag("subtitle-0", useUnmergedTree = true)
        val titlePixels = pixels(title)
        val subtitlePixels = pixels(subtitle)
        val titleBounds = title.fetchSemanticsNode().boundsInRoot
        val neighbor = rule.onNodeWithTag("artwork-1", useUnmergedTree = true)
        val neighborPixels = pixels(neighbor)
        card.fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        card.performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(300)
        val artwork = rule.onNodeWithTag("artwork-0", useUnmergedTree = true)
        val first = pixels(artwork)
        val settledCompositions = compositions
        rule.mainClock.advanceTimeBy(700)
        assertTrue(!first.contentEquals(pixels(artwork)), "The highlight must travel, not remain a static border")
        assertEquals(settledCompositions, compositions, "Animation must redraw only, not recompose card content")
        assertTrue(titlePixels.contentEquals(pixels(title)))
        assertTrue(subtitlePixels.contentEquals(pixels(subtitle)))
        assertEquals(titleBounds, title.fetchSemanticsNode().boundsInRoot)
        assertTrue(neighborPixels.contentEquals(pixels(neighbor)), "Other cards must stay idle")

        rule.runOnIdle { windowFocused.value = false }
        rule.mainClock.advanceTimeBy(300)
        val unfocused = pixels(artwork)
        rule.mainClock.advanceTimeBy(700)
        assertTrue(unfocused.contentEquals(pixels(artwork)), "Background windows must not keep animating")
        rule.runOnIdle { windowFocused.value = true }
        rule.mainClock.advanceTimeBy(300)
        card.performMouseInput { moveTo(Offset(-12f, -12f)) }
        rule.mainClock.advanceTimeBy(300)
        val exited = pixels(artwork)
        rule.mainClock.advanceTimeBy(700)
        assertTrue(exited.contentEquals(pixels(artwork)), "The frame loop must stop after pointer exit")
        rule.mainClock.autoAdvance = true
    }

    @Test fun keyboardFocusIsAStableRingWithoutAChasingLight() {
        val requester = FocusRequester()
        rule.setContent {
            MaFeiTheme {
                val interaction = rememberMediaCardInteraction()
                Box(Modifier.padding(20.dp).focusRequester(requester)
                    .mafeiMediaCardInteraction(interaction, {}, "card")) {
                    Box(Modifier.size(160.dp, 240.dp).testTag("artwork")
                        .mafeiArtworkFeedback(interaction, RoundedCornerShape(10.dp)).background(Color.DarkGray))
                }
            }
        }
        rule.runOnIdle { requester.requestFocus() }
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(300)
        val artwork = rule.onNodeWithTag("artwork", useUnmergedTree = true)
        val first = pixels(artwork)
        rule.mainClock.advanceTimeBy(1000)
        assertTrue(first.contentEquals(pixels(artwork)))
        rule.mainClock.autoAdvance = true
    }

    private fun pixels(node: SemanticsNodeInteraction): IntArray {
        val image = node.captureToImage().toPixelMap()
        return IntArray(image.width * image.height) { index -> image[index % image.width, index / image.width].hashCode() }
    }
}
