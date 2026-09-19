package cn.mafei.cmp.common.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals

/** The confirmation owns dismissal, so only the pressed control may fire. */
class MaFeiGlassConfirmDialogTest {
    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private var confirmed = 0
    private var dismissed = 0

    private fun showDialog() {
        composeRule.setContent {
            MaterialTheme {
                val backdrop = rememberGlassBackdrop()
                Surface(Modifier.fillMaxSize()) { Text("scene") }
                MaFeiGlassConfirmDialog(
                    title = "删除账号",
                    content = "确定要删除「xiaobai」这个账号吗？",
                    confirmText = "删除",
                    dismissText = "取消",
                    destructive = true,
                    backdrop = backdrop,
                    onConfirm = { confirmed++ },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    @Test
    fun showsTheRequestAndDispatchesOnlyThePressedAction() {
        showDialog()
        composeRule.onNodeWithText("删除账号").assertIsDisplayed()
        composeRule.onNodeWithText("确定要删除「xiaobai」这个账号吗？").assertIsDisplayed()

        // The panel swallows its own taps: pressing inside never dismisses.
        composeRule.onNodeWithTag("mafeiGlassConfirmCard").performClick()
        assertEquals(0, dismissed)
        assertEquals(0, confirmed)

        composeRule.onNodeWithText("取消").performClick()
        assertEquals(1, dismissed)
        assertEquals(0, confirmed)

        composeRule.onNodeWithText("删除").performClick()
        assertEquals(1, confirmed)
        assertEquals(1, dismissed)
    }

    @Test
    fun tappingOutsideThePanelDismissesWithoutConfirming() {
        showDialog()
        // A corner of the scrim, never the centred panel.
        composeRule.onNodeWithTag("mafeiGlassConfirmScrim")
            .performTouchInput { click(Offset(8f, 8f)) }
        assertEquals(1, dismissed)
        assertEquals(0, confirmed)
    }
}
