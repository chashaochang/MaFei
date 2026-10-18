package cn.mafei.cmp.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.ui.LocalMaFeiPageActive
import cn.mafei.cmp.core.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HomeActiveLifecycleTest {
    @get:org.junit.Rule val rule = createComposeRule()

    @Test
    fun inactiveHomeCancelsPrefetchAndKeepsBackdropOwnerUntilResumeOrDisposal() {
        val active = mutableStateOf(true)
        val mounted = mutableStateOf(true)
        val feed = mutableStateOf(listOf(movie("first"), movie("second")))
        val backdrop = HomeHeroBackdropState()
        var started = 0
        var cancelled = 0
        val prefetch: suspend (MediaItem) -> Unit = {
            started++
            try { awaitCancellation() } finally { cancelled++ }
        }
        rule.setContent {
            MaFeiTheme {
                CompositionLocalProvider(LocalMaFeiPageActive provides active.value) {
                    if (mounted.value) {
                        fixture(feed.value, showHero = true, backdrop = backdrop, prefetch = prefetch)
                    }
                }
            }
        }
        rule.waitUntil(5_000) { started == 1 }
        val owner = backdrop.pagerState
        val grid = backdrop.gridState
        val publishedItems = backdrop.items
        rule.runOnIdle { active.value = false }
        rule.waitForIdle()
        assertEquals(1, cancelled, "Hiding the retained page must cancel an in-flight image request")
        rule.runOnIdle { feed.value = listOf(movie("refreshed")) }
        rule.waitForIdle()
        assertEquals(1, started, "A hidden feed update must not start prefetch again")
        assertSame(owner, backdrop.pagerState, "A background pager replacement must not detach the published owner")
        assertSame(grid, backdrop.gridState)
        assertEquals(publishedItems, backdrop.items, "Hidden content changes must not repaint the window backdrop")

        rule.runOnIdle { active.value = true }
        rule.waitForIdle()
        assertEquals(feed.value, backdrop.items)
        assertNotSame(owner, backdrop.pagerState, "Reactivation publishes the current pager")
        val resumedOwner = backdrop.pagerState
        rule.runOnIdle { active.value = false; feed.value = listOf(movie("third")) }
        rule.waitForIdle()
        assertSame(resumedOwner, backdrop.pagerState)
        rule.runOnIdle { mounted.value = false }
        rule.waitForIdle()
        assertEquals(emptyList(), backdrop.items, "True disposal releases the last owned background")
        assertEquals(null, backdrop.pagerState)
        assertEquals(null, backdrop.gridState)
    }

    @Test
    fun hiddenLibraryDoesNotPageUntilItBecomesActive() {
        val active = mutableStateOf(false)
        val feed = mutableStateOf(listOf(movie("first")))
        var loads = 0
        rule.setContent {
            MaFeiTheme {
                CompositionLocalProvider(LocalMaFeiPageActive provides active.value) {
                    fixture(feed.value, overview = false, canLoadMore = true, onLoadMore = { loads++ })
                }
            }
        }
        rule.waitForIdle()
        assertEquals(0, loads)
        rule.runOnIdle { active.value = true }
        rule.waitUntil { loads > 0 }
        rule.runOnIdle { active.value = false }
        rule.waitForIdle()
        val visibleLoads = loads
        rule.runOnIdle { feed.value = feed.value + movie("second") }
        rule.waitForIdle()
        assertEquals(visibleLoads, loads, "Background layout/content changes must not consume the media cursor")
    }

    @Test
    fun hidingHomeCancelsScrollWithoutChangingItsSavedAnchorAfterwards() {
        val active = mutableStateOf(true)
        lateinit var grid: LazyGridState
        lateinit var scope: CoroutineScope
        lateinit var scroll: Job
        rule.setContent {
            MaFeiTheme {
                CompositionLocalProvider(LocalMaFeiPageActive provides active.value) {
                    grid = rememberLazyGridState()
                    scope = rememberCoroutineScope()
                    fixture((0..59).map { movie("movie-$it") }, overview = false, grid = grid)
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { scroll = scope.launch { grid.animateScrollToItem(20) } }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { active.value = false }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle {
            assertFalse(scroll.isActive)
            assertFalse(grid.isScrollInProgress)
        }
        val anchor = grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset
        rule.mainClock.advanceTimeBy(1_000)
        rule.runOnIdle { assertEquals(anchor, grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset) }
    }

    @Test
    fun sharedArtworkRegistrationPausesWithThePageAndReturnsWithItsSelection() {
        val active = mutableStateOf(true)
        val item = movie("selected artwork")
        var registrations = 0
        rule.setContent {
            MaFeiTheme {
                CompositionLocalProvider(LocalMaFeiPageActive provides active.value) {
                    fixture(listOf(item), sharedArtwork = { _, bounds ->
                        DisposableEffect(Unit) {
                            registrations++
                            onDispose { registrations-- }
                        }
                        bounds
                    })
                }
            }
        }
        rule.onNodeWithText(item.title).performClick()
        rule.waitForIdle()
        assertEquals(1, registrations)
        rule.runOnIdle { active.value = false }
        rule.waitForIdle()
        assertEquals(0, registrations, "A retained background tab must not participate in shared bounds")
        rule.runOnIdle { active.value = true }
        rule.waitForIdle()
        assertEquals(1, registrations, "The selected source survives the tab switch")
    }

    @Composable
    private fun fixture(
        feed: List<MediaItem>,
        overview: Boolean = true,
        showHero: Boolean = false,
        backdrop: HomeHeroBackdropState? = null,
        prefetch: (suspend (MediaItem) -> Unit)? = null,
        canLoadMore: Boolean = false,
        onLoadMore: () -> Unit = {},
        grid: LazyGridState = rememberLazyGridState(),
        sharedArtwork: (@Composable (MediaItem, Modifier) -> Modifier)? = null,
    ) {
        Box(Modifier.size(390.dp, 844.dp)) {
            HomeScreen(
                collections = emptyList(), items = feed, homeRecommendations = feed,
                homeLatest = feed, homeResume = emptyList(), homeNextUp = emptyList(),
                selectedCollection = null, loading = false, homeLoading = false,
                error = null, stale = false, canLoadMore = canLoadMore, overview = overview,
                layout = mafeiLayout(390f, 844f), onCollection = {}, onItem = {}, onRetry = {},
                onLoadMore = onLoadMore, onOpenLibrary = {}, showHomeHero = showHero,
                heroAutoplay = false, gridState = grid, heroBackdropState = backdrop,
                prefetchBackdrop = prefetch, sharedArtworkModifier = sharedArtwork,
            )
        }
    }

    private fun movie(id: String) = MediaItem(MediaId(id), ServerId("server"), ProviderKind.Jellyfin,
        id, mediaType = MediaType.Movie)
}
