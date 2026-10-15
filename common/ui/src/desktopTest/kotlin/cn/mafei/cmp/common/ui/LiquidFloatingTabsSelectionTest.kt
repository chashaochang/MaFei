package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.glass.GlassStyleScope
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidFloatingTabs
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquidFloatingTabsSelectionTest {
    @get:org.junit.Rule val compose = createComposeRule()

    @Test fun whiteBackgroundFollowsSelectionInEveryMaterialAndTheme() {
        val selected = mutableStateOf(0)
        val style = mutableStateOf(GlassStyle.Liquid)
        val dark = mutableStateOf(true)
        val labels = listOf("最近添加", "电影", "剧集")
        compose.setContent {
            MaFeiTheme(darkTheme = dark.value) {
                GlassStyleScope(style.value) {
                    val backdrop = rememberGlassBackdrop()
                    Box(Modifier.size(390.dp, 80.dp)) {
                        Box(Modifier.size(390.dp, 80.dp).captureGlassBackdrop(backdrop)
                            .background(Color(0xFF526F8A)))
                        LiquidFloatingTabs(labels, selected.value, { selected.value = it }, backdrop)
                    }
                }
            }
        }
        for (material in GlassStyle.entries) for (isDark in listOf(true, false)) {
            compose.runOnIdle { style.value = material; dark.value = isDark; selected.value = 0 }
            for (index in listOf(0, 1, 2, 0)) {
                compose.onNodeWithText(labels[index], useUnmergedTree = true).onParent()
                    .assertHasClickAction().performClick()
                compose.waitForIdle()
                assertEquals(index, selected.value)
                labels.forEachIndexed { labelIndex, label ->
                    val tab = compose.onNodeWithText(label, useUnmergedTree = true).onParent()
                    if (labelIndex == index) tab.assertIsSelected() else tab.assertIsNotSelected()
                    val pixels = tab.captureToImage().toPixelMap()
                    val body = pixels[7, pixels.height / 2]
                    val white = body.red > .95f && body.green > .95f && body.blue > .95f
                    assertTrue(white == (labelIndex == index),
                        "$material/$isDark: only selected $index must be white; $label was $body")
                }
            }
        }
    }
}
