package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    private var surfaceColor = Color.Unspecified
    private val destinations = listOf(
        MaFeiNavItem("home", "首页", MaFeiGlyph.Home),
        MaFeiNavItem("favorite", "收藏", MaFeiGlyph.Favorite),
    )

    @Test fun drawerHasAnOrdinaryThemeBackgroundInEveryStyle() {
        mount()
        compose.onNodeWithContentDescription("展开侧边栏").performClick()
        compose.waitForIdle()
        for (material in GlassStyle.entries) for (isDark in listOf(true, false)) {
            compose.runOnIdle { style.value = material; dark.value = isDark }
            for (scene in listOf(Color.Red, Color.Blue)) {
                compose.runOnIdle { sceneColor.value = scene }
                val pixels = compose.onNodeWithTag("drawerSidebarPanel", useUnmergedTree = true)
                    .captureToImage().toPixelMap()
                val sample = pixels[pixels.width - 4, pixels.height / 2]
                assertEquals(surfaceColor.red, sample.red, .005f, "$material/$isDark red")
                assertEquals(surfaceColor.green, sample.green, .005f, "$material/$isDark green")
                assertEquals(surfaceColor.blue, sample.blue, .005f, "$material/$isDark blue")
                if (material == GlassStyle.Liquid && scene == Color.Blue) {
                    capture("drawer-${if (isDark) "dark" else "light"}")
                }
            }
        }
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
                    Box(Modifier.size(740.dp, 1000.dp)) {
                        MaFeiAdaptiveShell(
                            layout = mafeiLayout(740f, 1000f),
                            destinations = destinations, selectedDestination = "home", onDestination = {},
                            libraries = emptyList(), selectedLibrary = null, onLibrary = {}, accountLabel = "测试",
                            background = {}, accountActions = {},
                            scene = { Box(Modifier.fillMaxSize().background(sceneColor.value)) }, overlay = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }
}
