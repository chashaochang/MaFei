package cn.mafei.cmp.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.ShortDramaSynopsis
import org.junit.Rule
import kotlin.test.Test

class ShortDramaSynopsisTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun showsExpansionOnlyForOverflowAndCanCollapseAgain() {
        val synopsis = mutableStateOf("A short synopsis.")
        rule.setContent {
            Box(Modifier.width(300.dp)) {
                ShortDramaSynopsis(synopsis.value, TextStyle(fontSize = 13.sp, lineHeight = 19.sp), 140.dp)
            }
        }
        rule.onNodeWithText(synopsis.value).assertIsDisplayed()
        rule.onNodeWithText("展开").assertDoesNotExist()

        rule.runOnIdle { synopsis.value = "A long synopsis to exercise the two-line preview. ".repeat(12) }
        val expand = rule.onNodeWithText("展开")
        expand.fetchSemanticsNode().boundsInRoot
        expand.performClick()
        expand.assertDoesNotExist()
        val collapse = rule.onNodeWithText("收起")
        collapse.fetchSemanticsNode().boundsInRoot
        collapse.performClick()
        expand.assertIsDisplayed()
    }
}
