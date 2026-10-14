package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.currentMaFeiSafeArea
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.CachedBlurredArtwork
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.feature.music.MusicCatalogStore
import cn.mafei.cmp.feature.music.MusicRepeatMode
import cn.mafei.cmp.player.ui.NowPlayingMedia
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.api.PlayerSeekPreviewFrame
import cn.mafei.cmp.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
internal fun BrowsePlayerHost(
    player: BrowsePlayerState,
    playerMedia: MediaItem?,
    preparedStream: cn.mafei.cmp.data.repositories.playback.PreparedStreamFacts?,
    qualityOptions: List<cn.mafei.cmp.player.ui.PlayerQualityOption>,
    repository: cn.mafei.cmp.data.repositories.MediaRepository,
    mediaSession: cn.mafei.cmp.data.repositories.MediaSession,
    canEditFavorites: Boolean,
    musicStore: MusicCatalogStore,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    playerSurface: @Composable (PlayerController, () -> Unit, () -> Unit, Boolean, Modifier) -> Unit,
    windowControls: @Composable RowScope.() -> Unit,
    windowDragArea: @Composable (@Composable () -> Unit) -> Unit,
    onPlayerFullscreenChanged: (Boolean) -> Unit,
    onReturnToBrowse: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val playback = player.playback
    val playingMedia = player.playingMedia
    val hostWindowFeatures = LocalMaFeiWindowFeatures.current
    if (playingMedia?.mediaType == MediaType.Audio && playback != null) {
        BoundMusicNativeSurface(
            controller = playback.controller, item = playingMedia!!, playerSurface = playerSurface,
            onBack = { player.playerExpanded = false }, onStop = { scope.launch { playback.stop() } },
            onSeek = { player.pendingSeekPosition = it }, onNext = player.playQueue?.takeIf { it.hasNext }?.let { player.playNextEpisode },
            onPrevious = player.playQueue?.takeIf { it.hasPrevious }?.let { player.playPreviousEpisode },
        )
    }
    if (player.playerExpanded && playerMedia?.mediaType == MediaType.Audio && playback != null) {
        CompositionLocalProvider(LocalMaFeiSafeArea provides currentMaFeiSafeArea()) {
        BoundMusicPlayer(
            item = playerMedia, controller = playback.controller, preparing = player.playbackBusy, error = player.playbackError,
            queue = player.playQueue?.items.orEmpty(), queueIndex = player.playQueue?.index ?: 0, repeatMode = player.musicRepeatMode,
            onBack = { player.playerExpanded = false }, onTogglePlay = player.togglePlayback, onRetry = { player.startPlayback(playerMedia); player.playerExpanded = true },
            onSeek = { player.pendingSeekPosition = it },
            onPrevious = player.playQueue?.takeIf { it.hasPrevious }?.let { player.playPreviousEpisode },
            onNext = player.playQueue?.takeIf { it.hasNext }?.let { player.playNextEpisode },
            onSelect = { player.playEpisodeAt(it); player.playerExpanded = true },
            onRepeat = { player.musicRepeatMode = when(player.musicRepeatMode) {
                MusicRepeatMode.Off -> MusicRepeatMode.All
                MusicRepeatMode.All -> MusicRepeatMode.One
                MusicRepeatMode.One -> MusicRepeatMode.Off
            } },
            onFavorite = if (canEditFavorites) {{
                val favorite = !playerMedia.userData.isFavorite
                repository.setFavorite(mediaSession, playerMedia.id, favorite)
                musicStore.favoriteChanged(playerMedia.id, favorite)
                val updated = playerMedia.copy(userData = playerMedia.userData.copy(isFavorite = favorite))
                player.playingMedia = updated
                player.playQueue = player.playQueue?.let { it.copy(items = it.items.map { track -> if (track.id == updated.id) updated else track }) }
            }} else null,
            onRemove = { index ->
                player.playQueue?.let { queue -> if (index in queue.items.indices && index != queue.index && !player.playbackBusy) {
                    player.playQueue = queue.copy(items = queue.items.filterIndexed { itemIndex, _ -> itemIndex != index },
                        index = if (index < queue.index) queue.index - 1 else queue.index)
                } }
            },
            onClear = { scope.launch {
                try {
                    playback.stop(); player.playQueue = null; player.playingMedia = null; player.playerExpanded = false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { player.playbackError = "停止播放失败，请重试" }
            } },
            artwork = { item, modifier -> MediaArtwork(item.preferredPoster(), item.title, artworkCache, fetchArtwork, modifier) },
            ambientArtwork = { item, modifier -> MediaArtwork(item.preferredPoster(), item.title, artworkCache,
                fetchArtwork, modifier, targetWidthPx = 480,
                bitmapContent = { bitmap, imageModifier -> CachedBlurredArtwork(bitmap, 64.dp, imageModifier, cache = artworkCache) }) },
        )
        }
    } else if (player.playerExpanded && playerMedia != null && playback != null) {
        BoundNowPlayingScreen(
            media = NowPlayingMedia(
                id = playerMedia.id.value,
                title = playerMedia.title,
                subtitle = playerMedia.miniPlayerSubtitle(),
                description = playerMedia.overview,
                live = playerMedia.mediaType == MediaType.LiveChannel,
            ),
            controller = playback.controller,
            providerTracks = playback.tracks,
            onBack = {
                player.playerExpanded = false
                onReturnToBrowse()
                player.playerFullscreen = false
                if (!hostWindowFeatures.windowedHost) onPlayerFullscreenChanged(false)
                scope.launch {
                    try {
                        playback.stop()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        player.playbackError = "停止播放失败，请重试"
                    }
                }
            },
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
                repository.previewFrame(
                    session = mediaSession,
                    item = playerMedia.id,
                    positionMs = positionMs,
                    mediaSourceId = preparedStream?.mediaSourceId,
                )?.let { frame ->
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
}
