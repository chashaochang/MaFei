package cn.mafei.cmp.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.ui.LocalMaFeiPageActive
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeWarmRecompositionTest {
    @get:org.junit.Rule val rule = createComposeRule()

    @Test
    fun warmPhoneActivationDoesNotTraverseUnchangedCollections() = checkWarmActivation(390f, 844f)

    @Test
    fun warmWideActivationDoesNotReenumerateTheLibraryShelves() = checkWarmActivation(1_024f, 768f)

    private fun checkWarmActivation(width: Float, height: Float) {
        val active = mutableStateOf(true)
        val libraries = (0..5).map { index ->
            MediaCollection(CollectionId("library-$index"), ServerId("server"), ProviderKind.Jellyfin, "Library $index")
        }
        val collections = CountingCollections(libraries)
        val feed = (0..59).map { index ->
            MediaItem(MediaId("movie-$index"), ServerId("server"), ProviderKind.Jellyfin, "Movie $index", MediaType.Movie)
        }
        val latestByLibrary = libraries.mapIndexed { index, collection ->
            collection.id to feed.subList(index * 10, index * 10 + 10)
        }.toMap()
        val layout = mafeiLayout(width, height)
        val grid = LazyGridState()
        val openedItems = mutableListOf<MediaId>()
        val onItem: (MediaItem) -> Unit = { openedItems += it.id }
        val onCollection: (MediaCollection) -> Unit = {}
        val noAction: () -> Unit = {}
        val artwork: @Composable (MediaItem, Modifier) -> Unit = { _, modifier -> Box(modifier) }

        // The active local is the only changing input. All data and callbacks keep
        // their identity, including the provider's per-library snapshots.
        rule.setContent {
            MaFeiTheme {
                CompositionLocalProvider(LocalMaFeiPageActive provides active.value) {
                    Box(Modifier.size(width.dp, height.dp)) {
                        HomeScreen(
                            collections = collections,
                            items = feed,
                            homeRecommendations = emptyList(),
                            homeResume = emptyList(),
                            homeNextUp = emptyList(),
                            homeLatest = feed,
                            homeLatestByCollection = latestByLibrary,
                            selectedCollection = libraries.first().id,
                            loading = false,
                            homeLoading = false,
                            error = null,
                            stale = false,
                            canLoadMore = false,
                            overview = true,
                            layout = layout,
                            onCollection = onCollection,
                            onItem = onItem,
                            onRetry = noAction,
                            onLoadMore = noAction,
                            onOpenLibrary = noAction,
                            showHomeHero = false,
                            heroAutoplay = false,
                            gridState = grid,
                            artwork = artwork,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(feed.first().title).assertIsDisplayed().performClick()
        rule.runOnIdle { runBlocking { grid.scrollToItem(2, 17) } }
        rule.waitForIdle()
        val baseline = rule.runOnIdle {
            assertEquals(listOf(feed.first().id), openedItems)
            assertTrue(collections.elementReads > 0, "The real HomeScreen must have consumed the input probe")
            assertTrue(grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 0)
            assertTrue(grid.layoutInfo.visibleItemsInfo.isNotEmpty())
            collections.reads() to gridSnapshot(grid)
        }

        repeat(2) {
            listOf(false, true).forEach { presented ->
                rule.runOnIdle { active.value = presented }
                rule.waitForIdle()
                rule.runOnIdle {
                    assertEquals(
                        baseline.first,
                        collections.reads(),
                        "Changing only page activity must not repeat collection traversal (active=$presented)",
                    )
                    assertEquals(baseline.second, gridSnapshot(grid), "Warm activation must preserve the settled feed and anchor")
                    assertEquals(listOf(feed.first().id), openedItems, "Activity changes must not replay a media action")
                }
            }
        }

        // The retained page still dispatches real card actions after resuming.
        // Scrolling/clicking are outside the measured activity-only interval.
        rule.runOnIdle { runBlocking { grid.scrollToItem(0) } }
        rule.waitForIdle()
        rule.onNodeWithText(feed.first().title).assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(listOf(feed.first().id, feed.first().id), openedItems) }
    }

    private fun gridSnapshot(grid: LazyGridState) = GridSnapshot(
        grid.firstVisibleItemIndex,
        grid.firstVisibleItemScrollOffset,
        grid.layoutInfo.visibleItemsInfo.map { it.key },
    )

    private data class GridSnapshot(val index: Int, val offset: Int, val visibleKeys: List<Any>)

    private data class ReadCounts(val iterators: Int, val elements: Int)

    /** Counts input work, not artwork composition, which Compose may legitimately skip. */
    private class CountingCollections(private val backing: List<MediaCollection>) : AbstractList<MediaCollection>() {
        var elementReads = 0
            private set
        private var iteratorReads = 0

        // A lifecycle effect may check emptiness without traversing the data.
        override val size: Int get() = backing.size

        override fun get(index: Int): MediaCollection {
            elementReads++
            return backing[index]
        }

        override fun iterator(): Iterator<MediaCollection> {
            iteratorReads++
            return super.iterator()
        }

        fun reads() = ReadCounts(iteratorReads, elementReads)
    }
}
