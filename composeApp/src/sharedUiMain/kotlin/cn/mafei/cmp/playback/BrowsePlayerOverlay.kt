package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.isPhoneNavigation
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.player.ui.MiniPlayerMedia
import cn.mafei.cmp.session.*

@Composable
internal fun BoxScope.BrowsePlayerOverlay(
    backdrop: cn.mafei.cmp.common.glass.GlassBackdrop,
    layout: cn.mafei.cmp.common.design_system.MaFeiLayout,
    player: BrowsePlayerState,
    playback: SessionPlaybackRuntime?,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    showingDetail: Boolean,
    musicMiniPlayerMedia: MediaItem?,
    chromeCompactProgress: State<Float>,
) {
    val safeArea = cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea.current
    val media = musicMiniPlayerMedia
    val playerController = playback?.controller
    cn.mafei.cmp.common.ui.LegacyTransientToast(player.playbackError, Modifier.align(Alignment.BottomCenter))
    if (media != null && playerController != null) {
        BoundLiquidMiniPlayer(
            media = MiniPlayerMedia(id = media.id.value, title = media.title, subtitle = media.miniPlayerSubtitle()),
            controller = playerController,
            backdrop = backdrop,
            onOpen = { player.playerExpanded = true },
            onTogglePlay = player.togglePlayback,
            compactProgress = { chromeCompactProgress.value },
            artwork = { modifier -> MediaArtwork(media.preferredPoster(), media.title, artworkCache, fetchArtwork, modifier) },
            modifier =
                Modifier.align(if (layout.isPhoneNavigation) Alignment.BottomCenter else Alignment.BottomEnd)
                    .padding(
                        start = safeArea.start + if (layout.isPhoneNavigation) 16.dp else 24.dp,
                        end = safeArea.end + 16.dp,
                        bottom = safeArea.bottom + if (layout.isPhoneNavigation && !showingDetail) 88.dp else 24.dp,
                    )
                    .widthIn(max = 430.dp)
                    .fillMaxWidth(),
        )
    }
}
