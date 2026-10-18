package cn.mafei.cmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule

class ApplicationNavDisplayTest {
    @get:Rule val rule = createComposeRule()
    private var navigation by mutableStateOf(BrowseNavigationState())
    private var session by mutableStateOf(0)
    private val roots = listOf(BrowseHomeRoute, BrowseLibraryIndexRoute, BrowseFavoriteRoute, BrowseChasingRoute)
    private val composed = mutableSetOf<BrowseRoute>()
    private val creations = mutableMapOf<BrowseRoute, Int>()
    private val tokens = mutableMapOf<BrowseRoute, Any>()
    private val lifecycles = mutableMapOf<BrowseRoute, Lifecycle>()
    private var selectHomeFeed: (String) -> Unit = {}

    @Test
    fun fourVisitedRootsKeepTheirCompositionOnWarmReturns() {
        showNavigation()
        roots.drop(1).forEach(::selectRoot)
        lateinit var originals: Map<BrowseRoute, Any>
        rule.runOnIdle {
            assertEquals(roots.toSet(), composed)
            assertEquals(4, navigation.retainedRoots.size)
            originals = tokens.toMap()
        }
        repeat(2) {
            roots.reversed().forEach { root ->
                selectRoot(root)
                rule.onNodeWithTag(pageTag(root)).assertIsDisplayed()
                rule.runOnIdle {
                    assertEquals(roots.toSet(), composed)
                    roots.forEach {
                        assertEquals(1, creations.getValue(it), "Warm return recreated $it")
                        assertSame(originals.getValue(it), tokens.getValue(it))
                    }
                }
            }
        }
    }

    @Test
    fun eachTabKeepsItsDetailStackAndRememberedDetailOnReturn() {
        showNavigation()
        val details = roots.associateWith { BrowseDetailRoute("film-$it") }
        roots.forEach { root ->
            selectRoot(root)
            rule.runOnIdle { navigation.browseBackStack += details.getValue(root) }
            settle()
        }
        lateinit var detailTokens: Map<BrowseRoute, Any>
        rule.runOnIdle {
            assertEquals<Set<BrowseRoute>>(details.values.toSet(), composed)
            detailTokens = details.values.associateWith { tokens.getValue(it) }
        }
        roots.forEach { root ->
            selectRoot(root)
            val detail = details.getValue(root)
            rule.onNodeWithTag(pageTag(detail)).assertIsDisplayed()
            rule.runOnIdle {
                assertEquals(detail, navigation.currentRoute)
                assertEquals(listOf(root, detail), navigation.backStackFor(root))
                assertSame(detailTokens.getValue(detail), tokens.getValue(detail))
                assertEquals(1, creations.getValue(detail))
            }
        }
    }

    @Test
    fun detailPushAndPopStillAnimateInsideTheSameRoot() {
        showNavigation()
        selectRoot(BrowseFavoriteRoute)
        selectRoot(BrowseHomeRoute)
        val detail = BrowseDetailRoute("film")
        rule.runOnIdle { navigation.browseBackStack += detail }
        rule.mainClock.advanceTimeBy(80)
        rule.runOnIdle { assertTrue(composed.containsAll(listOf(BrowseHomeRoute, detail))) }
        settle()
        rule.runOnIdle { assertEquals(setOf(BrowseFavoriteRoute, detail), composed) }
        rule.runOnIdle { navigation.browseBackStack.removeAt(navigation.browseBackStack.lastIndex) }
        rule.mainClock.advanceTimeBy(80)
        rule.runOnIdle { assertTrue(composed.containsAll(listOf(BrowseHomeRoute, detail))) }
        settle()
        rule.runOnIdle {
            assertEquals(setOf<BrowseRoute>(BrowseHomeRoute, BrowseFavoriteRoute), composed)
            assertEquals(listOf<BrowseRoute>(BrowseFavoriteRoute), navigation.backStackFor(BrowseFavoriteRoute))
        }
    }

    @Test
    fun returningHomeKeepsItsFeedAndNewSessionDiscardsAllRetainedPages() {
        showNavigation()
        rule.runOnIdle { selectHomeFeed("library-films") }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("library-films").assertExists()
        lateinit var originalHome: Any
        rule.runOnIdle { originalHome = tokens.getValue(BrowseHomeRoute) }
        selectRoot(BrowseFavoriteRoute)
        rule.runOnIdle { navigation.browseBackStack += BrowseDetailRoute("favorite-film") }
        settle()
        selectRoot(BrowseHomeRoute)
        rule.onNodeWithText("library-films").assertIsDisplayed()
        rule.runOnIdle { assertSame(originalHome, tokens.getValue(BrowseHomeRoute)) }
        lateinit var oldLifecycles: List<Lifecycle>
        rule.runOnIdle {
            oldLifecycles = composed.map { lifecycles.getValue(it) }
            navigation = BrowseNavigationState()
            session++
        }
        settle()
        rule.onNodeWithText("recent").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(setOf<BrowseRoute>(BrowseHomeRoute), composed)
            assertEquals(listOf<BrowseRoute>(BrowseHomeRoute), navigation.retainedRoots)
            assertNotSame(originalHome, tokens.getValue(BrowseHomeRoute))
            oldLifecycles.forEach { assertEquals(Lifecycle.State.DESTROYED, it.currentState) }
        }
    }

    @Test
    fun hiddenRootHasAtMostCreatedLifecycleAndNoMergedContentSemantics() {
        showNavigation()
        selectRoot(BrowseFavoriteRoute)
        rule.onNodeWithTag(pageTag(BrowseHomeRoute)).assertDoesNotExist()
        rule.onNodeWithTag(pageTag(BrowseHomeRoute), useUnmergedTree = true)
            .assertExists().assertIsNotDisplayed()
        rule.onNodeWithTag(pageTag(BrowseFavoriteRoute)).assertIsDisplayed()
        rule.runOnIdle {
            assertTrue(lifecycles.getValue(BrowseHomeRoute).currentState <= Lifecycle.State.CREATED)
            assertEquals(Lifecycle.State.RESUMED, lifecycles.getValue(BrowseFavoriteRoute).currentState)
        }
        selectRoot(BrowseHomeRoute)
        rule.onNodeWithTag(pageTag(BrowseHomeRoute)).assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(Lifecycle.State.RESUMED, lifecycles.getValue(BrowseHomeRoute).currentState)
            assertTrue(lifecycles.getValue(BrowseFavoriteRoute).currentState <= Lifecycle.State.CREATED)
        }
    }

    @Test
    fun leastRecentlyUsedRootIsEvictedAndTemporaryLibraryIsRemovedOnExit() {
        showNavigation()
        selectRoot(BrowseLibraryIndexRoute)
        val oldDetail = BrowseDetailRoute("evicted-library-film")
        rule.runOnIdle { navigation.browseBackStack += oldDetail }
        settle()
        selectRoot(BrowseFavoriteRoute)
        selectRoot(BrowseChasingRoute)
        selectRoot(BrowseHomeRoute) // Refresh Home, making LibraryIndex the oldest.
        lateinit var evictedLifecycle: Lifecycle
        lateinit var homeToken: Any
        rule.runOnIdle {
            evictedLifecycle = lifecycles.getValue(oldDetail)
            homeToken = tokens.getValue(BrowseHomeRoute)
        }
        val temporary = BrowseLibraryRoute("films")
        selectRoot(temporary)
        rule.runOnIdle {
            assertEquals(4, navigation.retainedRoots.size)
            assertEquals(listOf(BrowseFavoriteRoute, BrowseChasingRoute, BrowseHomeRoute, temporary), navigation.retainedRoots)
            assertFalse(oldDetail in composed)
            assertEquals(Lifecycle.State.DESTROYED, evictedLifecycle.currentState)
        }
        selectRoot(BrowseHomeRoute)
        rule.runOnIdle {
            assertFalse(temporary in navigation.retainedRoots)
            assertFalse(temporary in composed)
            assertEquals(3, navigation.retainedRoots.size)
            assertSame(homeToken, tokens.getValue(BrowseHomeRoute))
        }
        selectRoot(BrowseLibraryIndexRoute)
        rule.runOnIdle {
            assertEquals(4, navigation.retainedRoots.size)
            assertEquals(listOf<BrowseRoute>(BrowseLibraryIndexRoute), navigation.browseBackStack)
            assertEquals(2, creations.getValue(BrowseLibraryIndexRoute))
        }
    }

    private fun selectRoot(root: BrowseRoute) {
        rule.runOnIdle { navigation.selectRoot(root) }
        rule.mainClock.advanceTimeBy(48)
    }

    private fun settle() = rule.mainClock.advanceTimeBy(500)
    private fun pageTag(route: BrowseRoute) = "page-$route"

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
                                navigation = navigation,
                                onBack = { navigation.browseBackStack.removeAt(navigation.browseBackStack.lastIndex) },
                                entryProvider = { route ->
                                    NavEntry(route) {
                                        val token = remember { Any() }
                                        val lifecycle = LocalLifecycleOwner.current.lifecycle
                                        DisposableEffect(route, token) {
                                            composed += route
                                            tokens[route] = token
                                            lifecycles[route] = lifecycle
                                            creations[route] = creations.getOrElse(route) { 0 } + 1
                                            onDispose {
                                                if (tokens[route] === token) {
                                                    composed -= route
                                                    tokens.remove(route)
                                                }
                                            }
                                        }
                                        if (route == BrowseHomeRoute) {
                                            var feed by rememberSaveable { mutableStateOf("recent") }
                                            selectHomeFeed = { feed = it }
                                            BasicText(feed, Modifier.testTag(pageTag(route)))
                                        } else {
                                            BasicText(route.toString(), Modifier.testTag(pageTag(route)))
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
