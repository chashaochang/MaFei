package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.MaFeiInsetsDp
import cn.mafei.cmp.common.design_system.MaFeiLayoutFamily
import cn.mafei.cmp.common.design_system.MaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.MaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.glass.GlassStyleScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MaFeiShellTopInsetTest {
    @get:org.junit.Rule val rule = createComposeRule()
    private val secondary = mutableStateOf(false)
    private val dark = mutableStateOf(true)
    private val family = mutableStateOf(MaFeiLayoutFamily.Desktop)
    private val topInset = mutableStateOf(0f)
    private var routeSafeArea = MaFeiSafeArea()
    private var pageClicks = 0
    private var windowClicks = 0
    private val paneColor = Color(0xFF36566B)

    @Test fun desktopPrimaryAndSecondaryFillTheContentPaneToTheTop() {
        setContent()
        for (isDark in listOf(true, false)) for (isSecondary in listOf(false, true)) {
            rule.runOnIdle { dark.value = isDark; secondary.value = isSecondary }
            val window = rule.onNodeWithTag("window").fetchSemanticsNode().boundsInRoot
            val pane = rule.onNodeWithTag("pane").fetchSemanticsNode().boundsInRoot
            assertEquals(window.top, pane.top, "The desktop shell must not add an empty titlebar above the page")
            assertEquals(window.bottom, pane.bottom)
            val bitmap = rule.onNodeWithTag("window").captureToImage().toPixelMap()
            assertEquals(paneColor, bitmap[bitmap.width - 80, 4], "The page background must reach the top, not expose a black strip")
        }
    }

    @Test fun onlyTheSidebarOwnsWindowControlsAndTheDragArea() {
        setContent()
        rule.onAllNodesWithTag("window-drag").assertCountEquals(1)
        val pane = rule.onNodeWithTag("pane").fetchSemanticsNode().boundsInRoot
        val drag = rule.onNodeWithTag("window-drag").fetchSemanticsNode().boundsInRoot
        val control = rule.onNodeWithTag("window-control").fetchSemanticsNode().boundsInRoot
        assertTrue(drag.right <= pane.left && control.right <= pane.left)
        // Both clicks use the measured nodes' centers, including a page control at the top edge.
        rule.onNodeWithTag("page-control").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("page-control").performMouseInput { click(center) }
        rule.onNodeWithTag("window-control").performMouseInput { click(center) }
        assertEquals(1, pageClicks)
        assertEquals(1, windowClicks)
    }

    @Test fun actualPhoneAndTabletInsetsStillReachThePageWithoutShellPadding() {
        setContent()
        for (form in listOf(MaFeiLayoutFamily.Phone, MaFeiLayoutFamily.Tablet, MaFeiLayoutFamily.Desktop)) {
            rule.runOnIdle { family.value = form; topInset.value = 28f }
            val window = rule.onNodeWithTag("window").fetchSemanticsNode().boundsInRoot
            val pane = rule.onNodeWithTag("pane").fetchSemanticsNode().boundsInRoot
            assertEquals(window.top, pane.top)
            assertEquals(28.dp, routeSafeArea.top, "Removing a synthetic desktop strip must not erase real system insets")
        }
    }

    private fun setContent() = rule.setContent {
        MaFeiTheme(darkTheme = dark.value) {
            GlassStyleScope(GlassStyle.Translucent) {
                val phone = family.value == MaFeiLayoutFamily.Phone
                val width = if (phone) 390.dp else 1000.dp
                val layout = mafeiLayout(width.value, 700f).copy(family = family.value)
                CompositionLocalProvider(LocalMaFeiWindowFeatures provides MaFeiWindowFeatures(
                    safeInsets = MaFeiInsetsDp(top = topInset.value),
                    desktopWindow = family.value == MaFeiLayoutFamily.Desktop,
                    windowedHost = !phone,
                )) {
                    Box(Modifier.size(width, 700.dp).testTag("window")) {
                        MaFeiAdaptiveShell(layout, listOf(MaFeiNavItem("home", "首页", MaFeiGlyph.Home)),
                            "home", onDestination = {}, libraries = emptyList(), selectedLibrary = null,
                            onLibrary = {}, accountLabel = "测试账号", contentNavigationActive = secondary.value,
                            background = { Box(Modifier.fillMaxSize().background(Color.Black)) }, accountActions = {},
                            windowControls = {
                                Box(Modifier.size(12.dp).testTag("window-control").clickable { windowClicks++ })
                            },
                            windowDragArea = { content ->
                                Box(Modifier.size(32.dp).testTag("window-drag")) { content() }
                            },
                            scene = {
                                routeSafeArea = LocalMaFeiSafeArea.current
                                Box(Modifier.fillMaxSize().testTag("pane").background(paneColor)) {
                                    Box(Modifier.align(Alignment.TopEnd).size(32.dp).testTag("page-control")
                                        .clickable { pageClicks++ })
                                }
                            }, overlay = {})
                    }
                }
            }
        }
    }
}
