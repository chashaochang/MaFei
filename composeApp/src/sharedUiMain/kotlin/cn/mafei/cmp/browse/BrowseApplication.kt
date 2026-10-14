package cn.mafei.cmp

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
    val state by browser.state.collectAsState()
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
    val chasingState by chasingBrowser.state.collectAsState()
    val chasingSelection = remember(session) { ChasingSelection(session.media.accountId, runtime.chasingSeriesStore) }
    val chasingSelectionState by chasingSelection.state.collectAsState()
    LaunchedEffect(chasingSelection) { if (browser.capabilities.chasing) chasingSelection.load() }
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
    LaunchedEffect(state.selectedCollection) { selection.librarySelectedIds = emptySet() }
    // Home always opens at its hero. Do not restore a process-saved grid
    // position; route changes are handled explicitly by selectDestination.
    val homeGridState = remember(session) { LazyGridState() }
    val chasingListState = rememberLazyListState()
    val favoriteGridState = rememberLazyGridState()
    val libraryGridState = rememberLazyGridState()
    val latestGridState = rememberLazyGridState()
    val searchGridState = rememberLazyGridState()
    val browseContentStateHolder = rememberSaveableStateHolder()
    val artworkCache = rememberArtworkMemoryCache(session, session.connection.credentialKey)
    val fetchArtwork: suspend (MediaImage, Int) -> ByteArray? =
        remember(session) { { image, targetWidthPx -> runtime.loadArtwork(session, image, targetWidthPx) } }
    val playback = remember(session) { runtime.playback(session) }
    val player = remember(session) { BrowsePlayerState(playback, browser, scope) }
    val playerShellState = rememberPlayerShellState(playback)
    BindBrowsePlayerLifecycle(player, playerShellState, playerRenderMode, state.detailId, onPlayerScreenChanged, onPlayerFullscreenChanged)
    val catalogs = rememberBrowseCatalogs(runtime.sessions.media, session.media)
    val musicStore = catalogs.music
    val liveStore = catalogs.live
    val photoStore = catalogs.photos
    val latestStore = catalogs.latest
    val preparedStream = playback?.prepared?.collectAsState()?.value
    val qualityOptions = rememberPlayerQualityOptions(player.playingMedia, preparedStream)
    val navigation = remember(session) { BrowseNavigationState() }
    val currentRoute = navigation.currentRoute
    // Keep the chrome selection derived from the route that opened the page.
    // `state.selectedCollection` is provider/browser state and may still point
    // at the last library even when the user opened a Home shelf item.
    val shellPresentation = currentRoute.shellPresentation()
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
    val reauthenticate: () -> Unit = {
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
    val destination = shellContext.destination
    // `destination` already carries Search's intentionally empty selection;
    // keep a named value because the shell and the content host use it for
    // different decisions below.
    val shellDestination = destination
    val chromeCompactProgress = rememberBrowseChromeProgress(destination, homeGridState, chasingListState, favoriteGridState, libraryGridState)

    BindBrowseDataLifecycle(browser, search, feiniu, session.selection.provider, state, libraryGridState, currentRoute, chasingBrowser, account, selection)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = rememberBrowseWindowLayout(maxWidth, maxHeight)
        LaunchedEffect(currentRoute, state.collections, layout.isPhoneNavigation) {
            if (currentRoute == BrowseHomeRoute && !layout.isPhoneNavigation) {
                state.collections.forEach { collection -> launch { browser.loadHomeLatest(collection.id) } }
            }
        }
        val navigator = BrowseNavigator(browser, navigation, scope, search, shellContext, artworkCache, fetchArtwork, layout.family == MaFeiLayoutFamily.Phone)
        val showingPersonDetail = shellPresentation.personDetail
        val showingDetail = shellPresentation.detail
        val showingFullPageRoute = shellPresentation.fullPage
        // 「我的」容器的两种形态：小屏（手机）全屏铺开、看起来就是普通页面跳转；其余尺寸
        // 用居中的毛玻璃模态。形态只由窗口决定，容器自己的回退栈不受影响。
        // Phone owns a full-screen Mine stack; tablet/desktop keep the root
        // shell visible while child content changes in the content stack.
        val mineFullScreen = layout.isPhoneNavigation
        val libraries = state.collections.map { collection -> MaFeiNavItem(collection.id.value, collection.name, MaFeiGlyph.Library) }
        val navigationItems =
            remember(browser.capabilities.chasing, libraries, libraryTabId) { browseNavigationItems(browser.capabilities.chasing, libraries, libraryTabId) }
        val destinations = navigationItems.bottom
        val sidebarDestinations = navigationItems.sidebar
        val topTabDestinations = navigationItems.topTabs
        val topTabSelectedDestination = shellContext.topTabDestination
        val selectDestination: (String) -> Unit = { next ->
            if (next == MineDestination) {
                // 「我的」是一个带独立导航的容器，不是一个普通页面：下层的浏览路由保持原样
                // （回来还是原来那页、原来那个滚动位置）。每次从标签栏进来都回到根页。
                account.mineBackStack.clear()
                account.mineBackStack += BrowseMineRoute
                account.minePresented = true
            } else {
                // 小屏上容器是全屏铺开的、标签栏仍然可见，所以切到别的标签必须把它收起来，
                // 否则会盖在上面走不掉。大屏是模态（有遮罩），这条只是顺带保持一致。
                account.minePresented = false
                if (showingPersonDetail) browser.clearPerson()
                if (state.detailId != null) browser.back()
                if (next == HomeDestination) {
                    scope.launch { homeGridState.scrollToItem(0) }
                }
                val selectedLibrary = libraries.firstOrNull { it.key == next }
                navigation.browseBackStack.clear()
                navigation.browseBackStack +=
                    when {
                        selectedLibrary != null -> {
                            state.collections
                                .firstOrNull { it.id.value == selectedLibrary.key }
                                ?.let { collection ->
                                    if (
                                        collection.id != state.selectedCollection && !(browser.capabilities.music && MediaType.Audio in collection.mediaTypes)
                                    ) {
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

        val mineContent: @Composable (Modifier) -> Unit = { modifier ->
            AccountRootBinding(
                modifier,
                account,
                scope,
                browser,
                state,
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
        val popMineRoute: () -> Unit = {
            when (val route = account.mineBackStack.lastOrNull()) {
                is BrowseConnectAccountRoute -> {
                    account.accountConnectError = null
                    if (route.returnToBrowseOnBack) account.minePresented = false
                }
                else -> Unit
            }
            if (account.mineBackStack.size > 1) account.mineBackStack.removeLastOrNull()
        }
        val mineNavContent: @Composable (Modifier) -> Unit = { mineHostModifier ->
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
                            NavEntry(route) { MineHistoryEntryBinding(scope, browser, state, layout, player, popMineRoute, artworkCache, fetchArtwork) }
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
                                    currentPlayerRenderMode,
                                    currentPlayerRenderModeChanged,
                                    playerRenderModeSupported,
                                )
                            }
                        BrowsePlayerSettingsRoute -> NavEntry(route) { PlayerSettingsEntryBinding(playback, popMineRoute, runtime.playerPreferences) }
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
        val activeMedia = player.playingMedia?.takeIf { it.id.value == playerShellState.resourceId }
        val playerMedia = if (player.playerExpanded) player.playingMedia else activeMedia
        // The floating mini player is the legacy MusicMiniPlayer. Video keeps
        // using the full player surface, but must not reserve music chrome or
        // appear above the app navigation after playback starts.
        val musicMiniPlayerMedia = activeMedia?.takeIf { it.mediaType == MediaType.Audio }

        val librarySortChoices = legacySortChoices(provider = session.connection.server.provider, direction = state.librarySort.direction)
        val favoriteSortChoices = legacySortChoices(provider = session.connection.server.provider, direction = state.favoriteSort.direction)

        val collapsedTopBarBack: (() -> Unit)? =
            when {
                shellPresentation.secondaryContent && navigation.browseBackStack.size > 1 -> navigator::pop
                account.minePresented && account.mineBackStack.size > 1 -> popMineRoute
                else -> null
            }

        // Root fallback for the tablet/desktop shell. Child sheet/player
        // handlers are registered deeper in the Navigation 3 tree and win
        // first; this handler only owns the root surface and route stacks.
        MaFeiBackHandler(
            enabled = account.minePresented || navigation.browseBackStack.size > 1,
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
                if (!player.playerExpanded || playerMedia == null || playback == null)
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
                            onSearch = navigator::openSearch,
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
                                    state,
                                    layout,
                                    artworkCache,
                                    fetchArtwork,
                                    homeCarouselVisible,
                                    heroBackdrop,
                                    currentRoute,
                                    showingDetail,
                                )
                            },
                            accountActions = {},
                            windowControls = windowControls,
                            windowDragArea = windowDragArea,
                            scene = {
                                BrowseArtworkTransitionHost { sharedTransitionScope ->
                                    ApplicationNavDisplay(
                                        backStack = navigation.browseBackStack,
                                        onBack = { navigator.pop() },
                                        entryProvider = { route ->
                                            when (route) {
                                                BrowseHomeRoute ->
                                                    NavEntry(route) {
                                                        HomeEntryBinding(
                                                            sharedTransitionScope,
                                                            authenticationFailure,
                                                            selectDestination,
                                                            scope,
                                                            browser,
                                                            state,
                                                            layout,
                                                            player,
                                                            artworkCache,
                                                            fetchArtwork,
                                                            navigator,
                                                            navigation,
                                                            homeGridState,
                                                            heroBackdrop,
                                                            homeCarouselVisible,
                                                            musicMiniPlayerMedia,
                                                        )
                                                    }
                                                is BrowseLatestRoute ->
                                                    NavEntry(route) {
                                                        LatestEntryBinding(
                                                            route,
                                                            showingFullPageRoute,
                                                            scope,
                                                            browser,
                                                            state,
                                                            layout,
                                                            player,
                                                            artworkCache,
                                                            fetchArtwork,
                                                            navigator,
                                                            latestGridState,
                                                            musicMiniPlayerMedia,
                                                            latestStore,
                                                        )
                                                    }
                                                BrowseLibraryIndexRoute ->
                                                    NavEntry(route) {
                                                        LibraryIndexEntryBinding(scope, browser, state, layout, navigator, navigation, musicMiniPlayerMedia)
                                                    }
                                                BrowseChasingRoute ->
                                                    NavEntry(route) {
                                                        ChasingEntryBinding(
                                                            scope,
                                                            state,
                                                            layout,
                                                            player,
                                                            artworkCache,
                                                            fetchArtwork,
                                                            navigator,
                                                            chasingBrowser,
                                                            chasingState,
                                                            chasingListState,
                                                            musicMiniPlayerMedia,
                                                        )
                                                    }
                                                BrowseFavoriteRoute ->
                                                    NavEntry(route) {
                                                        FavoriteEntryBinding(
                                                            scope,
                                                            browser,
                                                            state,
                                                            layout,
                                                            player,
                                                            artworkCache,
                                                            fetchArtwork,
                                                            navigator,
                                                            favoriteGridState,
                                                            musicMiniPlayerMedia,
                                                            favoriteSortChoices,
                                                        )
                                                    }
                                                BrowseSearchRoute ->
                                                    NavEntry(route) {
                                                        SearchEntryBinding(
                                                            scope,
                                                            browser,
                                                            state,
                                                            layout,
                                                            player,
                                                            artworkCache,
                                                            fetchArtwork,
                                                            navigator,
                                                            search,
                                                            searchGridState,
                                                        )
                                                    }
                                                is BrowseLibraryRoute ->
                                                    NavEntry(route) {
                                                        LibraryEntryBinding(
                                                            route,
                                                            showingFullPageRoute,
                                                            playerShellState,
                                                            { image -> runtime.loadPhotoArtwork(session, image) },
                                                            scope,
                                                            browser,
                                                            state,
                                                            layout,
                                                            player,
                                                            artworkCache,
                                                            fetchArtwork,
                                                            navigator,
                                                            navigation,
                                                            selection,
                                                            libraryGridState,
                                                            musicMiniPlayerMedia,
                                                            librarySortChoices,
                                                            musicStore,
                                                            liveStore,
                                                            photoStore,
                                                        )
                                                    }
                                                is BrowseMusicDetailRoute ->
                                                    NavEntry(route) {
                                                        MusicDetailEntryBinding(
                                                            route,
                                                            playerShellState,
                                                            browser,
                                                            player,
                                                            artworkCache,
                                                            fetchArtwork,
                                                            navigator,
                                                            navigation,
                                                            musicMiniPlayerMedia,
                                                            musicStore,
                                                            runtime.sessions.media,
                                                            session.media,
                                                        )
                                                    }
                                                is BrowsePhotoAlbumRoute ->
                                                    NavEntry(route) {
                                                        PhotoAlbumEntryBinding(
                                                            route,
                                                            { image -> runtime.loadPhotoArtwork(session, image) },
                                                            artworkCache,
                                                            navigator,
                                                            navigation,
                                                            photoStore,
                                                        )
                                                    }
                                                is BrowsePhotoViewerRoute ->
                                                    NavEntry(route) {
                                                        PhotoViewerEntryBinding(
                                                            route,
                                                            runtime.photoSaver,
                                                            { image -> runtime.loadPhotoArtwork(session, image) },
                                                            session,
                                                            navigator,
                                                            navigation,
                                                            photoStore,
                                                            runtime.sessions.media,
                                                            session.media,
                                                        )
                                                    }
                                                is BrowseDetailRoute ->
                                                    NavEntry(route) {
                                                        DetailEntryBinding(
                                                            route,
                                                            playback,
                                                            uriHandler,
                                                            sharedTransitionScope,
                                                            scope,
                                                            browser,
                                                            state,
                                                            layout,
                                                            player,
                                                            artworkCache,
                                                            fetchArtwork,
                                                            navigator,
                                                            navigation,
                                                            selection,
                                                            chasingSelection,
                                                            chasingSelectionState,
                                                        )
                                                    }
                                                is BrowsePersonRoute ->
                                                    NavEntry(route) { PersonEntryBinding(route, scope, browser, state, artworkCache, fetchArtwork, navigator) }
                                                is BrowseSeasonRoute ->
                                                    NavEntry(route) {
                                                        SeasonEntryBinding(
                                                            route,
                                                            playback,
                                                            sharedTransitionScope,
                                                            scope,
                                                            browser,
                                                            state,
                                                            layout,
                                                            player,
                                                            artworkCache,
                                                            fetchArtwork,
                                                            navigator,
                                                            navigation,
                                                        )
                                                    }
                                                is BrowseEpisodeRoute ->
                                                    NavEntry(route) {
                                                        EpisodeEntryBinding(
                                                            route,
                                                            playback,
                                                            uriHandler,
                                                            sharedTransitionScope,
                                                            scope,
                                                            browser,
                                                            state,
                                                            layout,
                                                            player,
                                                            artworkCache,
                                                            fetchArtwork,
                                                            navigator,
                                                            navigation,
                                                            selection,
                                                        )
                                                    }
                                                // 「我的」的分支已搬到它自己的导航容器（mineNavContent）。这里兜底。
                                                else -> NavEntry(route) {}
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
                                if (account.minePresented && mineFullScreen) {
                                    MineFullScreenHost { mineNavContent(it) }
                                }
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
        BrowseDeleteConfirmation(scope, browser, state, navigation, selection, deleteConfirmBackdrop)
    }
}
