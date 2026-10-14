package cn.mafei.cmp.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.core.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeTabletShelfLayoutTest {
    @get:org.junit.Rule val compose = createComposeRule()

    private val movies = collection("movies", "电影", MediaType.Movie)
    private val shows = collection("shows", "剧集", MediaType.Series)
    private val movie = item("movie", MediaType.Movie)
    private val series = item("series", MediaType.Series)
    private val sizes = mutableStateOf(700.dp to 900.dp)
    private val layout = mutableStateOf(mafeiLayout(700f, 900f))
    private lateinit var grid: LazyGridState
    private val openedLatest = mutableListOf<CollectionId>()
    private val openedItems = mutableListOf<MediaId>()
    private val selectedFeeds = mutableListOf<CollectionId>()

    @Test fun portraitTabletUsesTheSamePerLibraryShelvesAndMoreRoutesAsLandscape() {
        mount()
        assertLibraryShelves()
        compose.onAllNodesWithText("更多")[0].performClick()
        assertEquals(listOf(movies.id), openedLatest)
        assertTrue(selectedFeeds.isEmpty(), "Tablet More must not select a phone feed")
        compose.onNodeWithText(movie.title).performClick()
        assertEquals(listOf(movie.id), openedItems)
        compose.runOnIdle { sizes.value = 1000.dp to 700.dp; layout.value = mafeiLayout(1000f, 700f) }
        assertLibraryShelves()
        compose.onAllNodesWithText("更多")[1].performClick()
        assertEquals(listOf(movies.id, shows.id), openedLatest)
        compose.onNodeWithText(series.title).performClick()
        assertEquals(listOf(movie.id, series.id), openedItems)
    }

    @Test fun switchingFromASelectedPhoneFeedToTabletDoesNotFilterTheShelves() {
        sizes.value = 390.dp to 700.dp
        layout.value = mafeiLayout(390f, 700f)
        mount()
        compose.onNodeWithText("电影").performClick()
        compose.waitForIdle()
        assertEquals(listOf(movies.id), selectedFeeds)
        compose.onNodeWithText(movie.title).assertExists()
        compose.onNodeWithText(series.title).assertDoesNotExist()
        compose.runOnIdle { sizes.value = 700.dp to 900.dp; layout.value = mafeiLayout(700f, 900f) }
        assertLibraryShelves()
        compose.runOnIdle { sizes.value = 390.dp to 700.dp; layout.value = mafeiLayout(390f, 700f) }
        compose.onNodeWithText("电影").assertExists()
        compose.onNodeWithText(movie.title).assertExists()
        compose.onNodeWithText(series.title).assertDoesNotExist()
        assertEquals(listOf(movies.id), selectedFeeds, "Window changes must not issue another feed selection")
    }

    @Test fun compactRailKeepsLibraryShelvesToo() {
        layout.value = mafeiLayout(700f, 900f).copy(navigation = NavigationPlacement.Rail)
        mount()
        assertLibraryShelves()
    }

    private fun assertLibraryShelves() {
        compose.waitForIdle()
        compose.onNodeWithText("最近添加").assertDoesNotExist()
        compose.onNodeWithText("电影").assertDoesNotExist()
        compose.onNodeWithText("最近添加 · 电影").assertExists()
        compose.onNodeWithText("最近添加 · 剧集").assertExists()
        compose.onNodeWithText(movie.title).assertExists()
        compose.onNodeWithText(series.title).assertExists()
        val keys = grid.layoutInfo.visibleItemsInfo.map { it.key }
        assertTrue("home-latest-shelf:movies" in keys)
        assertTrue("home-latest-shelf:shows" in keys)
        assertFalse("home-latest-feed-anchor" in keys)
    }

    private fun mount() {
        compose.setContent {
            MaFeiTheme {
                grid = rememberLazyGridState()
                Box(Modifier.size(sizes.value.first, sizes.value.second)) {
                    HomeScreen(
                        collections = listOf(movies, shows), items = emptyList(),
                        homeLatest = listOf(movie, series),
                        homeLatestByCollection = mapOf(movies.id to listOf(movie), shows.id to listOf(series)),
                        homeResume = emptyList(), homeNextUp = emptyList(),
                        selectedCollection = null, loading = false, homeLoading = false,
                        error = null, stale = false, canLoadMore = false, overview = true, layout = layout.value,
                        onCollection = { selectedFeeds += it.id }, onOpenCollectionLatest = { openedLatest += it.id },
                        onItem = { openedItems += it.id }, onRetry = {}, onLoadMore = {}, onOpenLibrary = {},
                        showHomeHero = false, gridState = grid,
                    )
                }
            }
        }
    }

    private fun collection(id: String, name: String, mediaType: MediaType) = MediaCollection(
        CollectionId(id), ServerId("server"), ProviderKind.Jellyfin, name, mediaTypes = setOf(mediaType),
    )
    private fun item(id: String, mediaType: MediaType) = MediaItem(
        MediaId(id), ServerId("server"), ProviderKind.Jellyfin, "$id-title", mediaType = mediaType,
    )
}
