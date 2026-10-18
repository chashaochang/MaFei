package cn.mafei.cmp.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.BottomNavigationType
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.glass.GlassStyleScope
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule

class BottomNavigationSchedulingTest {
    @get:Rule val rule = createComposeRule()
    private var route by mutableStateOf("home")
    private val navigated = mutableListOf<String>()
    private val refreshed = mutableListOf<String>()
    private val interaction = MaFeiTabInteractionState()

    @Test fun navigationWaitsForTheFirstVisualFrameAndRefreshWaitsForSettling() {
        showTabs()
        val chooseLibrary = clickAction("媒体库")
        rule.runOnIdle { assertTrue(chooseLibrary()) }
        rule.runOnIdle {
            assertEquals("home", route)
            assertTrue(interaction.isBusy)
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals("home", route, "The first visual frame must not rebuild the page") }
        rule.mainClock.advanceTimeBy(64)
        rule.runOnIdle {
            assertEquals(listOf("library"), navigated)
            assertEquals(listOf("home"), refreshed, "Automatic refresh must wait for the droplet")
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle { assertEquals(listOf("home", "library"), refreshed) }
    }

    @Test fun rapidSelectionsOnlyDeliverTheLatestDestination() {
        showTabs()
        val chooseLibrary = clickAction("媒体库")
        val chooseMine = clickAction("我的")
        rule.runOnIdle {
            assertTrue(chooseLibrary())
            assertTrue(chooseMine())
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle {
            assertEquals("mine", route)
            assertEquals(listOf("mine"), navigated)
            assertEquals(listOf("home", "mine"), refreshed)
        }
    }

    @Test fun acknowledgingDeliveredDestinationDoesNotCancelTheNextSelection() {
        lateinit var chooseMine: () -> Boolean
        showTabs { destination ->
            if (destination == "library") {
                // Delivery has happened, but the route's composition/effect has
                // not acknowledged it yet. Queue the next real semantics action.
                assertTrue(chooseMine())
                assertTrue(interaction.isBusy)
            }
        }
        chooseMine = clickAction("我的")
        val chooseLibrary = clickAction("媒体库")
        rule.runOnIdle { assertTrue(chooseLibrary()) }
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle {
            assertEquals("mine", route)
            assertEquals(listOf("library", "mine"), navigated)
            assertEquals(listOf("home", "mine"), refreshed)
            assertFalse(interaction.isBusy)
        }
    }

    @Test fun selectingTheCurrentDestinationStillDeliversAndSettles() {
        showTabs()
        val chooseHome = clickAction("首页")
        rule.runOnIdle { assertTrue(chooseHome()) }
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle {
            assertEquals(listOf("home"), navigated)
            assertFalse(interaction.isBusy)
        }
    }

    @Test fun aBusyPulseBetweenFramesRequiresAnotherStableIdleFrame() = runBlocking {
        val frameClock = BroadcastFrameClock()
        var resumed = false
        val waiter = launch(frameClock, start = CoroutineStart.UNDISPATCHED) {
            interaction.awaitIdle()
            resumed = true
        }
        try {
            assertTrue(frameClock.hasAwaiters)
            interaction.animationRunning(true)
            interaction.animationRunning(false)
            frameClock.sendFrame(1L)
            yield()
            assertFalse(resumed)
            assertTrue(frameClock.hasAwaiters)
            frameClock.sendFrame(2L)
            waiter.join()
            assertTrue(resumed)
        } finally {
            waiter.cancelAndJoin()
        }
    }

    private fun clickAction(label: String): () -> Boolean = requireNotNull(
        rule.onNodeWithText(label).fetchSemanticsNode().config[SemanticsActions.OnClick].action
    )

    private fun showTabs(afterNavigation: (String) -> Unit = {}) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaFeiTheme {
                GlassStyleScope(GlassStyle.Translucent) {
                    CompositionLocalProvider(LocalMaFeiTabInteractionState provides interaction) {
                        LaunchedEffect(route) {
                            interaction.awaitIdle()
                            refreshed += route
                        }
                        Box(Modifier.size(390.dp, 200.dp)) {
                            BottomNavigationLayer(
                                destinations = listOf(
                                    MaFeiNavItem("home", "首页", MaFeiGlyph.Home),
                                    MaFeiNavItem("library", "媒体库", MaFeiGlyph.Library),
                                    MaFeiNavItem("mine", "我的", MaFeiGlyph.Account),
                                ),
                                selectedDestination = route,
                                onDestination = {
                                    route = it
                                    navigated += it
                                    afterNavigation(it)
                                },
                                backdrop = rememberGlassBackdrop(), compactProgress = { 0f },
                                type = BottomNavigationType.Floating, bottomInset = 0.dp,
                            )
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(48)
    }
}
