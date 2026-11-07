package cn.mafei.cmp

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.common.design_system.MaFeiLayoutFamily
import cn.mafei.cmp.common.design_system.isPhoneNavigation
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.media.rememberArtworkMemoryCache
import cn.mafei.cmp.common.ui.MaFeiAdaptiveShell
import cn.mafei.cmp.common.ui.MaFeiBackHandler
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiNavItem
import cn.mafei.cmp.common.ui.LocalMaFeiTabInteractionState
import cn.mafei.cmp.common.ui.MaFeiTabInteractionState
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.feature.chasing.ChasingBrowser
import cn.mafei.cmp.feature.chasing.ChasingSelection
import cn.mafei.cmp.feature.home.HomeHeroBackdropState
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.home.authenticationRecoveryMessage
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.api.PlayerRenderMode
import cn.mafei.cmp.session.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun BrowseApplication(
    runtime: ApplicationRuntime,
    session: ApplicationSession,
    busy: Boolean,
    liquidGlassIntensity: Float,
    onLiquidGlassIntensityChanged: (Float) -> Unit,
    homeCarouselVisible: State<Boolean>,
    onHomeCarouselVisibleChanged: (Boolean) -> Unit,
    playerRenderMode: PlayerRenderMode,
    playerRenderModeSupported: Boolean,
    onPlayerRenderModeChanged: (PlayerRenderMode) -> Unit,
    playerSurface: @Composable (PlayerController, onBack: () -> Unit, onExitFullscreen: () -> Unit, fullscreen: Boolean, Modifier) -> Unit,
    onPlayerScreenChanged: (Boolean) -> Unit,
    onPlayerFullscreenChanged: (Boolean) -> Unit,
    windowControls: @Composable RowScope.() -> Unit,
    windowDragArea: @Composable (@Composable () -> Unit) -> Unit,
    onLogout: () -> Unit,
) {
    val tabInteraction = remember(session) { MaFeiTabInteractionState() }
    CompositionLocalProvider(LocalMaFeiTabInteractionState provides tabInteraction) {
    // Navigation 3 keeps an existing NavEntry composed while its parent route
    // remains on the stack. Keep the setting in a stable State holder so the
    // already-composed SettingsScreen observes drag/click updates immediately;
    // passing the primitive directly would leave the entry showing the value
    // captured when it was first pushed and only appear updated after leaving
    // and re-entering the page.
    val currentLiquidGlassIntensity = rememberUpdatedState(liquidGlassIntensity)
    val currentLiquidGlassIntensityChanged = rememberUpdatedState(onLiquidGlassIntensityChanged)
    val currentPlayerRenderMode = rememberUpdatedState(playerRenderMode)
    val currentPlayerRenderModeChanged = rememberUpdatedState(onPlayerRenderModeChanged)
    val bottomNavigationType by runtime.glassStyle.bottomNavigation.collectAsState()
    val browser =
        remember(session) {
            MediaBrowser(
                repository = runtime.sessions.media,
                session = session.media,
                capabilities = session.binding.adapter.capabilities,
                playbackPreferences = runtime.playbackPreferences,
            )
        }
    val browserState = browser.state.collectAsState()
    val authenticationFailure by browser.authenticationFailure.collectAsState()
    val authenticationMessage = authenticationFailure?.let(::authenticationRecoveryMessage)
    val chasingBrowser =
        remember(session) {
            ChasingBrowser(
                repository = runtime.sessions.media,
                session = session.media,
                capabilities = session.binding.adapter.capabilities,
                store = runtime.chasingSeriesStore,
                nowEpochMs = SystemEpochClock::nowEpochMs,
            )
        }
    // 追剧仍未对用户开放：保留 provider、存储与迁移实现，只隐藏产品入口。
    val chasingEnabled = ChasingFeatureReleased && browser.capabilities.chasing
    val chasingSelection = remember(session) { ChasingSelection(session.media.accountId, runtime.chasingSeriesStore) }
    LaunchedEffect(chasingSelection, chasingEnabled) { if (chasingEnabled) chasingSelection.load() }
    val scope = rememberCoroutineScope()
    val search = remember(session) { BrowseSearchState(browser::search, runtime::searchHistory, runtime::saveSearchHistory, scope) }
    val selection = remember(session) { BrowseSelectionState() }
    val account = remember(session) { AccountFlowState(runtime.accountAccess(), scope) }
    val feiniu = remember(session) { FeiniuPreferenceState(runtime::loadFeiniuRoutePreference, runtime::saveFeiniuRoutePreference, scope) }
    val uriHandler = LocalUriHandler.current
    val embeddedWebHost = LocalEmbeddedWebHost.current
    val embeddedWebMemory = remember(session) { EmbeddedWebPageState() }
    // 管理员媒体删除：确认弹窗 + 待删集合。入口由 browser 探测的服务器策略决定。
    val deleteConfirmBackdrop = rememberGlassBackdrop()
    // 列表页多选状态（legacy selectionMode）：切库/离开页面时自动退出多选。
    ResetBrowseSelectionOnCollectionChange(browserState, selection)
    // Start a new session at the hero, but retain this grid's anchor when
    // switching tabs or returning from a document within the same session.
    val homeGridState = remember(session) { LazyGridState() }
    val chasingListState = rememberLazyListState()
    val pageScrollStates = remember(session) { BrowsePageScrollStates() }
    val browseContentStateHolder = rememberSaveableStateHolder()
    val artworkCache = rememberArtworkMemoryCache(session, session.connection.credentialKey)
    val fetchArtwork: suspend (MediaImage, Int) -> ByteArray? =
        remember(session) { { image, targetWidthPx -> runtime.loadArtwork(session, image, targetWidthPx) } }
    val fetchPhotoArtwork: suspend (MediaImage) -> ByteArray? =
        remember(session) { { image -> runtime.loadPhotoArtwork(session, image) } }
    val playback = remember(session) { runtime.playback(session) }
    val player = remember(session) { BrowsePlayerState(playback, browser, scope) }
    val playerShellState = rememberPlayerShellState(playback)
    BindBrowseSessionPlayerLifecycle(player, playerShellState, playerRenderMode, browserState, onPlayerScreenChanged, onPlayerFullscreenChanged)
    val catalogs = rememberBrowseCatalogs(runtime.sessions.media, session.media)
    val musicStore = catalogs.music
    val preparedStream = playback?.prepared?.collectAsState()?.value
    val qualityOptions = rememberPlayerQualityOptions(player.playingMedia, preparedStream)
    val navigation = remember(session) {
        BrowseNavigationState().also { browser.switchTabContext(it.activeRoot.toString()) }
    }
    PruneBrowsePageScrollStates(navigation, pageScrollStates)
    BrowseShellRouteScope(navigation) { shellPresentation ->
    val collections by remember(browserState) {
        derivedStateOf(structuralEqualityPolicy()) { browserState.value.collections }
    }
    val libraryGridState by remember(navigation, pageScrollStates) {
        derivedStateOf {
            val route = navigation.browseBackStack.lastOrNull { it is BrowseLibraryRoute } ?: BrowseLibraryIndexRoute
            pageScrollStates.grid(navigation.activeRoot, route)
        }
    }
    val favoriteGridState by remember(navigation, pageScrollStates) {
        derivedStateOf {
            val route = navigation.browseBackStack.lastOrNull { it == BrowseFavoriteRoute } ?: BrowseFavoriteRoute
            pageScrollStates.grid(navigation.activeRoot, route)
        }
    }
    // Keep the chrome selection derived from the route that opened the page.
    // `state.selectedCollection` is provider/browser state and may still point
    // at the last library even when the user opened a Home shelf item.
    val shellContext = shellPresentation.context
    val libraryTabId =
        rememberLibraryTabId(
            currentLibraryId = shellContext.libraryId,
            serverId = session.selection.serverId.value,
            accountId = session.selection.accountId.value,
        )
    // 首页轮播背景的窗口级状态：背景画在 shell 窗口画布上（全窗、与侧栏让位
    // 无关），HomeScreen 只往里喂实时状态（items/pager/滚动跟随参数）。
    val heroBackdrop = remember { HomeHeroBackdropState() }
    val reauthenticate: () -> Unit = remember(account, session) {
        {
            account.authenticationNoticeDismissed = true
            account.accountConnectError = null
            account.pendingDiscoveredServerAddress = null
            account.mineBackStack.clear()
            account.mineBackStack += BrowseMineRoute
            account.mineBackStack +=
                BrowseConnectAccountRoute(
                    provider = session.connection.server.provider,
                    presetBaseUrl = session.connection.feiniuOriginalAddress ?: session.connection.server.baseUrl,
                    presetUsername = session.connection.account.userName,
                    credentialKey = session.connection.credentialKey,
                    returnToBrowseOnBack = true,
                )
            account.minePresented = true
        }
    }
    val destination = shellContext.destination
    // `destination` already carries Search's intentionally empty selection;
    // keep a named value because the shell and the content host use it for
    // different decisions below.
    val shellDestination = destination
    val chromeCompactProgress = rememberBrowseChromeProgress(destination, homeGridState, chasingListState, favoriteGridState, libraryGridState)

    BindBrowseNavigationDataLifecycle(browser, search, feiniu, session.selection.provider, navigation, chasingBrowser, account, selection)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = rememberBrowseWindowLayout(maxWidth, maxHeight)
        BindHomeLibraryPrefetch(navigation, browser, collections, layout.isPhoneNavigation, active = !account.minePresented)
        val currentPhoneLayout = rememberUpdatedState(layout.family == MaFeiLayoutFamily.Phone)
        val navigator = remember(browser, navigation, scope, search, artworkCache, fetchArtwork) {
            BrowseNavigator(browser, navigation, scope, search, artworkCache, fetchArtwork) { currentPhoneLayout.value }
        }
        val onBrowseBack = navigator.onBack
        val showingDetail = shellPresentation.detail
        val showingFullPageRoute = shellPresentation.fullPage
        // 「我的」容器的两种形态：小屏（手机）全屏铺开、看起来就是普通页面跳转；其余尺寸
        // 用居中的毛玻璃模态。形态只由窗口决定，容器自己的回退栈不受影响。
        // Phone owns a full-screen Mine stack; tablet/desktop keep the root
        // shell visible while child content changes in the content stack.
        val mineFullScreen = layout.isPhoneNavigation
        val libraries = remember(collections) {
            collections.map { collection -> MaFeiNavItem(collection.id.value, collection.name, MaFeiGlyph.Library) }
        }
        val navigationItems = remember(chasingEnabled, libraries, libraryTabId) { browseNavigationItems(chasingEnabled, libraries, libraryTabId) }
        val destinations = navigationItems.bottom
        val sidebarDestinations = navigationItems.sidebar
        val topTabDestinations = navigationItems.topTabs
        val topTabSelectedDestination = shellContext.topTabDestination
        val selectDestination: (String) -> Unit = remember(account, browser, navigator) {
            { next ->
                if (next == MineDestination) {
                    account.minePresented = true
                } else {
                    account.minePresented = false
                    val selectedCollection = browser.state.value.collections.firstOrNull { it.id.value == next }
                    if (selectedCollection != null) {
                        navigator.selectLibraryRoot(selectedCollection)
                    } else {
                        val root = when (next) {
                            HomeDestination -> BrowseHomeRoute
                            ChasingDestination -> BrowseChasingRoute
                            FavoriteDestination -> BrowseFavoriteRoute
                            else -> BrowseLibraryIndexRoute
                        }
                        navigator.selectRoot(root)
                    }
                }
            }
        }

        val popMineRoute: () -> Unit = remember(account) {
            {
                when (val route = account.mineBackStack.lastOrNull()) {
                    is BrowseConnectAccountRoute -> {
                        account.accountConnectError = null
                        if (route.returnToBrowseOnBack) account.minePresented = false
                    }
                    else -> Unit
                }
                if (account.mineBackStack.size > 1) account.mineBackStack.removeLastOrNull()
            }
        }
        val mineNavContent = rememberRetainedAccountContent(
            account, scope, browser, browserState, session, layout, busy, player,
            artworkCache, fetchArtwork, uriHandler, embeddedWebHost, reauthenticate,
            onLogout, authenticationFailure, popMineRoute, feiniu, playback, runtime,
            homeCarouselVisible, onHomeCarouselVisibleChanged, currentPlayerRenderMode,
            currentPlayerRenderModeChanged, playerRenderModeSupported,
            currentLiquidGlassIntensity, currentLiquidGlassIntensityChanged,
            embeddedWebMemory, onPlayerFullscreenChanged,
        ) {
            val mineContent: @Composable (Modifier) -> Unit = { modifier ->
                AccountRootBinding(
                    modifier,
                    account,
                    scope,
                    browser,
                    browserState,
                    session,
                    layout,
                    busy,
                    player,
                    artworkCache,
                    fetchArtwork,
                    uriHandler,
                    embeddedWebHost,
                    reauthenticate,
                    onLogout,
                    authenticationFailure,
                )
            }
            val accountNavContent: @Composable (Modifier) -> Unit = { mineHostModifier ->
                // Registered before NavDisplay so child routes/sheets handle Back
                // first; at the account root it closes the whole account surface.
                cn.mafei.cmp.common.ui.MaFeiBackHandler(enabled = account.minePresented) { account.minePresented = false }
                NavDisplay(
                    backStack = account.mineBackStack,
                    onBack = { popMineRoute() },
                    entryProvider = { route ->
                        when (route) {
                            BrowseMineRoute -> NavEntry(route) { MineEntryBinding(mineContent) }
                            BrowseMineHistoryRoute ->
                                NavEntry(route) { MineHistoryEntryBinding(scope, browser, browserState, layout, player, popMineRoute, artworkCache, fetchArtwork) }
                            BrowseAccountSwitchRoute -> NavEntry(route) { AccountSwitchEntryBinding(account, session, popMineRoute) }
                            BrowseAddAccountRoute -> NavEntry(route) { AddAccountEntryBinding(account, popMineRoute) }
                            is BrowseConnectAccountRoute -> NavEntry(route) { ConnectAccountEntryBinding(route, account, session, busy, popMineRoute) }
                            BrowseServerDiscoveryRoute -> NavEntry(route) { ServerDiscoveryEntryBinding(account, popMineRoute) }
                            BrowseSettingsRoute ->
                                NavEntry(route) {
                                    SettingsEntryBinding(
                                        account,
                                        feiniu,
                                        session,
                                        playback,
                                        popMineRoute,
                                        runtime.appearance,
                                        runtime.glassStyle,
                                        homeCarouselVisible,
                                        onHomeCarouselVisibleChanged,
                                    )
                                }
                            BrowsePlayerSettingsRoute ->
                                NavEntry(route) {
                                    PlayerSettingsEntryBinding(
                                        playback,
                                        popMineRoute,
                                        runtime.playerPreferences,
                                        currentPlayerRenderMode,
                                        currentPlayerRenderModeChanged,
                                        playerRenderModeSupported,
                                    )
                                }
                            is BrowseManagementRoute ->
                                NavEntry(route) { ManagementEntryBinding(route, account, session.media, popMineRoute, runtime.sessions.media) }
                            is BrowseManagementDevicesRoute ->
                                NavEntry(route) { ManagementDevicesEntryBinding(route, account, session.media, popMineRoute, runtime.sessions.media) }
                            BrowseFeiniuConnectionPreferenceRoute -> NavEntry(route) { FeiniuConnectionPreferenceEntryBinding(feiniu, popMineRoute) }
                            BrowseAppearanceRoute -> NavEntry(route) { AppearanceEntryBinding(popMineRoute, runtime.appearance) }
                            BrowseGlassStyleRoute ->
                                NavEntry(route) {
                                    GlassStyleEntryBinding(popMineRoute, runtime.glassStyle, currentLiquidGlassIntensity, currentLiquidGlassIntensityChanged)
                                }
                            BrowseAddGroupRoute -> NavEntry(route) { AddGroupEntryBinding(popMineRoute) }
                            BrowseAboutPrivacyRoute -> NavEntry(route) { AboutPrivacyEntryBinding(account, popMineRoute, uriHandler, embeddedWebHost) }
                            BrowsePrivacyWebRoute -> NavEntry(route) { PrivacyWebEntryBinding(popMineRoute, uriHandler) }
                            BrowseWebITongRoute ->
                                NavEntry(route) {
                                    WebITongEntryBinding(
                                        account,
                                        scope,
                                        session,
                                        player,
                                        playback,
                                        popMineRoute,
                                        uriHandler,
                                        embeddedWebMemory,
                                        onPlayerFullscreenChanged,
                                        { runtime.sessions.state.value.session === session },
                                        runtime.sessions.media,
                                        runtime.deviceControls,
                                    )
                                }
                            BrowseUserAgreementRoute -> NavEntry(route) { UserAgreementEntryBinding(popMineRoute) }
                            else -> NavEntry(route) {}
                        }
                    },
                    modifier = mineHostModifier,
                )
            }
            accountNavContent
        }

        val activeMedia = player.playingMedia?.takeIf { it.id.value == playerShellState.resourceId }
        val playerMedia = if (player.playerExpanded) player.playingMedia else activeMedia
        // The floating mini player is the legacy MusicMiniPlayer. Video keeps
        // using the full player surface, but must not reserve music chrome or
        // appear above the app navigation after playback starts.
        val musicMiniPlayerMedia = activeMedia?.takeIf { it.mediaType == MediaType.Audio }

        val browseCanGoBack by remember(navigation) { derivedStateOf { navigation.browseBackStack.size > 1 } }
        val collapsedTopBarBack: (() -> Unit)? =
            when {
                shellPresentation.secondaryContent && browseCanGoBack -> onBrowseBack
                account.minePresented && account.mineBackStack.size > 1 -> popMineRoute
                else -> null
            }

        // Root fallback for the tablet/desktop shell. Child sheet/player
        // handlers are registered deeper in the Navigation 3 tree and win
        // first; this handler only owns the root surface and route stacks.
        MaFeiBackHandler(
            enabled = account.minePresented || browseCanGoBack,
            onBack = {
                when {
                    account.minePresented && account.mineBackStack.size > 1 -> popMineRoute()
                    navigation.browseBackStack.size > 1 -> navigator.pop()
                    account.minePresented -> account.minePresented = false
                }
            },
        )

        // 删除确认弹窗的玻璃源：捕获层包住外壳/播放器，弹窗是它的**兄弟** —— 记录者与
        // 消费者绝不能在同一棵子树里（详见 N1 崩溃规则）。只在弹窗出现时挂捕获，平时零开销。
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(if (selection.pendingDeleteMedia != null) Modifier.captureGlassBackdrop(deleteConfirmBackdrop) else Modifier)) {
                BrowsePlayerHost(
                    player = player,
                    playerMedia = playerMedia,
                    preparedStream = preparedStream,
                    qualityOptions = qualityOptions,
                    repository = runtime.sessions.media,
                    mediaSession = session.media,
                    canEditFavorites = browser.capabilities.favoritesEdit,
                    musicStore = musicStore,
                    artworkCache = artworkCache,
                    fetchArtwork = fetchArtwork,
                    playerSurface = playerSurface,
                    windowControls = windowControls,
                    windowDragArea = windowDragArea,
                    onPlayerFullscreenChanged = onPlayerFullscreenChanged,
                    onReturnToBrowse = {
                        if (account.returnToEmbeddedWeb) {
                            account.returnToEmbeddedWeb = false
                            account.minePresented = true
                        }
                    },
                )
                // PiP collapses the player chrome back to the existing browse shell,
                // but leaves the same player route and ArkUI XComponent composed below
                // it. The system PiP window owns that surface until restoration; popping
                // the player route here would dispose the native host and release the
                // active playback session.
                if (!player.playerExpanded || playerShellState.pictureInPicture)
                    browseContentStateHolder.SaveableStateProvider("browse-content") {
                        MaFeiAdaptiveShell(
                            layout = layout,
                            bottomNavigationType = bottomNavigationType,
                            destinations = destinations,
                            sidebarDestinations = sidebarDestinations,
                            topTabDestinations = topTabDestinations,
                            topTabSelectedDestination = topTabSelectedDestination,
                            selectedDestination = if (account.minePresented) MineDestination else shellDestination,
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
                            showBottomNavigation =
                                shellPresentation.showBottomNavigation(
                                    isPhoneNavigation = layout.isPhoneNavigation,
                                    accountPresented = account.minePresented,
                                    accountStackDepth = account.mineBackStack.size,
                                ),
                            // Search is the primary shell action represented by the final
                            // icon in the collapsed top bar. Keep that bar mounted with the
                            // search action selected; only pushed details and the Mine stack
                            // switch a collapsed large sidebar to the compact rail.
                            contentNavigationActive = shellPresentation.secondaryContent || account.minePresented,
                            onSearch = navigator.onOpenSearch,
                            selectedSearch = shellPresentation.search,
                            // The compact dock is the other half of the mini-player morph.
                            // With no active media there is nothing to merge into, so page
                            // scrolling must leave the primary navigation at full size.
                            bottomNavigationCompactProgress = { if (musicMiniPlayerMedia != null) chromeCompactProgress.value else 0f },
                            // 一级页面的标题随内容滚走，滚过状态栏后顶部才浮现毛玻璃。二级页面
                            // （详情/设置/搜索）自带头部，头部材质由各页自己的 collapseProgress 驱动，
                            // 这里必须让位，否则两层毛玻璃会叠在一起。
                            topChromeScrolledProgress = { if (showingFullPageRoute) 0f else chromeCompactProgress.value },
                            background = {
                                BrowseWindowBackground(
                                    layout,
                                    artworkCache,
                                    fetchArtwork,
                                    homeCarouselVisible,
                                    heroBackdrop,
                                    navigation.currentRoute,
                                    showingDetail,
                                    browseActive = !account.minePresented,
                                    browseVisible = !(account.minePresented && mineFullScreen),
                                )
                            },
                            accountActions = {},
                            windowControls = windowControls,
                            windowDragArea = windowDragArea,
                            scene = {
                                val repository = runtime.sessions.media
                                val photoSaver = runtime.photoSaver
                                val entryProvider = rememberBrowseEntryProvider(
                                    browser, scope, layout, player, playerShellState,
                                    navigator, navigation, selection, selectDestination,
                                    artworkCache, fetchArtwork, fetchPhotoArtwork,
                                    homeGridState, pageScrollStates, chasingListState,
                                    heroBackdrop, homeCarouselVisible, musicMiniPlayerMedia,
                                    catalogs, search, chasingBrowser, chasingSelection,
                                    chasingEnabled, playback, uriHandler, session, repository, photoSaver,
                                ) {
                                    { route ->
                                        BrowsePageEntry(
                                            route, browser, scope, layout, player, playerShellState,
                                            navigator, navigation, selection, selectDestination,
                                            artworkCache, fetchArtwork, fetchPhotoArtwork,
                                            homeGridState, pageScrollStates, chasingListState,
                                            heroBackdrop, homeCarouselVisible, musicMiniPlayerMedia,
                                            catalogs, search, chasingBrowser, chasingSelection,
                                            chasingEnabled, playback, uriHandler, session,
                                            repository, photoSaver,
                                        )
                                    }
                                }
                                ApplicationNavDisplay(
                                    navigation = navigation,
                                    onBack = onBrowseBack,
                                    entryProvider = entryProvider,
                                    modifier = Modifier.fillMaxSize(),
                                    active = !account.minePresented,
                                    visible = !(account.minePresented && mineFullScreen),
                                )
                                // 小屏（手机）：同一个「我的」容器，改成全屏铺开 —— 非模态，看起来和普通
                                // 页面跳转一样。
                                //
                                // 关键：它和首页/收藏一样画在**场景里**，不是 overlay 槽。外壳的底部标签栏
                                // 是场景的兄弟节点（在场景之后合成），所以标签栏照样折射"我的"页面上的内容；
                                // 放进 overlay 会让标签栏得到一个平底，看起来就是"两层、标签栏不动"。
                                // 内外边距由页面自己处理：MineScreen 底部留 112dp 给标签栏，子页面各自带头部。
                                RetainedPhoneAccountHost(mineFullScreen, account.minePresented, mineNavContent)
                            },
                            overlay = { backdrop ->
                                BrowsePlayerOverlay(
                                    backdrop,
                                    layout,
                                    player,
                                    playback,
                                    artworkCache,
                                    fetchArtwork,
                                    showingDetail,
                                    musicMiniPlayerMedia,
                                    chromeCompactProgress,
                                )
                            },
                            modal = { backdrop ->
                                BrowseAccountModal(backdrop, layout, player, account, mineFullScreen, mineNavContent, authenticationMessage, reauthenticate)
                            },
                        )
                    }
            }
        }
        // 管理员媒体删除确认（列表多选 / 详情页单条共用）。
        BrowseDeleteConfirmation(scope, browser, browserState, navigation, selection, deleteConfirmBackdrop)
    }
    }
    }
}
