package cn.mafei.cmp.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.BottomNavigationType
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.glass.GlassStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class GlassStyleScreenTest {
    @get:org.junit.Rule val compose = createComposeRule()

    @Test fun navigationChoiceIsIndependentOfMaterialAndIntensityVisibility() {
        val material = mutableStateOf(GlassStyle.Translucent)
        val navigation = mutableStateOf(BottomNavigationType.Floating)
        compose.setContent {
            MaFeiTheme {
                Box(Modifier.size(390.dp, 700.dp)) {
                    GlassStyleScreen({}, style = material.value, bottomNavigationType = navigation.value,
                        onSelectStyle = { material.value = it }, onSelectBottomNavigation = { navigation.value = it })
                }
            }
        }
        compose.onNodeWithContentDescription("悬浮").assertIsSelected()
        compose.onNodeWithText("无背景模糊").assertDoesNotExist()
        compose.onNodeWithContentDescription("底栏").performClick()
        assertEquals(BottomNavigationType.Bar, navigation.value)
        assertEquals(GlassStyle.Translucent, material.value)
        compose.onNodeWithContentDescription("液态玻璃").performClick()
        compose.onNodeWithText("无背景模糊").assertExists()
        compose.onNodeWithContentDescription("底栏").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("高斯模糊").performScrollTo().performClick()
        compose.onNodeWithText("无背景模糊").assertDoesNotExist()
        assertEquals(BottomNavigationType.Bar, navigation.value)
    }
}
