package cn.mafei.cmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule

class ApplicationNavDisplayTest {
    @get:Rule val rule = createComposeRule()
    private var stack by mutableStateOf<List<BrowseRoute>>(listOf(BrowseHomeRoute))
    private var session by mutableStateOf(0)
    private val composed = mutableSetOf<BrowseRoute>()
    private var selectHomeFeed: (String) -> Unit = {}

    @Test
    fun switchingRootsDisposesOutgoingPageWithoutWaitingForATransition() {
        showNavigation()
        for (root in listOf(BrowseFavoriteRoute, BrowseLibraryIndexRoute, BrowseHomeRoute)) {
            rule.runOnIdle { stack = listOf(root) }
            rule.mainClock.advanceTimeBy(48)
            rule.runOnIdle {
                assertEquals(setOf(root), composed, "Tab changes must not retain an outgoing animated page")
            }
        }
    }

    @Test
    fun returningHomeRestoresItsFeedAndNewSessionStartsFresh() {
        showNavigation()
        rule.runOnIdle { selectHomeFeed("library-films") }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("library-films").assertExists()

        rule.runOnIdle { stack = listOf(BrowseFavoriteRoute) }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { stack = listOf(BrowseHomeRoute) }
        rule.mainClock.advanceTimeBy(48)
        rule.onNodeWithText("library-films").assertExists()

        rule.runOnIdle { session++ }
        rule.mainClock.advanceTimeBy(48)
        rule.onNodeWithText("recent").assertExists()
    }

    @Test
    fun detailPushAndPopStillAnimateInsideTheSameRoot() {
        showNavigation()
        val detail = BrowseDetailRoute("film")
        rule.runOnIdle { stack = listOf(BrowseHomeRoute, detail) }
        rule.mainClock.advanceTimeBy(80)
        rule.runOnIdle { assertTrue(composed.containsAll(listOf(BrowseHomeRoute, detail))) }
        rule.mainClock.advanceTimeBy(500)
        rule.runOnIdle { assertEquals(setOf<BrowseRoute>(detail), composed) }

        rule.runOnIdle { stack = listOf(BrowseHomeRoute) }
        rule.mainClock.advanceTimeBy(80)
        rule.runOnIdle { assertTrue(composed.containsAll(listOf(BrowseHomeRoute, detail))) }
        rule.mainClock.advanceTimeBy(500)
        rule.runOnIdle { assertEquals(setOf<BrowseRoute>(BrowseHomeRoute), composed) }
    }

    private fun showNavigation() {
        rule.mainClock.autoAdvance = false
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                DesktopNavigationOwner {
                    key(session) {
                        Box(Modifier.size(400.dp, 600.dp)) {
                            ApplicationNavDisplay(
                                backStack = stack,
                                onBack = { stack = stack.dropLast(1) },
                                entryProvider = { route ->
                                    NavEntry(route) {
                                        DisposableEffect(route) {
                                            composed += route
                                            onDispose { composed -= route }
                                        }
                                        if (route == BrowseHomeRoute) {
                                            var feed by rememberSaveable { mutableStateOf("recent") }
                                            selectHomeFeed = { feed = it }
                                            BasicText(feed)
                                        } else {
                                            BasicText(route.toString())
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(48)
    }
}
