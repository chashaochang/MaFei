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
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.BottomNavigationType
import cn.mafei.cmp.common.design_system.MaFeiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class BottomNavigationSettingsScreenTest {
    @get:org.junit.Rule val compose = createComposeRule()

    @Test fun navigationChoiceRemainsAvailableWithoutMaterialSettings() {
        val navigation = mutableStateOf(BottomNavigationType.Floating)
        compose.setContent {
            MaFeiTheme {
                Box(Modifier.size(390.dp, 700.dp)) {
                    BottomNavigationSettingsScreen({}, bottomNavigationType = navigation.value,
                        onSelectBottomNavigation = { navigation.value = it })
                }
            }
        }
        compose.onNodeWithContentDescription("悬浮").assertIsSelected()
        compose.onNodeWithText("液态玻璃").assertDoesNotExist()
        compose.onNodeWithText("高斯模糊").assertDoesNotExist()
        compose.onNodeWithContentDescription("底栏").performClick()
        assertEquals(BottomNavigationType.Bar, navigation.value)
        compose.onNodeWithContentDescription("底栏").assertIsSelected()
        compose.onNodeWithContentDescription("悬浮").performClick()
        assertEquals(BottomNavigationType.Floating, navigation.value)
    }
}
