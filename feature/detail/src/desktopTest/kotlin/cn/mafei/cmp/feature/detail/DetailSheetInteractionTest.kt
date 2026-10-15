package cn.mafei.cmp.feature.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiAnimatedModalOverlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetailSheetInteractionTest {
    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private var visible by mutableStateOf(true)

    @Test
    fun moreLinkIsASeparateAccessibleActionWithoutMakingTheBodyClickable() {
        var opened = 0
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(200.dp)) {
                    OverviewSection("用于核对简介链接的长文字。".repeat(40), onOpen = { opened++ })
                }
            }
        }
        composeRule.onNodeWithText("用于核对简介链接", substring = true)
            .performTouchInput { click(Offset(2f, 2f)) }
        assertEquals(0, opened)
        composeRule.onNode(hasClickAction(), useUnmergedTree = true).performClick()
        assertEquals(1, opened)
    }

    private fun showOverview(overview: String = "用于验证弹窗内容区下拉关闭的简介内容。") {
        visible = true
        composeRule.setContent {
            MaterialTheme {
                val backdrop = rememberGlassBackdrop()
                Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop))
                Box(Modifier.fillMaxSize()) {
                    MaFeiAnimatedModalOverlay(value = overview.takeIf { visible }, backdrop = backdrop,
                        onDismiss = { visible = false }) { text, source, motion ->
                        OverviewSheetOverlay(
                            overview = text,
                            backdrop = source,
                            onDismiss = { visible = false },
                            surfaceMotion = motion,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun draggingDownAtBottomScrollsContentInsteadOfSheet() {
        showOverview((1..100).joinToString("\n") { "第 $it 行简介内容" })
        val body = composeRule.onNode(hasScrollAction())
        body.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        composeRule.waitForIdle()
        val scrollBefore = body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val headerBefore = composeRule.onNodeWithText("简介").fetchSemanticsNode().boundsInRoot.top

        body.performTouchInput {
            down(center)
            // Start below touch slop, just like a finger beginning a downward drag.
            repeat(4) { moveBy(Offset(0f, 1f), delayMillis = 16) }
            repeat(12) { moveBy(Offset(0f, 8f), delayMillis = 16) }
            advanceEventTime(100)
            up()
        }
        composeRule.waitForIdle()

        assertTrue(visible, "Scrolling back through the body must not dismiss the sheet")
        val scrollAfter = body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(scrollAfter < scrollBefore, "The body should scroll toward its beginning")
        assertEquals(headerBefore, composeRule.onNodeWithText("简介").fetchSemanticsNode().boundsInRoot.top)
    }

    @Test
    fun closeButtonDismissesOverviewSheet() {
        showOverview()

        composeRule.onNodeWithContentDescription("关闭简介").performClick()

        composeRule.onAllNodesWithContentDescription("关闭简介").assertCountEquals(0)
    }

    @Test
    fun tappingOutsideOverviewSheetDismissesIt() {
        showOverview()

        composeRule.onNodeWithTag("detailSheetScrim")
            .performTouchInput { click(Offset(8f, 8f)) }
        composeRule.waitUntil(timeoutMillis = 2_000) { !visible }
    }

    @Test
    fun pullingDownContentDismissesOverviewSheet() {
        showOverview()

        composeRule.onNodeWithText("用于验证弹窗内容区下拉关闭的简介内容。")
            .performTouchInput { swipeDown(durationMillis = 400) }
        composeRule.waitUntil(timeoutMillis = 2_000) { !visible }

        composeRule.onAllNodesWithContentDescription("关闭简介").assertCountEquals(0)
    }
}
