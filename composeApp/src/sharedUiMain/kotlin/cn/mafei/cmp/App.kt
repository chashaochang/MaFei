package cn.mafei.cmp

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.design_system.MaFeiLayoutFamily
import cn.mafei.cmp.common.design_system.MaFeiPersistentSidebarWidthDp
import cn.mafei.cmp.common.design_system.isPhoneNavigation
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.design_system.MaFeiWindowEnvironment
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiSafeArea
import cn.mafei.cmp.common.design_system.currentMaFeiSafeArea
import cn.mafei.cmp.common.design_system.mafeiAdaptiveWindow
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.glass.GlassRenderingEnvironment
import cn.mafei.cmp.common.glass.DefaultGlassIntensity
import cn.mafei.cmp.common.glass.rememberGlassRenderHealth
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.glass.rememberCombinedGlassBackdrop
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassMaterial
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.design_system.MaFeiSurface
import cn.mafei.cmp.common.design_system.LocalMaFeiPopupColors
import cn.mafei.cmp.common.design_system.MaFeiPopupReferenceColors
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.media.landscapeArtworkCandidates
import cn.mafei.cmp.common.media.legacyDetailBackdropArtworkCandidates
import cn.mafei.cmp.common.media.legacyPrimaryArtworkCandidates
import cn.mafei.cmp.common.media.legacyPrimaryThenBackdropArtworkCandidates
import cn.mafei.cmp.common.media.posterArtworkCandidates
import cn.mafei.cmp.common.media.prefetchArtwork
import cn.mafei.cmp.common.media.prefetchLandscapeArtwork
import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.common.ui.MaFeiAdaptiveShell
import cn.mafei.cmp.common.ui.MaFeiBackHandler
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiGlassConfirmDialog
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.common.ui.MaFeiNavItem
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.LegacyPosterPlaceholder
import cn.mafei.cmp.common.ui.LegacySortChoice
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaDisplayStyle
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaSort
import cn.mafei.cmp.core.domain.MediaSortField
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.SortDirection
import cn.mafei.cmp.feature.connect.ConnectAtmosphere
import cn.mafei.cmp.feature.connect.AccountHistoryScreen
import cn.mafei.cmp.feature.connect.ConnectScreen
import cn.mafei.cmp.feature.connect.ConnectionHistoryItem
import cn.mafei.cmp.feature.connect.ServerDiscoveryScreen
import cn.mafei.cmp.feature.connect.ServerDiscoverySuggestion
import cn.mafei.cmp.feature.connect.ServerSuggestionType
import cn.mafei.cmp.feature.connect.rememberConnectFormState
import cn.mafei.cmp.feature.chasing.ChasingBrowser
import cn.mafei.cmp.feature.chasing.ChasingScreen
import cn.mafei.cmp.feature.detail.DetailScreen
import cn.mafei.cmp.feature.detail.PersonDetailScreen
import cn.mafei.cmp.feature.detail.SeasonDetailScreen
import cn.mafei.cmp.feature.detail.legacyPlaybackResumePositionMs
import cn.mafei.cmp.feature.favorite.FavoriteScreen
import cn.mafei.cmp.feature.home.HomeHeroBackdropState
import cn.mafei.cmp.feature.home.HomeHeroWindowBackdrop
import cn.mafei.cmp.feature.home.HomeLatestByLibraryScreen
import cn.mafei.cmp.feature.home.HomeScreen
import cn.mafei.cmp.feature.home.LibraryIndexScreen
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.home.PlaybackQueue
import cn.mafei.cmp.feature.home.legacyHomeHeroDetailTarget
import cn.mafei.cmp.feature.home.legacyHomeLatestMediaName
import cn.mafei.cmp.feature.media.VideoListScreen
import cn.mafei.cmp.feature.search.SearchScreen
import cn.mafei.cmp.feature.settings.AccountSwitchItem
import cn.mafei.cmp.feature.settings.AccountSwitchScreen
import cn.mafei.cmp.feature.settings.AddAccountScreen
import cn.mafei.cmp.feature.settings.AddGroupScreen
import cn.mafei.cmp.feature.settings.AboutPrivacyScreen
import cn.mafei.cmp.feature.settings.AppearanceScreen
import cn.mafei.cmp.feature.settings.FeiniuConnectionPreference
import cn.mafei.cmp.feature.settings.FeiniuConnectionPreferenceScreen
import cn.mafei.cmp.feature.settings.MineHistoryScreen
import cn.mafei.cmp.feature.settings.MineProfile
import cn.mafei.cmp.feature.settings.MineScreen as LegacyMineScreen
import cn.mafei.cmp.feature.settings.SettingsScreen
import cn.mafei.cmp.feature.settings.UserAgreementScreen
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.ui.LiquidMiniPlayer
import cn.mafei.cmp.player.ui.MiniPlayerMedia
import cn.mafei.cmp.player.ui.NowPlayingMedia
import cn.mafei.cmp.player.ui.NowPlayingScreen
import cn.mafei.cmp.player.ui.toMiniPlayerPresentation
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.api.PlayerTrack
import cn.mafei.cmp.player.api.PlayerSeekPreviewFrame
import cn.mafei.cmp.providers.jellyfin.discovery.discoverLocalJellyfinServers
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoutePreference
import cn.mafei.cmp.session.*
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource

val LocalApplicationRuntime = staticCompositionLocalOf<ApplicationRuntime> {
    error("Application runtime is not installed")
}

/**
 * Only state that changes application-level routing/chrome.
 *
 * Player progress arrives every 250-500 ms. Collecting the complete
 * [PlayerState] in [BrowseApplication] used to recompose the whole browse
 * tree for every progress tick even though only the player surfaces consume
 * position/buffer data.
 */
private data class PlayerShellState(
    val phase: PlayerPhase = PlayerPhase.Idle,
    val resourceId: String? = null,
)

@Composable
private fun BoundNowPlayingScreen(
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
    playerSurface: @Composable (
        PlayerController,
        onBack: () -> Unit,
        onExitFullscreen: () -> Unit,
        fullscreen: Boolean,
        Modifier,
    ) -> Unit,
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
    /**
     * 本次协商的**实际结果**（媒体源 / 播放方式 / 档位表 / 生效档位）。
     * 「播放信息」里的一切都从它来 —— 不用界面自己最后点的那一下去猜。
     */
    prepared: cn.mafei.cmp.data.repositories.playback.PreparedStreamFacts? = null,
    onSelectContentMode: ((cn.mafei.cmp.player.api.PlayerContentMode) -> Unit)? = null,
    /** 全屏是显式状态（旧版 AppUIState.isFullScreen），不是从窗口横竖推出来的。 */
    fullscreen: Boolean = false,
    loadSeekPreview: (suspend (Long) -> PlayerSeekPreviewFrame?)? = null,
) {
    // Keep the 250-500 ms progress stream inside the player subtree. The
    // application shell only observes the low-frequency phase/resource pair.
    val state by controller.state.collectAsState()
    val negotiatedTracks by providerTracks.collectAsState()
    val visibleTracks = negotiatedTracks.map { track ->
        PlayerTrack(
            id = track.index.toString(),
            kind = when (track.kind) {
                cn.mafei.cmp.data.adapter_contract.PlaybackTrackKind.Audio ->
                    cn.mafei.cmp.player.api.TrackKind.Audio
                cn.mafei.cmp.data.adapter_contract.PlaybackTrackKind.Subtitle ->
                    cn.mafei.cmp.player.api.TrackKind.Subtitle
            },
            label = track.displayTitle?.takeIf(String::isNotBlank)
                ?: track.title?.takeIf(String::isNotBlank)
                ?: if (track.kind == cn.mafei.cmp.data.adapter_contract.PlaybackTrackKind.Audio) {
                    "音轨 ${track.index}"
                } else {
                    "字幕 ${track.index}"
                },
            language = track.language,
            selected = track.selected,
        )
    }.ifEmpty { state.tracks }
    // 平台亮度/音量来自 ApplicationRuntime（宿主注入）；缺省实现让两条竖向手势都不介入。
    val deviceControls = LocalApplicationRuntime.current.deviceControls
    val playbackFacts = catalogItem?.let { item ->
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
        capabilities = controller.capabilities.copy(
            trackSelection = controller.capabilities.trackSelection || negotiatedTracks.isNotEmpty(),
        ),
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
        loadSeekPreview = loadSeekPreview,
        fullscreen = fullscreen,
        onFullscreenRequest = { requested -> onFullscreenChanged(requested) },
        videoSurface = { surfaceModifier ->
            playerSurface(
                controller,
                onBack,
                { onFullscreenChanged(false) },
                fullscreen,
                surfaceModifier,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun BoundLiquidMiniPlayer(
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

@Composable
fun App(
    runtime: ApplicationRuntime,
    onThemeChanged: (Boolean) -> Unit = {},
    playerSurface: @Composable (
        PlayerController,
        onBack: () -> Unit,
        onExitFullscreen: () -> Unit,
        fullscreen: Boolean,
        Modifier,
    ) -> Unit = { _, _, _, _, modifier ->
        Box(modifier.background(Color.Black))
    },
    onPlayerScreenChanged: (Boolean) -> Unit = {},
    onPlayerFullscreenChanged: (Boolean) -> Unit = {},
    windowControls: @Composable RowScope.() -> Unit = {},
    windowDragArea: @Composable (@Composable () -> Unit) -> Unit = { content -> content() },
) {
    val sessionState by runtime.sessions.state.collectAsState()
    val scope = rememberCoroutineScope()
    var probe by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var starting by remember(runtime) { mutableStateOf(true) }
    var showStartupProgress by remember(runtime) { mutableStateOf(false) }
    var startupExiting by remember(runtime) { mutableStateOf(false) }
    var liquidGlassIntensity by remember(runtime) { mutableFloatStateOf(DefaultGlassIntensity) }
    var glassIntensitySaveJob by remember(runtime) { mutableStateOf<Job?>(null) }
    var homeCarouselVisible by remember(runtime) { mutableStateOf(true) }
    var homeCarouselSaveJob by remember(runtime) { mutableStateOf<Job?>(null) }

    LaunchedEffect(runtime) {
        liquidGlassIntensity = runtime.loadGlassIntensity()
    }

    LaunchedEffect(runtime) {
        homeCarouselVisible = runtime.loadHomeCarouselVisible()
    }

    LaunchedEffect(runtime) {
        val progress = launch {
            delay(450)
            showStartupProgress = true
        }
        try {
            runtime.start()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "旧版本数据迁移未完成，请稍后重试"
        } finally {
            progress.cancel()
            startupExiting = true
            delay(220)
            starting = false
        }
    }
    CompositionLocalProvider(LocalApplicationRuntime provides runtime) {
        if (probe) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .safeDrawingPadding()
                        .padding(horizontal = 12.dp)
                        .mafeiInteractive(onClick = { probe = false }, label = "返回映光影院"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaFeiIcon(MaFeiGlyph.Back, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("返回映光影院")
                }
                Box(Modifier.weight(1f)) { FoundationProbeScreen(onThemeChanged) }
            }
        } else {
            // Dark only. Every product page is built dark-first (light text on dark
            // atmospheres) and the login/history backgrounds are dark images, so an
            // appearance setting could only tint the surfaces and leave the pages
            // behind — the "light" option was removed rather than left half-working.
            // Hosts are still told which appearance to paint their system chrome in.
            SideEffect { onThemeChanged(true) }
            MaFeiTheme {
                val health = rememberGlassRenderHealth()
                GlassRenderingEnvironment(health, intensity = liquidGlassIntensity) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (starting) {
                            StaticAppCanvas {
                                if (showStartupProgress) PonyStartupLoading(exiting = startupExiting)
                            }
                        } else when (sessionState.status) {
                            ApplicationSessionStatus.NotStarted,
                            ApplicationSessionStatus.Restoring -> StaticAppCanvas { }

                            ApplicationSessionStatus.SignedOut -> SignedOutFlow(
                                runtime = runtime,
                                busy = busy,
                                error = error,
                                onBusyChange = { busy = it },
                                onErrorChange = { error = it },
                                windowControls = windowControls,
                                windowDragArea = windowDragArea,
                            )

                            ApplicationSessionStatus.Ready -> {
                                val session = sessionState.session
                                if (session == null) {
                                    StaticAppCanvas { SessionMessage("账号会话尚未就绪，请稍后重试") }
                                } else {
                                    key(session) {
                                        BrowseContent(
                                            runtime = runtime,
                                            session = session,
                                            busy = busy,
                                            liquidGlassIntensity = liquidGlassIntensity,
                                            onLiquidGlassIntensityChanged = { value ->
                                                liquidGlassIntensity = value
                                                glassIntensitySaveJob?.cancel()
                                                glassIntensitySaveJob = scope.launch {
                                                    delay(180)
                                                    runtime.saveGlassIntensity(value)
                                                }
                                            },
                                            homeCarouselVisible = homeCarouselVisible,
                                            onHomeCarouselVisibleChanged = { visible ->
                                                homeCarouselVisible = visible
                                                homeCarouselSaveJob?.cancel()
                                                homeCarouselSaveJob = scope.launch {
                                                    runtime.saveHomeCarouselVisible(visible)
                                                }
                                            },
                                            playerSurface = playerSurface,
                                            onPlayerScreenChanged = onPlayerScreenChanged,
                                            onPlayerFullscreenChanged = onPlayerFullscreenChanged,
                                            windowControls = windowControls,
                                            windowDragArea = windowDragArea,
                                            onLogout = {
                                                if (!busy) {
                                                    busy = true
                                                    error = null
                                                    scope.launch {
                                                        try {
                                                            runtime.sessions.logout(session)
                                                        } catch (cancelled: CancellationException) {
                                                            throw cancelled
                                                        } catch (_: Exception) {
                                                            error = "安全凭证清理未完成，请重试"
                                                        } finally {
                                                            busy = false
                                                        }
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }

                            ApplicationSessionStatus.NeedsAttention -> StaticAppCanvas {
                                SessionMessage(
                                    message = error ?: "账号恢复或保存未完成。可以重试恢复，或先完成退出再登录。",
                                    busy = busy,
                                    onRetry = { scope.launch { runtime.sessions.start() } },
                                    onCleanup = {
                                        if (!busy) {
                                            busy = true
                                            scope.launch {
                                                try {
                                                    runtime.sessions.retryCleanup()
                                                    error = null
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (_: Exception) {
                                                    error = "安全凭证清理仍未完成，请稍后重试"
                                                } finally {
                                                    busy = false
                                                }
                                            }
                                        }
                                    },
                                )
                            }

                            ApplicationSessionStatus.Closed -> StaticAppCanvas {
                                SessionMessage("应用会话已关闭，请重新启动")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Serializable
private sealed interface SignedOutRoute : NavKey

@Serializable
private data object ConnectRoute : SignedOutRoute

@Serializable
private data object AccountHistoryRoute : SignedOutRoute

@Serializable
private data object ServerDiscoveryRoute : SignedOutRoute

@Serializable
private sealed interface BrowseRoute : NavKey

@Serializable
private data object BrowseHomeRoute : BrowseRoute

@Serializable
private data object BrowseChasingRoute : BrowseRoute

@Serializable
private data object BrowseFavoriteRoute : BrowseRoute

@Serializable
private data object BrowseLibraryIndexRoute : BrowseRoute

/** The home "最近添加 / 更多" feed. This is a list of the home feed, not the library index. */
@Serializable
private data object BrowseLatestRoute : BrowseRoute

@Serializable
private data object BrowseSearchRoute : BrowseRoute

@Serializable
private data object BrowseMineRoute : BrowseRoute

@Serializable
private data object BrowseMineHistoryRoute : BrowseRoute

@Serializable
private data object BrowseAccountSwitchRoute : BrowseRoute

@Serializable
private data object BrowseAddAccountRoute : BrowseRoute

@Serializable
private data class BrowseConnectAccountRoute(
    val provider: ProviderKind,
    /** 重新登录既有账号时预填（保存的凭据已不可恢复）。 */
    val presetBaseUrl: String? = null,
    val presetUsername: String? = null,
) : BrowseRoute

@Serializable
private data object BrowseServerDiscoveryRoute : BrowseRoute

@Serializable
private data object BrowseSettingsRoute : BrowseRoute

@Serializable
private data object BrowseFeiniuConnectionPreferenceRoute : BrowseRoute

/** Reachable only once the appearance entry in `SettingsScreen` is re-enabled. */
@Serializable
private data object BrowseAppearanceRoute : BrowseRoute

@Serializable
private data object BrowseAddGroupRoute : BrowseRoute

@Serializable
private data object BrowseAboutPrivacyRoute : BrowseRoute

@Serializable
private data object BrowseUserAgreementRoute : BrowseRoute

@Serializable
private data class BrowseLibraryRoute(val collectionId: String) : BrowseRoute

/**
 * The shell selection is part of the navigation route, not of the browser's
 * mutable collection state.  A home shelf can open a detail while the last
 * selected collection is still, for example, 短剧; using that mutable state
 * for the sidebar would then highlight the wrong library.
 */
@Serializable
private enum class BrowseDetailOrigin {
    Home,
    Chasing,
    Favorite,
    Search,
    Library,
}

@Serializable
private data class BrowseDetailRoute(
    val mediaId: String,
    /** Legacy VideoDetailPage route parameter used in the secondary metadata line. */
    val mediaName: String? = null,
    val origin: BrowseDetailOrigin = BrowseDetailOrigin.Home,
    val libraryId: String? = null,
) : BrowseRoute

@Serializable
private data class BrowsePersonRoute(
    val personId: String,
    val personName: String,
    val origin: BrowseDetailOrigin = BrowseDetailOrigin.Home,
    val libraryId: String? = null,
) : BrowseRoute

@Serializable
private data class BrowseSeasonRoute(
    val seriesId: String,
    val seasonId: String,
    /** Preserved through series -> season -> episode exactly like the ArkUI route chain. */
    val mediaName: String? = null,
    val origin: BrowseDetailOrigin = BrowseDetailOrigin.Home,
    val libraryId: String? = null,
) : BrowseRoute

@Serializable
private data class BrowseEpisodeRoute(
    val seriesId: String,
    val seasonId: String,
    val episodeId: String,
    val mediaName: String? = null,
    val origin: BrowseDetailOrigin = BrowseDetailOrigin.Home,
    val libraryId: String? = null,
) : BrowseRoute

private data class BrowseShellContext(
    val destination: String,
    val libraryId: String? = null,
    val origin: BrowseDetailOrigin? = null,
)

private fun BrowseDetailOrigin.shellContext(libraryId: String?): BrowseShellContext = when (this) {
    BrowseDetailOrigin.Home -> BrowseShellContext(HomeDestination, origin = this)
    BrowseDetailOrigin.Chasing -> BrowseShellContext(ChasingDestination, origin = this)
    BrowseDetailOrigin.Favorite -> BrowseShellContext(FavoriteDestination, origin = this)
    // Search is rendered by the home content host, but has no selected sidebar
    // destination while it is open.
    BrowseDetailOrigin.Search -> BrowseShellContext("", origin = this)
    BrowseDetailOrigin.Library -> BrowseShellContext(LibraryDestination, libraryId, this)
}

private fun BrowseRoute.shellContext(): BrowseShellContext = when (this) {
    BrowseHomeRoute -> BrowseDetailOrigin.Home.shellContext(null)
    BrowseChasingRoute -> BrowseDetailOrigin.Chasing.shellContext(null)
    BrowseFavoriteRoute -> BrowseDetailOrigin.Favorite.shellContext(null)
    BrowseLatestRoute -> BrowseDetailOrigin.Home.shellContext(null)
    BrowseSearchRoute -> BrowseDetailOrigin.Search.shellContext(null)
    BrowseLibraryIndexRoute -> BrowseDetailOrigin.Library.shellContext(null)
    is BrowseLibraryRoute -> BrowseDetailOrigin.Library.shellContext(collectionId)
    is BrowseDetailRoute -> origin.shellContext(libraryId)
    is BrowsePersonRoute -> origin.shellContext(libraryId)
    is BrowseSeasonRoute -> origin.shellContext(libraryId)
    is BrowseEpisodeRoute -> origin.shellContext(libraryId)
    else -> BrowseShellContext(MineDestination)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SignedOutFlow(
    runtime: ApplicationRuntime,
    busy: Boolean,
    error: String?,
    onBusyChange: (Boolean) -> Unit,
    onErrorChange: (String?) -> Unit,
    windowControls: @Composable RowScope.() -> Unit,
    windowDragArea: @Composable (@Composable () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf<SignedOutRoute>(ConnectRoute) }
    var historyConnections by remember(runtime) { mutableStateOf(emptyList<SavedConnection>()) }
    var rememberedPasswordKeys by remember(runtime) { mutableStateOf(emptySet<String>()) }
    var historyLoading by remember(runtime) { mutableStateOf(false) }
    var historyError by remember(runtime) { mutableStateOf<String?>(null) }
    var deletingAccountKey by remember(runtime) { mutableStateOf<String?>(null) }
    var pendingDeleteAccount by remember(runtime) { mutableStateOf<ConnectionHistoryItem?>(null) }
    // 窗口背景层的源：影视墙铺在窗口层、两个面板共享，所以它在整窗里是同一面墙。
    // 建在这里是因为两个面板的 lambda 都要引用它。
    val pageBackdrop = rememberGlassBackdrop()
    val connectFormState = rememberConnectFormState()
    // Signed out means no account is in use, but the history panel must never
    // offer to delete whatever account the runtime still holds.
    val activeSelection = runtime.sessions.state.value.session?.selection
    val historyItems = historyConnections.map { connection ->
        ConnectionHistoryItem(
            key = connection.credentialKey,
            provider = connection.server.provider,
            serverId = connection.server.id,
            displayName = connection.account.displayName,
            serverName = connection.server.name,
            address = connection.server.baseUrl,
            hasRememberedPassword = connection.credentialKey in rememberedPasswordKeys,
            isCurrent = connection.selection == activeSelection,
        )
    }
    val loadHistory: () -> Unit = {
        if (!historyLoading) {
            historyLoading = true
            historyError = null
            scope.launch {
                try {
                    val connections = runtime.savedConnections()
                    val passwordKeys = buildSet {
                        for (connection in connections) {
                            if (runtime.hasSavedPassword(connection)) add(connection.credentialKey)
                        }
                    }
                    historyConnections = connections
                    rememberedPasswordKeys = passwordKeys
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    historyConnections = emptyList()
                    rememberedPasswordKeys = emptySet()
                    historyError = "历史账号读取失败，请稍后重试"
                } finally {
                    historyLoading = false
                }
            }
        }
    }

    // Deletes restorable credentials, the remembered password and the history row
    // itself. The account in use refuses, which its row already does not offer.
    val deleteAccount: (ConnectionHistoryItem) -> Unit = { item ->
        val connection = historyConnections.firstOrNull { it.credentialKey == item.key }
        if (connection == null) {
            historyError = "历史账号已失效，请刷新后重试"
        } else if (deletingAccountKey == null && !busy) {
            deletingAccountKey = item.key
            historyError = null
            scope.launch {
                try {
                    if (runtime.deleteSavedConnection(connection)) {
                        // The mutation already succeeded. Update the local source directly so
                        // deleting the last row does not start a loading state that would remove
                        // the shared dissolve surface before its animation finishes.
                        historyConnections = historyConnections.filterNot {
                            it.credentialKey == item.key
                        }
                        rememberedPasswordKeys = rememberedPasswordKeys - item.key
                    } else {
                        historyError = "该账号正在使用，无法删除"
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    historyError = "账号删除失败，请稍后重试"
                } finally {
                    deletingAccountKey = null
                }
            }
        }
    }

    val connectContent: @Composable () -> Unit = {
        ConnectScreen(
            busy = busy,
            error = error,
            onOpenHistory = {
                if (backStack.lastOrNull() != AccountHistoryRoute) {
                    backStack.add(AccountHistoryRoute)
                }
                loadHistory()
            },
            onOpenServerDiscovery = {
                if (backStack.lastOrNull() != ServerDiscoveryRoute) {
                    backStack.add(ServerDiscoveryRoute)
                }
            },
            onSubmit = { provider, address, name, password, rememberPassword ->
                if (!busy) {
                    onBusyChange(true)
                    onErrorChange(null)
                    scope.launch {
                        try {
                            runtime.connect(provider, address, name, password, rememberPassword)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            onErrorChange(connectFailureMessage(failure))
                        } finally {
                            onBusyChange(false)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            pageBackdrop = pageBackdrop,
            formState = connectFormState,
        )
    }
    val historyContent: @Composable () -> Unit = {
        AccountHistoryScreen(
            items = historyItems,
            // Keep the rendered rows visible during a post-delete refresh so the shared
            // dissolve animation is not replaced by a full-screen loading indicator.
            loading = historyLoading && historyItems.isEmpty(),
            error = historyError,
            busy = busy,
            onBack = { backStack.removeLastOrNull() },
            onRetry = loadHistory,
            onSelected = { item ->
                val connection = historyConnections.firstOrNull {
                    it.credentialKey == item.key
                }
                if (connection == null) {
                    historyError = "历史账号已失效，请刷新后重试"
                } else if (!busy) {
                    onBusyChange(true)
                    historyError = null
                    scope.launch {
                        try {
                            runtime.switchAccount(connection)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Logout deliberately removes restorable secrets,
                            // while the non-secret history entry remains. Fall
                            // back to a prefilled login form for re-authentication.
                            val savedPassword = try {
                                runtime.savedPassword(connection)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                null
                            }
                            connectFormState.provider = connection.server.provider
                            connectFormState.baseUrl = connection.server.baseUrl
                            connectFormState.username = connection.account.displayName
                            connectFormState.password = savedPassword.orEmpty()
                            connectFormState.rememberPassword = savedPassword != null
                            historyError = null
                            onErrorChange(null)
                            backStack.removeLastOrNull()
                        } finally {
                            onBusyChange(false)
                        }
                    }
                }
            },
            deletingKey = deletingAccountKey,
            onRequestDelete = { pendingDeleteAccount = it },
            modifier = Modifier.fillMaxSize(),
            pageBackdrop = pageBackdrop,
        )
    }
    val discoveryContent: @Composable () -> Unit = {
        ServerDiscoveryScreen(
            loadSavedServers = { savedJellyfinServerSuggestions(runtime) },
            discoverServers = { discoveredJellyfinServerSuggestions() },
            onBack = { backStack.removeLastOrNull() },
            onSelectServer = { address ->
                connectFormState.provider = ProviderKind.Jellyfin
                connectFormState.baseUrl = address
                connectFormState.requestServerAddressFocus()
                backStack.removeLastOrNull()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fontScale = LocalDensity.current.fontScale
        val windowFeatures = LocalMaFeiWindowFeatures.current
        val adaptiveWindow = remember(maxWidth, maxHeight, fontScale, windowFeatures) {
            mafeiAdaptiveWindow(
                MaFeiWindowEnvironment(
                    widthDp = maxWidth.value,
                    heightDp = maxHeight.value,
                    fontScale = fontScale,
                    safeInsets = windowFeatures.safeInsets,
                    occlusions = windowFeatures.occlusions,
                    foldPosture = windowFeatures.foldPosture,
                    displayRole = windowFeatures.displayRole,
                ),
            )
        }
        val supportsTwoPane = adaptiveWindow.contentBounds.width >= 900f
        // The window's own capture source. The confirmation panel below samples it,
        // so it is captured on the pane host and never on the panel itself.
        val shellBackdrop = rememberGlassBackdrop()
        // 窗口层 Box 会遮蔽 BoxWithConstraintsScope，先把宽度取出来。
        val paneHostWidth = maxWidth

        // 免登录流程（连接页 / 账号历史）**不走外壳**，但里面的 `MaFeiActionBar` 读的是
        // `LocalMaFeiSafeArea` —— 外壳之外那个组合本地的默认值是全 0，于是标题栏会顶进
        // 状态栏里（而列表用的是 `currentMaFeiSafeArea()`，所以只有标题栏没适配）。这里把
        // 真实窗口安全区补上，两条取法就一致了。
        CompositionLocalProvider(LocalMaFeiSafeArea provides currentMaFeiSafeArea()) {
        // 影视墙铺在窗口层：两个面板共享同一个源 —— 墙在整窗里是连续的（各栏各画一遍
        // 会在分界线上拼出两套格子），而且源不随面板宽度动画变化，模糊不会被拉花。
        Box(Modifier.fillMaxSize().captureGlassBackdrop(shellBackdrop)) {
            Box(Modifier.fillMaxSize().captureGlassBackdrop(pageBackdrop)) {
                ConnectAtmosphere()
            }
            if (supportsTwoPane && backStack.lastOrNull() != ServerDiscoveryRoute) {
                val historyOpen = backStack.lastOrNull() == AccountHistoryRoute
                val historyConnectPaneWidth = (paneHostWidth * .43f).coerceIn(420.dp, 560.dp)
                val connectPaneWidth by animateDpAsState(
                    targetValue = if (historyOpen) historyConnectPaneWidth else paneHostWidth,
                    animationSpec = tween(durationMillis = 240),
                    label = "signed out connect pane width",
                )
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(connectPaneWidth).fillMaxHeight()) {
                        connectContent()
                    }
                    AnimatedVisibility(
                        visible = historyOpen,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        enter = fadeIn(tween(180, delayMillis = 60)) +
                            slideInHorizontally(tween(240)) { it / 8 },
                        exit = fadeOut(tween(120)) +
                            slideOutHorizontally(tween(180)) { it / 10 },
                    ) {
                        Row(Modifier.fillMaxSize()) {
                            Box(
                                Modifier.width(1.dp).fillMaxHeight()
                                    .background(Color.White.copy(alpha = .12f)),
                            )
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                historyContent()
                            }
                        }
                    }
                }
            } else {
                NavDisplay(
                    backStack = backStack,
                    onBack = {
                        if (backStack.size > 1) backStack.removeLastOrNull()
                    },
                    transitionSpec = {
                        (slideInHorizontally(tween(durationMillis = 320)) { it } +
                            fadeIn(tween(durationMillis = 180))) togetherWith
                            (slideOutHorizontally(tween(durationMillis = 320)) { -it / 5 } +
                                fadeOut(tween(durationMillis = 180)))
                    },
                    popTransitionSpec = {
                        (slideInHorizontally(tween(durationMillis = 300)) { -it / 5 } +
                            fadeIn(tween(durationMillis = 180))) togetherWith
                            (slideOutHorizontally(tween(durationMillis = 300)) { it } +
                                fadeOut(tween(durationMillis = 180)))
                    },
                    predictivePopTransitionSpec = {
                        (slideInHorizontally(tween(durationMillis = 300)) { -it / 5 } +
                            fadeIn(tween(durationMillis = 180))) togetherWith
                            (slideOutHorizontally(tween(durationMillis = 300)) { it } +
                                fadeOut(tween(durationMillis = 180)))
                    },
                    entryProvider = { route ->
                        when (route) {
                            ConnectRoute -> NavEntry(route) { connectContent() }
                            AccountHistoryRoute -> NavEntry(route) { historyContent() }
                            ServerDiscoveryRoute -> NavEntry(route) { discoveryContent() }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        pendingDeleteAccount?.let { item ->
            MaFeiGlassConfirmDialog(
                title = "删除账号",
                content = "确定要删除「${item.displayName}」这个账号吗？",
                confirmText = "删除",
                dismissText = "取消",
                destructive = true,
                backdrop = shellBackdrop,
                onDismiss = { pendingDeleteAccount = null },
                onConfirm = {
                    pendingDeleteAccount = null
                    deleteAccount(item)
                },
            )
        }
        // An undecorated Mac window also needs a close/minimize/maximize hit
        // area before an account is connected. The signed-in shell places the
        // same controls in its sidebar header; the signed-out flow keeps only
        // the compact chrome row so the login content remains unchanged.
        Box(
            Modifier.align(Alignment.TopStart)
                .padding(top = currentMaFeiSafeArea().top + 10.dp, start = 12.dp),
        ) {
            windowDragArea {
                Row(
                    Modifier.height(44.dp).width(74.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    windowControls()
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun BrowseContent(
    runtime: ApplicationRuntime,
    session: ApplicationSession,
    busy: Boolean,
    liquidGlassIntensity: Float,
    onLiquidGlassIntensityChanged: (Float) -> Unit,
    homeCarouselVisible: Boolean,
    onHomeCarouselVisibleChanged: (Boolean) -> Unit,
    playerSurface: @Composable (
        PlayerController,
        onBack: () -> Unit,
        onExitFullscreen: () -> Unit,
        fullscreen: Boolean,
        Modifier,
    ) -> Unit,
    onPlayerScreenChanged: (Boolean) -> Unit,
    onPlayerFullscreenChanged: (Boolean) -> Unit,
    windowControls: @Composable RowScope.() -> Unit,
    windowDragArea: @Composable (@Composable () -> Unit) -> Unit,
    onLogout: () -> Unit,
) {
    // Navigation 3 keeps an existing NavEntry composed while its parent route
    // remains on the stack. Keep the setting in a stable State holder so the
    // already-composed SettingsScreen observes drag/click updates immediately;
    // passing the primitive directly would leave the entry showing the value
    // captured when it was first pushed and only appear updated after leaving
    // and re-entering the page.
    val currentLiquidGlassIntensity = rememberUpdatedState(liquidGlassIntensity)
    val currentLiquidGlassIntensityChanged = rememberUpdatedState(onLiquidGlassIntensityChanged)
    val browser = remember(session) {
        MediaBrowser(
            repository = runtime.sessions.media,
            session = session.media,
            capabilities = session.binding.adapter.capabilities,
            playbackPreferences = runtime.playbackPreferences,
        )
    }
    val state by browser.state.collectAsState()
    val chasingBrowser = remember(session) {
        ChasingBrowser(
            repository = runtime.sessions.media,
            session = session.media,
            capabilities = session.binding.adapter.capabilities,
            store = runtime.chasingSeriesStore,
            nowEpochMs = SystemEpochClock::nowEpochMs,
        )
    }
    val chasingState by chasingBrowser.state.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    // 管理员媒体删除：确认弹窗 + 待删集合。入口由 browser 探测的服务器策略决定。
    var pendingDeleteMedia by remember(session) { mutableStateOf<List<MediaId>?>(null) }
    val deleteConfirmBackdrop = rememberGlassBackdrop()
    // 列表页多选状态（legacy selectionMode）：切库/离开页面时自动退出多选。
    var librarySelectedIds by remember(session) { mutableStateOf(emptySet<String>()) }
    val librarySelectionMode = librarySelectedIds.isNotEmpty()
    LaunchedEffect(state.selectedCollection) {
        librarySelectedIds = emptySet()
    }
    // Home always opens at its hero. Do not restore a process-saved grid
    // position; route changes are handled explicitly by selectDestination.
    val homeGridState = remember(session) { LazyGridState() }
    val chasingListState = rememberLazyListState()
    val favoriteGridState = rememberLazyGridState()
    val libraryGridState = rememberLazyGridState()
    val latestGridState = rememberLazyGridState()
    val searchGridState = rememberLazyGridState()
    var searchJob by remember(session) { mutableStateOf<Job?>(null) }
    var searchHistories by remember(session) { mutableStateOf(emptyList<String>()) }
    var accountConnections by remember(session) { mutableStateOf(emptyList<SavedConnection>()) }
    var accountsLoading by remember(session) { mutableStateOf(false) }
    var accountsError by remember(session) { mutableStateOf<String?>(null) }
    var switchingAccountKey by remember(session) { mutableStateOf<String?>(null) }
    var deletingAccountKey by remember(session) { mutableStateOf<String?>(null) }
    var accountConnectBusy by remember(session) { mutableStateOf(false) }
    var accountConnectError by remember(session) { mutableStateOf<String?>(null) }
    var pendingDiscoveredServerAddress by remember(session) { mutableStateOf<String?>(null) }
    var feiniuConnectionPreference by remember(session) {
        mutableStateOf(FeiniuConnectionPreference.Speed)
    }
    var feiniuConnectionPreferenceError by remember(session) { mutableStateOf<String?>(null) }
    var feiniuConnectionPreferenceSaving by remember(session) { mutableStateOf(false) }
    val artworkCache = remember(session) { ArtworkMemoryCache() }
    val fetchArtwork: suspend (MediaImage, Int) -> ByteArray? = remember(session) {
        { image, targetWidthPx -> runtime.loadArtwork(session, image, targetWidthPx) }
    }
    val playback = remember(session) { runtime.playback(session) }
    val playerShellState = if (playback == null) {
        PlayerShellState()
    } else {
        val shellState = remember(playback) {
            playback.controller.state
                .map { PlayerShellState(phase = it.phase, resourceId = it.resourceId) }
                .distinctUntilChanged()
        }
        shellState.collectAsState(
            initial = playback.controller.state.value.let {
                PlayerShellState(phase = it.phase, resourceId = it.resourceId)
            },
        ).value
    }
    var playingMedia by remember(session) { mutableStateOf<MediaItem?>(null) }
    // 全屏是页面状态而不是几何推断：横屏平板宽 > 高，按窗口比例判「已是全屏」会让返回键
    // 永远停在「退出全屏」那一步（平板退出全屏也不会变竖屏），结果退不出播放页。
    var playerFullscreen by remember(session) { mutableStateOf(false) }
    var playbackBusy by remember(session) { mutableStateOf(false) }
    var playbackError by remember(session) { mutableStateOf<String?>(null) }
    // 播放队列（旧版 QueueManager 的等价物）：由起播时的浏览上下文构造，播放页的
    // 「选集」「下一集」和播完自动衔接都读它。
    var playQueue by remember(session) { mutableStateOf<PlaybackQueue?>(null) }
    // 清晰度有「请求」和「实际」两个值，故意分开：
    //  - requestedBitrate 是我们**要求**的上限（本次播放会话的用户意图，会带给下一集）；
    //  - 实际生效的档位只信协商结果（prepared.appliedBitrate）。
    // 界面只呈现后者，所以协商没兑现时不会出现「标签换了、其实没换」。
    var requestedBitrate by remember(session) { mutableStateOf<Int?>(null) }
    val preparedStream = playback?.prepared?.collectAsState()?.value
    val qualityOptions = remember(playingMedia, preparedStream) {
        val facts = preparedStream ?: return@remember emptyList()
        val applied = facts.appliedBitrate
        val providerLadder = facts.qualities
        when {
            // provider 自己给了档位表（fnOS 的上游直链）：原样呈现，不造表。
            !providerLadder.isNullOrEmpty() -> cn.mafei.cmp.player.ui.PlayerQualityOptions.fromProvider(
                renditions = providerLadder.map { quality -> quality.label to quality.bitrate },
                appliedBitrate = applied,
            )
            // 空表 = 这条流没有别的档位（飞牛 range 原画直连）：**不给菜单**，
            // 否则就是旧版那个 bug —— 点了档位却还是同一个文件。
            providerLadder != null -> emptyList()
            // null = provider 按需重编，可以自己造档位表（Jellyfin）。
            else -> {
                val videoStream = playingMedia?.mediaSources
                    ?.firstOrNull { source -> source.videoStream != null }
                    ?.videoStream
                cn.mafei.cmp.player.ui.PlayerQualityOptions.applicable(
                    videoWidthPx = videoStream?.widthPx,
                    videoHeightPx = videoStream?.heightPx,
                    appliedBitrate = applied,
                )
            }
        }
    }
    var playerExpanded by remember(
        session.selection.serverId.value,
        session.selection.accountId.value,
    ) { mutableStateOf(false) }
    DisposableEffect(playerExpanded, onPlayerScreenChanged) {
        onPlayerScreenChanged(playerExpanded)
        onDispose {
            if (playerExpanded) onPlayerScreenChanged(false)
        }
    }
    DisposableEffect(playerExpanded, onPlayerFullscreenChanged) {
        if (!playerExpanded) {
            playerFullscreen = false
            // 清晰度是**本次播放会话**的选择，不是全局偏好：看完退出就回到自动。
            requestedBitrate = null
        }
        onDispose { onPlayerFullscreenChanged(false) }
    }
    var pendingSeekPosition by remember(session) { mutableStateOf<Long?>(null) }
    // Apple presents the account surface above the current document instead of
    // navigating away from it. "我的" therefore opens a modal sheet; the browse
    // route, its scroll position and its loaded data all stay untouched behind it.
    var minePresented by remember(session.selection.serverId.value, session.selection.accountId.value) {
        mutableStateOf(false)
    }
    val browseBackStack = remember(session.selection.serverId.value, session.selection.accountId.value) {
        mutableStateListOf<BrowseRoute>(BrowseHomeRoute)
    }
    val currentRoute = browseBackStack.lastOrNull() ?: BrowseHomeRoute
    // Keep the chrome selection derived from the route that opened the page.
    // `state.selectedCollection` is provider/browser state and may still point
    // at the last library even when the user opened a Home shelf item.
    val shellContext = currentRoute.shellContext()
    val libraryTabId = rememberLibraryTabId(
        currentLibraryId = shellContext.libraryId,
        serverId = session.selection.serverId.value,
        accountId = session.selection.accountId.value,
    )
    // 首页轮播背景的窗口级状态：背景画在 shell 窗口画布上（全窗、与侧栏让位
    // 无关），HomeScreen 只往里喂实时状态（items/pager/滚动跟随参数）。
    val heroBackdrop = remember { HomeHeroBackdropState() }
    // 「我的」有独立的导航容器：回退栈只装「我的」自己的页面，从我的进入的二级页不会压到
    // 主浏览栈上。容器在大屏以居中毛玻璃模态呈现、小屏以全屏页呈现，切换形态时这个栈原样
    // 保留（它是状态而不是渲染树的一部分），所以用户不会觉得断片。
    val mineBackStack = remember(session.selection.serverId.value, session.selection.accountId.value) {
        mutableStateListOf<BrowseRoute>(BrowseMineRoute)
    }
    val destination = shellContext.destination
    // `destination` already carries Search's intentionally empty selection;
    // keep a named value because the shell and the content host use it for
    // different decisions below.
    val shellDestination = destination
    val chromeCompactProgress = remember(
        destination,
        homeGridState,
        chasingListState,
        favoriteGridState,
        libraryGridState,
    ) {
        derivedStateOf {
            if (destination == ChasingDestination) {
                if (chasingListState.firstVisibleItemIndex > 0) 1f
                else (chasingListState.firstVisibleItemScrollOffset / 160f).coerceIn(0f, 1f)
            } else {
                val activeGrid = when (destination) {
                    HomeDestination -> homeGridState
                    FavoriteDestination -> favoriteGridState
                    LibraryDestination -> libraryGridState
                    else -> return@derivedStateOf 0f
                }
                if (activeGrid.firstVisibleItemIndex > 0) 1f
                else (activeGrid.firstVisibleItemScrollOffset / 160f).coerceIn(0f, 1f)
            }
        }
    }

    LaunchedEffect(browser) { browser.start() }
    LaunchedEffect(runtime, session) {
        searchHistories = try {
            runtime.searchHistory()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }
    LaunchedEffect(runtime, session.selection) {
        if (session.selection.provider == ProviderKind.Feiniu) {
            feiniuConnectionPreference = runtime.loadFeiniuRoutePreference().toPresentationPreference()
        }
    }
    LaunchedEffect(state.selectedCollection) {
        libraryGridState.scrollToItem(0)
    }
    LaunchedEffect(currentRoute) {
        if (currentRoute == BrowseFavoriteRoute || currentRoute == BrowseLibraryIndexRoute) {
            browser.refreshFavorites()
        }
        if (currentRoute == BrowseChasingRoute) chasingBrowser.refresh()
    }
    // Legacy MineTab refreshes its independent resume + completed preview every
    // time the account surface becomes visible; it never borrows HomeFeed.resume.
    LaunchedEffect(minePresented) {
        if (minePresented) browser.loadHistoryPreview()
    }
    // 独立播放历史：该页面已经迁入「我的」自己的回退栈，不能再监听主浏览路由。
    // 否则从账号浮层进入时 currentRoute 仍是首页/媒体库，只会留下 Mine 预览数据。
    LaunchedEffect(mineBackStack.lastOrNull()) {
        if (mineBackStack.lastOrNull() == BrowseMineHistoryRoute) browser.loadHistory()
        librarySelectedIds = emptySet()
    }
    // 删除可用性只探测一次：服务器用户策略决定是否出现删除入口。
    LaunchedEffect(browser) {
        browser.refreshDeleteAvailability()
        browser.refreshAdministratorStatus()
    }
    LaunchedEffect(state.detailId) { playbackError = null }
    LaunchedEffect(playerShellState.phase) {
        if (playerShellState.phase == PlayerPhase.Error) playbackError = "播放失败，请重试"
        if (!playbackBusy &&
            (playerShellState.phase == PlayerPhase.Idle || playerShellState.phase == PlayerPhase.Released)
        ) {
            playerExpanded = false
        }
    }
    LaunchedEffect(pendingSeekPosition, playback) {
        val target = pendingSeekPosition ?: return@LaunchedEffect
        delay(90)
        try {
            playback?.controller?.seek(target)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            playbackError = "跳转播放位置失败，请重试"
        } finally {
            if (pendingSeekPosition == target) pendingSeekPosition = null
        }
    }
    val loadAccounts: () -> Unit = {
        if (!accountsLoading) {
            accountsLoading = true
            accountsError = null
            scope.launch {
                try {
                    accountConnections = runtime.savedConnections()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    accountConnections = emptyList()
                    accountsError = "加载失败"
                } finally {
                    accountsLoading = false
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fontScale = LocalDensity.current.fontScale
        val windowFeatures = LocalMaFeiWindowFeatures.current
        val layout = remember(maxWidth, maxHeight, fontScale, windowFeatures) {
            val adaptiveWindow = mafeiAdaptiveWindow(
                MaFeiWindowEnvironment(
                    widthDp = maxWidth.value,
                    heightDp = maxHeight.value,
                    fontScale = fontScale,
                    safeInsets = windowFeatures.safeInsets,
                    occlusions = windowFeatures.occlusions,
                    foldPosture = windowFeatures.foldPosture,
                    displayRole = windowFeatures.displayRole,
                ),
            )
            mafeiLayout(adaptiveWindow).let { base ->
                // The desktop host owns a minimum window size and therefore
                // must not fall back to the tablet drawer when the user
                // resizes the window within the supported desktop range.
                // Content density still follows the actual width below.
                if (windowFeatures.desktopWindow) {
                    base.copy(
                        family = MaFeiLayoutFamily.Desktop,
                        navigation = NavigationPlacement.Sidebar,
                        navigationWidthDp = MaFeiPersistentSidebarWidthDp.value,
                    )
                } else {
                    base
                }
            }
        }
        LaunchedEffect(currentRoute, state.collections, layout.isPhoneNavigation) {
            if (currentRoute == BrowseLatestRoute && !layout.isPhoneNavigation) {
                state.collections.forEach { collection ->
                    launch { browser.loadHomeLatest(collection.id) }
                }
            }
        }
        val showingMediaDetail = currentRoute is BrowseDetailRoute ||
            currentRoute is BrowseSeasonRoute || currentRoute is BrowseEpisodeRoute
        val showingPersonDetail = currentRoute is BrowsePersonRoute
        val showingDetail = showingMediaDetail || showingPersonDetail
        // 「我的」的二级页现在住在它自己的回退栈里，不再出现在 currentRoute 上。
        // A concrete media library is a pushed secondary page, not another
        // bottom-tab root.  The legacy ArkUI route uses VideoListPage as a
        // NavDestination after selecting a library, so the bottom navigation
        // must leave the scene entirely while that list is visible.  Keeping
        // it mounted made the list appear underneath the dock; adding more
        // scroll padding could only mask that routing error.
        val showingFullPageRoute = showingDetail ||
            currentRoute == BrowseLatestRoute ||
            currentRoute == BrowseSearchRoute ||
            currentRoute == BrowseFavoriteRoute ||
            currentRoute is BrowseLibraryRoute
        // 「我的」容器的两种形态：小屏（手机）全屏铺开、看起来就是普通页面跳转；其余尺寸
        // 用居中的毛玻璃模态。形态只由窗口决定，容器自己的回退栈不受影响。
        // Phone owns a full-screen Mine stack; tablet/desktop keep the root
        // shell visible while child content changes in the content stack.
        val mineFullScreen = layout.isPhoneNavigation
        val routedSeason = (currentRoute as? BrowseSeasonRoute)?.let { route ->
            state.detailSeasons.firstOrNull { it.id.value == route.seasonId }
        }
        val routedEpisode = (currentRoute as? BrowseEpisodeRoute)?.let { route ->
            state.detailEpisodes.firstOrNull { it.id.value == route.episodeId }
        }
        val ambientItem = when {
            routedEpisode != null -> routedEpisode
            routedSeason != null -> routedSeason
            showingMediaDetail -> state.detail ?: state.items.firstOrNull { it.id == state.detailId }
            else -> null
        }
        val destinations = remember {
            buildList {
                add(MaFeiNavItem(HomeDestination, "首页", MaFeiGlyph.Home))
                // 追剧页的产品结构和交互仍在设计中，暂不向用户暴露入口。
                // 保留 ChasingBrowser、路由和数据模型，待页面方案冻结后只需打开此开关；
                // 首页的「继续观看」仍属于播放主链路，不受这个入口开关影响。
                if (ShowChasingDestination) {
                    add(MaFeiNavItem(ChasingDestination, "追剧", MaFeiGlyph.History))
                }
                add(MaFeiNavItem(LibraryDestination, "媒体库", MaFeiGlyph.Library))
                add(MaFeiNavItem(MineDestination, "我的", MaFeiGlyph.Account))
            }
        }
        // 收藏 is a content entry in the full sidebar, but it is grouped into
        // 媒体库 on the phone bottom bar. Keep that distinction explicit
        // instead of reintroducing a fourth bottom tab.
        val sidebarDestinations = remember(destinations) {
            destinations.toMutableList().apply {
                add(1, MaFeiNavItem(FavoriteDestination, "收藏", MaFeiGlyph.Favorite))
            }
        }
        val libraries = state.collections.map { collection ->
            MaFeiNavItem(collection.id.value, collection.name, MaFeiGlyph.Library)
        }
        // The collapsed large-screen bar follows the iPadOS-style shell:
        // it always keeps Home and Favorite. A third text tab is added only
        // after a concrete secondary library has been selected; the generic
        // library index is intentionally not duplicated in this bar.
        val topTabDestinations = buildList {
            destinations.firstOrNull { it.key == HomeDestination }?.let(::add)
            if (ShowChasingDestination) {
                destinations.firstOrNull { it.key == ChasingDestination }?.let(::add)
            }
            sidebarDestinations.firstOrNull { it.key == FavoriteDestination }?.let(::add)
            if (libraryTabId != null) {
                libraries.firstOrNull { it.key == libraryTabId }?.let(::add)
            }
        }
        val topTabSelectedDestination = when {
            currentRoute is BrowseLibraryRoute -> currentRoute.collectionId
            shellContext.libraryId != null -> shellContext.libraryId
            destination == LibraryDestination -> ""
            else -> shellDestination
        }
        val selectDestination: (String) -> Unit = { next ->
            if (next == MineDestination) {
                // 「我的」是一个带独立导航的容器，不是一个普通页面：下层的浏览路由保持原样
                // （回来还是原来那页、原来那个滚动位置）。每次从标签栏进来都回到根页。
                mineBackStack.clear()
                mineBackStack += BrowseMineRoute
                minePresented = true
            } else {
                // 小屏上容器是全屏铺开的、标签栏仍然可见，所以切到别的标签必须把它收起来，
                // 否则会盖在上面走不掉。大屏是模态（有遮罩），这条只是顺带保持一致。
                minePresented = false
                if (showingPersonDetail) browser.clearPerson()
                if (state.detailId != null) browser.back()
                if (next == HomeDestination) {
                    scope.launch { homeGridState.scrollToItem(0) }
                }
                val selectedLibrary = libraries.firstOrNull { it.key == next }
                browseBackStack.clear()
                browseBackStack += when {
                    selectedLibrary != null -> {
                        state.collections.firstOrNull { it.id.value == selectedLibrary.key }?.let { collection ->
                            if (collection.id != state.selectedCollection) {
                                scope.launch { browser.select(collection.id) }
                            }
                        }
                        BrowseLibraryRoute(selectedLibrary.key)
                    }
                    next == HomeDestination -> BrowseHomeRoute
                    next == ChasingDestination -> BrowseChasingRoute
                    next == FavoriteDestination -> BrowseFavoriteRoute
                    else -> BrowseLibraryIndexRoute
                }
            }
        }
        val startPlayback: (MediaItem) -> Unit = { item ->
            if (playback != null && !playbackBusy) {
                playbackBusy = true
                playbackError = null
                playingMedia = item
                playerExpanded = true
                val existing = playQueue?.takeIf { queue -> queue.items.any { it.id == item.id } }
                val positionedQueue = existing?.movedTo(item.id)
                // 播放器先拿到一个立即可用的队列；首页缺少季列表时，出画后再异步扩充。
                playQueue = positionedQueue ?: PlaybackQueue.forItem(item, emptyList())
                scope.launch {
                    var started = false
                    try {
                        // 详情页上选过的版本/音轨/字幕只对当前详情条目生效。
                        val generation = playback.play(
                            itemId = item.id,
                            resumePositionMs = legacyPlaybackResumePositionMs(item),
                            options = browser.playbackOptionsFor(item)
                                .copy(maxStreamingBitrate = requestedBitrate),
                        )
                        if (generation == null) {
                            playbackError = "当前媒体暂时无法播放"
                        } else {
                            started = true
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        println("[MaFeiPlayback] prepare failed: $failure")
                        playbackError = "播放准备失败，请重试"
                    } finally {
                        playbackBusy = false
                    }
                    // 旧版也是在播放器完成 load 后扩充剧集队列。网络慢或失败都不阻塞出画；
                    // 已有队列（上一集/下一集/选集）则直接复用，绝不重复拉整季。
                    if (started && positionedQueue == null && item.mediaType == MediaType.Episode) {
                        val expanded = browser.playbackQueueFor(item)
                        if (playingMedia?.id == item.id && playerExpanded) {
                            playQueue = expanded
                        }
                    }
                }
            }
        }
        val playNextEpisode: () -> Unit = {
            playQueue?.next()?.let { next ->
                playQueue = next
                startPlayback(next.current)
            }
        }
        val playPreviousEpisode: () -> Unit = {
            playQueue?.previous()?.let { previous ->
                playQueue = previous
                startPlayback(previous.current)
            }
        }
        val playEpisodeAt: (Int) -> Unit = { index ->
            playQueue?.select(index)?.let { selected ->
                playQueue = selected
                startPlayback(selected.current)
            }
        }
        // 清晰度：0 = 自动（恢复直连），其余请求该码率的上限。
        // 只有协商真的产出了新流才提交请求值 —— 失败时标签继续跟随实际结果（自动/原档），
        // 下一集也不会把这个没兑现的上限带过去。
        val selectQuality: (Int) -> Unit = { bitrate ->
            val ceiling = bitrate.takeIf { it > 0 }
            // 已经是这个档：不重复协商、不重复起播（旧版同样直接返回）。
            if (ceiling != requestedBitrate) scope.launch {
                try {
                    if (playback?.selectQuality(ceiling) == null) {
                        // 协商没产出新流（被拒 / 被后续请求取代）：请求值不提交，
                        // 界面继续显示实际在播的那一档。
                        playbackError = "当前清晰度暂时无法切换，已保持原清晰度"
                    } else {
                        requestedBitrate = ceiling
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    println("[MaFeiPlayback] quality switch failed: $failure")
                    playbackError = "切换清晰度失败，请重试"
                }
            }
        }
        // 播完自动衔接下一集（旧版队列的连续播放）。phase 由「播放中」变为
        // 「已结束」时 effect 重启一次，因此只会自动前进一集。
        LaunchedEffect(playerShellState.phase) {
            if (playerShellState.phase == PlayerPhase.Ended) playNextEpisode()
        }
        val openSearch: () -> Unit = {
            if (browseBackStack.lastOrNull() != BrowseSearchRoute) {
                browser.clearSearch()
                browseBackStack += BrowseSearchRoute
            }
        }
        val openLatest: () -> Unit = {
            if (browseBackStack.lastOrNull() != BrowseLatestRoute) {
                browseBackStack += BrowseLatestRoute
            }
        }
        // One source for the account surface, shared by the modal sheet and the
        // (now unreachable from the sidebar) legacy route. Secondary entries close
        // the sheet first so the pushed page is not hidden behind the modal.
        val mineContent: @Composable (Modifier) -> Unit = { mineModifier ->
            // Navigation 3 keeps the outgoing entry composed during a transition.
            // Read the route at event time so a late pointer delivered to that
            // stale entry cannot mutate the active session.
            fun mineRootAction(action: () -> Unit): () -> Unit = {
                if (minePresented && mineBackStack.lastOrNull() == BrowseMineRoute) action()
            }
            fun mineRootMediaAction(action: (MediaItem) -> Unit): (MediaItem) -> Unit = { item ->
                if (minePresented && mineBackStack.lastOrNull() == BrowseMineRoute) action(item)
            }
            LegacyMineScreen(
                profile = MineProfile(
                    userName = session.connection.account.displayName,
                    serverName = legacyVisibleServerName(session.connection),
                    serverVersion = session.connection.server.serverVersion.orEmpty(),
                    connectionDescription = legacyConnectionDescription(session.connection),
                    policy = if (state.administrator == true) "管理员" else "普通用户",
                ),
                historyItems = state.historyItems,
                historyLoading = state.historyLoading && state.historyItems.isEmpty(),
                historyError = state.historyError,
                layout = layout,
                busy = busy,
                onHistoryItem = mineRootMediaAction(startPlayback),
                onHistoryRetry = mineRootAction { scope.launch { browser.loadHistoryPreview() } },
                // 「我的」里的二级页推到**容器自己的**回退栈上，容器不关闭 —— 模态 / 全屏
                // 两种呈现共用同一条栈。
                onHistoryMore = {
                    if (minePresented && mineBackStack.lastOrNull() == BrowseMineRoute) {
                        mineBackStack += BrowseMineHistoryRoute
                    }
                },
                onSwitchAccount = mineRootAction {
                    loadAccounts()
                    if (mineBackStack.lastOrNull() != BrowseAccountSwitchRoute) {
                        mineBackStack += BrowseAccountSwitchRoute
                    }
                },
                onSettings = mineRootAction {
                    if (mineBackStack.lastOrNull() != BrowseSettingsRoute) {
                        mineBackStack += BrowseSettingsRoute
                    }
                },
                onPrivacy = mineRootAction { uriHandler.openUri(PRIVACY_AGREEMENT_URL) },
                onAddGroup = mineRootAction {
                    if (mineBackStack.lastOrNull() != BrowseAddGroupRoute) {
                        mineBackStack += BrowseAddGroupRoute
                    }
                },
                onLogout = {
                    if (minePresented && mineBackStack.lastOrNull() == BrowseMineRoute) {
                        minePresented = false
                        onLogout()
                    }
                },
                modifier = mineModifier,
                avatar = session.connection.account.avatarImage?.let { image ->
                    { avatarModifier ->
                        MediaArtwork(
                            image = image,
                            title = session.connection.account.displayName,
                            cache = artworkCache,
                            fetch = fetchArtwork,
                            modifier = avatarModifier,
                        )
                    }
                },
                artwork = { item, itemModifier ->
                    MediaArtwork(item.preferredPoster(), item.title, artworkCache, fetchArtwork, itemModifier)
                },
            )
        }
        val popMineRoute: () -> Unit = {
            when (mineBackStack.lastOrNull()) {
                is BrowseConnectAccountRoute -> accountConnectError = null
                else -> Unit
            }
            if (mineBackStack.size > 1) mineBackStack.removeLastOrNull()
        }
        val mineNavContent: @Composable (Modifier) -> Unit = { mineHostModifier ->
            // Registered before NavDisplay so child routes/sheets handle Back
            // first; at the account root it closes the whole account surface.
            cn.mafei.cmp.common.ui.MaFeiBackHandler(enabled = minePresented) {
                minePresented = false
            }
            NavDisplay(
                backStack = mineBackStack,
                onBack = { popMineRoute() },
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInHorizontally(tween(260)) { it / 8 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -it / 8 })
                },
                popTransitionSpec = {
                    (fadeIn(tween(180)) + slideInHorizontally(tween(260)) { -it / 8 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 8 })
                },
                predictivePopTransitionSpec = {
                    (fadeIn(tween(180)) + slideInHorizontally(tween(260)) { -it / 8 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 8 })
                },
                entryProvider = { route ->
                    when (route) {
                            BrowseMineRoute -> NavEntry(route) {
                                // 不给它加标题栏：旧版「我的」作为底部标签时顶部是**空的**
                                // （只留 `safeTop + ACTION_BAR_HEIGHT` 的占位），标题栏只在
                                // 「我的」被当页面推出来（fromHome）时才出现。页面自己按同样的
                                // 规则留位，见 MineScreen.destinationOwnsTitleBar。
                                mineContent(Modifier.fillMaxSize())
                            }
                            BrowseMineHistoryRoute -> NavEntry(route) {
                                MineHistoryScreen(
                                    items = state.historyItems,
                                    loading = state.historyLoading && state.historyItems.isEmpty(),
                                    error = state.historyError,
                                    layout = layout,
                                    onBack = popMineRoute,
                                    onRetry = { scope.launch { browser.loadHistory() } },
                                    onItem = startPlayback,
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
                            BrowseAccountSwitchRoute -> NavEntry(route) {
                                val accountItems = accountConnections.map { connection ->
                                    AccountSwitchItem(
                                        key = connection.credentialKey,
                                        provider = connection.server.provider,
                                        serverId = connection.server.id,
                                        serverName = connection.server.name,
                                        displayName = connection.account.displayName,
                                        address = connection.server.baseUrl,
                                        lastUsedEpochMs = connection.account.lastActiveEpochMs,
                                        isCurrent = connection.selection == session.selection,
                                    )
                                }
                                AccountSwitchScreen(
                                    items = accountItems,
                                    loading = accountsLoading,
                                    busyKey = switchingAccountKey,
                                    deletingKey = deletingAccountKey,
                                    error = accountsError,
                                    onBack = popMineRoute,
                                    onRetry = loadAccounts,
                                    onAccount = { item ->
                                        val connection = accountConnections.firstOrNull {
                                            it.credentialKey == item.key
                                        }
                                        if (connection != null && switchingAccountKey == null) {
                                            switchingAccountKey = item.key
                                            accountsError = null
                                            scope.launch {
                                                try {
                                                    runtime.switchAccount(connection)
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (needsLogin: SavedAccountRequiresLoginException) {
                                                    // 保存的凭据不可恢复（系统清理/卸载重装/当初未落盘）：
                                                    // 这不是"切换失败"，而是"需要重新登录"。把用户带到
                                                    // 这台服务器的登录页并预填地址与用户名，登录一次即可。
                                                    accountsError = null
                                                    mineBackStack += BrowseConnectAccountRoute(
                                                        provider = connection.server.provider,
                                                        presetBaseUrl = connection.server.baseUrl,
                                                        presetUsername = connection.account.userName,
                                                    )
                                                } catch (_: Exception) {
                                                    accountsError = "账号切换失败，请稍后重试"
                                                } finally {
                                                    switchingAccountKey = null
                                                }
                                            }
                                        }
                                    },
                                    onDelete = { item ->
                                        val connection = accountConnections.firstOrNull {
                                            it.credentialKey == item.key
                                        }
                                        if (connection != null && !item.isCurrent &&
                                            switchingAccountKey == null && deletingAccountKey == null
                                        ) {
                                            deletingAccountKey = item.key
                                            accountsError = null
                                            scope.launch {
                                                try {
                                                    runtime.deleteSavedConnection(connection)
                                                    accountConnections = runtime.savedConnections()
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (_: Exception) {
                                                    accountsError = "账号删除失败，请稍后重试"
                                                } finally {
                                                    deletingAccountKey = null
                                                }
                                            }
                                        }
                                    },
                                    onAddProvider = { provider ->
                                        accountConnectError = null
                                        mineBackStack += BrowseConnectAccountRoute(provider)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            BrowseAddAccountRoute -> NavEntry(route) {
                                AddAccountScreen(
                                    onBack = popMineRoute,
                                    onProvider = { provider ->
                                        accountConnectError = null
                                        mineBackStack += BrowseConnectAccountRoute(provider)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            is BrowseConnectAccountRoute -> NavEntry(route) {
                                val formState = rememberConnectFormState()
                                LaunchedEffect(formState, route) {
                                    // Returning from discovery may recreate this entry. Preserve the
                                    // selected address until the result effect below consumes it.
                                    if (pendingDiscoveredServerAddress == null) {
                                        formState.provider = route.provider
                                        formState.baseUrl = route.presetBaseUrl.orEmpty()
                                        formState.username = route.presetUsername.orEmpty()
                                        formState.password = ""
                                        formState.rememberPassword = true
                                    }
                                }
                                LaunchedEffect(formState, pendingDiscoveredServerAddress) {
                                    val address = pendingDiscoveredServerAddress ?: return@LaunchedEffect
                                    formState.provider = ProviderKind.Jellyfin
                                    formState.baseUrl = address
                                    formState.requestServerAddressFocus()
                                    pendingDiscoveredServerAddress = null
                                }
                                ConnectScreen(
                                    busy = accountConnectBusy,
                                    error = accountConnectError,
                                    onSubmit = { provider, address, name, password, rememberPassword ->
                                        if (!accountConnectBusy) {
                                            accountConnectBusy = true
                                            accountConnectError = null
                                            scope.launch {
                                                try {
                                                    runtime.connect(provider, address, name, password, rememberPassword)
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (failure: Exception) {
                                                    accountConnectError = connectFailureMessage(failure)
                                                } finally {
                                                    accountConnectBusy = false
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    formState = formState,
                                    routeTitle = if (route.provider == ProviderKind.Jellyfin) {
                                        "连接 Jellyfin"
                                    } else {
                                        "连接飞牛影视"
                                    },
                                    onBack = popMineRoute,
                                    showProviderSwitcher = false,
                                    showHistory = false,
                                    onOpenServerDiscovery = {
                                        if (mineBackStack.lastOrNull() != BrowseServerDiscoveryRoute) {
                                            mineBackStack += BrowseServerDiscoveryRoute
                                        }
                                    },
                                )
                            }
                            BrowseServerDiscoveryRoute -> NavEntry(route) {
                                ServerDiscoveryScreen(
                                    loadSavedServers = { savedJellyfinServerSuggestions(runtime) },
                                    discoverServers = { discoveredJellyfinServerSuggestions() },
                                    onBack = popMineRoute,
                                    onSelectServer = { address ->
                                        pendingDiscoveredServerAddress = address
                                        popMineRoute()
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            BrowseSettingsRoute -> NavEntry(route) {
                                SettingsScreen(
                                    onBack = popMineRoute,
                                    onOpenAppearance = {
                                        if (mineBackStack.lastOrNull() != BrowseAppearanceRoute) {
                                            mineBackStack += BrowseAppearanceRoute
                                        }
                                    },
                                    feiniuRoutePreferenceTitle = if (
                                        session.selection.provider == ProviderKind.Feiniu
                                    ) {
                                        feiniuConnectionPreference.title
                                    } else {
                                        null
                                    },
                                    onOpenFeiniuPreference = if (
                                        session.selection.provider == ProviderKind.Feiniu
                                    ) {
                                        {
                                            feiniuConnectionPreferenceError = null
                                            if (mineBackStack.lastOrNull() != BrowseFeiniuConnectionPreferenceRoute) {
                                                mineBackStack += BrowseFeiniuConnectionPreferenceRoute
                                            }
                                        }
                                        } else {
                                        null
                                    },
                                    liquidGlassIntensity = { currentLiquidGlassIntensity.value },
                                    onLiquidGlassIntensityChanged = { value ->
                                        currentLiquidGlassIntensityChanged.value(value)
                                    },
                                    homeCarouselVisible = { homeCarouselVisible },
                                    onHomeCarouselVisibleChanged = onHomeCarouselVisibleChanged,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            BrowseFeiniuConnectionPreferenceRoute -> NavEntry(route) {
                                FeiniuConnectionPreferenceScreen(
                                    selectedPreference = feiniuConnectionPreference,
                                    onSelect = { selected ->
                                        if (selected != feiniuConnectionPreference &&
                                            !feiniuConnectionPreferenceSaving
                                        ) {
                                            feiniuConnectionPreferenceError = null
                                            feiniuConnectionPreferenceSaving = true
                                            scope.launch {
                                                try {
                                                    runtime.saveFeiniuRoutePreference(selected.toProviderPreference())
                                                    feiniuConnectionPreference = selected
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (_: Exception) {
                                                    feiniuConnectionPreferenceError = "连接策略保存失败，请重试"
                                                } finally {
                                                    feiniuConnectionPreferenceSaving = false
                                                }
                                            }
                                        }
                                    },
                                    onBack = popMineRoute,
                                    errorMessage = feiniuConnectionPreferenceError,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            BrowseAppearanceRoute -> NavEntry(route) {
                                AppearanceScreen(
                                    onBack = popMineRoute,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            BrowseAddGroupRoute -> NavEntry(route) {
                                AddGroupScreen(
                                    onBack = popMineRoute,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            BrowseAboutPrivacyRoute -> NavEntry(route) {
                                AboutPrivacyScreen(
                                    onBack = popMineRoute,
                                    onPrivacy = { uriHandler.openUri(PRIVACY_AGREEMENT_URL) },
                                    onUserAgreement = {
                                        if (mineBackStack.lastOrNull() != BrowseUserAgreementRoute) {
                                            mineBackStack += BrowseUserAgreementRoute
                                        }
                                    },
                                    onOpenUrl = uriHandler::openUri,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            BrowseUserAgreementRoute -> NavEntry(route) {
                                UserAgreementScreen(
                                    onBack = popMineRoute,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        else -> NavEntry(route) { }
                    }
                },
                modifier = mineHostModifier,
            )
        }
        val togglePlayback: () -> Unit = {
            scope.launch {
                try {
                    when (playback?.controller?.state?.value?.phase ?: PlayerPhase.Idle) {
                        PlayerPhase.Playing, PlayerPhase.Buffering -> playback?.controller?.pause()
                        PlayerPhase.Ended, PlayerPhase.Error -> playingMedia?.let(startPlayback)
                        PlayerPhase.Ready, PlayerPhase.Paused -> playback?.controller?.play()
                        PlayerPhase.Idle, PlayerPhase.Loading, PlayerPhase.Released -> Unit
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    playbackError = "播放器操作失败，请重试"
                }
            }
        }
        val activeMedia = playingMedia?.takeIf { it.id.value == playerShellState.resourceId }
        val playerMedia = if (playerExpanded) playingMedia else activeMedia
        // The floating mini player is the legacy MusicMiniPlayer. Video keeps
        // using the full player surface, but must not reserve music chrome or
        // appear above the app navigation after playback starts.
        val musicMiniPlayerMedia = activeMedia?.takeIf { it.mediaType == MediaType.Audio }
        val recordSearchHistory: (String) -> Unit = { raw ->
            val term = raw.trim()
            if (term.isNotEmpty()) {
                val updated = (listOf(term) + searchHistories.filterNot {
                    it.equals(term, ignoreCase = true)
                }).take(10)
                searchHistories = updated
                scope.launch {
                    try {
                        runtime.saveSearchHistory(updated)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Unit
                    }
                }
            }
        }
        val librarySortChoices = legacySortChoices(
            provider = session.connection.server.provider,
            direction = state.librarySort.direction,
        )
        val favoriteSortChoices = legacySortChoices(
            provider = session.connection.server.provider,
            direction = state.favoriteSort.direction,
        )

        // The browse stack belongs to this root shell, not to an individual
        // page entry. Keep the pop operation here so the OHOS ability Back
        // bridge and Navigation 3 use exactly the same transition/data rules.
        val popBrowseRoute: () -> Unit = {
            val route = browseBackStack.lastOrNull()
            val revealed = browseBackStack.getOrNull(browseBackStack.lastIndex - 1)
            val deferDetailClear = route is BrowseDetailRoute
            when (route) {
                // Keep the detail document composed while NavDisplay runs the
                // pop transition so the shared-element source remains alive.
                is BrowseDetailRoute -> Unit
                is BrowsePersonRoute -> browser.clearPerson()
                BrowseSearchRoute -> {
                    searchJob?.cancel()
                    searchJob = null
                    browser.clearSearch()
                }
                else -> Unit
            }
            if (browseBackStack.size > 1) {
                browseBackStack.removeLastOrNull()
                if (deferDetailClear) {
                    val expectedStack = browseBackStack.toList()
                    scope.launch {
                        delay(BrowseSharedReturnDurationMs)
                        if (browseBackStack == expectedStack) browser.back()
                    }
                }
                when (revealed) {
                    BrowseHomeRoute -> scope.launch { browser.refreshHome() }
                    is BrowseDetailRoute -> scope.launch {
                        val id = MediaId(revealed.mediaId)
                        if (state.detail?.id == id) browser.refreshDetail(id) else browser.openDetail(id)
                    }
                    is BrowseEpisodeRoute -> scope.launch {
                        val id = MediaId(revealed.episodeId)
                        if (state.detail?.id == id) browser.refreshDetail(id) else browser.openDetail(id)
                    }
                    is BrowseSeasonRoute -> scope.launch {
                        val seriesId = MediaId(revealed.seriesId)
                        val seasonId = MediaId(revealed.seasonId)
                        val requiresRestore = state.detail?.id != seriesId ||
                            state.selectedSeasonId != seasonId
                        if (state.detail?.id != seriesId) browser.openDetail(seriesId)
                        if (browser.state.value.selectedSeasonId != seasonId) {
                            browser.selectSeason(seasonId)
                        }
                        if (!requiresRestore) browser.refreshDetail(seriesId)
                    }
                    else -> Unit
                }
            }
        }

        // On a collapsed large-screen shell, secondary routes keep the same
        // top-tab row. Its leading slot is the route back action; root pages
        // deliberately leave that slot as the sidebar-expander instead.
        val collapsedTopBarBack: (() -> Unit)? = when {
            (showingDetail || currentRoute == BrowseLatestRoute) && browseBackStack.size > 1 -> popBrowseRoute
            minePresented && mineBackStack.size > 1 -> popMineRoute
            else -> null
        }

        // Root fallback for the tablet/desktop shell. Child sheet/player
        // handlers are registered deeper in the Navigation 3 tree and win
        // first; this handler only owns the root surface and route stacks.
        MaFeiBackHandler(
            enabled = minePresented || browseBackStack.size > 1,
            onBack = {
                when {
                    minePresented && mineBackStack.size > 1 -> popMineRoute()
                    browseBackStack.size > 1 -> popBrowseRoute()
                    minePresented -> minePresented = false
                }
            },
        )


        // 删除确认弹窗的玻璃源：捕获层包住外壳/播放器，弹窗是它的**兄弟** —— 记录者与
        // 消费者绝不能在同一棵子树里（详见 N1 崩溃规则）。只在弹窗出现时挂捕获，平时零开销。
        Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().then(
                if (pendingDeleteMedia != null) Modifier.captureGlassBackdrop(deleteConfirmBackdrop) else Modifier,
            ),
        ) {
        if (playerExpanded && playerMedia != null && playback != null) {
            BoundNowPlayingScreen(
                media = NowPlayingMedia(
                    id = playerMedia.id.value,
                    title = playerMedia.title,
                    subtitle = playerMedia.miniPlayerSubtitle(),
                    description = playerMedia.overview,
                ),
                controller = playback.controller,
                providerTracks = playback.tracks,
                onBack = {
                    playerExpanded = false
                    playerFullscreen = false
                    onPlayerFullscreenChanged(false)
                    scope.launch {
                        try {
                            playback.stop()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            playbackError = "停止播放失败，请重试"
                        }
                    }
                },
                onTogglePlay = togglePlayback,
                onRetry = { startPlayback(playerMedia) },
                onSeek = { pendingSeekPosition = it },
                onSpeed = { speed ->
                    scope.launch {
                        try {
                            playback.controller.speed(speed)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            playbackError = "播放器操作失败，请重试"
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
                            playbackError = "播放器操作失败，请重试"
                        }
                    }
                },
                playerSurface = playerSurface,
                preparing = playbackBusy,
                playbackError = playbackError,
                modifier = Modifier.fillMaxSize(),
                queue = playQueue,
                onNext = playQueue?.takeIf { it.hasNext }?.let { { playNextEpisode() } },
                onPrevious = playQueue?.takeIf { it.hasPrevious }?.let { { playPreviousEpisode() } },
                onSelectEpisode = playQueue?.takeIf { it.size > 1 }?.let { { playEpisodeAt(it) } },
                qualityOptions = qualityOptions,
                onSelectQuality = { selectQuality(it) },
                catalogItem = playerMedia,
                prepared = preparedStream,
                loadSeekPreview = { positionMs ->
                    runtime.sessions.media.previewFrame(
                        session = session.media,
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
                    }
                },
                // 全屏请求先落在页面状态上，再交给宿主（旋转窗口 / 隐藏系统栏）。
                // 页面状态先行的原因：宿主是异步的，UI 布局不能等它。
                fullscreen = playerFullscreen,
                onFullscreenChanged = { requested ->
                    playerFullscreen = requested
                    onPlayerFullscreenChanged(requested)
                },
                onSelectContentMode = { mode ->
                    scope.launch {
                        try {
                            playback.controller.setContentMode(mode)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            playbackError = "切换画面比例失败，请重试"
                        }
                    }
                },
            )
        } else MaFeiAdaptiveShell(
            layout = layout,
            destinations = destinations,
            sidebarDestinations = sidebarDestinations,
            topTabDestinations = topTabDestinations,
            topTabSelectedDestination = topTabSelectedDestination,
            selectedDestination = if (minePresented) MineDestination else shellDestination,
            onDestination = selectDestination,
            onTopBarBack = collapsedTopBarBack,
            accountName = session.connection.account.displayName,
            libraries = libraries,
            // Do not read the browser's last selected collection here.  A
            // Home/Favorite/Search detail must keep its opening root selected,
            // even while that mutable provider state still contains another
            // library.
            selectedLibrary = shellContext.libraryId,
            onLibrary = selectDestination,
            accountLabel = "${session.connection.account.displayName}\n${session.connection.server.name}",
            // 只在手机上隐藏 Bottom Tab。Drawer/Sidebar/Rail 的导航入口必须始终保留：
            // 具体媒体库、搜索和详情页虽然是全屏内容，但用户仍需要通过顶部 Tab、抽屉
            // 或常驻侧栏切换根页面；之前把 showingFullPageRoute 直接传进外壳，会连大屏
            // 导航一起隐藏，进入媒体库列表后就像导航栈被清空了一样。
            showBottomNavigation = !layout.isPhoneNavigation ||
                (!showingFullPageRoute &&
                    !(minePresented && mineFullScreen && mineBackStack.size > 1)),
            // Search is the primary shell action represented by the final
            // icon in the collapsed top bar. Keep that bar mounted with the
            // search action selected; only pushed details and the Mine stack
            // switch a collapsed large sidebar to the compact rail.
            contentNavigationActive = showingDetail || currentRoute == BrowseLatestRoute || minePresented,
            onSearch = openSearch,
            selectedSearch = currentRoute == BrowseSearchRoute,
            // The compact dock is the other half of the mini-player morph.
            // With no active media there is nothing to merge into, so page
            // scrolling must leave the primary navigation at full size.
            bottomNavigationCompactProgress = {
                if (musicMiniPlayerMedia != null) chromeCompactProgress.value else 0f
            },
            // 一级页面的标题随内容滚走，滚过状态栏后顶部才浮现毛玻璃。二级页面
            // （详情/设置/搜索）自带头部，头部材质由各页自己的 collapseProgress 驱动，
            // 这里必须让位，否则两层毛玻璃会叠在一起。
            topChromeScrolledProgress = {
                if (showingFullPageRoute) 0f else chromeCompactProgress.value
            },
            background = {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                // 首页轮播背景画在窗口画布上：全窗、与侧栏让位无关。轮播背景是
                // 「窗口的背景」而不是「被侧栏挤开的内容区里伸回去的补丁」——
                // 侧栏收不收起，它都逐像素静止。
                if (currentRoute == BrowseHomeRoute && !showingDetail && homeCarouselVisible) {
                    HomeHeroWindowBackdrop(
                        state = heroBackdrop,
                        artwork = { item, modifier ->
                            MediaArtwork(
                                item.landscapeArtworkCandidates(),
                                item.title,
                                artworkCache,
                                fetchArtwork,
                                modifier,
                                // The phone hero is a single-column visual band. A 720px
                                // decode is enough for its viewport and avoids keeping two
                                // 960px copies (clear hero + ambient backdrop) alive during
                                // the first scroll.
                                targetWidthPx = if (layout.family == MaFeiLayoutFamily.Phone) 720 else 960,
                            )
                        },
                    )
                }
            },
            accountActions = {},
            windowControls = windowControls,
            windowDragArea = windowDragArea,
            scene = {
                fun openDetailId(
                    id: MediaId,
                    mediaName: String?,
                    initialItem: MediaItem? = null,
                    origin: BrowseDetailOrigin = shellContext.origin ?: BrowseDetailOrigin.Home,
                    libraryId: String? = shellContext.libraryId,
                ) {
                    if (browseBackStack.lastOrNull() !is BrowseDetailRoute) {
                        browseBackStack += BrowseDetailRoute(
                            mediaId = id.value,
                            mediaName = mediaName,
                            origin = origin,
                            libraryId = libraryId,
                        )
                    }
                    // Start the landscape request as soon as a home card is
                    // tapped. The portrait card remains the shared-transition
                    // first frame; the detail Hero can then fade in the real
                    // Backdrop without waiting for the detail JSON round trip.
                    initialItem?.let { tappedItem ->
                        scope.launch {
                            prefetchArtwork(
                                tappedItem.legacyDetailBackdropArtworkCandidates(),
                                artworkCache,
                                fetchArtwork,
                                targetWidthPx = if (layout.family == MaFeiLayoutFamily.Phone) 720 else 960,
                            )
                        }
                    }
                    scope.launch { browser.openDetail(id, initialItem) }
                }
                fun openDetail(
                    item: MediaItem,
                    mediaName: String?,
                    origin: BrowseDetailOrigin = shellContext.origin ?: BrowseDetailOrigin.Home,
                    libraryId: String? = shellContext.libraryId,
                ) {
                    openDetailId(item.id, mediaName, item, origin, libraryId)
                }
                val openPerson: (cn.mafei.cmp.core.domain.PersonRef) -> Unit = { person ->
                    person.id?.let { id ->
                        val route = BrowsePersonRoute(
                            personId = id.value,
                            personName = person.name,
                            origin = shellContext.origin ?: BrowseDetailOrigin.Home,
                            libraryId = shellContext.libraryId,
                        )
                        if (browseBackStack.lastOrNull() != route) {
                            browseBackStack += route
                            scope.launch { browser.openPerson(id, person.name) }
                        }
                    }
                }
                val browseCollection: (cn.mafei.cmp.core.domain.MediaCollection) -> Unit = { collection ->
                    if (collection.id != state.selectedCollection) {
                        scope.launch { browser.select(collection.id) }
                    }
                    if (browseBackStack.lastOrNull() != BrowseLibraryRoute(collection.id.value)) {
                        browseBackStack += BrowseLibraryRoute(collection.id.value)
                    }
                }
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    val sharedTransitionScope = this
                    NavDisplay(
                    backStack = browseBackStack,
                    onBack = { popBrowseRoute() },
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInHorizontally(tween(260)) { it / 8 }) togetherWith
                            (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -it / 8 })
                    },
                    popTransitionSpec = {
                        (fadeIn(tween(180)) + slideInHorizontally(tween(260)) { -it / 8 }) togetherWith
                            (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 8 })
                    },
                    predictivePopTransitionSpec = {
                        (fadeIn(tween(180)) + slideInHorizontally(tween(260)) { -it / 8 }) togetherWith
                            (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 8 })
                    },
                    entryProvider = { route ->
                        when (route) {
                            BrowseHomeRoute -> NavEntry(route) {
                                val sharedArtworkModifier: @Composable (MediaItem, Modifier) -> Modifier = { item, modifier ->
                                    with(sharedTransitionScope) {
                                        modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState(
                                                key = "media-artwork:${item.id.value}",
                                            ),
                                            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                            boundsTransform = { _, _ -> tween(420) },
                                            zIndexInOverlay = 1f,
                                        )
                                    }
                                }
                                HomeScreen(
                                    collections = state.collections,
                                    items = state.items,
                                    homeRecommendations = state.homeFeed.recommendations,
                                    homeResume = state.homeFeed.resume,
                                    homeNextUp = state.homeFeed.nextUp,
                                    homeLatest = state.homeFeed.latest,
                                    homeLatestByCollection = state.homeLatestByCollection,
                                    homeLatestLoading = state.homeLatestLoading,
                                    homeLatestErrors = state.homeLatestErrors,
                                    selectedCollection = state.selectedCollection,
                                    loading = state.loading,
                                    refreshing = state.refreshing,
                                    loadingMore = state.loadingMore,
                                    error = state.homeError ?: state.error,
                                    stale = state.stale || state.homeStale,
                                    canLoadMore = state.nextCursor != null,
                                    overview = true,
                                    showHomeHero = homeCarouselVisible,
                                    layout = layout,
                                    onCollection = { collection -> scope.launch { browser.loadHomeLatest(collection.id) } },
                                    onBrowseCollection = browseCollection,
                                    onItem = { item ->
                                        openDetail(item, item.legacyHomeLatestMediaName(state.collections))
                                    },
                                    onHeroItem = { item ->
                                        val target = item.legacyHomeHeroDetailTarget()
                                        openDetailId(
                                            target.mediaId,
                                            target.mediaName,
                                            item.takeIf { it.id == target.mediaId },
                                        )
                                    },
                                    onPlay = startPlayback,
                                    onRetry = { scope.launch { browser.refresh() } },
                                    onLoadMore = { scope.launch { browser.loadMore() } },
                                    // The library shelf still opens the library index. The
                                    // separate home latest shelf uses its own list route.
                                    onOpenLibrary = { selectDestination(LibraryDestination) },
                                    onOpenLatest = openLatest,
                                    onSearch = openSearch,
                                    bottomOverlayInset = if (musicMiniPlayerMedia != null) 76.dp else 0.dp,
                                    modifier = Modifier.fillMaxSize(),
                                    gridState = homeGridState,
                                    artwork = { item, modifier ->
                                        MediaArtwork(
                                            item.posterArtworkCandidates(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            // Wide home cards are 140dp. On the
                                            // 2.5x MatePad canvas that is about
                                            // 350px, so decoding 480px posters
                                            // only adds allocation pressure while
                                            // the shelf is entering the viewport.
                                            targetWidthPx = if (layout.family == MaFeiLayoutFamily.Phone) 480 else 360,
                                        )
                                    },
                                    sharedArtworkModifier = sharedArtworkModifier,
                                    backdropArtwork = { item, modifier ->
                                        MediaArtwork(
                                            item.landscapeArtworkCandidates(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            targetWidthPx = if (layout.family == MaFeiLayoutFamily.Phone) 720 else 960,
                                        )
                                    },
                                    prefetchBackdrop = { item ->
                                        prefetchLandscapeArtwork(
                                            item,
                                            artworkCache,
                                            fetchArtwork,
                                            targetWidthPx = if (layout.family == MaFeiLayoutFamily.Phone) 720 else 960,
                                        )
                                    },
                                    heroBackdropState = heroBackdrop,
                                    shelfArtwork = { item, modifier ->
                                        MediaArtwork(
                                            item.landscapeArtworkCandidates(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            targetWidthPx = 480,
                                        )
                                    },
                                )
                            }
                            BrowseLatestRoute -> NavEntry(route) {
                                if (layout.isPhoneNavigation) {
                                    VideoListScreen(
                                        title = "最近添加",
                                        items = state.homeFeed.latest,
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
                                        loading = state.homeFeed.latest.isEmpty() &&
                                            state.homeError == null &&
                                            (state.loading || state.refreshing),
                                        refreshing = false,
                                        loadingMore = false,
                                        error = state.homeError,
                                        canLoadMore = false,
                                        layout = layout,
                                        gridState = latestGridState,
                                        onBack = popBrowseRoute,
                                        onSortSelected = {},
                                        onToggleSortDirection = {},
                                        onRetry = { scope.launch { browser.refreshHome() } },
                                        onLoadMore = {},
                                        onItem = { item -> openDetail(item, "最近添加") },
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
                                        onBack = popBrowseRoute,
                                        onRetry = {
                                            state.collections.forEach { collection ->
                                                scope.launch { browser.loadHomeLatest(collection.id) }
                                            }
                                        },
                                        onItem = { item ->
                                            openDetail(item, item.legacyHomeLatestMediaName(state.collections))
                                        },
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
                            BrowseLibraryIndexRoute -> NavEntry(route) {
                                LibraryIndexScreen(
                                    collections = state.collections,
                                    favoriteCount = state.favoriteTotalEstimate ?: state.favoriteItems.size,
                                    loading = state.loading,
                                    refreshing = state.refreshing,
                                    error = state.error,
                                    layout = layout,
                                    onCollection = browseCollection,
                                    onFavorite = {
                                        if (browseBackStack.lastOrNull() != BrowseFavoriteRoute) {
                                            browseBackStack += BrowseFavoriteRoute
                                        }
                                    },
                                    onRetry = { scope.launch { browser.refresh() } },
                                    bottomOverlayInset = if (musicMiniPlayerMedia != null) 76.dp else 0.dp,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            BrowseChasingRoute -> NavEntry(route) {
                                ChasingScreen(
                                    state = chasingState,
                                    layout = layout,
                                    listState = chasingListState,
                                    onRetry = { scope.launch { chasingBrowser.refresh() } },
                                    onPlay = startPlayback,
                                    onOpenSeries = { id -> openDetailId(id, "电视节目") },
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
                            BrowseFavoriteRoute -> NavEntry(route) {
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
                                    onItem = { item -> openDetail(item, "收藏") },
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
                            BrowseSearchRoute -> NavEntry(route) {
                                SearchScreen(
                                    query = state.searchQuery,
                                    results = state.searchItems,
                                    histories = searchHistories,
                                    loading = state.searchLoading,
                                    error = state.searchError,
                                    layout = layout,
                                    gridState = searchGridState,
                                    onBack = popBrowseRoute,
                                    onQueryChange = { term ->
                                        searchJob?.cancel()
                                        searchJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                            if (browser.search(term) && searchGridState.layoutInfo.totalItemsCount > 0) {
                                                searchGridState.scrollToItem(0)
                                            }
                                        }
                                    },
                                    onSubmit = { term ->
                                        searchJob?.cancel()
                                        searchJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                            if (browser.search(term)) {
                                                if (searchGridState.layoutInfo.totalItemsCount > 0) {
                                                    searchGridState.scrollToItem(0)
                                                }
                                                recordSearchHistory(term)
                                            }
                                        }
                                    },
                                    onRecordHistory = recordSearchHistory,
                                    onClearHistories = {
                                        searchHistories = emptyList()
                                        scope.launch {
                                            try {
                                                runtime.saveSearchHistory(emptyList())
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                Unit
                                            }
                                        }
                                    },
                                    onOpen = { item -> openDetail(item, null) },
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
                            is BrowseLibraryRoute -> NavEntry(route) {
                                VideoListScreen(
                                    title = state.collections.firstOrNull { it.id.value == route.collectionId }?.name.orEmpty(),
                                    items = state.items,
                                    selectedSort = state.librarySort,
                                    sortChoices = librarySortChoices,
                                    loading = state.loading,
                                    refreshing = state.refreshing,
                                    loadingMore = state.loadingMore,
                                    error = state.error,
                                    canLoadMore = state.nextCursor != null,                                    layout = layout,
                                    gridState = libraryGridState,
                                    onBack = popBrowseRoute.takeIf { layout.isPhoneNavigation },
                                    onSortSelected = { sort ->
                                        scope.launch { browser.setLibrarySort(sort) }
                                    },
                                    onToggleSortDirection = {
                                        val direction = if (state.librarySort.direction == SortDirection.Descending) {
                                            SortDirection.Ascending
                                        } else {
                                            SortDirection.Descending
                                        }
                                        scope.launch {
                                            browser.setLibrarySort(state.librarySort.copy(direction = direction))
                                        }
                                    },
                                    onItem = { item ->
                                        openDetail(
                                            item,
                                            state.collections.firstOrNull { it.id.value == route.collectionId }?.name,
                                        )
                                    },
                                    onRetry = { scope.launch { browser.refresh() } },
                                    onLoadMore = { scope.launch { browser.loadMore() } },
                                    canDeleteMedia = state.deleteAvailable == true,
                                    deleteBusy = state.deleteBusy,
                                    selectionMode = librarySelectionMode,
                                    selectedIds = librarySelectedIds,
                                    onEnterSelection = { item -> librarySelectedIds = setOf(item.id.value) },
                                    onToggleSelected = { item ->
                                        librarySelectedIds = if (item.id.value in librarySelectedIds) {
                                            librarySelectedIds - item.id.value
                                        } else {
                                            librarySelectedIds + item.id.value
                                        }
                                    },
                                    onExitSelection = { librarySelectedIds = emptySet() },
                                    onDeleteSelected = {
                                        if (librarySelectedIds.isNotEmpty()) {
                                            pendingDeleteMedia = librarySelectedIds.map { MediaId(it) }
                                        }
                                    },
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
                            }
                            is BrowseDetailRoute -> NavEntry(route) {
                                val sharedArtworkModifier: @Composable (MediaItem, Modifier) -> Modifier = { item, modifier ->
                                    with(sharedTransitionScope) {
                                        modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState(
                                                key = "media-artwork:${item.id.value}",
                                            ),
                                            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                            boundsTransform = { _, _ -> tween(420) },
                                            zIndexInOverlay = 1f,
                                        )
                                    }
                                }
                                val detailPlayTarget = state.detailPlayTarget?.takeIf { it.canPlay }
                                DetailScreen(
                                    item = state.detail,
                                    loading = state.detailLoading,
                                    error = state.detailError,
                                    onBack = popBrowseRoute,
                                    onRetry = {
                                        state.detailId?.let { id -> scope.launch { browser.openDetail(id) } }
                                    },
                                    onPlay = detailPlayTarget?.takeIf { playback != null }?.let { item ->
                                        { startPlayback(item) }
                                    },
                                    playTarget = detailPlayTarget,
                                    onOpenExternalUrl = { url ->
                                        try {
                                            uriHandler.openUri(url)
                                        } catch (_: Exception) {
                                            playbackError = "无法打开外部链接"
                                        }
                                    },
                                    onDelete = if (state.deleteAvailable == true) {
                                        { state.detailId?.let { id -> pendingDeleteMedia = listOf(id) } }
                                    } else null,
                                    selectedMediaSourceId = state.selectedMediaSourceId,
                                    onSelectMediaSource = { id ->
                                        scope.launch { browser.selectMediaSource(id) }
                                    },
                                    selectedAudioStreamIndex = state.selectedAudioStreamIndex,
                                    onSelectAudioTrack = { browser.selectAudioTrack(it) },
                                    selectedSubtitleStreamIndex = state.selectedSubtitleStreamIndex,
                                    onSelectSubtitleTrack = { browser.selectSubtitleTrack(it) },
                                    onToggleFavorite = if (browser.capabilities.favoritesEdit) {
                                        { scope.launch { browser.toggleFavorite() } }
                                    } else null,
                                    onTogglePlayed = if (browser.capabilities.watchedEdit) {
                                        { scope.launch { browser.toggleWatched() } }
                                    } else null,
                                    actionsBusy = state.detailActionBusy,
                                    actionsError = state.detailActionError,
                                    seasons = state.detailSeasons,
                                    sectionsLoading = state.detailSectionsLoading,
                                    sectionsError = state.detailSectionsError,
                                    onSeasonSelected = { season ->
                                        if (browseBackStack.lastOrNull() !is BrowseSeasonRoute) {
                                            browseBackStack += BrowseSeasonRoute(
                                                seriesId = route.mediaId,
                                                seasonId = season.id.value,
                                                mediaName = route.mediaName,
                                                origin = route.origin,
                                                libraryId = route.libraryId,
                                            )
                                        }
                                    },
                                    playbackBusy = playbackBusy,
                                    playbackError = playbackError,
                                    libraryName = route.mediaName,
                                    modifier = Modifier.fillMaxSize(),
                                    artwork = { item, modifier ->
                                        MediaArtwork(
                                            item.legacyPrimaryThenBackdropArtworkCandidates(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            targetWidthPx = 480,
                                        )
                                    },
                                    sharedArtworkModifier = sharedArtworkModifier,
                                    backdropArtwork = { item, modifier ->
                                        MediaArtwork(
                                            item.legacyDetailBackdropArtworkCandidates(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            targetWidthPx = 720,
                                            warmStartImages = item.posterArtworkCandidates(),
                                            revealDurationMillis = 360,
                                        )
                                    },
                                    logoArtwork = { item, modifier ->
                                        MediaArtwork(
                                            image = item.preferredLogo(),
                                            title = item.title,
                                            cache = artworkCache,
                                            fetch = fetchArtwork,
                                            modifier = modifier,
                                            contentScale = ContentScale.Fit,
                                            fallback = { fallback -> Box(fallback) },
                                            targetWidthPx = 480,
                                        )
                                    },
                                    personArtwork = { person, modifier ->
                                        LegacyPersonArtwork(
                                            image = person.image,
                                            name = person.name,
                                            cache = artworkCache,
                                            fetch = fetchArtwork,
                                            modifier = modifier,
                                        )
                                    },
                                    onPersonSelected = openPerson,
                                    atmosphereArtwork = { item, modifier ->
                                        MediaArtwork(
                                            images = item.legacyDetailBackdropArtworkCandidates(),
                                            title = item.title,
                                            cache = artworkCache,
                                            fetch = fetchArtwork,
                                            modifier = modifier,
                                            targetWidthPx = if (layout.family == MaFeiLayoutFamily.Phone) 240 else 960,
                                        )
                                    },
                                )
                            }
                            is BrowsePersonRoute -> NavEntry(route) {
                                PersonDetailScreen(
                                    person = state.person?.takeIf { it.id.value == route.personId },
                                    movies = state.personMovies,
                                    series = state.personSeries,
                                    episodes = state.personEpisodes,
                                    loading = state.personLoading,
                                    sectionsLoading = state.personSectionsLoading,
                                    error = state.personError,
                                    onBack = popBrowseRoute,
                                    onRetry = {
                                        scope.launch {
                                            browser.openPerson(MediaId(route.personId), route.personName)
                                        }
                                    },
                                    onItem = { item ->
                                        openDetail(
                                            item,
                                            if (item.mediaType == MediaType.Series) "电视节目" else "电影",
                                        )
                                    },
                                    canFavorite = browser.capabilities.favoritesEdit,
                                    onToggleFavorite = { scope.launch { browser.togglePersonFavorite() } },
                                    favoriteBusy = state.personFavoriteBusy,
                                    modifier = Modifier.fillMaxSize(),
                                    personArtwork = { person, modifier ->
                                        LegacyPersonArtwork(
                                            image = person.image,
                                            name = person.name,
                                            cache = artworkCache,
                                            fetch = fetchArtwork,
                                            modifier = modifier,
                                            targetWidthPx = 360,
                                        )
                                    },
                                    artwork = { item, modifier ->
                                        MediaArtwork(
                                            item.legacyPrimaryArtworkCandidates(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            fallback = { fallbackModifier ->
                                                LegacyPosterPlaceholder(item.mediaType, fallbackModifier)
                                            },
                                            targetWidthPx = 360,
                                        )
                                    },
                                )
                            }
                            is BrowseSeasonRoute -> NavEntry(route) {
                                // The ArkUI season page owns its own ViewModel. Our
                                // provider-neutral browser has one detail slot, so an
                                // episode page temporarily replaces the series in that
                                // slot. Make the route the source of truth: entering or
                                // returning to this destination restores the exact
                                // series + season before rendering it.
                                LaunchedEffect(route.seriesId, route.seasonId) {
                                    if (state.detail?.id?.value != route.seriesId) {
                                        browser.openDetail(MediaId(route.seriesId))
                                    }
                                    if (state.selectedSeasonId?.value != route.seasonId ||
                                        state.selectedSeasonDetail?.id?.value != route.seasonId
                                    ) {
                                        browser.selectSeason(MediaId(route.seasonId))
                                    }
                                }
                                val series = state.detail?.takeIf { it.id.value == route.seriesId }
                                val seasonSummary = state.detailSeasons.firstOrNull { it.id.value == route.seasonId }
                                val season = state.selectedSeasonDetail
                                    ?.takeIf { it.id.value == route.seasonId }
                                    ?: seasonSummary
                                if (series == null || season == null) {
                                    DetailScreen(
                                        item = null,
                                        loading = state.detailLoading || state.detailSectionsLoading ||
                                            (state.detailError == null && state.detailSectionsError == null),
                                        error = state.detailError ?: state.detailSectionsError,
                                        onBack = popBrowseRoute,
                                        onRetry = {
                                            scope.launch {
                                                browser.openDetail(MediaId(route.seriesId))
                                                browser.selectSeason(MediaId(route.seasonId))
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    // The legacy season page always exposes playFirst(): it resolves
                                    // progress/unwatched/first from the episode queue and lets playback
                                    // preparation decide whether the target can actually be opened.
                                    // Display-profile episode rows may not carry canPlay, so filtering it
                                    // here leaves a visible but empty action capsule.
                                    val playTarget = state.selectedSeasonPlayTarget
                                        ?: state.detailEpisodes.firstOrNull()
                                    SeasonDetailScreen(
                                        series = series,
                                        season = season,
                                        seasons = state.detailSeasons,
                                        episodes = state.detailEpisodes,
                                        loading = state.detailSectionsLoading,
                                        onBack = popBrowseRoute,
                                        onSeasonSelected = { nextSeason ->
                                            val lastIndex = browseBackStack.lastIndex
                                            if (lastIndex >= 0) {
                                                browseBackStack[lastIndex] = BrowseSeasonRoute(
                                                    seriesId = route.seriesId,
                                                    seasonId = nextSeason.id.value,
                                                    mediaName = route.mediaName,
                                                    origin = route.origin,
                                                    libraryId = route.libraryId,
                                                )
                                            }
                                        },
                                        onEpisodeSelected = { episode ->
                                            browseBackStack += BrowseEpisodeRoute(
                                                seriesId = route.seriesId,
                                                seasonId = route.seasonId,
                                                episodeId = episode.id.value,
                                                mediaName = route.mediaName,
                                                origin = route.origin,
                                                libraryId = route.libraryId,
                                            )
                                        },
                                        onPlayFirst = playTarget?.takeIf { playback != null }?.let { item ->
                                            { startPlayback(item) }
                                        },
                                        playTarget = playTarget,
                                        onTogglePlayed = if (browser.capabilities.watchedEdit) {
                                            { scope.launch { browser.toggleWatched(season) } }
                                        } else null,
                                        playbackBusy = playbackBusy,
                                        actionsBusy = state.detailActionBusy,
                                        actionsError = state.detailActionError,
                                        libraryName = route.mediaName,
                                        modifier = Modifier.fillMaxSize(),
                                        posterArtwork = { item, modifier ->
                                            MediaArtwork(
                                                item.legacyPrimaryArtworkCandidates(),
                                                item.title,
                                                artworkCache,
                                                fetchArtwork,
                                                modifier,
                                                targetWidthPx = 480,
                                            )
                                        },
                                        landscapeArtwork = { item, modifier ->
                                            MediaArtwork(
                                                item.legacyDetailBackdropArtworkCandidates(),
                                                item.title,
                                                artworkCache,
                                                fetchArtwork,
                                                modifier,
                                                targetWidthPx = 720,
                                                warmStartImages = item.posterArtworkCandidates(),
                                                revealDurationMillis = 360,
                                            )
                                        },
                                        episodeArtwork = { item, modifier ->
                                            MediaArtwork(
                                                item.legacyPrimaryThenBackdropArtworkCandidates(),
                                                item.title,
                                                artworkCache,
                                                fetchArtwork,
                                                modifier,
                                                targetWidthPx = 720,
                                            )
                                        },
                                        personArtwork = { person, modifier ->
                                            LegacyPersonArtwork(
                                                image = person.image,
                                                name = person.name,
                                                cache = artworkCache,
                                                fetch = fetchArtwork,
                                                modifier = modifier,
                                            )
                                        },
                                        onPersonSelected = openPerson,
                                        atmosphereArtwork = { item, modifier ->
                                            MediaArtwork(
                                                images = item.legacyDetailBackdropArtworkCandidates(),
                                                title = item.title,
                                                cache = artworkCache,
                                                fetch = fetchArtwork,
                                                modifier = modifier,
                                                targetWidthPx = if (layout.family == MaFeiLayoutFamily.Phone) 240 else 960,
                                            )
                                        },
                                    )
                                }
                            }
                            is BrowseEpisodeRoute -> NavEntry(route) {
                                LaunchedEffect(route.episodeId) {
                                    if (state.detailId?.value != route.episodeId) {
                                        browser.openDetail(MediaId(route.episodeId))
                                    }
                                }
                                val episode = state.detail?.takeIf { it.id.value == route.episodeId }
                                DetailScreen(
                                    item = episode,
                                    loading = state.detailLoading ||
                                        (episode == null && state.detailError == null),
                                    error = state.detailError,
                                    onBack = popBrowseRoute,
                                    onRetry = {
                                        scope.launch { browser.openDetail(MediaId(route.episodeId)) }
                                    },
                                    onPlay = episode?.takeIf { it.canPlay && playback != null }?.let { item ->
                                        { startPlayback(item) }
                                    },
                                    playTarget = episode,
                                    onOpenExternalUrl = { url ->
                                        try {
                                            uriHandler.openUri(url)
                                        } catch (_: Exception) {
                                            playbackError = "无法打开外部链接"
                                        }
                                    },
                                    onDelete = if (state.deleteAvailable == true) {
                                        { state.detailId?.let { id -> pendingDeleteMedia = listOf(id) } }
                                    } else null,
                                    selectedMediaSourceId = state.selectedMediaSourceId,
                                    onSelectMediaSource = { id ->
                                        scope.launch { browser.selectMediaSource(id) }
                                    },
                                    selectedAudioStreamIndex = state.selectedAudioStreamIndex,
                                    onSelectAudioTrack = { browser.selectAudioTrack(it) },
                                    selectedSubtitleStreamIndex = state.selectedSubtitleStreamIndex,
                                    onSelectSubtitleTrack = { browser.selectSubtitleTrack(it) },
                                    onToggleFavorite = if (browser.capabilities.favoritesEdit) {
                                        { scope.launch { browser.toggleFavorite() } }
                                    } else null,
                                    onTogglePlayed = if (browser.capabilities.watchedEdit) {
                                        { scope.launch { browser.toggleWatched() } }
                                    } else null,
                                    actionsBusy = state.detailActionBusy,
                                    actionsError = state.detailActionError,
                                    playbackBusy = playbackBusy,
                                    playbackError = playbackError,
                                    libraryName = route.mediaName,
                                    modifier = Modifier.fillMaxSize(),
                                    artwork = { item, modifier ->
                                        MediaArtwork(
                                            item.legacyPrimaryThenBackdropArtworkCandidates(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            targetWidthPx = 480,
                                        )
                                    },
                                    backdropArtwork = { item, modifier ->
                                        MediaArtwork(
                                            item.legacyDetailBackdropArtworkCandidates(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            targetWidthPx = 720,
                                            warmStartImages = item.posterArtworkCandidates(),
                                            revealDurationMillis = 360,
                                        )
                                    },
                                    logoArtwork = { item, modifier ->
                                        MediaArtwork(
                                            image = item.preferredLogo(),
                                            title = item.title,
                                            cache = artworkCache,
                                            fetch = fetchArtwork,
                                            modifier = modifier,
                                            contentScale = ContentScale.Fit,
                                            fallback = { fallback -> Box(fallback) },
                                            targetWidthPx = 480,
                                        )
                                    },
                                    personArtwork = { person, modifier ->
                                        LegacyPersonArtwork(
                                            image = person.image,
                                            name = person.name,
                                            cache = artworkCache,
                                            fetch = fetchArtwork,
                                            modifier = modifier,
                                        )
                                    },
                                    onPersonSelected = openPerson,
                                    atmosphereArtwork = { item, modifier ->
                                        MediaArtwork(
                                            images = item.legacyDetailBackdropArtworkCandidates(),
                                            title = item.title,
                                            cache = artworkCache,
                                            fetch = fetchArtwork,
                                            modifier = modifier,
                                            targetWidthPx = if (layout.family == MaFeiLayoutFamily.Phone) 240 else 960,
                                        )
                                    },
                                )
                            }
                            // 「我的」的分支已搬到它自己的导航容器（mineNavContent）。这里兜底。
                            else -> NavEntry(route) { }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    )
                }
                // 小屏（手机）：同一个「我的」容器，改成全屏铺开 —— 非模态，看起来和普通
                // 页面跳转一样。
                //
                // 关键：它和首页/收藏一样画在**场景里**，不是 overlay 槽。外壳的底部标签栏
                // 是场景的兄弟节点（在场景之后合成），所以标签栏照样折射"我的"页面上的内容；
                // 放进 overlay 会让标签栏得到一个平底，看起来就是"两层、标签栏不动"。
                // 内外边距由页面自己处理：MineScreen 底部留 112dp 给标签栏，子页面各自带头部。
                if (minePresented && mineFullScreen) {
                    MineFullScreenHost { mineNavContent(it) }
                }
            },
            overlay = { backdrop ->
                val safeArea = cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea.current
                val media = musicMiniPlayerMedia
                val playerController = playback?.controller
                if (media != null && playerController != null && !showingDetail) {
                    BoundLiquidMiniPlayer(
                        media = MiniPlayerMedia(
                            id = media.id.value,
                            title = media.title,
                            subtitle = media.miniPlayerSubtitle(),
                        ),
                        controller = playerController,
                        backdrop = backdrop,
                        onOpen = { playerExpanded = true },
                        onTogglePlay = togglePlayback,
                        compactProgress = { chromeCompactProgress.value },
                        artwork = { modifier ->
                            MediaArtwork(media.preferredPoster(), media.title, artworkCache, fetchArtwork, modifier)
                        },
                        modifier = Modifier
                            .align(
                                if (layout.isPhoneNavigation) Alignment.BottomCenter
                                else Alignment.BottomEnd,
                            )
                            .padding(
                                start = safeArea.start + if (layout.isPhoneNavigation) 16.dp else 24.dp,
                                end = safeArea.end + 16.dp,
                                bottom = safeArea.bottom + if (layout.isPhoneNavigation && !showingDetail) 88.dp else 24.dp,
                            )
                            .widthIn(max = 430.dp)
                            .fillMaxWidth(),
                    )
                }
            },
            modal = { backdrop ->
                // 大屏：居中的详情页同规格模态。复用同一个 mineNavContent，
                // 所以来回切形态时里面停在哪一页、翻了几层都保持不变。
                if (minePresented && !mineFullScreen) {
                    MineAccountSheet(
                        layout = layout,
                        backdrop = backdrop,
                        onDismiss = { minePresented = false },
                        content = mineNavContent,
                    )
                }
            },
        )
        }
        }
        // 管理员媒体删除确认（列表多选 / 详情页单条共用）。
        pendingDeleteMedia?.let { ids ->
            MaFeiGlassConfirmDialog(
                title = "删除媒体",
                content = "确定要删除选中的 ${ids.size} 项媒体吗？删除后无法恢复。",
                confirmText = "删除",
                dismissText = "取消",
                destructive = true,
                backdrop = deleteConfirmBackdrop,
                onDismiss = { pendingDeleteMedia = null },
                onConfirm = {
                    pendingDeleteMedia = null
                    librarySelectedIds = emptySet()
                    scope.launch {
                        browser.deleteMedia(ids)
                        // 详情页里删除自身：数据已删，回退出详情（与旧版行为一致）。
                        if (ids.any { it == state.detailId }) {
                            if (browseBackStack.lastOrNull() is BrowseDetailRoute ||
                                browseBackStack.lastOrNull() is BrowseEpisodeRoute
                            ) {
                                browseBackStack.removeLastOrNull()
                            }
                            browser.back()
                        }
                    }
                },
            )
        }
    }
}

/**
 * 小屏（手机）上的「我的」容器：全屏铺开、非模态，看起来和普通页面跳转一样。
 *
 * 它画在场景里（`scene` 槽，在浏览页之后），所以外壳的底部标签栏是它的兄弟节点：标签栏
 * 照样折射这一页的内容，不会变成一块压在上面的"死"浮层。铺一层窗口底色是为了盖住下面
 * 仍然活着的浏览页（NavDisplay 换了路由是先画后画、不是替换，所以底下那页还在）。
 */
@Composable
private fun MineFullScreenHost(
    content: @Composable (Modifier) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        content(Modifier.fillMaxSize())
    }
}

/**
 * Modal account surface, modelled on the macOS/iPadOS account popover.
 *
 * It is a sibling of the scene rather than a platform Dialog, so the glass panel
 * samples the real page underneath, the scrim dims the whole window (sidebar
 * included) and dismissing restores the untouched route. Escape, the scrim and
 * the explicit close control all dismiss it; the panel swallows its own taps so
 * pressing inside never falls through to the scrim.
 */
@Composable
private fun MineAccountSheet(
    layout: MaFeiLayout,
    backdrop: GlassBackdrop,
    onDismiss: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val largeWindow = layout.navigation != NavigationPlacement.Bottom
        val sheetWidth = if (largeWindow) {
            // Keep the modal visually subordinate to the page behind it. The
            // previous large-screen width is the baseline, then the sheet is
            // reduced to roughly two thirds of that width.
            ((maxWidth - 48.dp).coerceIn(280.dp, 720.dp) * (2f / 3f))
                .coerceAtLeast(280.dp)
        } else {
            maxWidth
        }
        val sheetHeight = (maxHeight * .74f).coerceAtLeast(320.dp)
        val topBarHeight = if (largeWindow) MineSheetLargeTopBarHeight else MineSheetTopBarHeight
        val closeButtonTopInset = (topBarHeight - 44.dp) / 2f
        val closeButtonEndInset = if (largeWindow) 16.dp else 8.dp
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .46f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
            contentAlignment = if (largeWindow) Alignment.Center else Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .width(sheetWidth)
                    .height(sheetHeight)
                    .clip(RoundedCornerShape(MineSheetCornerDp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { contentDescription = "我的" },
            ) {
                val sheetBackdrop = rememberGlassBackdrop()
                val closeButtonBackdrop = sheetBackdrop
                GlassSurface(
                    backdrop = backdrop,
                    spec = MineAccountSheetSurfaceSpec,
                    // The modal is a canvas; its list groups/cards use surface.
                    // Using surface here as well made every item disappear into
                    // the popup body.
                    tint = MaFeiPopupReferenceColors.container,
                    modifier = Modifier.fillMaxSize(),
                    exportedBackdrop = sheetBackdrop,
                ) {
                    CompositionLocalProvider(
                        LocalMaFeiSafeArea provides MaFeiSafeArea(),
                        LocalMaFeiPopupColors provides MaFeiPopupReferenceColors,
                        // MineScreen is also rendered as a window-level dark
                        // surface. Keep its loading/empty labels in the same
                        // light ink hierarchy as the rest of the popup.
                        LocalContentColor provides Color.White,
                    ) {
                        content(
                            Modifier.fillMaxSize().padding(top = topBarHeight)
                                .testTag("mineAccountSheetContent"),
                        )
                    }
                }
                // 移动端保留底部弹窗拖拽指示器；大屏居中弹窗只保留标题和关闭按钮。
                Box(
                    Modifier.fillMaxWidth().height(topBarHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!largeWindow) {
                        Box(
                            Modifier.align(Alignment.TopCenter).padding(top = 8.dp).width(36.dp).height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .36f)),
                        )
                    }
                    Text(
                        "我的",
                        Modifier.fillMaxWidth().padding(horizontal = 52.dp),
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    )
                }
                LiquidButton(
                    onClick = onDismiss,
                    backdrop = closeButtonBackdrop,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = closeButtonTopInset, end = closeButtonEndInset)
                        .size(44.dp)
                        .semantics { contentDescription = "关闭我的" },
                    height = 44.dp,
                    horizontalPadding = 0.dp,
                ) {
                    MaFeiIcon(
                        MaFeiGlyph.Close,
                        Modifier.size(20.dp),
                        Color.White,
                    )
                }
            }
        }
    }
}

/** 弹窗顶部导航区（关闭按钮所在条带）的高度：所有子页面内容在此之下。 */
private val MineSheetTopBarHeight = 56.dp
// Keep the 12dp inset used by the reference while centering the 44dp control:
// (68 - 44) / 2 = 12dp on both sides.
private val MineSheetLargeTopBarHeight = 68.dp
private const val MineSheetCornerDp = 24f
private const val BrowseSharedReturnDurationMs = 460L

private val MineAccountSheetSurfaceSpec = GlassSurfaceSpec(
    role = GlassRole.Dialog,
    level = GlassLevel.Opaque,
    tintAlpha = 1f,
    intensity = 0f,
    cornerRadiusDp = MineSheetCornerDp,
    material = GlassMaterial.Frosted,
)

private const val PRIVACY_AGREEMENT_URL =
    "https://agreement-drcn.hispace.dbankcloud.cn/index.html?lang=zh&agreementId=1915508141466875008"

private fun legacyVisibleServerName(connection: SavedConnection): String {
    if (connection.server.provider != ProviderKind.Feiniu) return connection.server.name
    val name = connection.server.name.trim()
    val address = connection.server.baseUrl.trim()
    val normalized = name.lowercase()
    return if (
        name.isEmpty() || name == address || normalized.startsWith("http://") ||
        normalized.startsWith("https://") || ".5ddd.com" in normalized || ".fnos.net" in normalized
    ) {
        "飞牛影视"
    } else {
        name
    }
}

private fun legacyConnectionDescription(connection: SavedConnection): String {
    if (connection.server.provider != ProviderKind.Feiniu) return ""
    val route = connection.feiniuRoutes.firstOrNull()
    val baseUrl = route?.baseUrl ?: connection.server.baseUrl
    val host = baseUrl.substringAfter("://", baseUrl).substringBefore('/').let { authority ->
        if (authority.startsWith('[')) authority.substringAfter('[').substringBefore(']')
        else authority.substringBefore(':')
    }.lowercase()
    val addressType = when {
        ':' in host -> "IPv6"
        host.split('.').let { parts -> parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 } } -> "IPv4"
        else -> ""
    }
    return when (route?.route) {
        "Relay" -> "飞牛中转"
        "Local" -> if (addressType.isNotEmpty()) "内网 $addressType" else "内网连接"
        "Public" -> if (addressType.isNotEmpty()) "公网 $addressType" else "公网连接"
        else -> when {
            host == "localhost" || host == "::1" -> "内网连接"
            host.startsWith("10.") || host.startsWith("127.") || host.startsWith("192.168.") ||
                host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80:") ||
                host.split('.').let { parts ->
                    parts.size == 4 && parts[0] == "172" && (parts[1].toIntOrNull() ?: -1) in 16..31
                } -> if (addressType.isNotEmpty()) "内网 $addressType" else "内网连接"
            addressType.isNotEmpty() -> "公网 $addressType"
            else -> "公网直连"
        }
    }
}

@Composable
private fun StaticAppCanvas(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        content = content,
    )
}

@Composable
private fun PonyStartupLoading(exiting: Boolean) {
    val density = LocalDensity.current
    val transition = rememberInfiniteTransition(label = "pony-startup")
    val frameProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pony-frames",
    )
    val streakProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(720, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pony-streaks",
    )
    val breathing by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pony-breathing",
    )
    val exitProgress by animateFloatAsState(
        targetValue = if (exiting) 1f else 0f,
        animationSpec = tween(220, easing = LinearEasing),
        label = "pony-exit",
    )
    val primary = MaterialTheme.colorScheme.primary
    val frames = remember { MaFeiLegacyDrawables.ponyLoadingFrames }
    val frame = frames[frameProgress.toInt().coerceIn(0, frames.lastIndex)]
    val imageYOffsetPx = with(density) { 18.dp.toPx() }
    val exitYOffsetPx = with(density) { 12.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
            .graphicsLayer { alpha = 1f - exitProgress },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(250.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize().padding(horizontal = 22.dp)) {
                val lineY = size.height * 0.83f
                val lineGap = 52.dp.toPx()
                val lineHeight = 3.dp.toPx()
                val phase = streakProgress * lineGap
                repeat(5) { index ->
                    val lineWidth = (28 + (index % 3) * 10).dp.toPx()
                    val x = size.width - ((index * lineGap + phase) % (size.width + lineWidth))
                    val y = lineY + ((index % 2) * 7).dp.toPx()
                    drawRoundRect(
                        color = primary.copy(alpha = 0.16f + breathing * 0.08f),
                        topLeft = Offset(x, y),
                        size = Size(lineWidth, lineHeight),
                        cornerRadius = CornerRadius(lineHeight / 2f),
                    )
                }
            }
            Image(
                painter = painterResource(frame),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer {
                        translationY = -imageYOffsetPx - exitProgress * exitYOffsetPx
                        val scale = 0.98f + breathing * 0.02f + exitProgress * 0.06f
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "正在启动…",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun SessionMessage(
    message: String,
    busy: Boolean = false,
    onRetry: (() -> Unit)? = null,
    onCleanup: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message)
            if (busy) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaFeiLoadingIndicator(style = MaFeiLoadingStyle.Button)
                    Text("正在处理", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            onRetry?.let {
                Text(
                    "重试恢复",
                    Modifier.mafeiInteractive(it, enabled = !busy, radiusDp = 20f, label = "重试恢复")
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (busy) .45f else 1f),
                )
            }
            onCleanup?.let {
                Text(
                    "完成退出，重新登录",
                    Modifier.mafeiInteractive(it, enabled = !busy, radiusDp = 20f, label = "完成退出，重新登录")
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (busy) .45f else 1f),
                )
            }
        }
    }
}

@Composable
private fun LegacyPersonArtwork(
    image: MediaImage?,
    name: String,
    cache: ArtworkMemoryCache,
    fetch: suspend (MediaImage, Int) -> ByteArray?,
    modifier: Modifier = Modifier,
    targetWidthPx: Int = 240,
) {
    MediaArtwork(
        image = image,
        title = name,
        cache = cache,
        fetch = fetch,
        modifier = modifier,
        fallback = { fallbackModifier ->
            Image(
                painter = painterResource(MaFeiLegacyDrawables.castAvatar),
                contentDescription = null,
                modifier = fallbackModifier,
            )
        },
        targetWidthPx = targetWidthPx,
    )
}

private fun MediaItem.preferredPoster(): List<MediaImage> = posterArtworkCandidates()

private fun MediaItem.preferredLandscape(): List<MediaImage> = landscapeArtworkCandidates()

private fun MediaItem.preferredLogo(): MediaImage? =
    images.firstOrNull { it.kind == ImageKind.Logo }

private fun MediaItem.miniPlayerSubtitle(): String = listOfNotNull(
    year?.toString(),
    when (mediaType) {
        cn.mafei.cmp.core.domain.MediaType.Movie -> "电影"
        cn.mafei.cmp.core.domain.MediaType.Series -> "剧集"
        cn.mafei.cmp.core.domain.MediaType.Episode -> "单集"
        cn.mafei.cmp.core.domain.MediaType.Audio -> "音乐"
        else -> null
    },
).joinToString(" · ")

private suspend fun savedJellyfinServerSuggestions(
    runtime: ApplicationRuntime,
): List<ServerDiscoverySuggestion> = runtime.savedConnections()
    .asSequence()
    .filter { it.server.provider == ProviderKind.Jellyfin }
    .distinctBy { it.server.baseUrl }
    // ArkUI's loadPreviouslyUsedServers() reads the current server only. The
    // CMP catalog is already ordered by last activity, so after sign-out the
    // first Jellyfin row is the retained equivalent of currentServerId; do not
    // turn the discovery page into a second account-history list.
    .take(1)
    .map { connection ->
        // The ArkUI page deliberately displayed the saved hostname in both rows;
        // the friendly server name is reserved for an actual UDP discovery reply.
        ServerDiscoverySuggestion(
            type = ServerSuggestionType.Saved,
            name = connection.server.baseUrl,
            address = connection.server.baseUrl,
        )
    }
    .toList()

private suspend fun discoveredJellyfinServerSuggestions(): List<ServerDiscoverySuggestion> =
    discoverLocalJellyfinServers().map { server ->
        ServerDiscoverySuggestion(
            type = ServerSuggestionType.Discovered,
            name = server.name.orEmpty(),
            address = server.address.orEmpty(),
        )
    }

private const val HomeDestination = "home"
/** Product gate: the chasing page stays implemented but hidden until its UX is finalized. */
private const val ShowChasingDestination = false
private const val ChasingDestination = "chasing"
private const val FavoriteDestination = "favorite"
private const val LibraryDestination = "library"
private const val MineDestination = "mine"

private fun FeiniuRoutePreference.toPresentationPreference(): FeiniuConnectionPreference = when (this) {
    FeiniuRoutePreference.Public -> FeiniuConnectionPreference.Public
    FeiniuRoutePreference.Relay -> FeiniuConnectionPreference.Relay
    FeiniuRoutePreference.Auto,
    FeiniuRoutePreference.Speed -> FeiniuConnectionPreference.Speed
}

private fun FeiniuConnectionPreference.toProviderPreference(): FeiniuRoutePreference = when (this) {
    FeiniuConnectionPreference.Speed -> FeiniuRoutePreference.Speed
    FeiniuConnectionPreference.Public -> FeiniuRoutePreference.Public
    FeiniuConnectionPreference.Relay -> FeiniuRoutePreference.Relay
}

private fun legacySortChoices(
    provider: ProviderKind,
    direction: SortDirection,
): List<LegacySortChoice<MediaSort>> {
    // 可排序字段集由 model 的展示风格表给出（core:domain/MediaPresentation.kt），
    // 此处不再判断 provider；UI 只负责把字段翻译成菜单文案。
    val labels = mapOf(
        MediaSortField.Default to "默认",
        MediaSortField.Title to "按名称",
        MediaSortField.DateAdded to "按添加日期",
        MediaSortField.PremiereDate to "按首播日期",
        MediaSortField.DatePlayed to "按播放日期",
        MediaSortField.CommunityRating to "按社区评分",
        MediaSortField.CriticRating to "按影评人评分",
        MediaSortField.ProductionYear to "按出品年份",
        MediaSortField.PlayCount to "按播放次数",
        MediaSortField.Runtime to "按时长",
        MediaSortField.OfficialRating to "按官方评级",
        MediaSortField.Random to "随机",
        MediaSortField.Album to "按专辑",
        MediaSortField.Artist to "按艺术家",
        MediaSortField.AlbumArtist to "按专辑艺术家",
        MediaSortField.AiredEpisodeOrder to "按播出集数顺序",
        MediaSortField.SeriesSortName to "按剧集名称",
        MediaSortField.Studio to "按制作公司",
        MediaSortField.IsFavoriteOrLiked to "按收藏/喜欢",
        MediaSortField.DateLastContentAdded to "按最后添加内容日期",
        MediaSortField.IsUnplayed to "未播放优先",
        MediaSortField.IsPlayed to "已播放优先",
    )
    return MediaDisplayStyle.forProvider(provider).sortableFields.map { field ->
        LegacySortChoice(labels.getValue(field), MediaSort(field, direction))
    }
}
