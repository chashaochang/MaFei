package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.design_system.mafeiLayout
import kotlin.test.*

class MaFeiModalMotionTest {
    @get:org.junit.Rule val rule = createComposeRule()
    private var modal by mutableStateOf<String?>(null)
    private var host: MaFeiWindowModalHostState? = null

    private fun show(wide: Boolean = false, portal: Boolean = false) {
        host = if (portal) MaFeiWindowModalHostState() else null
        val width = if (wide) 900 else 360
        val height = if (wide) 600 else 780
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val chrome = LocalMaFeiNavigationChrome.current.copy(effectiveLayout = mafeiLayout(width.toFloat(), height.toFloat()))
            CompositionLocalProvider(LocalMaFeiNavigationChrome provides chrome, LocalMaFeiWindowModalHost provides host) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    val backdrop = rememberGlassBackdrop()
                    MaFeiAnimatedModalOverlay(modal, backdrop, onDismiss = { modal = null }, useWindowHost = portal) { title, _, motion ->
                        Box(Modifier.align(if (wide) Alignment.Center else Alignment.BottomCenter).fillMaxWidth().height(280.dp)
                            .then(motion).background(Color.DarkGray).testTag("modal-panel")) {
                            Text(title)
                        }
                    }
                    host?.entry?.let { entry -> entry.content(this, backdrop) }
                }
            }
        }
        rule.runOnIdle { modal = "简介" }
        rule.mainClock.advanceTimeBy(80)
        rule.waitForIdle()
    }

    @Test fun phoneSheetMovesUpInsteadOfAppearingAtItsFinalPosition() {
        show()
        val entering = rule.onNodeWithTag("modal-panel").fetchSemanticsNode().boundsInRoot.top
        rule.mainClock.advanceTimeBy(320)
        rule.waitForIdle()
        val settled = rule.onNodeWithTag("modal-panel").fetchSemanticsNode().boundsInRoot.top
        assertTrue(entering > settled, "The sheet must slide from below its final position")
    }

    @Test fun closingRetainsContentUntilExitCompletes() {
        show()
        rule.mainClock.advanceTimeBy(320)
        rule.runOnIdle { modal = null }
        rule.mainClock.advanceTimeBy(48)
        rule.onNodeWithText("简介").assertExists()
        rule.mainClock.advanceTimeBy(320)
        rule.onNodeWithTag("modal-panel").assertDoesNotExist()
    }

    @Test fun reopeningDuringExitKeepsTheNewModalContent() {
        show()
        rule.mainClock.advanceTimeBy(320)
        rule.runOnIdle { modal = null }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { modal = "字幕" }
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithText("字幕").assertExists()
        rule.onNodeWithText("简介").assertDoesNotExist()
    }

    @Test fun wideModalRemainsCenteredWhileScalingIn() {
        show(wide = true)
        val entering = rule.onNodeWithTag("modal-panel").fetchSemanticsNode().boundsInRoot
        rule.mainClock.advanceTimeBy(320)
        val settled = rule.onNodeWithTag("modal-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(entering.width < settled.width)
        assertEquals(settled.center.y, entering.center.y, .5f)
    }

    @Test fun windowPortalSurvivesUntilTheExitAnimationCompletes() {
        show(wide = true, portal = true)
        rule.mainClock.advanceTimeBy(320)
        rule.runOnIdle { modal = null }
        rule.mainClock.advanceTimeBy(48)
        rule.onNodeWithText("简介").assertExists()
        rule.runOnIdle { assertNotNull(host?.entry) }
        rule.mainClock.advanceTimeBy(320)
        rule.onNodeWithTag("modal-panel").assertDoesNotExist()
        rule.runOnIdle { assertNull(host?.entry) }
    }
}
