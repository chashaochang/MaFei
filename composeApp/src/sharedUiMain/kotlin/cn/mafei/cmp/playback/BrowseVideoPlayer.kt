package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.api.PlayerSeekPreviewFrame
import cn.mafei.cmp.player.ui.NowPlayingMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun BrowseVideoPlayer(
    player: BrowsePlayerState,
    playerMedia: MediaItem,
    preparedStream: cn.mafei.cmp.data.repositories.playback.PreparedStreamFacts?,
    qualityOptions: List<cn.mafei.cmp.player.ui.PlayerQualityOption>,
    repository: cn.mafei.cmp.data.repositories.MediaRepository,
    mediaSession: cn.mafei.cmp.data.repositories.MediaSession,
    playerSurface: @Composable (PlayerController, () -> Unit, () -> Unit, Boolean, Modifier) -> Unit,
    windowControls: @Composable RowScope.() -> Unit,
    windowDragArea: @Composable (@Composable () -> Unit) -> Unit,
    onPlayerFullscreenChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val playback = player.playback ?: return
    val hostWindowFeatures = LocalMaFeiWindowFeatures.current
    LaunchedEffect(hostWindowFeatures.windowedHost, hostWindowFeatures.windowFullscreen) {
        if (hostWindowFeatures.windowedHost) player.playerFullscreen = hostWindowFeatures.windowFullscreen
    }
    BoundNowPlayingScreen(
        media =
            NowPlayingMedia(
                id = playerMedia.id.value,
                title = playerMedia.title,
                subtitle = playerMedia.miniPlayerSubtitle(),
                description = playerMedia.overview,
                live = playerMedia.mediaType == MediaType.LiveChannel,
            ),
        controller = playback.controller,
        providerTracks = playback.tracks,
        onBack = onClose,
        onTogglePlay = player.togglePlayback,
        onRetry = { player.startPlayback(playerMedia) },
        onSeek = { player.pendingSeekPosition = it },
        onSpeed = { speed ->
            scope.launch {
                try {
                    playback.controller.speed(speed)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    player.playbackError = "播放器操作失败，请重试"
                }
            }
        },
        onSelectTrack = { kind, id ->
            scope.launch {
                try {
                    playback.selectTrack(kind, id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    player.playbackError = "播放器操作失败，请重试"
                }
            }
        },
        playerSurface = playerSurface,
        preparing = player.playbackBusy,
        hostWindowControls = windowControls,
        hostWindowDragArea = windowDragArea,
        playbackError = player.playbackError,
        modifier = Modifier.fillMaxSize(),
        queue = player.playQueue,
        onNext = player.playQueue?.takeIf { it.hasNext }?.let { { player.playNextEpisode() } },
        onPrevious = player.playQueue?.takeIf { it.hasPrevious }?.let { { player.playPreviousEpisode() } },
        onSelectEpisode = player.playQueue?.takeIf { it.size > 1 }?.let { { player.playEpisodeAt(it) } },
        qualityOptions = qualityOptions,
        onSelectQuality = { player.selectQuality(it) },
        catalogItem = playerMedia,
        prepared = preparedStream,
        loadSeekPreview = { positionMs ->
            repository
                .previewFrame(session = mediaSession, item = playerMedia.id, positionMs = positionMs, mediaSourceId = preparedStream?.mediaSourceId)
                ?.let { frame ->
                    PlayerSeekPreviewFrame(
                        encodedTile = frame.encodedTile,
                        frameWidthPx = frame.frameWidthPx,
                        frameHeightPx = frame.frameHeightPx,
                        tileColumn = frame.tileColumn,
                        tileRow = frame.tileRow,
                    )
                } ?: playback.previewFrame(positionMs)
        },
        // 全屏请求先落在页面状态上，再交给宿主（旋转窗口 / 隐藏系统栏）。
        // 页面状态先行的原因：宿主是异步的，UI 布局不能等它。
        fullscreen = if (hostWindowFeatures.windowedHost) hostWindowFeatures.windowFullscreen else player.playerFullscreen,
        onFullscreenChanged = { requested ->
            player.playerFullscreen = requested
            onPlayerFullscreenChanged(requested)
        },
        onSelectContentMode = { mode ->
            scope.launch {
                try {
                    playback.controller.setContentMode(mode)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    player.playbackError = "切换画面比例失败，请重试"
                }
            }
        },
    )
}
