package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MediaArtworkLiftTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var clearFocus: () -> Unit

    @Test fun shelfFirstAndLastEnlargeOutsideTheirSlotWithoutClippingOrMovingTitles() {
        lateinit var state: LazyListState
        rule.setContent {
            TestTheme {
                state = rememberLazyListState()
                Box(Modifier.size(420.dp, 350.dp).testTag("viewport").clipToBounds()
                    .background(MaterialTheme.colorScheme.background).padding(horizontal = 24.dp)) {
                    val gutter = mediaArtworkHoverSideInset(140.dp)
                    LazyRow(Modifier.fillMaxWidth().mafeiArtworkShelfBleed(gutter), state = state,
                        contentPadding = PaddingValues(start = gutter, end = gutter, top = mediaArtworkHoverTopInset(210.dp)),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(6, key = { it }) { Card(it) }
                    }
                }
            }
        }
        rule.runOnIdle { clearFocus() }
        assertRaisedWithinViewport(0)
        rule.runOnIdle { runBlocking { state.scrollToItem(5) } }
        rule.waitForIdle()
        assertRaisedWithinViewport(5)
    }

    @Test fun gridCornersKeepTheirCaptionsAndDoNotClipAtTheViewport() {
        rule.setContent {
            TestTheme {
                LazyVerticalGrid(GridCells.Fixed(2), Modifier.size(336.dp, 650.dp)
                    .testTag("viewport").clipToBounds().background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 34.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(34.dp)) {
                    items(4) { Card(it) }
                }
            }
        }
        rule.runOnIdle { clearFocus() }
        repeat(4) { assertRaisedWithinViewport(it) }
    }

    @Test fun liftedActionsFollowTheirVisualPositionAndDoNotAlsoOpenDetails() {
        val landscape = mutableStateOf(false)
        val wide = mutableStateOf(false)
        var opens = 0
        var plays = 0
        var watched = 0
        var favorites = 0
        rule.setContent {
            TestTheme {
                Box(Modifier.size(420.dp, 450.dp).padding(36.dp)) {
                    val interaction = rememberMediaCardInteraction(liftArtwork = true)
                    Column(Modifier.width(if (wide.value) 240.dp else 140.dp).testTag("card")
                        .mafeiMediaCardInteraction(interaction, { opens++ }, "details")) {
                        Box(Modifier.fillMaxWidth().aspectRatio(if (landscape.value) 16f / 9f else 2f / 3f)
                            .mafeiArtworkFeedback(interaction, RoundedCornerShape(10.dp))) {
                            Box(Modifier.fillMaxSize().testTag("art").background(Color(0xFF75939D)))
                            MediaCardHoverOverlay("影片", { plays++ }, { watched++ }, { favorites++ },
                                visible = interaction.showActions, liftArtwork = true)
                        }
                    }
                }
            }
        }
        for ((isLandscape, isWide) in listOf(false to false, true to false, true to true)) {
            rule.runOnIdle { landscape.value = isLandscape; wide.value = isWide; clearFocus() }
            val card = rule.onNodeWithTag("card")
            card.performMouseInput { moveTo(center) }
            rule.mainClock.advanceTimeBy(400)
            val artwork = rule.onNodeWithTag("art", true).fetchSemanticsNode().boundsInRoot
            val actions = listOf("播放影片", "设置已观看", "收藏").map(::action)
            val bounds = actions.map { it.fetchSemanticsNode().boundsInRoot }
            bounds.forEach { assertTrue(artwork.contains(it.topLeft) && artwork.contains(it.bottomRight)) }
            assertEquals(artwork.center.x, bounds[0].center.x, 1f, "Play stays centered")
            assertEquals(artwork.center.y, bounds[0].center.y, 1f, "Play stays centered vertically")
            assertTrue(bounds[1].center.x < artwork.center.x, "Watched belongs at the bottom left")
            assertTrue(bounds[2].center.x > artwork.center.x, "Favorite belongs at the bottom right")
            assertEquals(bounds[1].center.y, bounds[2].center.y, 1f)
            assertEquals(artwork.right - bounds[2].right, bounds[1].left - artwork.left, 1f)
            for (i in bounds.indices) for (j in i + 1 until bounds.size)
                assertTrue(!bounds[i].overlaps(bounds[j]), "Actions overlap: $isLandscape/$isWide")
            actions.forEach { it.performMouseInput { moveTo(center); click() } }
            assertEquals(0, opens, "A child action must not open the details page")
            card.performMouseInput { moveTo(Offset(-60f, -60f)) }
            rule.mainClock.advanceTimeBy(400)
        }
        assertEquals(3, plays)
        assertEquals(3, watched)
        assertEquals(3, favorites)
    }

    @Test fun quickPassDoesNotFlashControlsAndSettledHoverDoesNotKeepAnimating() {
        val windowFocused = mutableStateOf(true)
        val info = object : WindowInfo { override val isWindowFocused get() = windowFocused.value }
        var compositions = 0
        rule.setContent {
            CompositionLocalProvider(LocalWindowInfo provides info) {
                TestTheme {
                    Box(Modifier.size(240.dp, 350.dp).testTag("viewport").padding(40.dp)) {
                        SideEffect { compositions++ }
                        Card(0)
                    }
                }
            }
        }
        rule.runOnIdle { clearFocus() }
        val card = rule.onNodeWithTag("card-0")
        rule.mainClock.autoAdvance = false
        card.performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(32)
        card.performMouseInput { moveTo(Offset(-60f, -60f)) }
        rule.mainClock.advanceTimeBy(400)
        action("播放影片 0").assertDoesNotExist()
        card.performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(500)
        action("播放影片 0").assertIsDisplayed()
        val settled = pixels(rule.onNodeWithTag("viewport"))
        val count = compositions
        rule.mainClock.advanceTimeBy(1200)
        assertTrue(settled.contentEquals(pixels(rule.onNodeWithTag("viewport"))))
        assertEquals(count, compositions)
        rule.runOnIdle { windowFocused.value = false }
        rule.mainClock.advanceTimeBy(500)
        action("播放影片 0").assertDoesNotExist()
        rule.mainClock.autoAdvance = true
    }

    @Test fun touchPressDoesNotExposePointerOnlyActions() {
        rule.setContent { TestTheme { Box(Modifier.padding(32.dp)) { Card(0) } } }
        rule.runOnIdle { clearFocus() }
        rule.onNodeWithTag("card-0").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(300)
        action("播放影片 0").assertDoesNotExist()
        rule.onNodeWithTag("card-0").performTouchInput { up() }
    }

    @Test fun enlargedOuterStripStillOpensDetails() {
        var opens = 0
        rule.setContent {
            TestTheme {
                Box(Modifier.padding(40.dp)) {
                    MediaCard("影片", "2026", { opens++ }, Modifier.width(140.dp).testTag("card"),
                        poster = { Box(it.testTag("art").background(Color.Cyan)) }, liftArtwork = true)
                }
            }
        }
        rule.runOnIdle { clearFocus() }
        rule.onNodeWithTag("card").performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(400)
        val art = rule.onNodeWithTag("art", true)
        art.fetchSemanticsNode().boundsInRoot
        art.performMouseInput { moveTo(Offset(2f, height / 2f)); click() }
        assertEquals(1, opens, "The enlarged visible strip outside the resting slot must remain clickable")
    }

    @Test fun captureRealArtworkSamplesWhenProvided() {
        val paths = System.getenv("MAFEI_HOVER_REVIEW_ARTWORK")?.split(File.pathSeparator) ?: return
        val directory = System.getenv("MAFEI_CAPTURE_DIR") ?: return
        val covers = paths.map { org.jetbrains.skia.Image.makeFromEncoded(File(it).readBytes()).toComposeImageBitmap() }
        val dark = mutableStateOf(true)
        val wide = mutableStateOf(true)
        val frames = mutableListOf<Pair<String, BufferedImage>>()
        rule.setContent {
            TestTheme(dark.value) {
                Column(Modifier.size(if (wide.value) 900.dp else 390.dp, 350.dp).testTag("scene")
                    .background(MaterialTheme.colorScheme.background).padding(horizontal = 24.dp)) {
                    Text("最近添加", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurface)
                    val gutter = mediaArtworkHoverSideInset(140.dp)
                    LazyRow(Modifier.fillMaxWidth().mafeiArtworkShelfBleed(gutter),
                        contentPadding = PaddingValues(start = gutter, end = gutter, top = mediaArtworkHoverTopInset(210.dp)),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(5) { index ->
                            MediaCard("封面样板 ${index + 1}", "2026 · 电影", {},
                                Modifier.width(140.dp).testTag("sample-$index"),
                                poster = { Image(covers[index % covers.size], null, it, contentScale = ContentScale.Crop) },
                                onToggleFavorite = {}, onToggleWatched = {}, liftArtwork = true)
                        }
                    }
                }
            }
        }
        for ((isWide, isDark) in listOf(true to true, false to true, true to false, false to false)) {
            rule.runOnIdle { wide.value = isWide; dark.value = isDark; clearFocus() }
            val card = rule.onNodeWithTag("sample-0")
            card.performMouseInput { moveTo(Offset(-60f, -60f)) }
            rule.mainClock.advanceTimeBy(400)
            val label = "${if (isWide) "Wide" else "Narrow"} / ${if (isDark) "Dark" else "Light"}"
            frames += "$label / Rest" to buffered(rule.onNodeWithTag("scene"))
            rule.mainClock.autoAdvance = false
            card.performMouseInput { moveTo(center) }
            rule.mainClock.advanceTimeBy(500)
            frames += "$label / Hover" to buffered(rule.onNodeWithTag("scene"))
            card.performMouseInput { moveTo(Offset(16f, 16f)); press() }
            rule.mainClock.advanceTimeBy(150)
            frames += "$label / Press" to buffered(rule.onNodeWithTag("scene"))
            card.performMouseInput { cancel(); moveTo(Offset(-60f, -60f)) }
            rule.mainClock.advanceTimeBy(400)
            rule.mainClock.autoAdvance = true
        }
        val sheet = BufferedImage(2700, 4 * 380, BufferedImage.TYPE_INT_RGB)
        sheet.createGraphics().apply {
            color = java.awt.Color(45, 45, 45); fillRect(0, 0, sheet.width, sheet.height)
            frames.forEachIndexed { index, (label, bitmap) ->
                val x = index % 3 * 900; val y = index / 3 * 380
                color = java.awt.Color.WHITE; drawString(label, x + 8, y + 20)
                drawImage(bitmap, x, y + 28, null)
            }
            dispose()
        }
        ImageIO.write(sheet, "png", File(File(directory).apply { mkdirs() }, "home-lift-review.png"))
    }

    private fun assertRaisedWithinViewport(index: Int) {
        val art = rule.onNodeWithTag("art-$index", true)
        val title = rule.onNodeWithText("影片 $index", useUnmergedTree = true)
        val idle = art.fetchSemanticsNode().boundsInRoot
        val titleBounds = title.fetchSemanticsNode().boundsInRoot
        val card = rule.onNodeWithTag("card-$index")
        card.performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(400)
        val lifted = art.fetchSemanticsNode().boundsInRoot
        val viewport = rule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(idle.width * ArtworkLiftScale, lifted.width, .6f)
        assertEquals(idle.height * ArtworkLiftScale, lifted.height, .6f)
        assertTrue(lifted.left > viewport.left && lifted.right < viewport.right)
        assertTrue(lifted.top > viewport.top && lifted.bottom < titleBounds.top,
            "Cover $lifted must fit above caption $titleBounds and inside $viewport")
        assertEquals(titleBounds, title.fetchSemanticsNode().boundsInRoot)
        card.performMouseInput { moveTo(Offset(-100f, -100f)) }
        rule.mainClock.advanceTimeBy(400)
        assertEquals(idle, art.fetchSemanticsNode().boundsInRoot)
    }

    @Composable private fun Card(index: Int) {
        MediaCard("影片 $index", "2026 · 电影", {}, Modifier.width(140.dp).testTag("card-$index"),
            poster = { Box(it.testTag("art-$index").background(Color(0xFF75939D))) }, liftArtwork = true)
    }

    @Composable private fun TestTheme(dark: Boolean = true, content: @Composable () -> Unit) {
        val manager = LocalFocusManager.current
        clearFocus = { manager.clearFocus(force = true) }
        MaFeiTheme(darkTheme = dark, content = content)
    }

    private fun action(label: String) = rule.onNode(SemanticsMatcher("click: $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    })
    private fun buffered(node: SemanticsNodeInteraction): BufferedImage {
        val pixels = node.captureToImage().toPixelMap()
        return BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB).apply {
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                setRGB(x, y, (255 shl 24) or ((color.red * 255).toInt() shl 16) or
                    ((color.green * 255).toInt() shl 8) or (color.blue * 255).toInt())
            }
        }
    }
    private fun pixels(node: SemanticsNodeInteraction): IntArray {
        val bitmap = buffered(node)
        return bitmap.getRGB(0, 0, bitmap.width, bitmap.height, null, 0, bitmap.width)
    }
}
