package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.ui.LegacyPosterPlaceholder
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.search.SearchScreen
import cn.mafei.cmp.session.*
import kotlinx.coroutines.launch

@Composable
internal fun SearchEntryBinding(
    scope: kotlinx.coroutines.CoroutineScope,
    browser: MediaBrowser,
    state: cn.mafei.cmp.feature.home.MediaBrowserState,
    layout: MaFeiLayout,
    player: BrowsePlayerState,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    navigator: BrowseNavigator,
    search: BrowseSearchState,
    searchGridState: LazyGridState,
) {
    SearchScreen(
        query = state.searchQuery,
        results = state.searchItems,
        histories = search.searchHistories,
        loading = state.searchLoading,
        error = state.searchError,
        layout = layout,
        gridState = searchGridState,
        onBack = navigator::pop,
        onQueryChange = { term ->
            search.query(term, submitted = false) { if (searchGridState.layoutInfo.totalItemsCount > 0) searchGridState.scrollToItem(0) }
        },
        onSubmit = { term -> search.query(term, submitted = true) { if (searchGridState.layoutInfo.totalItemsCount > 0) searchGridState.scrollToItem(0) } },
        onRecordHistory = search::record,
        onClearHistories = search::clearHistory,
        onOpen = { item -> navigator.openDetail(item, null) },
        onPlay = player.startPlayback,
        onToggleWatched =
            if (browser.capabilities.watchedEdit) {
                { item -> scope.launch { browser.toggleWatched(item) } }
            } else null,
        onToggleFavorite =
            if (browser.capabilities.favoritesEdit) {
                { item -> scope.launch { browser.toggleFavorite(item) } }
            } else null,
        modifier = Modifier.fillMaxSize(),
        artwork = { item, modifier ->
            MediaArtwork(
                item.preferredPoster(),
                item.title,
                artworkCache,
                fetchArtwork,
                modifier,
                fallback = { fallbackModifier -> LegacyPosterPlaceholder(item.mediaType, fallbackModifier) },
            )
        },
    )
}
