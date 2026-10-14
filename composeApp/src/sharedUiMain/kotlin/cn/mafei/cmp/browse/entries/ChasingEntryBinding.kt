package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.media.landscapeArtworkCandidates
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.feature.chasing.ChasingBrowser
import cn.mafei.cmp.feature.chasing.ChasingScreen
import cn.mafei.cmp.session.*
import kotlinx.coroutines.launch
import cn.mafei.cmp.common.design_system.MaFeiLayout

@Composable
internal fun ChasingEntryBinding(
    scope: kotlinx.coroutines.CoroutineScope,
    state: cn.mafei.cmp.feature.home.MediaBrowserState,
    layout: MaFeiLayout,
    player: BrowsePlayerState,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    navigator: BrowseNavigator,
    chasingBrowser: ChasingBrowser,
    chasingState: cn.mafei.cmp.feature.chasing.ChasingState,
    chasingListState: androidx.compose.foundation.lazy.LazyListState,
    musicMiniPlayerMedia: MediaItem?,
) {
    ChasingScreen(
        state = chasingState,
        layout = layout,
        listState = chasingListState,
        onRetry = { scope.launch { chasingBrowser.refresh() } },
        onPlay = player.startPlayback,
        onOpenSeries = { id -> navigator.openDetailId(id, "电视节目") },
        bottomOverlayInset = if (musicMiniPlayerMedia != null) 76.dp else 0.dp,
        modifier = Modifier.fillMaxSize(),
        artwork = { item, modifier ->
            MediaArtwork(
                item.landscapeArtworkCandidates(),
                item.title,
                artworkCache,
                fetchArtwork,
                modifier,
            )
        },
    )
}
