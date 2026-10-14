package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.glass.GlassStyleScope
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidBottomTab
import cn.mafei.cmp.common.glass.components.LiquidBottomTabs
import cn.mafei.cmp.common.glass.components.LiquidBottomTabsStyle
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals

class BottomNavigationTypeTest {
    @get:org.junit.Rule val compose = createComposeRule()

    @Test fun barFillsWidthAndBottomSafeAreaWithoutCollapsing() {
        val style = mutableStateOf(LiquidBottomTabsStyle.Bar)
        val width = mutableStateOf(390.dp)
        val selected = mutableStateOf(0)
        var selections = 0
        compose.setContent {
            MaFeiTheme {
                GlassStyleScope(GlassStyle.Translucent) {
                    val backdrop = rememberGlassBackdrop()
                    Box(Modifier.size(width.value, 200.dp)) {
                        Box(Modifier.size(width.value, 200.dp).captureGlassBackdrop(backdrop).background(Color.Red))
                        LiquidBottomTabs({ selected.value }, { selected.value = it; selections++ }, backdrop,
                            tabsCount = 3, widthBaselineTabCount = 4, style = style.value,
                            compactProgress = { 1f }, bottomInset = 24.dp,
                            modifier = Modifier.align(Alignment.BottomCenter)) {
                            listOf("首页", "收藏", "我的").forEachIndexed { index, title ->
                                LiquidBottomTab({ selected.value = index; selections++ }, index = index,
                                    isSelected = selected.value == index) { Text(title) }
                            }
                        }
                    }
                }
            }
        }
        val bar = compose.onNodeWithTag("liquid-bottom-tabs")
        bar.assertWidthIsEqualTo(390.dp).assertHeightIsEqualTo(88.dp)
        val bottom = bar.fetchSemanticsNode().boundsInRoot.bottom
        compose.onNodeWithText("收藏").performClick()
        compose.waitForIdle()
        assertEquals(1, selected.value)
        assertEquals(1, selections, "A tab click must deliver only one navigation event")
        compose.runOnIdle { width.value = 500.dp }
        bar.assertWidthIsEqualTo(500.dp)
        compose.runOnIdle { style.value = LiquidBottomTabsStyle.Standard }
        bar.assertWidthIsEqualTo(58.dp).assertHeightIsEqualTo(58.dp)
        assertEquals(bottom, bar.fetchSemanticsNode().boundsInRoot.bottom)
        assertEquals(1, selected.value)
        compose.runOnIdle { style.value = LiquidBottomTabsStyle.Bar }
        bar.assertWidthIsEqualTo(500.dp).assertHeightIsEqualTo(88.dp)
    }

    @Test fun externalSelectionIsSilentAndDraggingStillSelectsOnceAfterResizing() {
        val selected = mutableStateOf(0)
        val width = mutableStateOf(390.dp)
        val events = mutableListOf<Int>()
        compose.setContent {
            MaFeiTheme {
                val backdrop = rememberGlassBackdrop()
                Box(Modifier.size(width.value, 200.dp)) {
                    Box(Modifier.size(width.value, 200.dp).captureGlassBackdrop(backdrop).background(Color.Blue))
                    LiquidBottomTabs({ selected.value }, { selected.value = it; events += it }, backdrop,
                        tabsCount = 3, style = LiquidBottomTabsStyle.Bar) {
                        listOf("首页", "收藏", "我的").forEachIndexed { index, title ->
                            LiquidBottomTab({ selected.value = index; events += index }, index = index,
                                isSelected = selected.value == index) { Text(title) }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { selected.value = 1; width.value = 500.dp }
        compose.waitForIdle()
        assertEquals(emptyList(), events, "Synchronizing the route must not navigate again")
        // Touch coordinates come from the real measured navigation bounds.
        compose.onNodeWithTag("liquid-bottom-tabs").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(this.width * .25f, 0f), delayMillis = 160)
            up()
        }
        compose.waitForIdle()
        assertEquals(2, selected.value)
        assertEquals(listOf(2), events)
    }

    @Test fun expandedFloatingDockRetainsItsExistingGeometry() {
        compose.setContent {
            MaFeiTheme {
                val backdrop = rememberGlassBackdrop()
                Box(Modifier.size(390.dp, 200.dp)) {
                    Box(Modifier.size(390.dp, 200.dp).captureGlassBackdrop(backdrop).background(Color.Blue))
                    LiquidBottomTabs({ 0 }, {}, backdrop, tabsCount = 3, widthBaselineTabCount = 4) {
                        repeat(3) { index -> LiquidBottomTab({}, index = index, isSelected = index == 0) { Text("Tab $index") } }
                    }
                }
            }
        }
        compose.onNodeWithTag("liquid-bottom-tabs").assertWidthIsEqualTo(296.dp).assertHeightIsEqualTo(64.dp)
    }

    @Test fun tabletSidebarDoesNotBecomeABottomBarWhenThePreferenceChanges() {
        val type = mutableStateOf(cn.mafei.cmp.common.design_system.BottomNavigationType.Bar)
        val items = listOf(MaFeiNavItem("home", "首页", MaFeiGlyph.Home),
            MaFeiNavItem("favorite", "收藏", MaFeiGlyph.Favorite))
        compose.setContent {
            MaFeiTheme {
                Box(Modifier.size(1000.dp, 700.dp)) {
                    MaFeiAdaptiveShell(mafeiLayout(1000f, 700f), items, "home", onDestination = {},
                        libraries = emptyList(), selectedLibrary = null, onLibrary = {}, accountLabel = "测试账号",
                        bottomNavigationType = type.value, background = {}, accountActions = {}, scene = {}, overlay = {})
                }
            }
        }
        compose.onNodeWithTag("liquid-bottom-tabs").assertDoesNotExist()
        compose.onNodeWithText("首页").assertExists()
        compose.runOnIdle { type.value = cn.mafei.cmp.common.design_system.BottomNavigationType.Floating }
        compose.onNodeWithTag("liquid-bottom-tabs").assertDoesNotExist()
    }
}
