package cn.mafei.cmp.common.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import kotlin.test.Test
import kotlin.test.assertEquals

/** Regression coverage for the blank area around the sort panel. */
class LegacySortSheetTest {
    @get:org.junit.Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingOutsideSortPanelDismissesIt() {
        var visible = true
        composeRule.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (visible) {
                        LegacySortSheet(
                            choices = listOf(LegacySortChoice("默认", "default")),
                            selected = "default",
                            onSelected = {},
                            onDismiss = { visible = false },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("排序").assertExists()
        composeRule.onNodeWithTag("legacySortScrim")
            .performTouchInput { click(Offset(8f, 8f)) }
        composeRule.waitUntil(2_000) { !visible }
        assertEquals(false, visible)
    }
}
