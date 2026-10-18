package cn.mafei.cmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
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
import org.junit.Rule

class BrowsePageScrollStatesTest {
    @get:Rule val rule = createComposeRule()
    private val navigation = BrowseNavigationState()
    private val states = BrowsePageScrollStates()
    private val displayedGrids = mutableMapOf<BrowseRoute, LazyGridState>()

    @Test
    fun sameFavoriteRouteInTwoRootsKeepsIndependentAnchorsOnWarmReturns() {
        showBothFavoriteEntries()
        val dedicated = gridFor(BrowseFavoriteRoute)
        val nested = gridFor(BrowseLibraryIndexRoute)
        assertNotSame(dedicated, nested)

        repeat(2) {
            selectRoot(BrowseFavoriteRoute)
            assertGrid(BrowseFavoriteRoute, dedicated, 18)
            selectRoot(BrowseLibraryIndexRoute)
            assertGrid(BrowseLibraryIndexRoute, nested, 42)
        }
    }

    @Test
    fun poppingNestedFavoriteReleasesOnlyItsAnchor() {
        showBothFavoriteEntries()
        val dedicated = gridFor(BrowseFavoriteRoute)
        val oldNested = gridFor(BrowseLibraryIndexRoute)

        rule.runOnIdle { navigation.browseBackStack.removeLastOrNull() }
        settle()
        rule.runOnIdle { assertEquals(BrowseLibraryIndexRoute, navigation.currentRoute) }
        selectRoot(BrowseFavoriteRoute)
        assertGrid(BrowseFavoriteRoute, dedicated, 18)

        selectRoot(BrowseLibraryIndexRoute)
        pushNestedFavorite()
        val replacement = gridFor(BrowseLibraryIndexRoute)
        assertNotSame(oldNested, replacement)
        assertGrid(BrowseLibraryIndexRoute, replacement, 0)
        selectRoot(BrowseFavoriteRoute)
        assertGrid(BrowseFavoriteRoute, dedicated, 18)
    }

    @Test
    fun evictingDedicatedFavoriteDoesNotReleaseTheOtherRootsFavoriteAnchor() {
        showBothFavoriteEntries()
        val oldDedicated = gridFor(BrowseFavoriteRoute)
        val nested = gridFor(BrowseLibraryIndexRoute)

        // Keep LibraryIndex newer than Favorite, then fill the four-root cache.
        selectRoot(BrowseHomeRoute)
        selectRoot(BrowseChasingRoute)
        selectRoot(BrowseLibraryRoute("temporary-library"))
        rule.runOnIdle {
            assertEquals(MaxRetainedBrowseRoots, navigation.retainedRoots.size)
            assertFalse(BrowseFavoriteRoute in navigation.retainedRoots)
            assertEquals(listOf(BrowseLibraryIndexRoute, BrowseFavoriteRoute), navigation.backStackFor(BrowseLibraryIndexRoute))
        }
        selectRoot(BrowseLibraryIndexRoute)
        assertGrid(BrowseLibraryIndexRoute, nested, 42)

        selectRoot(BrowseFavoriteRoute)
        val replacement = gridFor(BrowseFavoriteRoute)
        assertNotSame(oldDedicated, replacement)
        assertGrid(BrowseFavoriteRoute, replacement, 0)
        selectRoot(BrowseLibraryIndexRoute)
        assertGrid(BrowseLibraryIndexRoute, nested, 42)
    }

    private fun showBothFavoriteEntries() {
        showNavigation()
        selectRoot(BrowseFavoriteRoute)
        scrollFavoriteTo(BrowseFavoriteRoute, 18)
        selectRoot(BrowseLibraryIndexRoute)
        pushNestedFavorite()
        scrollFavoriteTo(BrowseLibraryIndexRoute, 42)
    }

    private fun gridFor(root: BrowseRoute): LazyGridState = rule.runOnIdle { displayedGrids.getValue(root) }

    private fun assertGrid(root: BrowseRoute, expected: LazyGridState, index: Int) {
        rule.onNodeWithTag(gridTag(root)).assertIsDisplayed()
        rule.onNodeWithTag(itemTag(root, index)).assertIsDisplayed()
        rule.runOnIdle {
            assertSame(expected, displayedGrids.getValue(root))
            assertEquals(index to 0, expected.firstVisibleItemIndex to expected.firstVisibleItemScrollOffset)
        }
    }

    private fun scrollFavoriteTo(root: BrowseRoute, index: Int) {
        rule.onNodeWithTag(gridTag(root)).performScrollToIndex(index)
        settle()
        assertGrid(root, gridFor(root), index)
    }

    private fun selectRoot(root: BrowseRoute) {
        rule.runOnIdle { navigation.selectRoot(root) }
        settle()
    }

    private fun pushNestedFavorite() {
        rule.runOnIdle { navigation.browseBackStack += BrowseFavoriteRoute }
        settle()
    }

    private fun settle() {
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
    }

    private fun gridTag(root: BrowseRoute) = "favorite-grid-$root"
    private fun itemTag(root: BrowseRoute, index: Int) = "favorite-item-$root-$index"

    private fun showNavigation() {
        rule.mainClock.autoAdvance = false
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                DesktopNavigationOwner {
                    PruneBrowsePageScrollStates(navigation, states)
                    Box(Modifier.size(400.dp, 360.dp)) {
                        ApplicationNavDisplay(
                            navigation = navigation,
                            onBack = { navigation.browseBackStack.removeLastOrNull() },
                            entryProvider = { route ->
                                NavEntry(route) {
                                    if (route == BrowseFavoriteRoute) {
                                        val root = LocalBrowseRoot.current
                                        val grid = states.grid(root, route)
                                        SideEffect { displayedGrids[root] = grid }
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(1),
                                            state = grid,
                                            modifier = Modifier.fillMaxSize().testTag(gridTag(root)),
                                        ) {
                                            items(80, key = { it }) { index ->
                                                BasicText(
                                                    "Favorite $index",
                                                    Modifier.fillMaxWidth().height(72.dp).testTag(itemTag(root, index)),
                                                )
                                            }
                                        }
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
        settle()
    }
}
