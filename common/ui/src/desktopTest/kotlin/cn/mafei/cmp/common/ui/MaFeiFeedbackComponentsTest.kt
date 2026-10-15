package cn.mafei.cmp.common.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals

class MaFeiFeedbackComponentsTest {
    @get:org.junit.Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStylesExposeOneProductOwnedLoadingSemantics() {
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    MaFeiLoadingIndicator(style = MaFeiLoadingStyle.Page)
                    MaFeiLoadingIndicator(style = MaFeiLoadingStyle.Inline)
                    MaFeiLoadingIndicator(style = MaFeiLoadingStyle.Button)
                }
            }
        }

        composeRule.onAllNodesWithContentDescription("正在加载").assertCountEquals(3)
    }

    @Test
    fun noticeActionDispatchesWithoutMaterialButton() {
        var clicks by mutableStateOf(0)
        composeRule.setContent {
            MaterialTheme {
                MaFeiInlineNotice(
                    message = "服务器暂时不可用",
                    actionLabel = "重试",
                    onAction = { clicks++ },
                )
            }
        }

        composeRule.onNodeWithText("服务器暂时不可用").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }
}
