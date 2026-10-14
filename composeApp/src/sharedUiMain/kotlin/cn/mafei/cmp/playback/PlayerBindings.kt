package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.feature.home.PlaybackQueue
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerSeekPreviewFrame
import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.api.PlayerTrack
import cn.mafei.cmp.player.ui.LiquidMiniPlayer
import cn.mafei.cmp.player.ui.MiniPlayerMedia
import cn.mafei.cmp.player.ui.NowPlayingMedia
import cn.mafei.cmp.player.ui.NowPlayingScreen
import cn.mafei.cmp.player.ui.toMiniPlayerPresentation
import cn.mafei.cmp.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Only state that changes application-level routing/chrome.
 *
 * Player progress arrives every 250-500 ms. Collecting the complete [PlayerState] in [BrowseApplication] used to recompose the whole browse tree for every
 * progress tick even though only the player surfaces consume position/buffer data.
 */
internal data class PlayerShellState(val phase: PlayerPhase = PlayerPhase.Idle, val resourceId: String? = null)

@Composable
internal fun BoundNowPlayingScreen(
    media: NowPlayingMedia,
    controller: PlayerController,
    providerTracks: kotlinx.coroutines.flow.StateFlow<List<cn.mafei.cmp.data.adapter_contract.PlaybackTrack>>,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onSelectTrack: (cn.mafei.cmp.player.api.TrackKind, String?) -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
    playerSurface: @Composable (PlayerController, onBack: () -> Unit, onExitFullscreen: () -> Unit, fullscreen: Boolean, Modifier) -> Unit,
    preparing: Boolean,
    playbackError: String?,
    modifier: Modifier = Modifier,
    queue: PlaybackQueue? = null,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onSelectEpisode: ((Int) -> Unit)? = null,
    qualityOptions: List<cn.mafei.cmp.player.ui.PlayerQualityOption> = emptyList(),
    onSelectQuality: ((Int) -> Unit)? = null,
    /** 目录条目：播放信息卡片要它的媒体源/流明细，播放控制本身只需要 NowPlayingMedia。 */
    catalogItem: cn.mafei.cmp.core.domain.MediaItem? = null,
    /** 本次协商的**实际结果**（媒体源 / 播放方式 / 档位表 / 生效档位）。 「播放信息」里的一切都从它来 —— 不用界面自己最后点的那一下去猜。 */
    prepared: cn.mafei.cmp.data.repositories.playback.PreparedStreamFacts? = null,
    onSelectContentMode: ((cn.mafei.cmp.player.api.PlayerContentMode) -> Unit)? = null,
    /** 全屏是显式状态（旧版 AppUIState.isFullScreen），不是从窗口横竖推出来的。 */
    fullscreen: Boolean = false,
    loadSeekPreview: (suspend (Long) -> PlayerSeekPreviewFrame?)? = null,
    hostWindowControls: @Composable RowScope.() -> Unit = {},
    hostWindowDragArea: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    // Keep the 250-500 ms progress stream inside the player subtree. The
    // application shell only observes the low-frequency phase/resource pair.
    val state by controller.state.collectAsState()
    val application = LocalApplicationRuntime.current
    val activeSession = application.sessions.state.collectAsState().value.session
    var danmaku by remember(media.id, activeSession) { mutableStateOf(emptyList<cn.mafei.cmp.player.api.DanmakuEntry>()) }
    LaunchedEffect(media.id, activeSession, state.engineType) {
        if (activeSession == null || state.engineType != cn.mafei.cmp.player.api.PlayerEngineType.System || catalogItem == null) return@LaunchedEffect
        try {
            val xml = application.sessions.media.danmakuXml(activeSession.media, catalogItem.id)
            danmaku =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { xml?.let(cn.mafei.cmp.player.api.DanmakuParser::parseXml).orEmpty() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            danmaku = emptyList()
        }
    }
    val negotiatedTracks by providerTracks.collectAsState()
    val systemScope = rememberCoroutineScope()
    val visibleTracks =
        negotiatedTracks
            .map { track ->
                PlayerTrack(
                    id = track.index.toString(),
                    kind =
                        when (track.kind) {
                            cn.mafei.cmp.data.adapter_contract.PlaybackTrackKind.Audio -> cn.mafei.cmp.player.api.TrackKind.Audio
                            cn.mafei.cmp.data.adapter_contract.PlaybackTrackKind.Subtitle -> cn.mafei.cmp.player.api.TrackKind.Subtitle
                        },
                    label =
                        track.displayTitle?.takeIf(String::isNotBlank)
                            ?: track.title?.takeIf(String::isNotBlank)
                            ?: if (track.kind == cn.mafei.cmp.data.adapter_contract.PlaybackTrackKind.Audio) {
                                "音轨 ${track.index}"
                            } else {
                                "字幕 ${track.index}"
                            },
                    language = track.language,
                    selected = track.selected,
                )
            }
            .ifEmpty { state.tracks }
    // 平台亮度/音量来自 ApplicationRuntime（宿主注入）；缺省实现让两条竖向手势都不介入。
    val deviceControls = LocalApplicationRuntime.current.deviceControls
    val windowControls = LocalApplicationRuntime.current.playerWindowControls
    val loadSystemArtwork = rememberPlatformArtworkLoader(catalogItem)
    val playbackFacts =
        catalogItem?.let { item ->
            playerPlaybackFacts(
                item = item,
                playingMediaSourceId = prepared?.mediaSourceId,
                transport = prepared?.transport,
                providerTracks = negotiatedTracks,
                protocol = prepared?.protocol,
                multiConnectionAllowed = prepared?.multiConnectionAllowed,
            )
        }
    NowPlayingScreen(
        media = media,
        state = state,
        tracks = visibleTracks,
        capabilities = controller.capabilities.copy(trackSelection = controller.capabilities.trackSelection || negotiatedTracks.isNotEmpty()),
        onBack = onBack,
        onTogglePlay = onTogglePlay,
        onRetry = onRetry,
        onSeek = onSeek,
        onSpeed = onSpeed,
        onSelectTrack = onSelectTrack,
        preparing = preparing,
        externalError = playbackError,
        episodes = queue?.items.orEmpty(),
        episodeIndex = queue?.index?.coerceAtLeast(0) ?: 0,
        onNext = onNext,
        onPrevious = onPrevious,
        onSelectEpisode = onSelectEpisode,
        qualityOptions = qualityOptions,
        onSelectQuality = onSelectQuality,
        playbackFacts = playbackFacts,
        onSelectContentMode = onSelectContentMode,
        deviceControls = deviceControls,
        danmaku = danmaku,
        onCasting =
            if (windowControls.castingSupported) {
                { windowControls.casting() }
            } else null,
        onPictureInPicture =
            if (windowControls.pictureInPictureSupported && !state.casting) {
                { windowControls.pictureInPicture() }
            } else null,
        loadSeekPreview = loadSeekPreview,
        hostWindowControls = hostWindowControls,
        hostWindowDragArea = hostWindowDragArea,
        fullscreen = fullscreen,
        onFullscreenRequest = { requested -> onFullscreenChanged(requested) },
        videoSurface = { surfaceModifier ->
            CompositionLocalProvider(
                LocalPlatformPlaybackActions provides
                    PlatformPlaybackActions(
                        generation = state.generation,
                        mediaId = catalogItem?.id?.value ?: state.resourceId.orEmpty(),
                        title = catalogItem?.title.orEmpty(),
                        onPlay = { systemScope.launch { controller.play() } },
                        onPause = { systemScope.launch { controller.pause() } },
                        onStop = onBack,
                        onSeek = onSeek,
                        onSpeed = onSpeed,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        loadArtwork = loadSystemArtwork,
                    )
            ) {
                playerSurface(controller, onBack, { onFullscreenChanged(false) }, fullscreen, surfaceModifier)
            }
        },
        modifier = modifier,
    )
}

@Composable
internal fun BoundLiquidMiniPlayer(
    media: MiniPlayerMedia,
    controller: PlayerController,
    backdrop: GlassBackdrop,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    compactProgress: () -> Float,
    artwork: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Mini-player progress is another high-frequency consumer and must not
    // invalidate Home/Library/Detail while playback advances.
    val state by controller.state.collectAsState()
    LiquidMiniPlayer(
        media = media,
        presentation = state.toMiniPlayerPresentation(),
        backdrop = backdrop,
        onOpen = onOpen,
        onTogglePlay = onTogglePlay,
        compactProgress = compactProgress,
        artwork = artwork,
        modifier = modifier,
    )
}
