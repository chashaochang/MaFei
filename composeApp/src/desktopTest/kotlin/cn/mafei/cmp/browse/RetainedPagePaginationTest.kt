package cn.mafei.cmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.ui.MaFeiRetainedContent
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.repositories.MediaPageState
import cn.mafei.cmp.feature.favorite.FavoriteScreen
import cn.mafei.cmp.feature.media.VideoListScreen
import cn.mafei.cmp.feature.music.*
import cn.mafei.cmp.feature.photo.PhotoCatalogScreen
import cn.mafei.cmp.feature.settings.MineHistoryScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.test.*

class RetainedPagePaginationTest {
    @get:org.junit.Rule val rule = createComposeRule()
    private enum class PageKind { Video, Favorite, Photo, Music, History }
    private val artwork: @Composable (MediaItem, Modifier) -> Unit = { _, modifier -> Box(modifier.background(Color.Gray)) }
    private fun item(index: Int) = MediaItem(MediaId("item-$index"), ServerId("server"), ProviderKind.Jellyfin, "Item $index", MediaType.Movie)

    @Test fun hiddenVideoListKeepsItsCursorUntilReactivated() = checkPaging(PageKind.Video)
    @Test fun hiddenFavoritesKeepTheirCursorUntilReactivated() = checkPaging(PageKind.Favorite)
    @Test fun hiddenPhotoCatalogKeepsItsCursorUntilReactivated() = checkPaging(PageKind.Photo)
    @Test fun hiddenMusicDetailKeepsItsCursorUntilReactivated() = checkPaging(PageKind.Music)
    @Test fun hiddenHistoryKeepsItsCursorUntilReactivated() = checkPaging(PageKind.History)

    private fun checkPaging(kind: PageKind) {
        val active = mutableStateOf(false)
        val items = mutableStateOf(listOf(item(1)))
        var loads = 0
        rule.setContent {
            MaFeiTheme {
                Box(Modifier.size(390.dp, 844.dp)) {
                    MaFeiRetainedContent(active.value) {
                        fixture(kind, items.value, rememberLazyGridState(), more = { loads++ })
                    }
                }
            }
        }
        rule.waitForIdle()
        assertEquals(0, loads, "A retained page which has never been laid out must not fetch or stop an idle scroll")
        rule.runOnIdle { active.value = true }
        rule.waitUntil { loads > 0 }
        rule.runOnIdle { active.value = false }
        rule.waitForIdle()
        val visibleLoads = loads
        rule.runOnIdle { items.value = items.value + item(2) }
        rule.waitForIdle()
        assertEquals(visibleLoads, loads, "A hidden page must not advance a cursor when its cached layout or items change")
        rule.runOnIdle { active.value = true }
        rule.waitUntil { loads > visibleLoads }
    }

    @Test fun hidingVideoListStopsItsScrollAndPreservesTheAnchor() = checkScroll(PageKind.Video)
    @Test fun hidingFavoritesStopsTheirScrollAndPreservesTheAnchor() = checkScroll(PageKind.Favorite)

    private fun checkScroll(kind: PageKind) {
        val active = mutableStateOf(true)
        lateinit var grid: LazyGridState
        lateinit var scope: CoroutineScope
        lateinit var scrolling: Job
        rule.setContent {
            MaFeiTheme {
                grid = rememberLazyGridState()
                scope = rememberCoroutineScope()
                Box(Modifier.size(390.dp, 844.dp)) {
                    MaFeiRetainedContent(active.value) { fixture(kind, (0..59).map(::item), grid, more = {}) }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { scrolling = scope.launch { grid.animateScrollToItem(25) } }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { active.value = false }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { assertFalse(scrolling.isActive); assertFalse(grid.isScrollInProgress) }
        val anchor = grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle { assertEquals(anchor, grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset) }
    }

    @Composable
    private fun fixture(kind: PageKind, items: List<MediaItem>, grid: LazyGridState, more: () -> Unit) {
        val layout = mafeiLayout(390f, 844f)
        val sort = MediaSort(MediaSortField.DateAdded, SortDirection.Descending)
        when (kind) {
            PageKind.Video -> VideoListScreen("Videos", items, sort, emptyList(), false, false, false, null,
                true, layout, grid, onBack = {}, onSortSelected = {}, onToggleSortDirection = {}, onRetry = {},
                onLoadMore = more, onItem = {}, artwork = artwork)
            PageKind.Favorite -> FavoriteScreen(items, sort, emptyList(), false, false, false, null, true,
                layout, grid, onSortSelected = {}, onToggleSortDirection = {}, onRetry = {}, onLoadMore = more,
                onItem = {}, artwork = artwork)
            PageKind.Photo -> PhotoCatalogScreen("Photos", true, MediaPageState(items, PageCursor("next"), initialized = true),
                onBack = {}, onSection = null, onRetry = {}, onMore = more, onPhoto = {}, onAlbum = {}, artwork = artwork)
            PageKind.Music -> MusicDetailScreen("Album", false, MusicDetailState(item(0).copy(mediaType = MediaType.MusicAlbum)),
                MusicPageState(items, PageCursor("next"), initialized = true), onBack = {}, onRetry = {},
                onMoreTracks = more, onMoreAlbums = {}, onAlbum = {}, onPlay = { _, _ -> }, onFavorite = null, artwork = artwork)
            PageKind.History -> MineHistoryScreen(items.map { PlaybackHistoryItem(it) }, false, null, layout,
                onBack = {}, onRetry = {}, onItem = {}, canLoadMore = true, onLoadMore = more, artwork = artwork)
        }
    }
}
