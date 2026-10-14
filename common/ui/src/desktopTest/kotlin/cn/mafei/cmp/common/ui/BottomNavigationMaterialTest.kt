package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.BottomNavigationType
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.glass.GlassStyleScope
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidBottomTab
import cn.mafei.cmp.common.glass.components.LiquidBottomTabs
import cn.mafei.cmp.common.glass.components.LiquidBottomTabsStyle
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BottomNavigationMaterialTest {
    @get:org.junit.Rule val compose = createComposeRule()

    @Test fun bothNavigationTypesRenderAndRespondInEveryMaterialAndTheme() {
        val material = mutableStateOf(GlassStyle.Liquid)
        val dark = mutableStateOf(true)
        val type = mutableStateOf(LiquidBottomTabsStyle.Bar)
        val selected = mutableStateOf(0)
        val captures = mutableListOf<Pair<String, BufferedImage>>()
        val captureDirectory = System.getenv("MAFEI_CAPTURE_DIR")
        compose.setContent {
            MaFeiTheme(darkTheme = dark.value) {
                GlassStyleScope(material.value) {
                    val backdrop = rememberGlassBackdrop()
                    Box(Modifier.size(390.dp, 180.dp).testTag("scene")) {
                        Canvas(Modifier.size(390.dp, 180.dp).captureGlassBackdrop(backdrop)) {
                            drawRect(Color(0xFF3F6F7A))
                            drawCircle(Color(0xFFCE9A68), 160.dp.toPx(), Offset(80.dp.toPx(), 150.dp.toPx()))
                            drawCircle(Color(0xFF1A2735), 120.dp.toPx(), Offset(340.dp.toPx(), 100.dp.toPx()))
                            repeat(12) { stripe ->
                                drawLine(Color.White.copy(alpha = .2f), Offset(stripe * 35.dp.toPx(), 0f),
                                    Offset(stripe * 35.dp.toPx() + 100.dp.toPx(), size.height), 6.dp.toPx())
                            }
                        }
                        LiquidButton({}, backdrop,
                            Modifier.align(Alignment.TopEnd).padding(12.dp).size(44.dp), horizontalPadding = 0.dp) {
                            MaFeiIcon(MaFeiGlyph.Search, color = MaterialTheme.colorScheme.onSurface)
                        }
                        LiquidBottomTabs({ selected.value }, { selected.value = it }, backdrop,
                            tabsCount = 3, widthBaselineTabCount = 4, style = type.value, bottomInset = 24.dp,
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .padding(bottom = if (type.value == LiquidBottomTabsStyle.Bar) 0.dp else 24.dp)) {
                            listOf("首页" to MaFeiGlyph.Home, "收藏" to MaFeiGlyph.Favorite, "我的" to MaFeiGlyph.Account)
                                .forEachIndexed { index, (title, glyph) ->
                                    LiquidBottomTab({ selected.value = index }, index = index, isSelected = selected.value == index) {
                                        MaFeiIcon(glyph, color = MaterialTheme.colorScheme.onSurface)
                                        Text(title, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                        }
                    }
                }
            }
        }
        fun capture(title: String) {
            val bitmap = captureScene("scene")
            if (captureDirectory != null) {
                captures += title to bitmap
            }
        }
        for (navigation in listOf(LiquidBottomTabsStyle.Bar, LiquidBottomTabsStyle.Standard)) {
            for (isDark in listOf(true, false)) {
                for (style in GlassStyle.entries) {
                    compose.runOnIdle { type.value = navigation; dark.value = isDark; material.value = style; selected.value = 0 }
                    compose.waitForIdle()
                    val label = "$navigation / $style / ${if (isDark) "dark" else "light"}"
                    capture("$label / idle")
                    val idleText = if (style != GlassStyle.Liquid) legibleTextPixels(isDark) else 0
                    compose.mainClock.autoAdvance = false
                    compose.onNodeWithText("首页").performTouchInput { down(center) }
                    compose.mainClock.advanceTimeBy(160)
                    capture("$label / pressed")
                    if (style != GlassStyle.Liquid) {
                        assertTrue(legibleTextPixels(isDark) >= idleText * .7f,
                            "Fallback selection must not blur its own label: $label")
                    }
                    compose.onNodeWithText("首页").performTouchInput { up() }
                    compose.mainClock.autoAdvance = true
                    compose.waitForIdle()
                    assertEquals(0, selected.value)
                }
            }
        }
        if (captureDirectory != null) {
            val cellWidth = captures.first().second.width
            val cellHeight = captures.first().second.height + 26
            val sheet = BufferedImage(cellWidth * 6, cellHeight * 4, BufferedImage.TYPE_INT_RGB)
            val graphics = sheet.createGraphics()
            graphics.color = java.awt.Color(35, 35, 35)
            graphics.fillRect(0, 0, sheet.width, sheet.height)
            captures.forEachIndexed { index, (title, bitmap) ->
                val x = index % 6 * cellWidth
                val y = index / 6 * cellHeight
                graphics.color = java.awt.Color.WHITE
                graphics.drawString(title, x + 8, y + 18)
                graphics.drawImage(bitmap, x, y + 26, null)
            }
            graphics.dispose()
            val directory = File(captureDirectory).apply { mkdirs() }
            ImageIO.write(sheet, "png", File(directory, "bottom-navigation-materials.png"))
        }
    }

    @Test fun wideNavigationRemainsASidebarInBothThemes() {
        val dark = mutableStateOf(true)
        val type = mutableStateOf(BottomNavigationType.Bar)
        val items = listOf(MaFeiNavItem("home", "首页", MaFeiGlyph.Home),
            MaFeiNavItem("favorite", "收藏", MaFeiGlyph.Favorite))
        compose.setContent {
            MaFeiTheme(darkTheme = dark.value) {
                Box(Modifier.size(1000.dp, 700.dp).testTag("wide-scene")) {
                    MaFeiAdaptiveShell(mafeiLayout(1000f, 700f), items, "home", onDestination = {},
                        libraries = listOf(MaFeiNavItem("movies", "电影", MaFeiGlyph.Library)), selectedLibrary = null, onLibrary = {},
                        accountLabel = "测试账号", bottomNavigationType = type.value,
                        background = { Canvas(Modifier.size(1000.dp, 700.dp)) {
                            drawRect(Color(0xFF3F6F7A))
                            drawCircle(Color(0xFFCE9A68), 400.dp.toPx(), Offset(600.dp.toPx(), 250.dp.toPx()))
                        } }, accountActions = {}, scene = {}, overlay = {})
                }
            }
        }
        for (isDark in listOf(true, false)) {
            compose.runOnIdle { dark.value = isDark; type.value = BottomNavigationType.Bar }
            compose.onNodeWithTag("liquid-bottom-tabs").assertDoesNotExist()
            val before = captureScene("wide-scene")
            compose.runOnIdle { type.value = BottomNavigationType.Floating }
            compose.onNodeWithTag("liquid-bottom-tabs").assertDoesNotExist()
            val after = captureScene("wide-scene")
            assertEquals(before.getRGB(24, 300), after.getRGB(24, 300))
            System.getenv("MAFEI_CAPTURE_DIR")?.let { directory ->
                val output = File(directory).apply { mkdirs() }
                ImageIO.write(after, "png", File(output, "sidebar-${if (isDark) "dark" else "light"}.png"))
            }
        }
    }

    private fun captureScene(tag: String): BufferedImage {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val output = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val c = pixels[x, y]
            output.setRGB(x, y, (255 shl 24) or ((c.red * 255).toInt() shl 16) or
                ((c.green * 255).toInt() shl 8) or (c.blue * 255).toInt())
        }
        return output
    }

    private fun legibleTextPixels(dark: Boolean): Int {
        val pixels = compose.onNodeWithText("首页").captureToImage().toPixelMap()
        var count = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val c = pixels[x, y]
            if (if (dark) c.red > .85f && c.green > .85f && c.blue > .85f
                else c.red < .15f && c.green < .15f && c.blue < .15f) count++
        }
        assertTrue(count > 0, "Navigation label must remain legible")
        return count
    }
}
