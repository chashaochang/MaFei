package cn.mafei.cmp.common.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule

class MaFeiRetainedContentTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun hiddenStateChangesKeepPlacedContentInvisibleAndResumeWithLatestValues() {
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
            Box(Modifier.size(200.dp).background(Color.Blue).testTag("stage")) {
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
        val hiddenImage = rule.onNodeWithTag("stage").captureToImage().toPixelMap()
        assertEquals(Color.Blue, hiddenImage[hiddenImage.width / 2, hiddenImage.height / 2])
        var settledHiddenDraws = 0
        rule.runOnIdle {
            assertEquals(1, observedRevision, "Hidden composition must still receive new state")
            assertSame(original, remembered)
            assertEquals(1, created)
            assertEquals(0, disposed)
            // Kept placement is intentional: not placing would release native layers.
            // New hidden content may update a display list once, but cannot appear
            // in the host or keep drawing when no page state changes.
            assertTrue(measures > hiddenCounts.first)
            assertTrue(placements > hiddenCounts.second)
            settledHiddenDraws = draws
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("stage").captureToImage()
        rule.runOnIdle {
            assertEquals(settledHiddenDraws, draws, "An idle hidden page must not keep recording")
            active = true
        }
        rule.onNodeWithTag("retained").assertIsDisplayed().assertWidthIsEqualTo(72.dp)
        val resumedImage = rule.onNodeWithTag("retained").captureToImage().toPixelMap()
        assertEquals(Color.Green, resumedImage[resumedImage.width / 2, resumedImage.height / 2])
        rule.onNodeWithTag("stage").captureToImage()
        rule.runOnIdle {
            assertSame(original, remembered)
            assertTrue(draws > 0)
            present = false
        }
        rule.runOnIdle { assertEquals(1, disposed) }
    }

    @Test
    fun warmVisibilitySwitchKeepsUnchangedChildDisplayList() {
        var active by mutableStateOf(true)
        var records = 0
        val childModifier = Modifier.size(80.dp).graphicsLayer().drawBehind {
            records++
            drawRect(Color.Red)
        }.testTag("cached-page")
        rule.setContent {
            val content: @Composable () -> Unit = remember { { Box(childModifier) } }
            Box(Modifier.size(200.dp).testTag("stage")) {
                MaFeiRetainedContent(active, content = content)
            }
        }
        rule.onNodeWithTag("stage").captureToImage()
        val initial = rule.runOnIdle { assertTrue(records > 0); records }
        repeat(3) {
            rule.runOnIdle { active = false }
            rule.onNodeWithTag("stage").captureToImage()
            rule.runOnIdle { active = true }
            rule.onNodeWithTag("cached-page").assertIsDisplayed()
            rule.onNodeWithTag("stage").captureToImage()
            rule.runOnIdle {
                assertEquals(initial, records, "A warm switch must not recreate an unchanged child's display list")
            }
        }
    }

    @Test
    fun hiddenTopPageCannotConsumeTouchesOrPolluteTheVisiblePage() {
        var active by mutableStateOf(false)
        var visibleClicks = 0
        var retainedClicks = 0
        rule.setContent {
            Box(Modifier.size(200.dp).testTag("stage")) {
                Box(Modifier.size(200.dp).background(Color.Blue).clickable(interactionSource = null, indication = null) { visibleClicks++ })
                MaFeiRetainedContent(active) {
                    Box(Modifier.size(200.dp).background(Color.Red).clickable(interactionSource = null, indication = null) { retainedClicks++ })
                }
            }
        }
        rule.onNodeWithTag("stage").performTouchInput { click(center) }
        val hiddenImage = rule.onNodeWithTag("stage").captureToImage().toPixelMap()
        assertEquals(Color.Blue, hiddenImage[hiddenImage.width / 2, hiddenImage.height / 2])
        rule.runOnIdle {
            assertEquals(1, visibleClicks)
            assertEquals(0, retainedClicks)
            active = true
        }
        rule.onNodeWithTag("stage").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, visibleClicks); assertEquals(1, retainedClicks) }
    }

    @Test
    fun smallHiddenPageCannotBeTranslatedIntoAnotherVisibleHitRegion() {
        var visibleClicks = 0
        var retainedClicks = 0
        rule.setContent {
            Box(Modifier.size(200.dp).testTag("stage")) {
                Box(Modifier.size(200.dp).clickable(interactionSource = null, indication = null) { visibleClicks++ })
                MaFeiRetainedContent(false) {
                    Box(Modifier.size(48.dp).clickable(interactionSource = null, indication = null) { retainedClicks++ })
                }
            }
        }
        // Translating the complete 48dp clip by twice its own width would put
        // it at x=97..145 inside this parent's center. Keep the clip stationary.
        rule.onNodeWithTag("stage").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, visibleClicks); assertEquals(0, retainedClicks) }
    }

    @Test
    fun hidingDuringAnExistingPressCancelsItsClick() {
        var active by mutableStateOf(true)
        var clicks = 0
        rule.setContent {
            Box(Modifier.size(200.dp).testTag("stage")) {
                MaFeiRetainedContent(active) {
                    Box(Modifier.size(200.dp).clickable(interactionSource = null, indication = null) { clicks++ })
                }
            }
        }
        rule.onNodeWithTag("stage").performTouchInput { down(center) }
        rule.runOnIdle { active = false }
        rule.onNodeWithTag("stage").performTouchInput { up() }
        rule.runOnIdle { assertEquals(0, clicks); active = true }
        rule.onNodeWithTag("stage").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun rtlChildrenKeepTheirOriginalRelativeAlignmentAfterResume() {
        var active by mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaFeiRetainedContent(active) {
                    Box(Modifier.size(40.dp).testTag("small"))
                    Box(Modifier.size(80.dp).testTag("large"))
                }
            }
        }
        repeat(2) {
            val small = rule.onNodeWithTag("small").fetchSemanticsNode().boundsInRoot
            val large = rule.onNodeWithTag("large").fetchSemanticsNode().boundsInRoot
            assertEquals(large.right, small.right)
            rule.runOnIdle { active = false }
            rule.runOnIdle { active = true }
        }
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
