package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.isPhoneNavigation
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.ui.LegacyPosterPlaceholder
import cn.mafei.cmp.common.ui.LegacySortChoice
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaSort
import cn.mafei.cmp.core.domain.MediaSortField
import cn.mafei.cmp.core.domain.SortDirection
import cn.mafei.cmp.feature.home.HomeLatestByLibraryScreen
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.home.legacyHomeLatestMediaName
import cn.mafei.cmp.feature.media.VideoListScreen
import cn.mafei.cmp.session.*
import kotlinx.coroutines.launch
import cn.mafei.cmp.common.design_system.MaFeiLayout

@Composable
internal fun LatestEntryBinding(
    route: BrowseLatestRoute,
    showingFullPageRoute: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    browser: MediaBrowser,
    state: cn.mafei.cmp.feature.home.MediaBrowserState,
    layout: MaFeiLayout,
    player: BrowsePlayerState,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    navigator: BrowseNavigator,
    latestGridState: LazyGridState,
    musicMiniPlayerMedia: MediaItem?,
    latestStore: cn.mafei.cmp.data.repositories.PagedMediaStore<cn.mafei.cmp.core.domain.MediaCollection>,
) {
    val collection = state.collections.firstOrNull { it.id.value == route.collectionId }
    val collectionLatest = route.collectionId != null
    val latestPages by latestStore.pages.collectAsState()
    val page = collection?.let { latestPages[it] }
        ?: cn.mafei.cmp.data.repositories.MediaPageState(
            loading = collection != null,
            error = if (collectionLatest && collection == null) "媒体库已不可用" else null,
        )
    val collectionGridState = remember(route.collectionId) { LazyGridState() }
    LaunchedEffect(latestStore, collection) {
        if (collection != null) latestStore.load(collection)
    }
    if (layout.isPhoneNavigation || collectionLatest) {
        VideoListScreen(
            title = collection?.let { "最近添加 · ${it.name}" } ?: "最近添加",
            items = if (collectionLatest) page.items else state.homeFeed.latest,
            selectedSort = MediaSort(
                MediaSortField.DateAdded,
                SortDirection.Descending,
            ),
            sortChoices = listOf(
                LegacySortChoice(
                    "按添加日期",
                    MediaSort(
                        MediaSortField.DateAdded,
                        SortDirection.Descending,
                    ),
                ),
            ),
            loading = if (collectionLatest) page.loading && page.items.isEmpty() else state.homeFeed.latest.isEmpty() &&
                state.homeError == null &&
                (state.loading || state.refreshing),
            refreshing = collectionLatest && page.loading && page.items.isNotEmpty(),
            loadingMore = collectionLatest && page.loadingMore,
            error = if (collectionLatest) page.error else state.homeError,
            canLoadMore = collectionLatest && page.cursor != null,
            layout = layout,
            gridState = if (collectionLatest) collectionGridState else latestGridState,
            onBack = navigator::pop,
            onSortSelected = {},
            onToggleSortDirection = {},
            onRetry = { scope.launch {
                if (collectionLatest) collection?.let { latestStore.load(it, refresh = true) }
                else browser.refreshHome()
            } },
            onLoadMore = { collection?.let { scope.launch { latestStore.load(it, more = true) } } },
            onItem = { item -> navigator.openDetail(item, item.legacyHomeLatestMediaName(state.collections)) },
            onPlay = player.startPlayback,
            onToggleWatched = if (browser.capabilities.watchedEdit) {
                { item -> scope.launch {
                    browser.toggleWatched(item)
                    collection?.let { latestStore.load(it, refresh = true) }
                } }
            } else null,
            onToggleFavorite = if (browser.capabilities.favoritesEdit) {
                { item -> scope.launch {
                    browser.toggleFavorite(item)
                    collection?.let { latestStore.load(it, refresh = true) }
                } }
            } else null,
            showSortControls = false,
            bottomOverlayInset = if (musicMiniPlayerMedia != null) 76.dp else 0.dp,
            bottomNavigationVisible = !showingFullPageRoute &&
                layout.isPhoneNavigation,
            modifier = Modifier.fillMaxSize(),
            artwork = { item, modifier ->
                MediaArtwork(
                    item.preferredPoster(),
                    item.title,
                    artworkCache,
                    fetchArtwork,
                    modifier,
                    fallback = { fallbackModifier ->
                        LegacyPosterPlaceholder(item.mediaType, fallbackModifier)
                    },
                )
            },
        )
    } else {
        HomeLatestByLibraryScreen(
            collections = state.collections,
            latestByCollection = state.homeLatestByCollection,
            loadingCollections = state.homeLatestLoading,
            errors = state.homeLatestErrors,
            layout = layout,
            gridState = latestGridState,
            // Expanded sidebars do not show the collapsed shell tab row;
            // let the page action bar own the back affordance there.
            onBack = navigator::pop,
            onRetry = {
                state.collections.forEach { collection ->
                    scope.launch { browser.loadHomeLatest(collection.id) }
                }
            },
            onItem = { item ->
                navigator.openDetail(item, item.legacyHomeLatestMediaName(state.collections))
            },
            onPlay = player.startPlayback,
            onToggleWatched = if (browser.capabilities.watchedEdit) {
                { item -> scope.launch { browser.toggleWatched(item) } }
            } else null,
            onToggleFavorite = if (browser.capabilities.favoritesEdit) {
                { item -> scope.launch { browser.toggleFavorite(item) } }
            } else null,
            bottomOverlayInset = if (musicMiniPlayerMedia != null) 76.dp else 0.dp,
            modifier = Modifier.fillMaxSize(),
            artwork = { item, modifier ->
                MediaArtwork(
                    item.preferredPoster(),
                    item.title,
                    artworkCache,
                    fetchArtwork,
                    modifier,
                    fallback = { fallbackModifier ->
                        LegacyPosterPlaceholder(item.mediaType, fallbackModifier)
                    },
                )
            },
        )
    }
}
