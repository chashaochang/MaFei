package cn.mafei.cmp

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.design_system.MaFeiWindowEnvironment
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.currentMaFeiSafeArea
import cn.mafei.cmp.common.design_system.mafeiAdaptiveWindow
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.glass.GlassRenderingEnvironment
import cn.mafei.cmp.common.glass.rememberGlassRenderHealth
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.design_system.MaFeiSurface
import cn.mafei.cmp.common.design_system.withUsableWidth
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.media.landscapeArtworkCandidates
import cn.mafei.cmp.common.media.posterArtworkCandidates
import cn.mafei.cmp.common.media.prefetchLandscapeArtwork
import cn.mafei.cmp.common.ui.MaFeiAdaptiveShell
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiGlassConfirmDialog
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.common.ui.MaFeiNavItem
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
import cn.mafei.cmp.feature.connect.rememberConnectFormState
import cn.mafei.cmp.feature.detail.DetailScreen
import cn.mafei.cmp.feature.detail.PersonDetailScreen
import cn.mafei.cmp.feature.detail.SeasonDetailScreen
import cn.mafei.cmp.feature.favorite.FavoriteScreen
import cn.mafei.cmp.feature.home.HomeHeroBackdropState
import cn.mafei.cmp.feature.home.HomeHeroWindowBackdrop
import cn.mafei.cmp.feature.home.HomeScreen
import cn.mafei.cmp.feature.home.LibraryIndexScreen
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.media.VideoListScreen
import cn.mafei.cmp.feature.search.SearchScreen
import cn.mafei.cmp.feature.settings.AccountSwitchItem
import cn.mafei.cmp.feature.settings.AccountSwitchScreen
import cn.mafei.cmp.feature.settings.AddAccountScreen
import cn.mafei.cmp.feature.settings.AddGroupScreen
import cn.mafei.cmp.feature.settings.AboutPrivacyScreen
import cn.mafei.cmp.feature.settings.AppearanceScreen
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
import cn.mafei.cmp.session.*
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource

val LocalApplicationRuntime = staticCompositionLocalOf<ApplicationRuntime> {
    error("Application runtime is not installed")
}

@Composable
fun App(runtime: ApplicationRuntime, onThemeChanged: (Boolean) -> Unit = {}) {
    val sessionState by runtime.sessions.state.collectAsState()
    val scope = rememberCoroutineScope()
    var probe by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var starting by remember(runtime) { mutableStateOf(true) }
    var showStartupProgress by remember(runtime) { mutableStateOf(false) }

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
            starting = false
        }
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
            // Dark only. Every product page is built dark-first (light text on dark
            // atmospheres) and the login/history backgrounds are dark images, so an
            // appearance setting could only tint the surfaces and leave the pages
            // behind — the "light" option was removed rather than left half-working.
            // Hosts are still told which appearance to paint their system chrome in.
            SideEffect { onThemeChanged(true) }
            MaFeiTheme {
                val health = rememberGlassRenderHealth()
                GlassRenderingEnvironment(health) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        if (starting) {
                            StaticAppCanvas {
                                if (showStartupProgress) SessionMessage("正在启动…", busy = true)
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
private sealed interface BrowseRoute : NavKey

@Serializable
private data object BrowseHomeRoute : BrowseRoute

@Serializable
private data object BrowseFavoriteRoute : BrowseRoute

@Serializable
private data object BrowseLibraryIndexRoute : BrowseRoute

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
private data class BrowseConnectAccountRoute(val provider: ProviderKind) : BrowseRoute

@Serializable
private data object BrowseSettingsRoute : BrowseRoute

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

@Serializable
private data class BrowseDetailRoute(val mediaId: String) : BrowseRoute

@Serializable
private data class BrowsePersonRoute(val personId: String) : BrowseRoute

@Serializable
private data class BrowseSeasonRoute(val seriesId: String, val seasonId: String) : BrowseRoute

@Serializable
private data class BrowseEpisodeRoute(
    val seriesId: String,
    val seasonId: String,
    val episodeId: String,
) : BrowseRoute

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SignedOutFlow(
    runtime: ApplicationRuntime,
    busy: Boolean,
    error: String?,
    onBusyChange: (Boolean) -> Unit,
    onErrorChange: (String?) -> Unit,
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
                    if (!runtime.deleteSavedConnection(connection)) {
                        historyError = "该账号正在使用，无法删除"
                    }
                    loadHistory()
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
            loading = historyLoading,
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
            if (supportsTwoPane) {
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
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun BrowseContent(
    runtime: ApplicationRuntime,
    session: ApplicationSession,
    busy: Boolean,
    onLogout: () -> Unit,
) {
    val browser = remember(session) {
        MediaBrowser(
            repository = runtime.sessions.media,
            session = session.media,
            capabilities = session.binding.adapter.capabilities,
            playbackPreferences = runtime.playbackPreferences,
        )
    }
    val state by browser.state.collectAsState()
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
    val favoriteGridState = rememberLazyGridState()
    val libraryGridState = rememberLazyGridState()
    val searchGridState = rememberLazyGridState()
    var searchHistories by remember(session) { mutableStateOf(emptyList<String>()) }
    var accountConnections by remember(session) { mutableStateOf(emptyList<SavedConnection>()) }
    var accountsLoading by remember(session) { mutableStateOf(false) }
    var accountsError by remember(session) { mutableStateOf<String?>(null) }
    var switchingAccountKey by remember(session) { mutableStateOf<String?>(null) }
    var deletingAccountKey by remember(session) { mutableStateOf<String?>(null) }
    var accountConnectBusy by remember(session) { mutableStateOf(false) }
    var accountConnectError by remember(session) { mutableStateOf<String?>(null) }
    val artworkCache = remember(session) { ArtworkMemoryCache() }
    val fetchArtwork: suspend (MediaImage, Int) -> ByteArray? = remember(session) {
        { image, targetWidthPx -> runtime.loadArtwork(session, image, targetWidthPx) }
    }
    val playback = remember(session) { runtime.playback(session) }
    val playerState = if (playback == null) PlayerState() else playback.controller.state.collectAsState().value
    var playingMedia by remember(session) { mutableStateOf<MediaItem?>(null) }
    var playbackBusy by remember(session) { mutableStateOf(false) }
    var playbackError by remember(session) { mutableStateOf<String?>(null) }
    var playerExpanded by rememberSaveable(
        session.selection.serverId.value,
        session.selection.accountId.value,
    ) { mutableStateOf(false) }
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
    // 首页轮播背景的窗口级状态：背景画在 shell 窗口画布上（全窗、与侧栏让位
    // 无关），HomeScreen 只往里喂实时状态（items/pager/滚动跟随参数）。
    val heroBackdrop = remember { HomeHeroBackdropState() }
    // 「我的」有独立的导航容器：回退栈只装「我的」自己的页面，从我的进入的二级页不会压到
    // 主浏览栈上。容器在大屏以居中毛玻璃模态呈现、小屏以全屏页呈现，切换形态时这个栈原样
    // 保留（它是状态而不是渲染树的一部分），所以用户不会觉得断片。
    val mineBackStack = remember(session.selection.serverId.value, session.selection.accountId.value) {
        mutableStateListOf<BrowseRoute>(BrowseMineRoute)
    }
    val destination = when (currentRoute) {
        BrowseHomeRoute, BrowseSearchRoute -> HomeDestination
        BrowseFavoriteRoute -> FavoriteDestination
        BrowseMineRoute,
        BrowseMineHistoryRoute,
        BrowseAccountSwitchRoute,
        BrowseAddAccountRoute,
        is BrowseConnectAccountRoute,
        BrowseSettingsRoute,
        BrowseAppearanceRoute,
        BrowseAddGroupRoute,
        BrowseAboutPrivacyRoute,
        BrowseUserAgreementRoute -> MineDestination
        else -> LibraryDestination
    }
    val chromeCompactProgress = remember(destination, homeGridState, favoriteGridState, libraryGridState) {
        derivedStateOf {
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
    LaunchedEffect(state.selectedCollection) {
        libraryGridState.scrollToItem(0)
    }
    LaunchedEffect(currentRoute) {
        if (currentRoute == BrowseFavoriteRoute) browser.refreshFavorites()
    }
    // 独立播放历史：进入「我的 → 播放历史」时加载（resume + 已看完双源分页）。
    LaunchedEffect(currentRoute) {
        if (currentRoute == BrowseMineHistoryRoute) browser.loadHistory()
        librarySelectedIds = emptySet()
    }
    // 删除可用性只探测一次：服务器用户策略决定是否出现删除入口。
    LaunchedEffect(browser) { browser.refreshDeleteAvailability() }
    LaunchedEffect(state.detailId) { playbackError = null }
    LaunchedEffect(playerState.phase) {
        if (playerState.phase == PlayerPhase.Error) playbackError = "播放失败，请重试"
        if (playerState.phase == PlayerPhase.Idle || playerState.phase == PlayerPhase.Released) {
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
            mafeiLayout(
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
                ),
            )
        }
        val showingMediaDetail = currentRoute is BrowseDetailRoute ||
            currentRoute is BrowseSeasonRoute || currentRoute is BrowseEpisodeRoute
        val showingPersonDetail = currentRoute is BrowsePersonRoute
        val showingDetail = showingMediaDetail || showingPersonDetail
        // 「我的」的二级页现在住在它自己的回退栈里，不再出现在 currentRoute 上。
        val showingFullPageRoute = showingDetail || currentRoute == BrowseSearchRoute
        // 「我的」容器的两种形态：小屏（手机）全屏铺开、看起来就是普通页面跳转；其余尺寸
        // 用居中的毛玻璃模态。形态只由窗口决定，容器自己的回退栈不受影响。
        val mineFullScreen = layout.navigation == NavigationPlacement.Bottom
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
            listOf(
                MaFeiNavItem(HomeDestination, "首页", MaFeiGlyph.Home),
                MaFeiNavItem(FavoriteDestination, "收藏", MaFeiGlyph.Favorite),
                MaFeiNavItem(LibraryDestination, "媒体库", MaFeiGlyph.Library),
                MaFeiNavItem(MineDestination, "我的", MaFeiGlyph.Account),
            )
        }
        val libraries = state.collections.map { collection ->
            MaFeiNavItem(collection.id.value, collection.name, MaFeiGlyph.Library)
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
                browseBackStack.clear()
                browseBackStack += when (next) {
                    HomeDestination -> BrowseHomeRoute
                    FavoriteDestination -> BrowseFavoriteRoute
                    else -> BrowseLibraryIndexRoute
                }
            }
        }
        val startPlayback: (MediaItem) -> Unit = { item ->
            if (playback != null && !playbackBusy) {
                playbackBusy = true
                playbackError = null
                scope.launch {
                    try {
                        // 详情页上选过的版本/音轨/字幕只对当前详情条目生效。
                        val generation = playback.play(
                            itemId = item.id,
                            resumePositionMs = item.playbackResumePositionMs(),
                            options = browser.playbackOptionsFor(item),
                        )
                        if (generation == null) {
                            playbackError = "当前媒体暂时无法播放"
                        } else {
                            playingMedia = item
                            playerExpanded = true
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
        val openSearch: () -> Unit = {
            if (browseBackStack.lastOrNull() != BrowseSearchRoute) {
                browser.clearSearch()
                browseBackStack += BrowseSearchRoute
            }
        }
        // One source for the account surface, shared by the modal sheet and the
        // (now unreachable from the sidebar) legacy route. Secondary entries close
        // the sheet first so the pushed page is not hidden behind the modal.
        val mineContent: @Composable (Modifier) -> Unit = { mineModifier ->
            LegacyMineScreen(
                profile = MineProfile(
                    userName = session.connection.account.displayName,
                    serverName = legacyVisibleServerName(session.connection),
                    serverVersion = session.connection.server.serverVersion.orEmpty(),
                    connectionDescription = legacyConnectionDescription(session.connection),
                ),
                historyItems = state.homeFeed.resume,
                historyLoading = state.loading && state.homeFeed.resume.isEmpty(),
                historyError = state.homeError,
                layout = layout,
                busy = busy,
                onHistoryItem = startPlayback,
                onHistoryRetry = { scope.launch { browser.refresh() } },
                // 「我的」里的二级页推到**容器自己的**回退栈上，容器不关闭 —— 模态 / 全屏
                // 两种呈现共用同一条栈。
                onHistoryMore = {
                    if (mineBackStack.lastOrNull() != BrowseMineHistoryRoute) {
                        mineBackStack += BrowseMineHistoryRoute
                    }
                },
                onSwitchAccount = {
                    loadAccounts()
                    if (mineBackStack.lastOrNull() != BrowseAccountSwitchRoute) {
                        mineBackStack += BrowseAccountSwitchRoute
                    }
                },
                onSettings = {
                    if (mineBackStack.lastOrNull() != BrowseSettingsRoute) {
                        mineBackStack += BrowseSettingsRoute
                    }
                },
                onAddGroup = {
                    if (mineBackStack.lastOrNull() != BrowseAddGroupRoute) {
                        mineBackStack += BrowseAddGroupRoute
                    }
                },
                onLogout = {
                    minePresented = false
                    onLogout()
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
                                    canLoadMore = state.historyResumeCursor != null ||
                                        state.historyCompletedCursor != null,
                                    loadingMore = state.historyLoadingMore,
                                    onLoadMore = { scope.launch { browser.loadMoreHistory() } },
                                    modifier = Modifier.fillMaxSize(),
                                    artwork = { item, modifier ->
                                        MediaArtwork(item.preferredPoster(), item.title, artworkCache, fetchArtwork, modifier)
                                    },
                                )
                            }
                            BrowseAccountSwitchRoute -> NavEntry(route) {
                                val accountItems = accountConnections.map { connection ->
                                    AccountSwitchItem(
                                        key = connection.credentialKey,
                                        provider = connection.server.provider,
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
                                LaunchedEffect(formState, route.provider) {
                                    formState.provider = route.provider
                                    formState.baseUrl = ""
                                    formState.username = ""
                                    formState.password = ""
                                    formState.rememberPassword = true
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
                                    // Moved here from 我的: settings owns the informational
                                    // entries, the account sheet keeps account actions.
                                    onOpenAbout = {
                                        if (layout.navigation == NavigationPlacement.Bottom) {
                                            uriHandler.openUri(PRIVACY_AGREEMENT_URL)
                                        } else if (mineBackStack.lastOrNull() != BrowseAboutPrivacyRoute) {
                                            mineBackStack += BrowseAboutPrivacyRoute
                                        }
                                    },
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
                    when (playerState.phase) {
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
        val activeMedia = playingMedia?.takeIf { it.id.value == playerState.resourceId }
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

        // 删除确认弹窗的玻璃源：捕获层包住外壳/播放器，弹窗是它的**兄弟** —— 记录者与
        // 消费者绝不能在同一棵子树里（详见 N1 崩溃规则）。只在弹窗出现时挂捕获，平时零开销。
        Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().then(
                if (pendingDeleteMedia != null) Modifier.captureGlassBackdrop(deleteConfirmBackdrop) else Modifier,
            ),
        ) {
        if (playerExpanded && activeMedia != null && playback != null) {
            NowPlayingScreen(
                media = NowPlayingMedia(
                    id = activeMedia.id.value,
                    title = activeMedia.title,
                    subtitle = activeMedia.miniPlayerSubtitle(),
                    description = activeMedia.overview,
                ),
                state = playerState,
                capabilities = playback.controller.capabilities,
                onBack = { playerExpanded = false },
                onTogglePlay = togglePlayback,
                onRetry = { startPlayback(activeMedia) },
                onSeek = { pendingSeekPosition = it },
                artwork = { modifier ->
                    MediaArtwork(
                        activeMedia.preferredPoster(),
                        activeMedia.title,
                        artworkCache,
                        fetchArtwork,
                        modifier,
                    )
                },
                backdropArtwork = { modifier ->
                    MediaArtwork(
                        activeMedia.preferredLandscape(),
                        activeMedia.title,
                        artworkCache,
                        fetchArtwork,
                        modifier,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else MaFeiAdaptiveShell(
            layout = layout,
            destinations = destinations,
            selectedDestination = if (minePresented) MineDestination else destination,
            onDestination = selectDestination,
            accountName = session.connection.account.displayName,
            libraries = libraries,
            selectedLibrary = state.selectedCollection?.value,
            onLibrary = { id ->
                if (showingPersonDetail) browser.clearPerson()
                if (state.detailId != null) browser.back()
                state.collections.firstOrNull { it.id.value == id }?.let { collection ->
                    if (collection.id != state.selectedCollection) {
                        scope.launch { browser.select(collection.id) }
                    }
                    browseBackStack.clear()
                    browseBackStack += BrowseLibraryRoute(collection.id.value)
                }
            },
            accountLabel = "${session.connection.account.displayName}\n${session.connection.server.name}",
            // 小屏上「我的」容器是场景里的全屏页：根页和标签页一样保留底部标签栏（MineScreen
            // 自己留了 112dp），但它里面的二级页在本重构之前本来就是"整页路由"，那时标签栏
            // 是收起来的，所以这里也让它们把标签栏收走 —— 否则那些页面的最后一行会被压住。
            showBottomNavigation = !showingFullPageRoute &&
                !(minePresented && mineFullScreen && mineBackStack.size > 1),
            onSearch = openSearch,
            selectedSearch = currentRoute == BrowseSearchRoute,
            // The compact dock is the other half of the mini-player morph.
            // With no active media there is nothing to merge into, so page
            // scrolling must leave the primary navigation at full size.
            bottomNavigationCompactProgress = {
                if (activeMedia != null) chromeCompactProgress.value else 0f
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
                if (currentRoute == BrowseHomeRoute && !showingDetail) {
                    HomeHeroWindowBackdrop(
                        state = heroBackdrop,
                        artwork = { item, modifier ->
                            MediaArtwork(
                                item.landscapeArtworkCandidates(),
                                item.title,
                                artworkCache,
                                fetchArtwork,
                                modifier,
                                targetWidthPx = 960,
                            )
                        },
                    )
                }
            },
            accountActions = {},
            scene = {
                val popBrowseRoute: () -> Unit = {
                    val route = browseBackStack.lastOrNull()
                    val revealed = browseBackStack.getOrNull(browseBackStack.lastIndex - 1)
                    when (route) {
                        is BrowseDetailRoute -> browser.back()
                        is BrowseEpisodeRoute -> scope.launch {
                            // ArkUI opens an episode as a real VideoDetailPage. Restore the
                            // parent series and selected season when that page is popped.
                            browser.openDetail(MediaId(route.seriesId))
                            browser.selectSeason(MediaId(route.seasonId))
                        }
                        is BrowsePersonRoute -> {
                            browser.clearPerson()
                            when (revealed) {
                                is BrowseDetailRoute -> if (state.detail?.id?.value != revealed.mediaId) {
                                    scope.launch { browser.openDetail(MediaId(revealed.mediaId)) }
                                }
                                is BrowseSeasonRoute -> if (
                                    state.detail?.id?.value != revealed.seriesId ||
                                    state.selectedSeasonId?.value != revealed.seasonId
                                ) {
                                    scope.launch {
                                        browser.openDetail(MediaId(revealed.seriesId))
                                        browser.selectSeason(MediaId(revealed.seasonId))
                                    }
                                }
                                is BrowseEpisodeRoute -> if (state.detail?.id?.value != revealed.episodeId) {
                                    scope.launch { browser.openDetail(MediaId(revealed.episodeId)) }
                                }
                                else -> Unit
                            }
                        }
                        BrowseSearchRoute -> browser.clearSearch()
                        else -> Unit
                    }
                    if (browseBackStack.size > 1) browseBackStack.removeLastOrNull()
                }
                val openDetail: (MediaItem) -> Unit = { item ->
                    if (browseBackStack.lastOrNull() !is BrowseDetailRoute) {
                        browseBackStack += BrowseDetailRoute(item.id.value)
                    }
                    scope.launch { browser.openDetail(item.id) }
                }
                val openPerson: (cn.mafei.cmp.core.domain.PersonRef) -> Unit = { person ->
                    person.id?.let { id ->
                        val route = BrowsePersonRoute(id.value)
                        if (browseBackStack.lastOrNull() != route) {
                            browseBackStack += route
                            scope.launch { browser.openPerson(id) }
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
                                    layout = layout,
                                    onCollection = { collection -> scope.launch { browser.loadHomeLatest(collection.id) } },
                                    onBrowseCollection = browseCollection,
                                    onItem = openDetail,
                                    onPlay = startPlayback,
                                    onRetry = { scope.launch { browser.refresh() } },
                                    onLoadMore = { scope.launch { browser.loadMore() } },
                                    onOpenLibrary = { selectDestination(LibraryDestination) },
                                    onSearch = openSearch,
                                    bottomOverlayInset = if (activeMedia != null) 76.dp else 0.dp,
                                    modifier = Modifier.fillMaxSize(),
                                    gridState = homeGridState,
                                    artwork = { item, modifier ->
                                        MediaArtwork(
                                            item.posterArtworkCandidates(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            targetWidthPx = 480,
                                        )
                                    },
                                    backdropArtwork = { item, modifier ->
                                        MediaArtwork(
                                            item.landscapeArtworkCandidates(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            targetWidthPx = 960,
                                        )
                                    },
                                    prefetchBackdrop = { item ->
                                        prefetchLandscapeArtwork(item, artworkCache, fetchArtwork)
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
                            BrowseLibraryIndexRoute -> NavEntry(route) {
                                LibraryIndexScreen(
                                    collections = state.collections,
                                    loading = state.loading,
                                    refreshing = state.refreshing,
                                    error = state.error,
                                    layout = layout,
                                    onCollection = browseCollection,
                                    onRetry = { scope.launch { browser.refresh() } },
                                    bottomOverlayInset = if (activeMedia != null) 76.dp else 0.dp,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            BrowseFavoriteRoute -> NavEntry(route) {
                                FavoriteScreen(
                                    items = state.favoriteItems,
                                    totalEstimate = state.favoriteTotalEstimate,
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
                                    onItem = openDetail,
                                    bottomOverlayInset = if (activeMedia != null) 76.dp else 0.dp,
                                    modifier = Modifier.fillMaxSize(),
                                    artwork = { item, modifier ->
                                        MediaArtwork(item.preferredPoster(), item.title, artworkCache, fetchArtwork, modifier)
                                    },
                                )
                            }
                            BrowseSearchRoute -> NavEntry(route) {
                                SearchScreen(
                                    query = state.searchQuery,
                                    results = state.searchItems,
                                    totalEstimate = state.searchTotalEstimate,
                                    histories = searchHistories,
                                    loading = state.searchLoading,
                                    loadingMore = state.searchLoadingMore,
                                    error = state.searchError,
                                    canLoadMore = state.searchNextCursor != null,
                                    layout = layout,
                                    gridState = searchGridState,
                                    onBack = popBrowseRoute,
                                    onQueryChange = { term ->
                                        scope.launch {
                                            searchGridState.scrollToItem(0)
                                            browser.search(term)
                                        }
                                    },
                                    onSubmit = { term ->
                                        recordSearchHistory(term)
                                        scope.launch {
                                            searchGridState.scrollToItem(0)
                                            browser.search(term)
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
                                    onOpen = openDetail,
                                    onLoadMore = { scope.launch { browser.loadMoreSearch() } },
                                    modifier = Modifier.fillMaxSize(),
                                    artwork = { item, modifier ->
                                        MediaArtwork(item.preferredPoster(), item.title, artworkCache, fetchArtwork, modifier)
                                    },
                                )
                            }
                            is BrowseLibraryRoute -> NavEntry(route) {
                                VideoListScreen(
                                    title = state.collections.firstOrNull { it.id.value == route.collectionId }?.name.orEmpty(),
                                    items = state.items,
                                    totalEstimate = state.totalEstimate,
                                    selectedSort = state.librarySort,
                                    sortChoices = librarySortChoices,
                                    loading = state.loading,
                                    refreshing = state.refreshing,
                                    loadingMore = state.loadingMore,
                                    error = state.error,
                                    canLoadMore = state.nextCursor != null,                                    layout = layout,
                                    gridState = libraryGridState,
                                    onBack = popBrowseRoute.takeIf { layout.navigation == NavigationPlacement.Bottom },
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
                                    onItem = openDetail,
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
                                    bottomOverlayInset = if (activeMedia != null) 76.dp else 0.dp,
                                    modifier = Modifier.fillMaxSize(),
                                    artwork = { item, modifier ->
                                        MediaArtwork(item.preferredPoster(), item.title, artworkCache, fetchArtwork, modifier)
                                    },
                                )
                            }
                            is BrowseDetailRoute -> NavEntry(route) {
                                val detailPlayTarget = state.detail?.takeIf { it.canPlay }
                                    ?: state.detailEpisodes.firstOrNull {
                                        it.canPlay && !it.userData.isPlayed && !it.userData.hasProgress
                                    }
                                    ?: state.detailEpisodes.firstOrNull { it.canPlay }
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
                                    similar = state.detailSimilar,
                                    similarLoading = state.detailSimilarLoading,
                                    onSimilarItemSelected = openDetail,
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
                                            browseBackStack += BrowseSeasonRoute(route.mediaId, season.id.value)
                                        }
                                        scope.launch { browser.selectSeason(season.id) }
                                    },
                                    playbackBusy = playbackBusy,
                                    playbackError = playbackError,
                                    libraryName = state.collections.firstOrNull {
                                        it.id == state.selectedCollection
                                    }?.name,
                                    modifier = Modifier.fillMaxSize(),
                                    artwork = { item, modifier ->
                                        MediaArtwork(
                                            item.preferredPoster(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            targetWidthPx = 480,
                                        )
                                    },
                                    backdropArtwork = { item, modifier ->
                                        MediaArtwork(
                                            item.preferredLandscape(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            targetWidthPx = 720,
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
                                            images = item.landscapeArtworkCandidates(),
                                            title = item.title,
                                            cache = artworkCache,
                                            fetch = fetchArtwork,
                                            modifier = modifier,
                                            targetWidthPx = 240,
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
                                    onRetry = { scope.launch { browser.openPerson(MediaId(route.personId)) } },
                                    onItem = openDetail,
                                    canFavorite = browser.capabilities.favoritesEdit,
                                    onToggleFavorite = { scope.launch { browser.togglePersonFavorite() } },
                                    favoriteBusy = state.personFavoriteBusy,
                                    onLoadMoreMovies = { scope.launch { browser.loadMorePersonWorks(MediaType.Movie) } },
                                    onLoadMoreSeries = { scope.launch { browser.loadMorePersonWorks(MediaType.Series) } },
                                    onLoadMoreEpisodes = { scope.launch { browser.loadMorePersonWorks(MediaType.Episode) } },
                                    loadingMoreSections = state.personLoadingMore.map { it.name.lowercase() }.toSet(),
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
                                            item.preferredPoster(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            targetWidthPx = 360,
                                        )
                                    },
                                )
                            }
                            is BrowseSeasonRoute -> NavEntry(route) {
                                val series = state.detail?.takeIf { it.id.value == route.seriesId }
                                val season = state.detailSeasons.firstOrNull { it.id.value == route.seasonId }
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
                                    val playTarget = state.detailEpisodes.firstOrNull {
                                        it.canPlay && !it.userData.isPlayed && it.userData.hasProgress
                                    } ?: state.detailEpisodes.firstOrNull {
                                        it.canPlay && !it.userData.isPlayed
                                    } ?: state.detailEpisodes.firstOrNull { it.canPlay }
                                    SeasonDetailScreen(
                                        series = series,
                                        season = season,
                                        seasons = state.detailSeasons,
                                        episodes = state.detailEpisodes,
                                        loading = state.detailSectionsLoading,
                                        error = state.detailSectionsError,
                                        onBack = popBrowseRoute,
                                        onSeasonSelected = { nextSeason ->
                                            val lastIndex = browseBackStack.lastIndex
                                            if (lastIndex >= 0) {
                                                browseBackStack[lastIndex] = BrowseSeasonRoute(route.seriesId, nextSeason.id.value)
                                            }
                                            scope.launch { browser.selectSeason(nextSeason.id) }
                                        },
                                        onEpisodeSelected = { episode ->
                                            browseBackStack += BrowseEpisodeRoute(
                                                seriesId = route.seriesId,
                                                seasonId = route.seasonId,
                                                episodeId = episode.id.value,
                                            )
                                        },
                                        onPlayFirst = playTarget?.takeIf { playback != null }?.let { item ->
                                            { startPlayback(item) }
                                        },
                                        playTarget = playTarget,
                                        canLoadMoreEpisodes = state.detailEpisodesNextCursor != null,
                                        episodesLoadingMore = state.detailEpisodesLoadingMore,
                                        onLoadMoreEpisodes = { scope.launch { browser.loadMoreEpisodes() } },
                                        onTogglePlayed = if (browser.capabilities.watchedEdit) {
                                            { scope.launch { browser.toggleWatched(season) } }
                                        } else null,
                                        playbackBusy = playbackBusy,
                                        actionsBusy = state.detailActionBusy,
                                        actionsError = state.detailActionError,
                                        libraryName = state.collections.firstOrNull {
                                            it.id == state.selectedCollection
                                        }?.name,
                                        modifier = Modifier.fillMaxSize(),
                                        posterArtwork = { item, modifier ->
                                            MediaArtwork(
                                                item.preferredPoster(),
                                                item.title,
                                                artworkCache,
                                                fetchArtwork,
                                                modifier,
                                                targetWidthPx = 480,
                                            )
                                        },
                                        landscapeArtwork = { item, modifier ->
                                            MediaArtwork(
                                                item.preferredLandscape(),
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
                                                images = item.landscapeArtworkCandidates(),
                                                title = item.title,
                                                cache = artworkCache,
                                                fetch = fetchArtwork,
                                                modifier = modifier,
                                                targetWidthPx = 240,
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
                                    similar = state.detailSimilar,
                                    similarLoading = state.detailSimilarLoading,
                                    onSimilarItemSelected = openDetail,
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
                                    libraryName = state.collections.firstOrNull {
                                        it.id == state.selectedCollection
                                    }?.name,
                                    modifier = Modifier.fillMaxSize(),
                                    artwork = { item, modifier ->
                                        MediaArtwork(
                                            item.preferredPoster(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            targetWidthPx = 480,
                                        )
                                    },
                                    backdropArtwork = { item, modifier ->
                                        MediaArtwork(
                                            item.preferredLandscape(),
                                            item.title,
                                            artworkCache,
                                            fetchArtwork,
                                            modifier,
                                            targetWidthPx = 720,
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
                                            images = item.landscapeArtworkCandidates(),
                                            title = item.title,
                                            cache = artworkCache,
                                            fetch = fetchArtwork,
                                            modifier = modifier,
                                            targetWidthPx = 240,
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
                val media = activeMedia
                if (media != null && !showingDetail) {
                    LiquidMiniPlayer(
                        media = MiniPlayerMedia(
                            id = media.id.value,
                            title = media.title,
                            subtitle = media.miniPlayerSubtitle(),
                        ),
                        presentation = playerState.toMiniPlayerPresentation(),
                        backdrop = backdrop,
                        onOpen = { playerExpanded = true },
                        onTogglePlay = togglePlayback,
                        compactProgress = { chromeCompactProgress.value },
                        artwork = { modifier ->
                            MediaArtwork(media.preferredPoster(), media.title, artworkCache, fetchArtwork, modifier)
                        },
                        modifier = Modifier
                            .align(
                                if (layout.navigation == NavigationPlacement.Bottom) Alignment.BottomCenter
                                else Alignment.BottomEnd,
                            )
                            .padding(
                                start = safeArea.start + if (layout.navigation == NavigationPlacement.Bottom) 16.dp else 24.dp,
                                end = safeArea.end + 16.dp,
                                bottom = safeArea.bottom + if (layout.navigation == NavigationPlacement.Bottom && !showingDetail) 88.dp else 24.dp,
                            )
                            .widthIn(max = 430.dp)
                            .fillMaxWidth(),
                    )
                }
            },
            modal = { backdrop ->
                // 大屏：居中的毛玻璃模态。同一个 mineNavContent，所以来回切形态时里面停在哪
                // 一页、翻了几层都保持不变。
                if (minePresented && !mineFullScreen) {
                    MineAccountSheet(
                        backdrop = backdrop,
                        layout = layout,
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
    backdrop: GlassBackdrop,
    layout: MaFeiLayout,
    onDismiss: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetWidth = (maxWidth - 96.dp).coerceIn(280.dp, 620.dp)
        val sheetHeight = (maxHeight - 72.dp).coerceAtMost(760.dp).coerceAtLeast(320.dp)
        // The sheet is narrower than the window: recompute the content rhythm for
        // the panel so poster columns and hero metrics fit instead of inheriting
        // the full-window grid.
        val sheetLayout = remember(layout, sheetWidth) {
            layout.withUsableWidth(with(density) { sheetWidth.toPx() / density.density })
        }
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
            contentAlignment = Alignment.Center,
        ) {
            MaFeiSurface(
                GlassSurfaceSpec(
                    GlassRole.Dialog,
                    GlassLevel.Full,
                    tintAlpha = .30f,
                    intensity = 1f,
                    cornerRadiusDp = 22f,
                ),
                Modifier
                    .width(sheetWidth)
                    .height(sheetHeight)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { contentDescription = "我的" },
                backdrop,
            ) {
                Box(Modifier.fillMaxSize()) {
                    content(Modifier.fillMaxSize().testTag("mineAccountSheetContent"))
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .mafeiInteractive(onDismiss, radiusDp = 18f, label = "关闭我的"),
                        contentAlignment = Alignment.Center,
                    ) {
                        MaFeiIcon(MaFeiGlyph.Close, Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

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
private const val FavoriteDestination = "favorite"
private const val LibraryDestination = "library"
private const val MineDestination = "mine"

private fun legacySortChoices(
    provider: ProviderKind,
    direction: SortDirection,
): List<LegacySortChoice<MediaSort>> {
    // 可排序字段集由 model 的展示风格表给出（core:domain/MediaPresentation.kt），
    // 此处不再判断 provider；UI 只负责把字段翻译成菜单文案。
    val labels = mapOf(
        MediaSortField.Title to "按名称",
        MediaSortField.DateAdded to "按添加日期",
        MediaSortField.PremiereDate to "按首播日期",
        MediaSortField.CommunityRating to "按社区评分",
        MediaSortField.Runtime to "按时长",
    )
    return MediaDisplayStyle.forProvider(provider).sortableFields.map { field ->
        LegacySortChoice(labels.getValue(field), MediaSort(field, direction))
    }
}
