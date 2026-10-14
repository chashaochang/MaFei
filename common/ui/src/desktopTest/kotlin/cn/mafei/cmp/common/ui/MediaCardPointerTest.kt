package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MediaCardPointerTest {
    @get:org.junit.Rule val rule = createComposeRule()
    private lateinit var focusManager: FocusManager
    private val item = MediaItem(MediaId("movie"), ServerId("server"), ProviderKind.Jellyfin,
        "影片标题", mediaType = MediaType.Movie, year = 2026)

    @Test fun inputGroupClearsHoverAndDoesNotTreatATouchPressAsKeyboardFocus() {
        lateinit var interaction: MediaCardInteraction
        rule.setContent {
            TestTheme {
                interaction = rememberMediaCardInteraction()
                Box(Modifier.size(200.dp).testTag("group").mafeiMediaCardInteraction(interaction, {}, "card")) {
                    Text("${interaction.hovered}/${interaction.focused}")
                }
            }
        }
        rule.runOnIdle { focusManager.clearFocus(force = true) }
        assertTrue(!interaction.showActions, "Initial: ${interaction.hovered}/${interaction.focused}")
        rule.onNodeWithTag("group").performMouseInput { moveTo(center) }
        rule.waitForIdle()
        assertTrue(interaction.hovered)
        rule.onNodeWithTag("group").performMouseInput { moveTo(Offset(-1f, -1f)) }
        rule.waitForIdle()
        assertTrue(!interaction.showActions, "Exited: ${interaction.hovered}/${interaction.focused}")
        rule.onNodeWithTag("group").performTouchInput { down(center) }
        rule.waitForIdle()
        assertTrue(!interaction.showActions, "Touch: ${interaction.hovered}/${interaction.focused}")
        rule.onNodeWithTag("group").performTouchInput { up() }
    }

    @Test fun captionsDoNotMoveOrChangePixelsWhenAnySharedCardIsHovered() {
        val kind = mutableStateOf(0)
        val dark = mutableStateOf(true)
        rule.setContent {
            TestTheme(dark.value) {
                Box(Modifier.size(390.dp, 400.dp).background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    when (kind.value) {
                        0 -> MediaCard("影片标题", "2026", {}, Modifier.width(160.dp).testTag("card"), poster = { Artwork(it) })
                        1 -> LegacyVideoCard(item, {}, Modifier.width(160.dp).testTag("card"), subtitle = "2026", poster = { Artwork(it) })
                        else -> LegacyBaseItemCard(item, {}, Modifier.testTag("card"), poster = { Artwork(it) })
                    }
                }
            }
        }
        for (isDark in listOf(true, false)) for (cardKind in 0..2) {
            rule.runOnIdle { kind.value = cardKind; dark.value = isDark; focusManager.clearFocus(force = true) }
            val title = rule.onNodeWithText("影片标题", useUnmergedTree = true)
            val subtitle = rule.onNodeWithText("2026", useUnmergedTree = true)
            val titleBounds = title.fetchSemanticsNode().boundsInRoot
            val subtitleBounds = subtitle.fetchSemanticsNode().boundsInRoot
            val titlePixels = pixels(title)
            val subtitlePixels = pixels(subtitle)
            val card = rule.onNodeWithTag("card")
            card.performMouseInput { moveTo(center) }
            rule.waitForIdle()
            action("播放影片标题").assertIsDisplayed()
            assertEquals(titleBounds, title.fetchSemanticsNode().boundsInRoot)
            assertEquals(subtitleBounds, subtitle.fetchSemanticsNode().boundsInRoot)
            assertTrue(titlePixels.contentEquals(pixels(title)), "Title must stay unchanged: $cardKind/$isDark")
            assertTrue(subtitlePixels.contentEquals(pixels(subtitle)), "Subtitle must stay unchanged: $cardKind/$isDark")
            card.performMouseInput { moveTo(Offset(-1f, -1f)) }
            rule.waitForIdle()
            action("播放影片标题").assertDoesNotExist()
        }
    }

    @Test fun quickActionsFitNarrowPostersAndLandscapeArtworkAndDispatchOnlyOnce() {
        val width = mutableStateOf(100)
        val aspect = mutableStateOf(2f / 3f)
        var opens = 0
        var plays = 0
        var favorites = 0
        var watched = 0
        rule.setContent {
            TestTheme {
                Box(Modifier.size(390.dp, 360.dp).padding(24.dp)) {
                    LegacyVideoCard(item, { opens++ }, Modifier.width(width.value.dp).testTag("card"),
                        poster = { Artwork(it.testTag("artwork")) }, posterAspectRatio = aspect.value,
                        onPlay = { plays++ }, onToggleFavorite = { favorites++ }, onToggleWatched = { watched++ })
                }
            }
        }
        for ((w, ratio) in listOf(100 to 2f / 3f, 160 to 16f / 9f, 240 to 16f / 9f)) {
            rule.runOnIdle { width.value = w; aspect.value = ratio; focusManager.clearFocus(force = true) }
            val card = rule.onNodeWithTag("card")
            card.performMouseInput { moveTo(center) }
            rule.waitForIdle()
            val artwork = rule.onNodeWithTag("artwork", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val actions = listOf("播放影片标题", "设置已观看", "收藏").map(::action)
            val bounds = actions.map { it.fetchSemanticsNode().boundsInRoot }
            bounds.forEach { assertTrue(artwork.contains(it.topLeft) && artwork.contains(it.bottomRight), "$w/$ratio: $it outside $artwork") }
            for (i in bounds.indices) for (j in i + 1 until bounds.size) {
                assertTrue(!bounds[i].overlaps(bounds[j]), "Quick actions must not overlap")
            }
            val opensBeforeActions = opens
            actions.forEach { action -> action.performMouseInput { moveTo(center); click() } }
            assertEquals(opensBeforeActions, opens, "Quick action must not also open details")
            card.performMouseInput { moveTo(Offset(-1f, -1f)) }
            rule.waitForIdle()
            card.performClick()
        }
        assertEquals(3, opens)
        assertEquals(3, plays)
        assertEquals(3, favorites)
        assertEquals(3, watched)
    }

    @Test fun movingBetweenArtworkCaptionAndActionsKeepsTheSameHoverGroup() {
        rule.setContent { TestTheme { MediaCard("影片标题", "2026", {}, Modifier.width(160.dp).testTag("card"), poster = { Artwork(it) }) } }
        rule.runOnIdle { focusManager.clearFocus(force = true) }
        val card = rule.onNodeWithTag("card")
        val bounds = card.fetchSemanticsNode().boundsInRoot
        val title = rule.onNodeWithText("影片标题", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        card.performMouseInput { moveTo(center) }
        card.performMouseInput { moveTo(title.center - bounds.topLeft) }
        action("播放影片标题").assertIsDisplayed()
        action("播放影片标题").performMouseInput { moveTo(center) }
        action("播放影片标题").assertIsDisplayed()
        card.performMouseInput { moveTo(Offset(-1f, -1f)) }
        action("播放影片标题").assertDoesNotExist()
    }

    @Test fun keyboardFocusCanReachQuickActionsWithoutDismissingThem() {
        val requester = FocusRequester()
        var plays = 0
        rule.setContent {
            TestTheme {
                MediaCard("影片标题", "2026", {}, Modifier.width(160.dp).focusRequester(requester).testTag("card"),
                    poster = { Artwork(it) }, onPlay = { plays++ })
            }
        }
        rule.runOnIdle { requester.requestFocus() }
        action("播放影片标题").assertIsDisplayed()
        rule.onNodeWithTag("card").performKeyInput { pressKey(Key.Tab) }
        val play = action("播放影片标题")
        play.assertIsDisplayed().assertIsFocused()
        play.performKeyInput { pressKey(Key.Enter) }
        assertEquals(1, plays)
    }

    @Test fun mouseActivationDoesNotLeaveKeyboardFeedbackAfterThePointerLeaves() {
        var plays = 0
        rule.setContent {
            TestTheme { MediaCard("影片标题", "2026", {}, Modifier.width(160.dp).testTag("card"),
                poster = { Artwork(it) }, onPlay = { plays++ }) }
        }
        rule.runOnIdle { focusManager.clearFocus(force = true) }
        val card = rule.onNodeWithTag("card")
        card.performMouseInput { moveTo(center) }
        action("播放影片标题").performMouseInput { moveTo(center); click() }
        card.performMouseInput { moveTo(Offset(-1f, -1f)) }
        action("播放影片标题").assertDoesNotExist()
        assertEquals(1, plays)
    }

    @Test fun pressAffectsOnlyArtworkAndTouchDoesNotRevealMouseControls() {
        rule.setContent {
            TestTheme {
                Box(Modifier.size(390.dp, 400.dp).background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    MediaCard("影片标题", "2026", {}, Modifier.width(160.dp).testTag("card"), poster = { Artwork(it.testTag("artwork")) })
                }
            }
        }
        rule.runOnIdle { focusManager.clearFocus(force = true) }
        val title = rule.onNodeWithText("影片标题", useUnmergedTree = true)
        val captionBefore = pixels(title)
        val artworkBefore = pixels(rule.onNodeWithTag("artwork", useUnmergedTree = true))
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("card").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(100)
        assertTrue(captionBefore.contentEquals(pixels(title)))
        assertNotEquals(artworkBefore.toList(), pixels(rule.onNodeWithTag("artwork", useUnmergedTree = true)).toList())
        action("播放影片标题").assertDoesNotExist()
        rule.onNodeWithTag("card").performTouchInput { up() }
        rule.mainClock.autoAdvance = true
    }

    @Test fun unavailableActionsAreNotShownAndHoverFadesInsteadOfHardSwitching() {
        rule.setContent { TestTheme { MediaCard("影片标题", "2026", {}, Modifier.width(160.dp).testTag("card"), poster = { Artwork(it) }) } }
        rule.runOnIdle { focusManager.clearFocus(force = true) }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("card").performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(48)
        val during = pixels(rule.onNodeWithTag("card"))
        rule.mainClock.advanceTimeBy(200)
        val settled = pixels(rule.onNodeWithTag("card"))
        assertTrue(!during.contentEquals(settled), "Hover has a transition, not a hard switch")
        action("收藏").assertDoesNotExist()
        action("设置已观看").assertDoesNotExist()
        rule.onNodeWithTag("card").performMouseInput { moveTo(Offset(-1f, -1f)) }
        rule.mainClock.advanceTimeBy(200)
        action("播放影片标题").assertDoesNotExist()
        rule.mainClock.autoAdvance = true
    }

    @Test fun capturesNarrowAndWideCardsInBothThemesAndEveryInteractionState() {
        val dark = mutableStateOf(true)
        val wide = mutableStateOf(false)
        val images = mutableListOf<Pair<String, BufferedImage>>()
        rule.setContent {
            TestTheme(dark.value) {
                Row(Modifier.size(if (wide.value) 1000.dp else 390.dp, 340.dp).testTag("scene")
                    .background(MaterialTheme.colorScheme.background).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MediaCard("影片标题", "2026 · 电影", {}, Modifier.width(160.dp).testTag("poster"),
                        poster = { Artwork(it) }, onToggleFavorite = {}, onToggleWatched = {})
                    LegacyVideoCard(item, {}, Modifier.width(if (wide.value) 240.dp else 160.dp).testTag("landscape"),
                        subtitle = "剧集标题 · 第 1 集", posterAspectRatio = 16f / 9f, poster = { Artwork(it) },
                        onToggleFavorite = {}, onToggleWatched = {})
                    if (wide.value) LegacyBaseItemCard(item, {}, poster = { Artwork(it) }, onToggleFavorite = {}, onToggleWatched = {})
                }
            }
        }
        for (isWide in listOf(false, true)) for (isDark in listOf(true, false)) {
            rule.runOnIdle { wide.value = isWide; dark.value = isDark; focusManager.clearFocus(force = true) }
            val label = "${if (isWide) "wide" else "phone"} / ${if (isDark) "dark" else "light"}"
            fun capture(state: String) { images += "$label / $state" to buffered(rule.onNodeWithTag("scene")) }
            capture("idle")
            rule.mainClock.autoAdvance = false
            rule.onNodeWithTag("poster").performMouseInput { moveTo(center) }
            rule.mainClock.advanceTimeBy(560)
            capture("hover")
            rule.onNodeWithTag("poster").performMouseInput { moveTo(Offset(12f, 12f)); press() }
            rule.mainClock.advanceTimeBy(120)
            capture("press")
            rule.onNodeWithTag("poster").performMouseInput { release() }
            rule.onNodeWithTag("poster").performMouseInput { moveTo(Offset(-1f, -1f)) }
            rule.mainClock.advanceTimeBy(200)
            rule.mainClock.autoAdvance = true
        }
        System.getenv("MAFEI_CAPTURE_DIR")?.let { directory ->
            val sheet = BufferedImage(3000, 4 * 370, BufferedImage.TYPE_INT_RGB)
            val graphics = sheet.createGraphics()
            graphics.color = java.awt.Color(45, 45, 45)
            graphics.fillRect(0, 0, sheet.width, sheet.height)
            images.forEachIndexed { index, (label, bitmap) ->
                val x = index % 3 * 1000
                val y = index / 3 * 370
                graphics.color = java.awt.Color.WHITE
                graphics.drawString(label, x + 8, y + 20)
                graphics.drawImage(bitmap, x, y + 28, null)
            }
            graphics.dispose()
            ImageIO.write(sheet, "png", File(File(directory).apply { mkdirs() }, "media-card-pointer-review.png"))
        }
    }

    @Composable private fun TestTheme(dark: Boolean = true, content: @Composable () -> Unit) {
        focusManager = LocalFocusManager.current
        MaFeiTheme(darkTheme = dark, content = content)
    }

    @Composable private fun Artwork(modifier: Modifier) {
        Canvas(modifier) {
            drawRect(Brush.linearGradient(listOf(Color(0xFF193343), Color(0xFF918374), Color(0xFFCCA472))))
            drawCircle(Color(0xFFC2D0BF), size.width * .23f, Offset(size.width * .70f, size.height * .28f))
            drawLine(Color(0xFF406054), Offset(0f, size.height * .85f), Offset(size.width, size.height * .45f), size.height * .22f)
        }
    }

    private fun pixels(node: SemanticsNodeInteraction): IntArray {
        val image = node.captureToImage().toPixelMap()
        return IntArray(image.width * image.height) { i -> image[i % image.width, i / image.width].hashCode() }
    }

    private fun action(label: String) = rule.onNode(SemanticsMatcher("click action: $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    })

    private fun buffered(node: SemanticsNodeInteraction): BufferedImage {
        val image = node.captureToImage().toPixelMap()
        val result = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val color = image[x, y]
            result.setRGB(x, y, (255 shl 24) or ((color.red * 255).toInt() shl 16) or
                ((color.green * 255).toInt() shl 8) or (color.blue * 255).toInt())
        }
        return result
    }
}
