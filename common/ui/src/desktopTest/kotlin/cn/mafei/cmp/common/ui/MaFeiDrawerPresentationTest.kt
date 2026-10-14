package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.glass.GlassStyleScope
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaFeiDrawerPresentationTest {
    @get:org.junit.Rule val compose = createComposeRule()
    private val sceneColor = mutableStateOf(Color.Red)
    private val style = mutableStateOf(GlassStyle.Liquid)
    private val dark = mutableStateOf(true)
    private val portrait = mutableStateOf(true)
    private var surfaceColor = Color.Unspecified
    private val destinations = listOf(
        MaFeiNavItem("home", "首页", MaFeiGlyph.Home),
        MaFeiNavItem("favorite", "收藏", MaFeiGlyph.Favorite),
    )

    @Test fun drawerAndPersistentSidebarShareTheSameLiveMaterialInEveryStyle() {
        mount()
        val landscapeSamples = mutableMapOf<Triple<GlassStyle, Boolean, Color>, Color>()
        for (isPortrait in listOf(false, true)) {
            compose.runOnIdle { portrait.value = isPortrait }
            if (isPortrait) {
                compose.onNodeWithContentDescription("展开侧边栏").performClick()
            }
            compose.waitForIdle()
            for (material in GlassStyle.entries) for (isDark in listOf(true, false)) {
                compose.runOnIdle { style.value = material; dark.value = isDark }
                for (scene in listOf(Color.Red, Color.Blue)) {
                    compose.runOnIdle { sceneColor.value = scene }
                    val pixels = compose.onRoot().captureToImage().toPixelMap()
                    // Empty panel inset, clear of navigation labels and its edge.
                    val sample = pixels[8, pixels.height / 2]
                    val alpha = when (material) {
                        GlassStyle.Translucent -> .60f
                        GlassStyle.GaussianBlur -> .42f
                        GlassStyle.Liquid -> .16f
                    }
                    // The translucent fallback has no backdrop, so its ordinary
                    // alpha composition includes the drawer scrim underneath.
                    val behind = if (isPortrait && material == GlassStyle.Translucent) {
                        Color.Black.copy(alpha = .5f).compositeOver(scene)
                    } else scene
                    val expected = surfaceColor.copy(alpha = alpha).compositeOver(behind)
                    val key = Triple(material, isDark, scene)
                    assertColor(expected, sample, "$key portrait=$isPortrait")
                    if (isPortrait && material != GlassStyle.Translucent) {
                        assertColor(landscapeSamples.getValue(key), sample, "orientation parity: $key")
                    } else if (!isPortrait) {
                        landscapeSamples[key] = sample
                    }
                    if (material == GlassStyle.Liquid && scene == Color.Blue) {
                        capture("sidebar-${if (isPortrait) "portrait" else "landscape"}-${if (isDark) "dark" else "light"}")
                    }
                }
            }
        }
    }

    private fun assertColor(expected: Color, actual: Color, message: String) {
        assertEquals(expected.red, actual.red, .012f, "$message red")
        assertEquals(expected.green, actual.green, .012f, "$message green")
        assertEquals(expected.blue, actual.blue, .012f, "$message blue")
    }

    @Test fun scrimFadesInBeforeThePanelAndNeverSlidesWithIt() {
        mount()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("展开侧边栏").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val scrim = compose.onNodeWithContentDescription("关闭侧边栏")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(root, scrim, "The scrim must cover the window even during panel entry")
        compose.onNodeWithTag("drawerSidebarPanel", useUnmergedTree = true).assertIsNotDisplayed()
        val entering = compose.onRoot().captureToImage().toPixelMap()
        assertTrue(entering[entering.width - 10, entering.height / 2].red < .98f,
            "Dim the page before revealing the sidebar")
        capture("drawer-scrim-before-panel")
        compose.mainClock.advanceTimeBy(180)
        compose.waitForIdle()
        val partial = compose.onNodeWithTag("drawerSidebarPanel", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        capture("drawer-sliding-panel")
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val settled = compose.onNodeWithTag("drawerSidebarPanel", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(partial.right < settled.right,
            "Only the panel should slide from the left: entering=$partial settled=$settled")
        assertEquals(root, compose.onNodeWithContentDescription("关闭侧边栏")
            .fetchSemanticsNode().boundsInRoot)
    }

    @Test fun scrimStaysUntilThePanelHasLeftAndTheDrawerCanReopen() {
        mount()
        compose.onNodeWithContentDescription("展开侧边栏").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        dismiss()
        compose.mainClock.advanceTimeBy(160)
        compose.onNodeWithContentDescription("关闭侧边栏").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("关闭侧边栏").assertDoesNotExist()
        compose.onNodeWithTag("drawerSidebarPanel", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("展开侧边栏").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("drawerSidebarPanel", useUnmergedTree = true).assertIsDisplayed()
        dismiss()
        compose.mainClock.advanceTimeBy(160)
        compose.onNodeWithContentDescription("关闭侧边栏").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("关闭侧边栏").assertDoesNotExist()
        compose.onNodeWithTag("drawerSidebarPanel", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun pressingTheScrimDoesNotAddButtonTintOrShapeFeedback() {
        mount()
        compose.onNodeWithContentDescription("展开侧边栏").performClick()
        compose.waitForIdle()
        val before = compose.onRoot().captureToImage().toPixelMap()
        val sample = before[before.width - 10, before.height / 2]
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("关闭侧边栏").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width - 8f, height / 2f))
        }
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        val held = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(sample, held[held.width - 10, held.height / 2],
            "A full-window scrim is not a pressable card")
        capture("drawer-scrim-pressed")
        compose.onNodeWithContentDescription("关闭侧边栏").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun dismiss() {
        compose.onNodeWithContentDescription("关闭侧边栏").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width - 8f, height / 2f))
        }
    }

    private fun capture(name: String) {
        val directory = System.getenv("MAFEI_CAPTURE_DIR") ?: return
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val bitmap = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            bitmap.setRGB(x, y, pixels[x, y].toArgb())
        }
        ImageIO.write(bitmap, "png", File(File(directory).apply { mkdirs() }, "$name.png"))
    }

    private fun mount() {
        compose.setContent {
            MaFeiTheme(darkTheme = dark.value) {
                surfaceColor = MaterialTheme.colorScheme.surface
                GlassStyleScope(style.value) {
                    val width = if (portrait.value) 740f else 1000f
                    val height = if (portrait.value) 1000f else 740f
                    Box(Modifier.size(width.dp, height.dp)) {
                        MaFeiAdaptiveShell(
                            layout = mafeiLayout(width, height),
                            destinations = destinations, selectedDestination = "home", onDestination = {},
                            libraries = emptyList(), selectedLibrary = null, onLibrary = {}, accountLabel = "测试",
                            background = { Box(Modifier.fillMaxSize().background(sceneColor.value)) },
                            accountActions = {}, scene = {}, overlay = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }
}
