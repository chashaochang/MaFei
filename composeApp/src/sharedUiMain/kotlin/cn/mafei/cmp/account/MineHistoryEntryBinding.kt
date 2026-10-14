package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.ui.LegacyPosterPlaceholder
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.settings.MineHistoryScreen
import cn.mafei.cmp.session.*
import kotlinx.coroutines.launch

@Composable
internal fun MineHistoryEntryBinding(
    scope: kotlinx.coroutines.CoroutineScope,
    browser: MediaBrowser,
    state: cn.mafei.cmp.feature.home.MediaBrowserState,
    layout: MaFeiLayout,
    player: BrowsePlayerState,
    popMineRoute: () -> Unit,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
) {
    MineHistoryScreen(
        items = state.historyItems,
        loading = state.historyLoading && state.historyItems.isEmpty(),
        error = state.historyError,
        layout = layout,
        onBack = popMineRoute,
        onRetry = { scope.launch { browser.loadHistory() } },
        onItem = player.startPlayback,
        canLoadMore = state.historyCanLoadMore,
        loadingMore = state.historyLoadingMore,
        loadMoreFailed = state.historyLoadMoreFailed,
        onLoadMore = { scope.launch { browser.loadMoreHistory() } },
        onRetryLoadMore = { scope.launch { browser.retryLoadMoreHistory() } },
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
