package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.ui.LegacyPosterPlaceholder
import cn.mafei.cmp.common.ui.LegacySortChoice
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaSort
import cn.mafei.cmp.core.domain.SortDirection
import cn.mafei.cmp.feature.favorite.FavoriteScreen
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.session.*
import kotlinx.coroutines.launch
import cn.mafei.cmp.common.design_system.MaFeiLayout

@Composable
internal fun FavoriteEntryBinding(
    scope: kotlinx.coroutines.CoroutineScope,
    browser: MediaBrowser,
    state: cn.mafei.cmp.feature.home.MediaBrowserState,
    layout: MaFeiLayout,
    player: BrowsePlayerState,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    navigator: BrowseNavigator,
    favoriteGridState: LazyGridState,
    musicMiniPlayerMedia: MediaItem?,
    favoriteSortChoices: List<LegacySortChoice<MediaSort>>,
) {
    FavoriteScreen(
        items = state.favoriteItems,
        selectedSort = state.favoriteSort,
        sortChoices = favoriteSortChoices,
        loading = state.favoriteLoading,
        refreshing = state.favoriteRefreshing,
        loadingMore = state.favoriteLoadingMore,
        error = state.favoriteError,
        canLoadMore = state.favoriteNextCursor != null,
        layout = layout,
        gridState = favoriteGridState,
        onSortSelected = { sort ->
            scope.launch { browser.setFavoriteSort(sort) }
        },
        onToggleSortDirection = {
            val direction = if (state.favoriteSort.direction == SortDirection.Descending) {
                SortDirection.Ascending
            } else {
                SortDirection.Descending
            }
            scope.launch {
                browser.setFavoriteSort(state.favoriteSort.copy(direction = direction))
            }
        },
        onRetry = { scope.launch { browser.refreshFavorites() } },
        onLoadMore = { scope.launch { browser.loadMoreFavorites() } },
        onItem = { item -> navigator.openDetail(item, "收藏") },
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
