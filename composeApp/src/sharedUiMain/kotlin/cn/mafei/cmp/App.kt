package cn.mafei.cmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.glass.GlassRenderingEnvironment
import cn.mafei.cmp.common.glass.rememberGlassRenderHealth
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.ui.MaFeiAdaptiveShell
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.common.ui.MaFeiNavItem
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.feature.connect.ConnectScreen
import cn.mafei.cmp.feature.detail.DetailScreen
import cn.mafei.cmp.feature.home.HomeScreen
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.ui.LiquidMiniPlayer
import cn.mafei.cmp.player.ui.MiniPlayerMedia
import cn.mafei.cmp.player.ui.toMiniPlayerPresentation
import cn.mafei.cmp.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

val LocalApplicationRuntime = staticCompositionLocalOf<ApplicationRuntime> {
    error("Application runtime is not installed")
}

@Composable
fun App(runtime: ApplicationRuntime, onThemeChanged: (Boolean) -> Unit = {}) {
    val sessionState by runtime.sessions.state.collectAsState()
    val scope = rememberCoroutineScope()
    var dark by rememberSaveable { mutableStateOf(true) }
    var probe by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(runtime) {
        try {
            // Legacy readers are optional per platform. When a platform reader
            // is supplied, migration must precede normal session restoration so
            // the imported current account enters the same activation path.
            runtime.migrateLegacyData()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "旧版本数据迁移未完成，请稍后重试"
        }
        runtime.sessions.start()
    }
    CompositionLocalProvider(LocalApplicationRuntime provides runtime) {
        if (probe) {
            Column(Modifier.fillMaxSize()) {
                TextButton(
                    onClick = { probe = false },
                    modifier = Modifier.safeDrawingPadding().padding(horizontal = 12.dp),
                ) {
                    MaFeiIcon(MaFeiGlyph.Back, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("返回映光影院")
                }
                Box(Modifier.weight(1f)) { FoundationProbeScreen(onThemeChanged) }
            }
        } else {
            SideEffect { onThemeChanged(dark) }
            MaFeiTheme(dark) {
                val health = rememberGlassRenderHealth()
                GlassRenderingEnvironment(health) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        when (sessionState.status) {
                            ApplicationSessionStatus.NotStarted,
                            ApplicationSessionStatus.Restoring -> StaticAppCanvas {
                                SessionMessage("正在恢复账号…", busy = true)
                            }

                            ApplicationSessionStatus.SignedOut -> StaticAppCanvas {
                                ConnectScreen(
                                    busy = busy,
                                    error = error,
                                    onSubmit = { provider, address, name, password ->
                                        if (!busy) {
                                            busy = true
                                            error = null
                                            scope.launch {
                                                try {
                                                    runtime.connect(provider, address, name, password)
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (failure: Exception) {
                                                    error = connectFailureMessage(failure)
                                                } finally {
                                                    busy = false
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.safeDrawingPadding(),
                                )
                            }

                            ApplicationSessionStatus.Ready -> {
                                val session = sessionState.session
                                if (session == null) {
                                    StaticAppCanvas { SessionMessage("账号会话尚未就绪，请稍后重试") }
                                } else {
                                    key(session) {
                                        BrowseContent(
                                            runtime = runtime,
                                            session = session,
                                            dark = dark,
                                            busy = busy,
                                            onToggleTheme = { dark = !dark },
                                            onOpenProbe = { probe = true },
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

@Composable
private fun BrowseContent(
    runtime: ApplicationRuntime,
    session: ApplicationSession,
    dark: Boolean,
    busy: Boolean,
    onToggleTheme: () -> Unit,
    onOpenProbe: () -> Unit,
    onLogout: () -> Unit,
) {
    val browser = remember(session) { MediaBrowser(runtime.sessions.media, session.media) }
    val state by browser.state.collectAsState()
    val scope = rememberCoroutineScope()
    val homeGridState = rememberLazyGridState()
    val libraryGridState = rememberLazyGridState()
    val artworkCache = remember(session) { ArtworkMemoryCache() }
    val fetchArtwork: suspend (MediaImage) -> ByteArray? = remember(session) {
        { image -> runtime.loadArtwork(session, image) }
    }
    val playback = remember(session) { runtime.playback(session) }
    val playerState = if (playback == null) PlayerState() else playback.controller.state.collectAsState().value
    var playingMedia by remember(session) { mutableStateOf<MediaItem?>(null) }
    var playbackBusy by remember(session) { mutableStateOf(false) }
    var playbackError by remember(session) { mutableStateOf<String?>(null) }
    var destination by rememberSaveable(
        session.selection.serverId.value,
        session.selection.accountId.value,
    ) { mutableStateOf(HomeDestination) }

    LaunchedEffect(browser) { browser.start() }
    LaunchedEffect(state.selectedCollection) {
        homeGridState.scrollToItem(0)
        libraryGridState.scrollToItem(0)
    }
    LaunchedEffect(state.detailId) { playbackError = null }
    LaunchedEffect(playerState.phase) {
        if (playerState.phase == PlayerPhase.Error) playbackError = "播放失败，请重试"
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val fontScale = LocalDensity.current.fontScale
        val layout = remember(maxWidth, maxHeight, fontScale) {
            mafeiLayout(maxWidth.value, maxHeight.value, fontScale)
        }
        val showingDetail = state.detailId != null
        val ambientItem = state.detail
            ?: state.items.firstOrNull { it.id == state.detailId }
            ?: state.items.maxWithOrNull(
                compareBy<MediaItem> { it.dateAddedEpochMs ?: Long.MIN_VALUE }.thenBy { it.title },
            )
        val destinations = remember {
            listOf(
                MaFeiNavItem(HomeDestination, "首页", MaFeiGlyph.Home),
                MaFeiNavItem(LibraryDestination, "媒体库", MaFeiGlyph.Library),
            )
        }
        val libraries = state.collections.map { collection ->
            MaFeiNavItem(collection.id.value, collection.name, MaFeiGlyph.Library)
        }
        val selectDestination: (String) -> Unit = { next ->
            if (showingDetail) browser.back()
            destination = next
        }
        val startPlayback: (MediaItem) -> Unit = { item ->
            if (playback != null && !playbackBusy) {
                playbackBusy = true
                playbackError = null
                scope.launch {
                    try {
                        val generation = playback.play(
                            itemId = item.id,
                            resumePositionMs = item.playbackResumePositionMs(),
                        )
                        if (generation == null) {
                            playbackError = "当前媒体暂时无法播放"
                        } else {
                            playingMedia = item
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        playbackError = "播放准备失败，请重试"
                    } finally {
                        playbackBusy = false
                    }
                }
            }
        }

        MaFeiAdaptiveShell(
            layout = layout,
            destinations = destinations,
            selectedDestination = destination,
            onDestination = selectDestination,
            libraries = libraries,
            selectedLibrary = state.selectedCollection?.value,
            onLibrary = { id ->
                if (showingDetail) browser.back()
                destination = LibraryDestination
                state.collections.firstOrNull { it.id.value == id }?.let { collection ->
                    if (collection.id != state.selectedCollection) {
                        scope.launch { browser.select(collection.id) }
                    }
                }
            },
            accountLabel = "${session.connection.account.displayName}\n${session.connection.server.name}",
            showBottomNavigation = !showingDetail,
            background = {
                AmbientBackground(
                    item = ambientItem,
                    cache = artworkCache,
                    fetch = fetchArtwork,
                )
            },
            accountActions = {
                AccountMenu(
                    expanded = layout.navigation == NavigationPlacement.Sidebar,
                    dark = dark,
                    busy = busy,
                    onToggleTheme = onToggleTheme,
                    onOpenProbe = onOpenProbe,
                    onLogout = onLogout,
                )
            },
            scene = {
                if (showingDetail) {
                    DetailScreen(
                        item = state.detail,
                        loading = state.detailLoading,
                        error = state.detailError,
                        onBack = browser::back,
                        onRetry = {
                            state.detailId?.let { id -> scope.launch { browser.openDetail(id) } }
                        },
                        onPlay = state.detail?.takeIf { it.canPlay && playback != null }?.let { item ->
                            { startPlayback(item) }
                        },
                        playbackBusy = playbackBusy,
                        playbackError = playbackError,
                        modifier = Modifier.fillMaxSize(),
                        artwork = { item, modifier ->
                            MediaArtwork(item.preferredPoster(), item.title, artworkCache, fetchArtwork, modifier)
                        },
                    )
                } else {
                    HomeScreen(
                        collections = state.collections,
                        items = state.items,
                        selectedCollection = state.selectedCollection,
                        loading = state.loading,
                        error = state.error,
                        stale = state.stale,
                        canLoadMore = state.nextCursor != null,
                        overview = destination == HomeDestination,
                        layout = layout,
                        onCollection = { collection -> scope.launch { browser.select(collection.id) } },
                        onItem = { item -> scope.launch { browser.openDetail(item.id) } },
                        onRetry = { scope.launch { browser.refresh() } },
                        onLoadMore = { scope.launch { browser.loadMore() } },
                        onOpenLibrary = { destination = LibraryDestination },
                        accountActions = {
                            AccountMenu(
                                expanded = false,
                                dark = dark,
                                busy = busy,
                                onToggleTheme = onToggleTheme,
                                onOpenProbe = onOpenProbe,
                                onLogout = onLogout,
                            )
                        },
                        brandTitle = "马飞",
                        brandSubtitle = "我的影院",
                        modifier = Modifier.fillMaxSize(),
                        gridState = if (destination == HomeDestination) homeGridState else libraryGridState,
                        artwork = { item, modifier ->
                            MediaArtwork(item.preferredPoster(), item.title, artworkCache, fetchArtwork, modifier)
                        },
                        backdropArtwork = { item, modifier ->
                            MediaArtwork(item.preferredLandscape(), item.title, artworkCache, fetchArtwork, modifier)
                        },
                    )
                }
            },
            overlay = { backdrop ->
                val media = playingMedia?.takeIf { it.id.value == playerState.resourceId }
                if (media != null) {
                    LiquidMiniPlayer(
                        media = MiniPlayerMedia(
                            id = media.id.value,
                            title = media.title,
                            subtitle = media.miniPlayerSubtitle(),
                        ),
                        presentation = playerState.toMiniPlayerPresentation(),
                        backdrop = backdrop,
                        onOpen = {
                            if (state.detailId != media.id) scope.launch { browser.openDetail(media.id) }
                        },
                        onTogglePlay = {
                            scope.launch {
                                try {
                                    when (playerState.phase) {
                                        PlayerPhase.Playing, PlayerPhase.Buffering -> playback?.controller?.pause()
                                        PlayerPhase.Ended, PlayerPhase.Error -> startPlayback(media)
                                        PlayerPhase.Ready, PlayerPhase.Paused -> playback?.controller?.play()
                                        PlayerPhase.Idle, PlayerPhase.Loading, PlayerPhase.Released -> Unit
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    playbackError = "播放器操作失败，请重试"
                                }
                            }
                        },
                        artwork = { modifier ->
                            MediaArtwork(media.preferredPoster(), media.title, artworkCache, fetchArtwork, modifier)
                        },
                        modifier = Modifier
                            .align(
                                if (layout.navigation == NavigationPlacement.Bottom) Alignment.BottomCenter
                                else Alignment.BottomEnd,
                            )
                            .padding(
                                start = if (layout.navigation == NavigationPlacement.Bottom) 16.dp else 24.dp,
                                end = 16.dp,
                                bottom = if (layout.navigation == NavigationPlacement.Bottom && !showingDetail) 88.dp else 24.dp,
                            )
                            .widthIn(max = 430.dp)
                            .fillMaxWidth(),
                    )
                }
            },
        )
    }
}

@Composable
private fun AccountMenu(
    expanded: Boolean,
    dark: Boolean,
    busy: Boolean,
    onToggleTheme: () -> Unit,
    onOpenProbe: () -> Unit,
    onLogout: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        if (expanded) {
            TextButton(onClick = { menu = true }, enabled = !busy) {
                AccountAvatar(32.dp)
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(1.dp), horizontalAlignment = Alignment.Start) {
                    Text("账号与设置", style = MaterialTheme.typography.labelLarge)
                    Text("外观、实验与退出", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                AccountAvatar(32.dp)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (dark) "切换浅色外观" else "切换深色外观") },
                onClick = {
                    menu = false
                    onToggleTheme()
                },
            )
            DropdownMenuItem(
                text = { Text("界面实验室") },
                onClick = {
                    menu = false
                    onOpenProbe()
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("退出登录", color = MaterialTheme.colorScheme.error) },
                enabled = !busy,
                onClick = {
                    menu = false
                    onLogout()
                },
            )
        }
    }
}

@Composable
private fun AccountAvatar(size: Dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = .18f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .42f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            MaFeiIcon(MaFeiGlyph.Account, Modifier.size(size * .58f), MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AmbientBackground(
    item: MediaItem?,
    cache: ArtworkMemoryCache,
    fetch: suspend (MediaImage) -> ByteArray?,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val image = item?.preferredLandscape()
        if (item != null && image != null) {
            MediaArtwork(
                image = image,
                title = item.title,
                cache = cache,
                fetch = fetch,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = 1.12f
                    scaleY = 1.12f
                    alpha = .68f
                }.blur(40.dp),
                fallback = { fallback ->
                    Box(fallback.background(MaterialTheme.colorScheme.background))
                },
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = .18f),
                        MaterialTheme.colorScheme.background.copy(alpha = .56f),
                        MaterialTheme.colorScheme.background.copy(alpha = .94f),
                    ),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = .08f),
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = .26f),
                    ),
                ),
            ),
        )
    }
}

@Composable
private fun StaticAppCanvas(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = .16f),
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.secondary.copy(alpha = .10f),
                ),
            ),
        ),
        content = content,
    )
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
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            onRetry?.let { OutlinedButton(it, enabled = !busy) { Text("重试恢复") } }
            onCleanup?.let { TextButton(it, enabled = !busy) { Text("完成退出，重新登录") } }
        }
    }
}

private fun MediaItem.preferredPoster(): MediaImage? =
    images.firstOrNull { it.kind == ImageKind.Primary }
        ?: images.firstOrNull { it.kind == ImageKind.Thumb }
        ?: images.firstOrNull()

private fun MediaItem.preferredLandscape(): MediaImage? =
    images.firstOrNull { it.kind == ImageKind.Backdrop }
        ?: images.firstOrNull { it.kind == ImageKind.Thumb }
        ?: preferredPoster()

private fun MediaItem.playbackResumePositionMs(): Long {
    val saved = userData.playbackPositionMs.coerceAtLeast(0L)
    val duration = runtimeMs?.takeIf { it > 0L } ?: return saved
    return saved.takeIf { it < (duration - 10_000L).coerceAtLeast(0L) } ?: 0L
}

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

private const val HomeDestination = "home"
private const val LibraryDestination = "library"
