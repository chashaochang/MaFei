package cn.mafei.cmp.common.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule

class MaFeiRetainedContentTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun hiddenStateChangesKeepCompositionWithoutMeasuringPlacingOrDrawingIt() {
        var active by mutableStateOf(true)
        var present by mutableStateOf(true)
        var revision by mutableStateOf(0)
        val extent = mutableStateOf(48.dp)
        val color = mutableStateOf(Color.Red)
        var created = 0
        var disposed = 0
        var measures = 0
        var placements = 0
        var draws = 0
        var observedRevision = -1
        lateinit var remembered: Any
        rule.setContent {
            Box(Modifier.size(200.dp).testTag("stage")) {
                if (present) {
                    MaFeiRetainedContent(active) {
                        val identity = remember { created++; Any() }
                        val currentRevision = revision
                        SideEffect { remembered = identity; observedRevision = currentRevision }
                        DisposableEffect(Unit) { onDispose { disposed++ } }
                        Layout(
                            content = {},
                            modifier = Modifier.testTag("retained").drawBehind {
                                draws++
                                drawRect(color.value)
                            },
                        ) { _, constraints ->
                            measures++
                            val size = extent.value.roundToPx()
                            layout(constraints.constrainWidth(size), constraints.constrainHeight(size)) {
                                placements++
                            }
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("stage").captureToImage()
        lateinit var original: Any
        rule.runOnIdle {
            original = remembered
            assertEquals(1, created)
            assertTrue(measures > 0 && placements > 0 && draws > 0)
            active = false
        }
        rule.waitForIdle()
        rule.onNodeWithTag("retained").assertDoesNotExist()
        rule.onNodeWithTag("retained", useUnmergedTree = true).assertExists().assertIsNotDisplayed()
        var hiddenCounts = Triple(0, 0, 0)
        rule.runOnIdle {
            hiddenCounts = Triple(measures, placements, draws)
            revision++
            extent.value = 72.dp // Invalidates the previously observed measure block.
            color.value = Color.Green // Invalidates the previously observed draw block.
        }
        rule.waitForIdle()
        rule.onNodeWithTag("stage").captureToImage()
        rule.runOnIdle {
            assertEquals(1, observedRevision, "Hidden composition must still receive new state")
            assertSame(original, remembered)
            assertEquals(1, created)
            assertEquals(0, disposed)
            assertEquals(hiddenCounts, Triple(measures, placements, draws))
            active = true
        }
        rule.onNodeWithTag("retained").assertIsDisplayed().assertWidthIsEqualTo(72.dp)
        rule.onNodeWithTag("stage").captureToImage()
        rule.runOnIdle {
            assertSame(original, remembered)
            assertTrue(measures > hiddenCounts.first)
            assertTrue(placements > hiddenCounts.second)
            assertTrue(draws > hiddenCounts.third)
            present = false
        }
        rule.runOnIdle { assertEquals(1, disposed) }
    }

    @Test
    fun nestedActivityRequiresBothParentAndChildToBeActive() {
        var parentActive by mutableStateOf(false)
        var childActive by mutableStateOf(true)
        var observedActive = true
        rule.setContent {
            MaFeiRetainedContent(parentActive) {
                MaFeiRetainedContent(childActive) {
                    val active = LocalMaFeiPageActive.current
                    SideEffect { observedActive = active }
                    BasicText("Nested", Modifier.testTag("nested"))
                }
            }
        }
        for ((parent, child) in listOf(false to true, false to false, true to false, true to true)) {
            rule.runOnIdle { parentActive = parent; childActive = child }
            rule.runOnIdle { assertEquals(parent && child, observedActive) }
            if (parent && child) rule.onNodeWithTag("nested").assertIsDisplayed()
            else rule.onNodeWithTag("nested").assertDoesNotExist()
        }
    }

    @Test
    fun hiddenGroupReleasesCapturedFocusAndRejectsProgrammaticFocusRequests() {
        var active by mutableStateOf(true)
        val first = FocusRequester()
        val second = FocusRequester()
        val outside = FocusRequester()
        var firstFocused = false
        var secondFocused = false
        var outsideFocused = false
        rule.setContent {
            Column {
                MaFeiRetainedContent(active) {
                    Column {
                        BasicText("First", Modifier.focusRequester(first)
                            .onFocusChanged { firstFocused = it.isFocused }.focusable())
                        BasicText("Second", Modifier.focusRequester(second)
                            .onFocusChanged { secondFocused = it.isFocused }.focusable())
                    }
                }
                BasicText("Outside", Modifier.focusRequester(outside)
                    .onFocusChanged { outsideFocused = it.isFocused }.focusable())
            }
        }
        rule.runOnIdle {
            assertTrue(first.requestFocus())
            assertTrue(first.captureFocus())
        }
        rule.runOnIdle {
            assertTrue(firstFocused)
            active = false
        }
        rule.runOnIdle {
            assertFalse(firstFocused)
            assertFalse(secondFocused)
            assertTrue(outside.requestFocus())
            assertFalse(first.requestFocus())
            assertFalse(second.requestFocus())
            assertTrue(outsideFocused)
            assertFalse(firstFocused)
            assertFalse(secondFocused)
            active = true
        }
        rule.runOnIdle { assertTrue(second.requestFocus()) }
        rule.runOnIdle {
            assertTrue(secondFocused)
            assertFalse(outsideFocused)
        }
    }
}
