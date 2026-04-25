package cn.xiaobai.mafei

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import cn.xiaobai.mafei.data.DetailRepository
import cn.xiaobai.mafei.data.FakeVideoRepository
import cn.xiaobai.mafei.data.FakeDetailRepository
import cn.xiaobai.mafei.data.FakeFavoritesRepository
import cn.xiaobai.mafei.data.FakeHomeRepository
import cn.xiaobai.mafei.data.FakeMediaViewRepository
import cn.xiaobai.mafei.data.FakePlaybackRepository
import cn.xiaobai.mafei.data.FakeSearchRepository
import cn.xiaobai.mafei.data.FavoriteMutationResult
import cn.xiaobai.mafei.data.FavoritesRepository
import cn.xiaobai.mafei.data.HomeMediaView
import cn.xiaobai.mafei.data.HomeRepository
import cn.xiaobai.mafei.data.HomeState
import cn.xiaobai.mafei.data.LibraryItem
import cn.xiaobai.mafei.data.MediaViewRepository
import cn.xiaobai.mafei.data.MediaViewSortOption
import cn.xiaobai.mafei.data.PlaybackContext
import cn.xiaobai.mafei.data.PlaybackRepository
import cn.xiaobai.mafei.data.SearchRepository
import cn.xiaobai.mafei.data.UpdateItem
import cn.xiaobai.mafei.data.VideoDetail
import cn.xiaobai.mafei.data.jellyfin.JellyfinAuthFailureType
import cn.xiaobai.mafei.data.jellyfin.JellyfinAuthRepository
import cn.xiaobai.mafei.data.jellyfin.JellyfinAuthResult
import cn.xiaobai.mafei.data.jellyfin.JellyfinConnectionProbe
import cn.xiaobai.mafei.data.jellyfin.JellyfinErrorCode
import cn.xiaobai.mafei.data.jellyfin.JellyfinLoadIssue
import cn.xiaobai.mafei.data.jellyfin.JellyfinProvider
import cn.xiaobai.mafei.data.jellyfin.JellyfinProbeResult
import cn.xiaobai.mafei.data.jellyfin.createJellyfinAuthRepository
import cn.xiaobai.mafei.data.jellyfin.createJellyfinConnectionProbe
import cn.xiaobai.mafei.data.jellyfin.createJellyfinProvider
import cn.xiaobai.mafei.data.jellyfin.normalizeBaseUrl
import cn.xiaobai.mafei.screens.AccountScreen
import cn.xiaobai.mafei.screens.AppHeaderRow
import cn.xiaobai.mafei.screens.AppInlineTip
import cn.xiaobai.mafei.screens.AppSectionCard
import cn.xiaobai.mafei.screens.AppStatusCard
import cn.xiaobai.mafei.screens.AppStatusTone
import cn.xiaobai.mafei.screens.DetailScreen
import cn.xiaobai.mafei.screens.FavoritesScreen
import cn.xiaobai.mafei.screens.HomeScreen
import cn.xiaobai.mafei.screens.JellyfinServer
import cn.xiaobai.mafei.screens.LibraryHubScreen
import cn.xiaobai.mafei.screens.LoginUiStatus
import cn.xiaobai.mafei.screens.MediaViewBrowseScreen
import cn.xiaobai.mafei.screens.PlayerScreen
import cn.xiaobai.mafei.screens.SearchScreen
import cn.xiaobai.mafei.screens.ServerConfigScreen
import cn.xiaobai.mafei.screens.ServerHealthStatus
import cn.xiaobai.mafei.screens.ServerManagerViewStatus
import cn.xiaobai.mafei.screens.SplashLoginScreen
import cn.xiaobai.mafei.screens.StartupRestoreScreen
import cn.xiaobai.mafei.screens.StartupRestoreStatus
import cn.xiaobai.mafei.screens.UpdatesScreen
import cn.xiaobai.mafei.logging.AppLogger
import cn.xiaobai.mafei.logging.summarizeBaseUrlForLog
import cn.xiaobai.mafei.logging.summarizeUsernameForLog
import cn.xiaobai.mafei.storage.SessionRecord
import cn.xiaobai.mafei.storage.createAppPersistence
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
object StartupRestoreRoute

@Serializable
object ServerConfigRoute

@Serializable
object SplashLoginRoute

@Serializable
object HomeRoute

@Serializable
object LibraryRoute

@Serializable
object UpdatesRoute

@Serializable
object SearchRoute

@Serializable
object FavoritesRoute

@Serializable
object AccountRoute

private enum class RootTab(
    val label: String,
) {
    HOME("首页"),
    LIBRARY("媒体库"),
    UPDATES("更新"),
    ACCOUNT("我的"),
}

@Serializable
data class MediaViewRoute(val viewId: String)

@Serializable
data class DetailRoute(val itemId: String)

@Serializable
data class PlayerRoute(
    val itemId: String,
    val episodeId: Int,
    val playbackItemId: String? = null,
)

@Composable
fun App(
    homeRepository: HomeRepository = FakeHomeRepository(FakeVideoRepository()),
    detailRepository: DetailRepository = FakeDetailRepository(FakeVideoRepository()),
    searchRepository: SearchRepository = FakeSearchRepository(FakeVideoRepository()),
    playbackRepository: PlaybackRepository = FakePlaybackRepository(FakeVideoRepository()),
    favoritesRepository: FavoritesRepository = FakeFavoritesRepository(FakeVideoRepository()),
    mediaViewRepository: MediaViewRepository = FakeMediaViewRepository(FakeVideoRepository()),
    onOpenPlayback: (PlaybackContext) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val appPersistence = remember { createAppPersistence() }
    val favoriteOverrides = remember { mutableStateMapOf<String, Boolean>() }
    val knownLibraryItems = remember { mutableStateMapOf<String, LibraryItem>() }
    var runtimeSession by remember { mutableStateOf<SessionRecord?>(null) }
    var jellyfinProvider: JellyfinProvider? by remember { mutableStateOf(null) }
    var jellyfinConnectionProbe: JellyfinConnectionProbe? by remember { mutableStateOf(null) }
    var jellyfinAuthRepository: JellyfinAuthRepository? by remember { mutableStateOf(null) }
    var favoriteRefreshVersion by remember { mutableStateOf(0) }

    fun getJellyfinProvider(): JellyfinProvider {
        jellyfinProvider?.let {
            AppLogger.debug("Provider", "Reusing cached Jellyfin provider.")
            return it
        }
        AppLogger.info("Provider", "Creating Jellyfin provider from App.")
        return createJellyfinProvider().also { jellyfinProvider = it }
    }

    fun getJellyfinConnectionProbe(): JellyfinConnectionProbe {
        jellyfinConnectionProbe?.let {
            AppLogger.debug("Probe", "Reusing cached Jellyfin connection probe.")
            return it
        }
        AppLogger.info("Probe", "Creating Jellyfin connection probe from App.")
        return createJellyfinConnectionProbe(getJellyfinProvider())
            .also { jellyfinConnectionProbe = it }
    }

    fun getJellyfinAuthRepository(): JellyfinAuthRepository {
        jellyfinAuthRepository?.let {
            AppLogger.debug("Auth", "Reusing cached Jellyfin auth repository.")
            return it
        }
        AppLogger.info("Auth", "Creating Jellyfin auth repository from App.")
        return createJellyfinAuthRepository(getJellyfinProvider())
            .also { jellyfinAuthRepository = it }
    }

    val servers = remember { mutableStateListOf<JellyfinServer>() }
    var startupStatus by remember { mutableStateOf(StartupRestoreStatus.LOADING) }
    var startupRouteResolved by remember { mutableStateOf(false) }
    var managerStatus by remember { mutableStateOf(ServerManagerViewStatus.EMPTY_LIST) }
    var editingServerId by remember { mutableStateOf<String?>(null) }
    var probeErrorMessage by remember { mutableStateOf<String?>(null) }
    var loginStatus by remember { mutableStateOf(LoginUiStatus.DEFAULT) }
    var loginStatusMessage by remember { mutableStateOf("请输入账号密码以登录 Jellyfin。") }
    var accountStatusMessage by remember { mutableStateOf<String?>(null) }
    var primaryMediaViewId by remember { mutableStateOf<String?>(null) }

    fun defaultServer(): JellyfinServer? = servers.firstOrNull { it.isDefault }
    fun isFullUrl(url: String): Boolean = url.startsWith("http://") || url.startsWith("https://")
    fun currentSession(): SessionRecord? = runtimeSession ?: appPersistence.loadSession()
    fun emptyHomeState(loadIssue: JellyfinLoadIssue? = null): HomeState {
        return HomeState(
            continueWatching = emptyList(),
            nextUp = emptyList(),
            mediaViews = emptyList(),
            updates = emptyList(),
            latestAdded = emptyList(),
            loadIssue = loadIssue,
        )
    }

    fun hasHomeContent(state: HomeState): Boolean {
        return state.continueWatching.isNotEmpty() ||
            state.nextUp.isNotEmpty() ||
            state.updates.isNotEmpty() ||
            state.mediaViews.isNotEmpty() ||
            state.latestAdded.isNotEmpty()
    }

    fun resetLoginUiStatus() {
        loginStatus = LoginUiStatus.DEFAULT
        loginStatusMessage = "请输入账号密码以登录 Jellyfin。"
    }

    fun actionHint(errorCode: JellyfinErrorCode, retryable: Boolean): String {
        return when (errorCode) {
            JellyfinErrorCode.INVALID_URL -> "请先修正服务器地址。"
            JellyfinErrorCode.TLS_CERTIFICATE_ERROR -> "请修复证书或切换服务器。"
            JellyfinErrorCode.AUTH_FAILED -> "请检查用户名和密码后重试。"
            JellyfinErrorCode.FORBIDDEN -> "当前账号无权限，请切换账号或服务器。"
            JellyfinErrorCode.NETWORK_UNREACHABLE,
            JellyfinErrorCode.SERVER_ERROR,
            JellyfinErrorCode.UNKNOWN -> if (retryable) "可直接重试。" else ""
        }
    }

    fun appendActionHint(
        message: String,
        errorCode: JellyfinErrorCode,
        retryable: Boolean,
    ): String {
        val hint = actionHint(errorCode, retryable)
        return if (hint.isBlank()) message else "$message $hint"
    }

    fun mapLoginStatus(
        errorCode: JellyfinErrorCode,
        fallbackType: JellyfinAuthFailureType,
    ): LoginUiStatus {
        return when (errorCode) {
            JellyfinErrorCode.INVALID_URL -> LoginUiStatus.VALIDATION_FAILED
            JellyfinErrorCode.AUTH_FAILED -> LoginUiStatus.LOGIN_FAILED
            JellyfinErrorCode.FORBIDDEN -> LoginUiStatus.LOGIN_FAILED
            JellyfinErrorCode.NETWORK_UNREACHABLE -> LoginUiStatus.SERVER_UNREACHABLE
            JellyfinErrorCode.TLS_CERTIFICATE_ERROR -> LoginUiStatus.CERTIFICATE_WARNING
            JellyfinErrorCode.SERVER_ERROR -> LoginUiStatus.SERVER_UNREACHABLE
            JellyfinErrorCode.UNKNOWN -> when (fallbackType) {
                JellyfinAuthFailureType.VALIDATION -> LoginUiStatus.VALIDATION_FAILED
                JellyfinAuthFailureType.INVALID_CREDENTIALS -> LoginUiStatus.LOGIN_FAILED
                JellyfinAuthFailureType.SERVER_UNREACHABLE -> LoginUiStatus.SERVER_UNREACHABLE
                JellyfinAuthFailureType.CERTIFICATE_WARNING -> LoginUiStatus.CERTIFICATE_WARNING
                JellyfinAuthFailureType.UNKNOWN -> LoginUiStatus.LOGIN_FAILED
            }
        }
    }

    fun syncManagerState() {
        managerStatus = if (servers.isEmpty()) {
            ServerManagerViewStatus.EMPTY_LIST
        } else {
            ServerManagerViewStatus.LIST
        }
    }

    fun resolveStartupStateAfterConfig() {
        val defaultServer = defaultServer()
        val session = currentSession()
        startupStatus = when {
            defaultServer == null -> StartupRestoreStatus.NO_DEFAULT_SERVER
            session != null &&
                session.rememberSession &&
                session.serverId == defaultServer.id &&
                !session.accessToken.isNullOrBlank() -> {
                StartupRestoreStatus.RESTORE_SUCCESS
            }
            else -> StartupRestoreStatus.SESSION_INVALID
        }
        AppLogger.info(
            "Startup",
            "Resolved startup state=$startupStatus, defaultServer=${defaultServer?.id ?: "<none>"}, hasSession=${session != null}, rememberSession=${session?.rememberSession == true}, hasAccessToken=${!session?.accessToken.isNullOrBlank()}."
        )
    }

    fun replaceServer(updated: JellyfinServer) {
        val index = servers.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            servers[index] = updated
        }
    }

    fun String.withFavoriteBadge(isFavorite: Boolean): String {
        val segments = split(" · ")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "已收藏" }
            .toMutableList()
        if (isFavorite) {
            segments.add("已收藏")
        }
        return segments.joinToString(" · ")
    }

    fun applyFavoriteOverride(item: LibraryItem): LibraryItem {
        val favorite = favoriteOverrides[item.itemId] ?: return item
        return item.copy(subtitle = item.subtitle.withFavoriteBadge(favorite))
    }

    fun rememberLibraryItems(items: List<LibraryItem>) {
        items.forEach { item ->
            knownLibraryItems[item.itemId] = applyFavoriteOverride(item)
        }
    }

    fun rememberHomeStateLibraryItems(state: HomeState) {
        rememberLibraryItems(state.latestAdded)
        state.mediaViews.forEach { view ->
            rememberLibraryItems(view.items)
        }
        primaryMediaViewId = state.mediaViews.firstOrNull()?.viewId ?: primaryMediaViewId
    }

    fun applyFavoriteOverrides(state: HomeState): HomeState {
        return state.copy(
            latestAdded = state.latestAdded.map(::applyFavoriteOverride),
            mediaViews = state.mediaViews.map { view ->
                view.copy(items = view.items.map(::applyFavoriteOverride))
            },
        )
    }

    fun applyFavoriteOverrides(items: List<LibraryItem>): List<LibraryItem> {
        val merged = linkedMapOf<String, LibraryItem>()
        items.forEach { item ->
            val overridden = applyFavoriteOverride(item)
            val favorite = favoriteOverrides[item.itemId]
            if (favorite != false) {
                merged[item.itemId] = overridden
            }
        }
        favoriteOverrides.forEach { (itemId, favorite) ->
            if (favorite) {
                val cached = knownLibraryItems[itemId] ?: return@forEach
                if (itemId !in merged) {
                    merged[itemId] = applyFavoriteOverride(cached)
                }
            } else {
                merged.remove(itemId)
            }
        }
        return merged.values.toList()
    }

    fun applyFavoriteOverride(detail: VideoDetail): VideoDetail {
        return detail.copy(
            isFavorite = favoriteOverrides[detail.itemId] ?: detail.isFavorite,
        )
    }

    fun rememberDetail(detail: VideoDetail) {
        knownLibraryItems[detail.itemId] = LibraryItem(
            itemId = detail.itemId,
            title = detail.title,
            subtitle = detail.metaLine.ifBlank { "详情页内容" },
        ).let(::applyFavoriteOverride)
    }

    fun markSingleDefault(serverId: String) {
        for (index in servers.indices) {
            val current = servers[index]
            servers[index] = current.copy(isDefault = current.id == serverId)
        }
    }

    fun ensureSingleDefaultServer(): Boolean {
        if (servers.isEmpty()) return false
        val preferredIndex = servers.indexOfFirst { it.isDefault }.let { index ->
            if (index >= 0) index else 0
        }
        var changed = false
        for (index in servers.indices) {
            val current = servers[index]
            val shouldBeDefault = index == preferredIndex
            if (current.isDefault != shouldBeDefault) {
                servers[index] = current.copy(isDefault = shouldBeDefault)
                changed = true
            }
        }
        return changed
    }

    fun reloadServersFromStorage() {
        servers.clear()
        servers.addAll(appPersistence.loadServers())
        val defaultServerHealed = ensureSingleDefaultServer()
        if (defaultServerHealed) {
            appPersistence.saveServers(servers.toList())
            AppLogger.warn("Startup", "Healed missing or inconsistent default server selection from storage.")
        }
        syncManagerState()
        AppLogger.info(
            "Startup",
            "Reloaded servers: count=${servers.size}, defaultServerId=${defaultServer()?.id ?: "<none>"}."
        )
    }

    LaunchedEffect(Unit) {
        AppLogger.info("Startup", "Startup restore launched.")
        startupStatus = StartupRestoreStatus.LOADING
        delay(300)
        reloadServersFromStorage()
        resolveStartupStateAfterConfig()
    }

    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) {
            darkColorScheme(
                primary = Color(0xFFFF7A59),
                onPrimary = Color(0xFFFFFFFF),
                secondary = Color(0xFFFFC98B),
                onSecondary = Color(0xFF2C1606),
                background = Color(0xFF111318),
                onBackground = Color(0xFFF4F1ED),
                surface = Color(0xFF191D24),
                onSurface = Color(0xFFF5F2EE),
                surfaceVariant = Color(0xFF242A33),
                onSurfaceVariant = Color(0xFFB5BCC7),
                errorContainer = Color(0xFF4A2624),
                onErrorContainer = Color(0xFFFFD9D2),
                outlineVariant = Color(0xFF343B46),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFE85D3A),
                onPrimary = Color(0xFFFFFFFF),
                secondary = Color(0xFFFFC389),
                onSecondary = Color(0xFF4B2508),
                background = Color(0xFFF4F6FA),
                onBackground = Color(0xFF18202B),
                surface = Color(0xFFFFFEFD),
                onSurface = Color(0xFF18202B),
                surfaceVariant = Color(0xFFF0F3F8),
                onSurfaceVariant = Color(0xFF657180),
                errorContainer = Color(0xFFFFEBE7),
                onErrorContainer = Color(0xFF8D3426),
                outlineVariant = Color(0xFFDDE3EC),
            )
        }
    ) {
        Surface {
            val navController: NavHostController = rememberNavController()
            val currentBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = currentBackStackEntry?.destination?.route.orEmpty()

            fun currentRootTab(): RootTab? = when {
                currentRoute.contains("HomeRoute") -> RootTab.HOME
                currentRoute.contains("LibraryRoute") -> RootTab.LIBRARY
                currentRoute.contains("UpdatesRoute") -> RootTab.UPDATES
                currentRoute.contains("AccountRoute") -> RootTab.ACCOUNT
                else -> null
            }
            val activeRootTab = currentRootTab()
            val showRootChrome = activeRootTab != null

            fun navigateToRootTab(tab: RootTab) {
                when (tab) {
                    RootTab.HOME -> {
                        navController.navigate(HomeRoute) {
                            popUpTo(HomeRoute) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    RootTab.LIBRARY -> {
                        navController.navigate(LibraryRoute) {
                            popUpTo(HomeRoute) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    RootTab.UPDATES -> {
                        navController.navigate(UpdatesRoute) {
                            popUpTo(HomeRoute) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    RootTab.ACCOUNT -> {
                        navController.navigate(AccountRoute) {
                            popUpTo(HomeRoute) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    if (activeRootTab != null) {
                        RootTopBar(
                            tab = activeRootTab,
                            activeServerName = defaultServer()?.serverName,
                            onOpenSearch = { navController.navigate(SearchRoute) },
                            onOpenServerConfig = { navController.navigate(ServerConfigRoute) },
                        )
                    }
                },
                bottomBar = {
                    if (showRootChrome) {
                        RootBottomBar(
                            activeRootTab = activeRootTab,
                            onSelectTab = { tab -> navigateToRootTab(tab) },
                        )
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (isSystemInDarkTheme()) 0.14f else 0.08f),
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                )
                            )
                        )
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = StartupRestoreRoute,
                        modifier = Modifier.padding(innerPadding),
                    ) {
                composable<StartupRestoreRoute> {
                    LaunchedEffect(startupStatus) {
                        if (startupRouteResolved) return@LaunchedEffect
                        if (startupStatus == StartupRestoreStatus.LOADING) return@LaunchedEffect

                        startupRouteResolved = true
                        when (startupStatus) {
                            StartupRestoreStatus.RESTORE_SUCCESS -> {
                                navController.navigate(HomeRoute) {
                                    popUpTo(StartupRestoreRoute) { inclusive = true }
                                }
                            }
                            StartupRestoreStatus.SESSION_INVALID -> {
                                resetLoginUiStatus()
                                navController.navigate(SplashLoginRoute) {
                                    popUpTo(StartupRestoreRoute) { inclusive = true }
                                }
                            }
                            StartupRestoreStatus.NO_DEFAULT_SERVER -> {
                                navController.navigate(ServerConfigRoute) {
                                    popUpTo(StartupRestoreRoute) { inclusive = true }
                                }
                            }
                            StartupRestoreStatus.LOADING -> Unit
                        }
                    }

                    StartupRestoreScreen(
                        status = startupStatus,
                        currentDefaultServer = defaultServer(),
                        onRetry = {
                            scope.launch {
                                AppLogger.info("Startup", "Startup restore retry requested.")
                                startupStatus = StartupRestoreStatus.LOADING
                                delay(600)
                                reloadServersFromStorage()
                                resolveStartupStateAfterConfig()
                            }
                        },
                        onOpenServerConfig = { navController.navigate(ServerConfigRoute) },
                        onGoLogin = {
                            resetLoginUiStatus()
                            navController.navigate(SplashLoginRoute)
                        },
                        onGoHome = {
                            navController.navigate(HomeRoute) {
                                popUpTo(StartupRestoreRoute) { inclusive = true }
                            }
                        },
                    )
                }

                composable<ServerConfigRoute> {
                    val editingServer = editingServerId?.let { id ->
                        servers.firstOrNull { it.id == id }
                    }

                    ServerConfigScreen(
                        servers = servers,
                        viewStatus = managerStatus,
                        editingServer = editingServer,
                        probeErrorMessage = probeErrorMessage,
                        onBack = {
                            resolveStartupStateAfterConfig()
                            navController.popBackStack()
                        },
                        onStartAdd = {
                            editingServerId = null
                            probeErrorMessage = null
                            managerStatus = ServerManagerViewStatus.ADDING
                        },
                        onStartEdit = { server ->
                            editingServerId = server.id
                            probeErrorMessage = null
                            managerStatus = ServerManagerViewStatus.EDITING
                        },
                        onDeleteServer = { server ->
                            servers.removeAll { it.id == server.id }
                            val session = currentSession()
                            if (session != null && servers.none { it.id == session.serverId }) {
                                runtimeSession = null
                                appPersistence.clearSession()
                            }
                            ensureSingleDefaultServer()
                            appPersistence.saveServers(servers.toList())
                            syncManagerState()
                            resolveStartupStateAfterConfig()
                        },
                        onSetDefault = { server ->
                            markSingleDefault(server.id)
                            appPersistence.saveServers(servers.toList())
                            resolveStartupStateAfterConfig()
                        },
                        onCancelForm = {
                            probeErrorMessage = null
                            editingServerId = null
                            syncManagerState()
                        },
                        onSaveServer = { serverName, baseUrl, isDefault ->
                            val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
                            if (serverName.isBlank()) {
                                managerStatus = ServerManagerViewStatus.PROBE_FAILED
                                probeErrorMessage = "Server name is required."
                                return@ServerConfigScreen
                            }

                            if (!isFullUrl(normalizedBaseUrl)) {
                                managerStatus = ServerManagerViewStatus.PROBE_FAILED
                                probeErrorMessage = "Base URL must be a full URL."
                                return@ServerConfigScreen
                            }

                            val targetId = editingServerId
                            val normalizedKey = normalizedBaseUrl.lowercase()
                            val duplicatedServer = servers.firstOrNull { server ->
                                server.id != targetId &&
                                    normalizeBaseUrl(server.baseUrl).lowercase() == normalizedKey
                            }
                            if (duplicatedServer != null) {
                                managerStatus = ServerManagerViewStatus.PROBE_FAILED
                                probeErrorMessage =
                                    "Server URL already exists in ${duplicatedServer.serverName}. Please reuse or edit it."
                                return@ServerConfigScreen
                            }

                            val incoming = if (targetId == null) {
                                JellyfinServer(
                                    id = "srv-${Random.nextInt(1000, 9999)}",
                                    serverName = serverName,
                                    baseUrl = normalizedBaseUrl,
                                    isDefault = isDefault || servers.none { it.isDefault },
                                    health = ServerHealthStatus.UNKNOWN,
                                    errorMessage = null,
                                )
                            } else {
                                val existing = servers.firstOrNull { it.id == targetId }
                                if (existing == null) {
                                    managerStatus = ServerManagerViewStatus.PROBE_FAILED
                                    probeErrorMessage = "Server to edit not found."
                                    return@ServerConfigScreen
                                }
                                existing.copy(
                                    serverName = serverName,
                                    baseUrl = normalizedBaseUrl,
                                    isDefault = isDefault,
                                    errorMessage = null,
                                )
                            }

                            if (targetId == null) {
                                if (incoming.isDefault) {
                                    for (index in servers.indices) {
                                        val current = servers[index]
                                        servers[index] = current.copy(isDefault = false)
                                    }
                                }
                                servers.add(incoming)
                            } else {
                                if (incoming.isDefault) {
                                    for (index in servers.indices) {
                                        val current = servers[index]
                                        servers[index] = current.copy(isDefault = false)
                                    }
                                }
                                replaceServer(incoming)
                            }

                            ensureSingleDefaultServer()
                            appPersistence.saveServers(servers.toList())
                            editingServerId = null
                            probeErrorMessage = null
                            syncManagerState()
                            resolveStartupStateAfterConfig()
                        },
                        onTestConnection = { _, baseUrl ->
                            val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
                            AppLogger.info(
                                "Probe",
                                "UI TestConnect requested: baseUrl=${summarizeBaseUrlForLog(normalizedBaseUrl)}, editingServerId=${editingServerId ?: "<new>"}."
                            )
                            if (!isFullUrl(normalizedBaseUrl)) {
                                AppLogger.warn("Probe", "UI rejected TestConnect due to invalid URL format.")
                                managerStatus = ServerManagerViewStatus.PROBE_FAILED
                                probeErrorMessage = "Invalid base URL format."
                                return@ServerConfigScreen
                            }

                            managerStatus = ServerManagerViewStatus.PROBING
                            probeErrorMessage = null

                            scope.launch {
                                when (val result = getJellyfinConnectionProbe().probe(normalizedBaseUrl)) {
                                    is JellyfinProbeResult.Success -> {
                                        AppLogger.info(
                                            "Probe",
                                            "UI TestConnect success: baseUrl=${summarizeBaseUrlForLog(result.normalizedBaseUrl)}."
                                        )
                                        managerStatus = if (editingServerId == null) {
                                            ServerManagerViewStatus.ADDING
                                        } else {
                                            ServerManagerViewStatus.EDITING
                                        }
                                        probeErrorMessage = null
                                    }

                                    is JellyfinProbeResult.Failure -> {
                                        AppLogger.warn(
                                            "Probe",
                                            "UI TestConnect failure: code=${result.errorCode}, retryable=${result.retryable}, certificate=${result.isCertificateIssue}."
                                        )
                                        managerStatus = ServerManagerViewStatus.PROBE_FAILED
                                        probeErrorMessage = appendActionHint(
                                            message = result.message,
                                            errorCode = result.errorCode,
                                            retryable = result.retryable,
                                        )
                                    }
                                }
                            }
                        },
                        onProceedToLogin = {
                            if (defaultServer() == null && servers.isNotEmpty()) {
                                ensureSingleDefaultServer()
                                appPersistence.saveServers(servers.toList())
                            }
                            resolveStartupStateAfterConfig()
                            if (defaultServer() == null) {
                                managerStatus = if (servers.isEmpty()) {
                                    ServerManagerViewStatus.EMPTY_LIST
                                } else {
                                    ServerManagerViewStatus.LIST
                                }
                                probeErrorMessage = "请先设置一台默认服务器后再登录。"
                                return@ServerConfigScreen
                            }
                            resetLoginUiStatus()
                            navController.navigate(SplashLoginRoute)
                        },
                    )
                }

                composable<SplashLoginRoute> {
                    SplashLoginScreen(
                        currentServer = defaultServer(),
                        loginStatus = loginStatus,
                        statusMessage = loginStatusMessage,
                        onSwitchServer = {
                            resetLoginUiStatus()
                            navController.navigate(ServerConfigRoute)
                        },
                        onEditAddress = {
                            resetLoginUiStatus()
                            navController.navigate(ServerConfigRoute)
                        },
                        onInputChanged = {
                            if (loginStatus != LoginUiStatus.LOGGING_IN) {
                                loginStatus = LoginUiStatus.INPUTTING
                                loginStatusMessage = "正在输入登录信息。"
                            }
                        },
                        onSubmitLogin = { username, password, rememberSession ->
                            val activeServer = defaultServer()
                            AppLogger.info(
                                "Auth",
                                "UI submit login: baseUrl=${summarizeBaseUrlForLog(activeServer?.baseUrl)}, username=${summarizeUsernameForLog(username)}, rememberSession=$rememberSession."
                            )
                            if (activeServer == null) {
                                AppLogger.warn("Auth", "UI login blocked: no default server.")
                                loginStatus = LoginUiStatus.VALIDATION_FAILED
                                loginStatusMessage = "未检测到默认服务器，请先在服务器管理中设置默认服务器。"
                                return@SplashLoginScreen
                            }
                            if (!isFullUrl(activeServer.baseUrl)) {
                                AppLogger.warn("Auth", "UI login blocked: invalid current server URL.")
                                loginStatus = LoginUiStatus.VALIDATION_FAILED
                                loginStatusMessage = "当前服务器地址无效，请先修正后再登录。"
                                return@SplashLoginScreen
                            }
                            if (username.isBlank() || password.isBlank()) {
                                AppLogger.warn("Auth", "UI login blocked: username or password empty.")
                                loginStatus = LoginUiStatus.VALIDATION_FAILED
                                loginStatusMessage = "请输入用户名和密码。"
                                return@SplashLoginScreen
                            }

                            scope.launch {
                                loginStatus = LoginUiStatus.LOGGING_IN
                                loginStatusMessage = "正在登录..."
                                AppLogger.info("Auth", "UI login switched to LOGGING_IN.")

                                when (
                                    val result = getJellyfinAuthRepository().login(
                                        baseUrl = activeServer.baseUrl,
                                        username = username,
                                        password = password,
                                    )
                                ) {
                                    is JellyfinAuthResult.Success -> {
                                        AppLogger.info(
                                            "Auth",
                                            "UI login success: baseUrl=${summarizeBaseUrlForLog(result.baseUrl)}, username=${summarizeUsernameForLog(result.username)}, rememberSession=$rememberSession."
                                        )
                                        favoriteOverrides.clear()
                                        knownLibraryItems.clear()
                                        favoriteRefreshVersion += 1
                                        val activeSession = SessionRecord(
                                            serverId = activeServer.id,
                                            userId = result.userId,
                                            username = result.username,
                                            rememberSession = rememberSession,
                                            accessToken = result.accessToken,
                                            savedAtEpochMillis = 0L,
                                        )
                                        runtimeSession = activeSession
                                        if (rememberSession) {
                                            appPersistence.saveSession(activeSession)
                                        } else {
                                            appPersistence.clearSession()
                                        }

                                        startupStatus = StartupRestoreStatus.RESTORE_SUCCESS
                                        accountStatusMessage = null
                                        loginStatus = LoginUiStatus.DEFAULT
                                        loginStatusMessage = "已登录为 ${result.username}。"
                                        navController.navigate(HomeRoute) {
                                            popUpTo(StartupRestoreRoute) { inclusive = false }
                                        }
                                    }

                                    is JellyfinAuthResult.Failure -> {
                                        AppLogger.warn(
                                            "Auth",
                                            "UI login failure: type=${result.type}, code=${result.errorCode}, retryable=${result.retryable}."
                                        )
                                        loginStatus = mapLoginStatus(
                                            errorCode = result.errorCode,
                                            fallbackType = result.type,
                                        )
                                        loginStatusMessage = appendActionHint(
                                            message = result.message,
                                            errorCode = result.errorCode,
                                            retryable = result.retryable,
                                        )
                                    }
                                }
                            }
                        },
                        onConfirmCertificate = { _, _, _ ->
                            loginStatus = LoginUiStatus.CERTIFICATE_WARNING
                            loginStatusMessage =
                                "当前暂不支持忽略证书风险，请修复证书或切换服务器。"
                        },
                    )
                }

                composable<HomeRoute> {
                    var homeState by remember { mutableStateOf(emptyHomeState()) }
                    var isHomeLoading by remember { mutableStateOf(true) }
                    val activeServer = defaultServer()
                    val session = currentSession()

                    suspend fun reloadHome() {
                        val previousState = homeState
                        val hadPreviousData = hasHomeContent(previousState)
                        isHomeLoading = true
                        val loadedState = runCatching {
                            homeRepository.loadHomeState(activeServer, session)
                        }.getOrElse { error ->
                            AppLogger.error(
                                tag = "Home",
                                message = "reloadHome failed unexpectedly.",
                                throwable = error,
                            )
                            val fallbackIssue = JellyfinLoadIssue(
                                errorCode = JellyfinErrorCode.UNKNOWN,
                                message = if (hadPreviousData) {
                                    "首页这次没有刷新完整，当前先展示可用内容。"
                                } else {
                                    "首页暂时还没准备好，稍后再刷新一次即可。"
                                },
                                retryable = true,
                            )
                            homeState = previousState.copy(loadIssue = fallbackIssue)
                            isHomeLoading = false
                            return
                        }

                        val resolvedState = loadedState
                            .let(::applyFavoriteOverrides)
                            .also(::rememberHomeStateLibraryItems)
                        val hasFreshData = hasHomeContent(resolvedState)
                        val shouldKeepPreviousData = resolvedState.loadIssue != null &&
                            !hasFreshData &&
                            hadPreviousData
                        homeState = if (shouldKeepPreviousData) {
                            previousState.copy(loadIssue = resolvedState.loadIssue)
                        } else {
                            resolvedState
                        }
                        isHomeLoading = false
                    }

                    suspend fun playFromHome(
                        itemId: String,
                        preferContinueEpisode: Boolean,
                    ) {
                        val resolvedDetail = runCatching {
                            detailRepository.loadDetail(
                                itemId = itemId,
                                defaultServer = activeServer,
                                session = session,
                            )
                        }.getOrNull()?.let(::applyFavoriteOverride)

                        if (resolvedDetail == null || resolvedDetail.episodes.isEmpty()) {
                            navController.navigate(DetailRoute(itemId))
                            return
                        }

                        val continueEpisode = resolvedDetail.episodes.firstOrNull {
                            it.id == resolvedDetail.continueEpisodeId
                        } ?: resolvedDetail.episodes.firstOrNull()
                        val firstPlayableEpisode = resolvedDetail.episodes.firstOrNull {
                            !it.playbackItemId.isNullOrBlank()
                        }
                        val targetEpisode = if (preferContinueEpisode) {
                            continueEpisode ?: firstPlayableEpisode
                        } else {
                            firstPlayableEpisode ?: continueEpisode
                        }

                        if (targetEpisode == null) {
                            navController.navigate(DetailRoute(itemId))
                            return
                        }

                        navController.navigate(
                            PlayerRoute(
                                itemId = resolvedDetail.itemId,
                                episodeId = targetEpisode.id,
                                playbackItemId = targetEpisode.playbackItemId,
                            )
                        )
                    }

                    LaunchedEffect(
                        activeServer?.id,
                        activeServer?.baseUrl,
                        session?.serverId,
                        session?.accessToken,
                        favoriteRefreshVersion,
                    ) {
                        reloadHome()
                    }

                    HomeScreen(
                        state = homeState,
                        isLoading = isHomeLoading,
                        onOpenUpdates = { navController.navigate(UpdatesRoute) },
                        onOpenSearch = { navController.navigate(SearchRoute) },
                        onOpenFavorites = { navController.navigate(FavoritesRoute) },
                        onOpenAccount = { navController.navigate(AccountRoute) },
                        onOpenMediaView = { view ->
                            navController.navigate(MediaViewRoute(view.viewId))
                        },
                        onOpenDetail = { itemId ->
                            navController.navigate(DetailRoute(itemId))
                        },
                        onPlayContinue = { itemId ->
                            scope.launch {
                                playFromHome(itemId = itemId, preferContinueEpisode = true)
                            }
                        },
                        onPlayNextUp = { itemId ->
                            scope.launch {
                                playFromHome(itemId = itemId, preferContinueEpisode = false)
                            }
                        },
                        onRetry = {
                            scope.launch {
                                reloadHome()
                            }
                        },
                    )
                }

                composable<LibraryRoute> {
                    var libraryState by remember { mutableStateOf(emptyHomeState()) }
                    var isLibraryLoading by remember { mutableStateOf(true) }
                    val activeServer = defaultServer()
                    val session = currentSession()

                    suspend fun reloadLibrary() {
                        val previousState = libraryState
                        val hadPreviousData = hasHomeContent(previousState)
                        isLibraryLoading = true
                        val loadedState = runCatching {
                            homeRepository.loadHomeState(activeServer, session)
                        }.getOrElse { error ->
                            AppLogger.error(
                                tag = "Library",
                                message = "reloadLibrary failed unexpectedly.",
                                throwable = error,
                            )
                            val fallbackIssue = JellyfinLoadIssue(
                                errorCode = JellyfinErrorCode.UNKNOWN,
                                message = if (hadPreviousData) {
                                    "媒体库这次没有刷新完整，当前先展示可用目录。"
                                } else {
                                    "媒体库暂时还没准备好，稍后再试一次即可。"
                                },
                                retryable = true,
                            )
                            libraryState = if (hadPreviousData) {
                                previousState.copy(loadIssue = fallbackIssue)
                            } else {
                                emptyHomeState(loadIssue = fallbackIssue)
                            }
                            isLibraryLoading = false
                            return
                        }
                        val resolvedState = loadedState
                            .let(::applyFavoriteOverrides)
                            .also(::rememberHomeStateLibraryItems)
                        val hasFreshData = hasHomeContent(resolvedState)
                        val shouldKeepPreviousData = resolvedState.loadIssue != null &&
                            !hasFreshData &&
                            hadPreviousData
                        libraryState = if (shouldKeepPreviousData) {
                            previousState.copy(loadIssue = resolvedState.loadIssue)
                        } else {
                            resolvedState
                        }
                        isLibraryLoading = false
                    }

                    LaunchedEffect(
                        activeServer?.id,
                        activeServer?.baseUrl,
                        session?.serverId,
                        session?.accessToken,
                        favoriteRefreshVersion,
                    ) {
                        reloadLibrary()
                    }

                    LibraryHubScreen(
                        state = libraryState,
                        isLoading = isLibraryLoading,
                        onOpenSearch = { navController.navigate(SearchRoute) },
                        onOpenMediaView = { view ->
                            navController.navigate(MediaViewRoute(view.viewId))
                        },
                        onOpenDetail = { itemId ->
                            navController.navigate(DetailRoute(itemId))
                        },
                        onRetry = {
                            scope.launch {
                                reloadLibrary()
                            }
                        },
                    )
                }

                composable<AccountRoute> {
                    val activeServer = defaultServer()
                    val session = currentSession()
                    val sessionHasToken = !session?.accessToken.isNullOrBlank()
                    val sessionHasUserId = !session?.userId.isNullOrBlank()
                    val sessionServerMatchesCurrent = session != null &&
                        activeServer != null &&
                        session.serverId == activeServer.id

                    fun buildSessionHealthIssueList(): List<String> {
                        if (session == null) return listOf("未发现本地会话记录")
                        val issues = mutableListOf<String>()
                        if (!session.rememberSession) {
                            issues += "当前为临时登录，会话不会持久化"
                        }
                        if (!sessionHasToken) {
                            issues += "会话缺少访问令牌"
                        }
                        if (activeServer == null) {
                            issues += "未检测到默认服务器"
                        } else if (!sessionServerMatchesCurrent) {
                            issues += "会话服务器与当前默认服务器不一致"
                        }
                        if (!sessionHasUserId) {
                            issues += "会话缺少用户标识"
                        }
                        return issues
                    }

                    fun buildSessionHealthMessage(): String {
                        val issues = buildSessionHealthIssueList()
                        if (issues.isEmpty()) {
                            return "会话健康检查通过：登录态、服务器绑定和令牌状态正常。"
                        }
                        return "会话健康检查：${issues.joinToString(separator = "；")}。建议执行“重新登录”或“服务器管理”进行修复。"
                    }

                    LaunchedEffect(
                        session?.serverId,
                        session?.userId,
                        session?.rememberSession,
                        session?.accessToken,
                        activeServer?.id,
                    ) {
                        val issues = buildSessionHealthIssueList()
                        if (issues.isNotEmpty() && accountStatusMessage.isNullOrBlank()) {
                            accountStatusMessage = buildSessionHealthMessage()
                        }
                    }

                    AccountScreen(
                        username = session?.username,
                        userId = session?.userId,
                        currentServer = activeServer,
                        rememberSession = session?.rememberSession == true,
                        hasSessionRecord = session != null,
                        hasSessionToken = sessionHasToken,
                        hasSessionUserId = sessionHasUserId,
                        sessionServerMatchesCurrent = sessionServerMatchesCurrent,
                        sessionSavedAtEpochMillis = session?.savedAtEpochMillis,
                        statusMessage = accountStatusMessage,
                        onOpenServerConfig = { navController.navigate(ServerConfigRoute) },
                        onOpenFavorites = { navController.navigate(FavoritesRoute) },
                        onOpenUpdates = { navController.navigate(UpdatesRoute) },
                        onRunSessionCheck = {
                            accountStatusMessage = buildSessionHealthMessage()
                        },
                        onGoLogin = {
                            resetLoginUiStatus()
                            navController.navigate(SplashLoginRoute)
                        },
                        onSignOut = {
                            runtimeSession = null
                            appPersistence.clearSession()
                            favoriteOverrides.clear()
                            knownLibraryItems.clear()
                            favoriteRefreshVersion += 1
                            startupStatus = StartupRestoreStatus.SESSION_INVALID
                            accountStatusMessage = "已退出登录。"
                            resetLoginUiStatus()
                            navController.navigate(SplashLoginRoute) {
                                popUpTo(HomeRoute) { inclusive = true }
                            }
                        },
                    )
                }

                composable<UpdatesRoute> {
                    var updates by remember { mutableStateOf<List<UpdateItem>>(emptyList()) }
                    var recentlyAdded by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
                    var isUpdatesLoading by remember { mutableStateOf(true) }
                    var isShowingStaleUpdates by remember { mutableStateOf(false) }
                    var updatesLoadIssueMessage by remember { mutableStateOf<String?>(null) }
                    val activeServer = defaultServer()
                    val session = currentSession()

                    suspend fun reloadUpdates() {
                        val previousUpdates = updates
                        val previousRecentlyAdded = recentlyAdded
                        val hadPreviousData = previousUpdates.isNotEmpty() || previousRecentlyAdded.isNotEmpty()
                        isUpdatesLoading = true
                        val state = runCatching {
                            homeRepository.loadHomeState(activeServer, session)
                        }.getOrElse { error ->
                            AppLogger.error(
                                tag = "Updates",
                                message = "reloadUpdates failed unexpectedly.",
                                throwable = error,
                            )
                            if (hadPreviousData) {
                                updates = previousUpdates
                                recentlyAdded = previousRecentlyAdded
                                isShowingStaleUpdates = true
                                updatesLoadIssueMessage = "这次没有拿到完整更新，当前先展示可用内容。"
                            } else {
                                updates = emptyList()
                                recentlyAdded = emptyList()
                                isShowingStaleUpdates = false
                                updatesLoadIssueMessage = "更新列表暂时还没准备好，稍后再刷新一次即可。"
                            }
                            isUpdatesLoading = false
                            return
                        }
                        val groupedUpdates = state.updates
                        val recentLibraryAdds = state.latestAdded
                            .filterNot { candidate ->
                                groupedUpdates.any { update -> update.itemId == candidate.itemId }
                            }
                            .distinctBy { candidate -> candidate.itemId }
                        val resolvedIssueMessage = state.loadIssue?.let { issue ->
                            appendActionHint(
                                message = issue.message,
                                errorCode = issue.errorCode,
                                retryable = issue.retryable,
                            )
                        }
                        val hasFreshData = groupedUpdates.isNotEmpty() || recentLibraryAdds.isNotEmpty()
                        val shouldKeepPreviousData = resolvedIssueMessage != null && !hasFreshData && hadPreviousData
                        if (shouldKeepPreviousData) {
                            updates = previousUpdates
                            recentlyAdded = previousRecentlyAdded
                            isShowingStaleUpdates = true
                        } else {
                            updates = groupedUpdates
                            recentlyAdded = recentLibraryAdds
                            isShowingStaleUpdates = false
                        }
                        updatesLoadIssueMessage = resolvedIssueMessage
                        isUpdatesLoading = false
                    }

                    LaunchedEffect(
                        activeServer?.id,
                        activeServer?.baseUrl,
                        session?.serverId,
                        session?.accessToken,
                        favoriteRefreshVersion,
                    ) {
                        reloadUpdates()
                    }

                    UpdatesScreen(
                        updates = updates,
                        recentlyAdded = recentlyAdded,
                        isLoading = isUpdatesLoading,
                        isShowingStaleData = isShowingStaleUpdates,
                        loadIssueMessage = updatesLoadIssueMessage,
                        onRefresh = {
                            if (!isUpdatesLoading) {
                                scope.launch {
                                    reloadUpdates()
                                }
                            }
                        },
                        onRetry = {
                            if (!isUpdatesLoading) {
                                scope.launch {
                                    reloadUpdates()
                                }
                            }
                        },
                        onOpenDetail = { itemId ->
                            navController.navigate(DetailRoute(itemId))
                        },
                    )
                }

                composable<SearchRoute> {
                    var keyword by remember { mutableStateOf("") }
                    var isLoading by remember { mutableStateOf(true) }
                    var isShowingStaleSearch by remember { mutableStateOf(false) }
                    var hintMessage by remember {
                        mutableStateOf("正在准备搜索内容…")
                    }
                    var results by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
                    var searchLoadIssueMessage by remember { mutableStateOf<String?>(null) }
                    val activeServer = defaultServer()
                    val session = currentSession()

                    suspend fun performSearch(query: String = keyword) {
                        isLoading = true
                        val previousResults = results
                        val repositoryResult = runCatching {
                            searchRepository.search(
                                keyword = query,
                                defaultServer = activeServer,
                                session = session,
                            )
                        }.getOrNull()

                        if (repositoryResult == null) {
                            if (previousResults.isNotEmpty()) {
                                results = previousResults
                                isShowingStaleSearch = true
                                searchLoadIssueMessage = "这次没有拿到完整搜索结果，当前先展示可用内容。"
                                hintMessage = "搜索结果还在更新中，当前先展示可用内容。"
                            } else {
                                results = emptyList()
                                isShowingStaleSearch = false
                                searchLoadIssueMessage = "搜索结果暂时还没准备好，稍后再试一次即可。"
                                hintMessage = if (query.isBlank()) {
                                    "输入关键词搜索媒体，或直接浏览推荐内容。"
                                } else {
                                    "搜索失败，请检查网络后重试。"
                                }
                            }
                            isLoading = false
                            return
                        }

                        val found = repositoryResult.items
                        val loadIssue = repositoryResult.loadIssue
                        val loadIssueMessage = loadIssue?.let { issue ->
                            appendActionHint(
                                message = issue.message,
                                errorCode = issue.errorCode,
                                retryable = issue.retryable,
                            )
                        }
                        val shouldKeepPrevious = loadIssue != null &&
                            previousResults.isNotEmpty() &&
                            found.isEmpty()
                        results = if (shouldKeepPrevious) previousResults else found
                        isShowingStaleSearch = loadIssue != null && results.isNotEmpty()
                        searchLoadIssueMessage = if (shouldKeepPrevious) {
                            listOfNotNull(
                                "这次没有拿到完整搜索结果，当前先展示可用内容。",
                                loadIssueMessage,
                            ).joinToString(" ")
                        } else {
                            loadIssueMessage
                        }
                        hintMessage = when {
                            query.isBlank() && results.isEmpty() -> {
                                "输入关键词搜索媒体，或直接浏览推荐内容。"
                            }

                            results.isEmpty() && loadIssue != null -> {
                                "搜索暂时还没准备好，稍后再试一次即可。"
                            }

                            results.isEmpty() -> {
                                "未搜索到相关内容，请换个关键词重试。"
                            }

                            isShowingStaleSearch -> {
                                "当前先展示已有结果，可稍后刷新获取最新内容。"
                            }

                            else -> {
                                "共找到 ${results.size} 条结果。"
                            }
                        }
                        isLoading = false
                    }

                    fun triggerSearch() {
                        scope.launch {
                            performSearch()
                        }
                    }

                    LaunchedEffect(
                        activeServer?.id,
                        activeServer?.baseUrl,
                        session?.serverId,
                        session?.accessToken,
                        keyword,
                    ) {
                        if (keyword.isBlank()) {
                            performSearch("")
                        } else {
                            delay(300)
                            performSearch(keyword)
                        }
                    }

                    SearchScreen(
                        keyword = keyword,
                        results = results,
                        isLoading = isLoading,
                        isShowingStaleData = isShowingStaleSearch,
                        hintMessage = hintMessage,
                        loadIssueMessage = searchLoadIssueMessage,
                        onKeywordChange = { keyword = it },
                        onSearch = { triggerSearch() },
                        onNavigateBack = { navController.popBackStack() },
                        onOpenLibrary = { navigateToRootTab(RootTab.LIBRARY) },
                        onOpenDetail = { itemId ->
                            navController.navigate(DetailRoute(itemId))
                        },
                    )
                }

                composable<FavoritesRoute> {
                    var favorites by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
                    var isFavoritesLoading by remember { mutableStateOf(true) }
                    var isShowingStaleFavorites by remember { mutableStateOf(false) }
                    var favoritesLoadIssueMessage by remember { mutableStateOf<String?>(null) }
                    val activeServer = defaultServer()
                    val session = currentSession()

                    suspend fun reloadFavorites() {
                        isFavoritesLoading = true
                        val previousFavorites = favorites
                        val result = runCatching {
                            favoritesRepository.loadFavorites(
                                defaultServer = activeServer,
                                session = session,
                            )
                        }.getOrElse { error ->
                            AppLogger.error(
                                tag = "Favorites",
                                message = "reloadFavorites failed unexpectedly.",
                                throwable = error,
                            )
                            if (previousFavorites.isNotEmpty()) {
                                favorites = previousFavorites
                                isShowingStaleFavorites = true
                                favoritesLoadIssueMessage = "这次没有拿到完整收藏列表，当前先展示可用内容。"
                            } else {
                                favorites = emptyList()
                                isShowingStaleFavorites = false
                                favoritesLoadIssueMessage = "收藏列表暂时还没准备好，稍后再刷新一次即可。"
                            }
                            isFavoritesLoading = false
                            return
                        }
                        val latestFavorites = applyFavoriteOverrides(result.items)
                        val hasIssue = result.loadIssue != null
                        val shouldKeepPrevious = hasIssue && previousFavorites.isNotEmpty() && latestFavorites.isEmpty()
                        favorites = if (shouldKeepPrevious) previousFavorites else latestFavorites
                        isShowingStaleFavorites = hasIssue && favorites.isNotEmpty()
                        rememberLibraryItems(favorites)
                        favoritesLoadIssueMessage = result.loadIssue?.let { issue ->
                            appendActionHint(
                                message = issue.message,
                                errorCode = issue.errorCode,
                                retryable = issue.retryable,
                            )
                        }
                        isFavoritesLoading = false
                    }

                    LaunchedEffect(
                        activeServer?.id,
                        activeServer?.baseUrl,
                        session?.serverId,
                        session?.accessToken,
                        favoriteRefreshVersion,
                    ) {
                        reloadFavorites()
                    }

                    FavoritesScreen(
                        favorites = favorites,
                        isLoading = isFavoritesLoading,
                        isShowingStaleData = isShowingStaleFavorites,
                        loadIssueMessage = favoritesLoadIssueMessage,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenLibrary = { navigateToRootTab(RootTab.LIBRARY) },
                        onRetry = {
                            if (!isFavoritesLoading) {
                                scope.launch {
                                    reloadFavorites()
                                }
                            }
                        },
                        onOpenDetail = { itemId ->
                            navController.navigate(DetailRoute(itemId))
                        },
                    )
                }

                composable<MediaViewRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<MediaViewRoute>()
                    val pageSize = 24
                    var sortOption by remember(route.viewId) { mutableStateOf(MediaViewSortOption.RECENT) }
                    var viewState by remember(route.viewId) { mutableStateOf<HomeMediaView?>(null) }
                    var loadIssueMessage by remember(route.viewId) { mutableStateOf<String?>(null) }
                    var hasMore by remember(route.viewId) { mutableStateOf(false) }
                    var nextStartIndex by remember(route.viewId) { mutableStateOf(0) }
                    var totalCount by remember(route.viewId) { mutableStateOf(0) }
                    var isRefreshing by remember(route.viewId) { mutableStateOf(true) }
                    var isLoadingMore by remember(route.viewId) { mutableStateOf(false) }
                    val activeServer = defaultServer()
                    val session = currentSession()

                    suspend fun loadMediaViewPage(startIndex: Int, append: Boolean) {
                        if (append) {
                            isLoadingMore = true
                        } else {
                            isRefreshing = true
                        }
                        try {
                            val result = runCatching {
                                mediaViewRepository.loadMediaView(
                                    viewId = route.viewId,
                                    startIndex = startIndex,
                                    limit = pageSize,
                                    sortOption = sortOption,
                                    defaultServer = activeServer,
                                    session = session,
                                )
                            }.getOrElse { error ->
                                AppLogger.error(
                                    tag = "MediaView",
                                    message = "loadMediaViewPage failed unexpectedly.",
                                    throwable = error,
                                )
                                loadIssueMessage = if (viewState?.items?.isNotEmpty() == true) {
                                    "这个分区这次没有刷新完整，当前先展示可用内容。"
                                } else {
                                    "这个分区暂时还没准备好，稍后再试一次即可。"
                                }
                                return
                            }
                            val incomingView = result.view?.copy(
                                items = result.view.items.map(::applyFavoriteOverride),
                            )
                            val currentView = viewState
                            incomingView?.let { rememberLibraryItems(it.items) }
                            when {
                                append && currentView != null && incomingView != null -> {
                                    viewState = currentView.copy(
                                        title = incomingView.title,
                                        subtitle = incomingView.subtitle,
                                        items = (currentView.items + incomingView.items).distinctBy { it.itemId },
                                    )
                                }
                                incomingView != null -> {
                                    viewState = incomingView
                                }
                            }
                            val shouldUpdatePaging = incomingView != null || result.loadIssue == null
                            if (shouldUpdatePaging) {
                                hasMore = result.hasMore
                                nextStartIndex = result.nextStartIndex
                                totalCount = maxOf(result.totalCount, viewState?.items?.size ?: 0)
                            } else {
                                totalCount = maxOf(totalCount, viewState?.items?.size ?: 0)
                            }
                            loadIssueMessage = result.loadIssue?.let { issue ->
                                appendActionHint(
                                    message = issue.message,
                                    errorCode = issue.errorCode,
                                    retryable = issue.retryable,
                                )
                            }
                        } finally {
                            isLoadingMore = false
                            isRefreshing = false
                        }
                    }

                    LaunchedEffect(
                        route.viewId,
                        activeServer?.id,
                        activeServer?.baseUrl,
                        session?.serverId,
                        session?.accessToken,
                        favoriteRefreshVersion,
                        sortOption,
                    ) {
                        loadIssueMessage = null
                        isLoadingMore = false
                        loadMediaViewPage(startIndex = 0, append = false)
                    }

                    MediaViewBrowseScreen(
                        view = viewState,
                        sortOption = sortOption,
                        totalCount = totalCount,
                        loadIssueMessage = loadIssueMessage,
                        isRefreshing = isRefreshing,
                        isLoadingMore = isLoadingMore,
                        hasMore = hasMore,
                        onBack = { navController.popBackStack() },
                        onSortChange = { selected ->
                            if (selected != sortOption) {
                                sortOption = selected
                            }
                        },
                        onRetry = {
                            if (!isRefreshing && !isLoadingMore) {
                                scope.launch {
                                    loadMediaViewPage(startIndex = 0, append = false)
                                }
                            }
                        },
                        onLoadMore = {
                            if (!isRefreshing && !isLoadingMore && hasMore) {
                                scope.launch {
                                    loadMediaViewPage(startIndex = nextStartIndex, append = true)
                                }
                            }
                        },
                        onOpenDetail = { itemId ->
                            navController.navigate(DetailRoute(itemId))
                        },
                    )
                }

                composable<DetailRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<DetailRoute>()
                    var detail by remember(route.itemId) { mutableStateOf<VideoDetail?>(null) }
                    var isDetailLoading by remember(route.itemId) { mutableStateOf(true) }
                    var isShowingStaleDetail by remember(route.itemId) { mutableStateOf(false) }
                    var detailRefreshIssueMessage by remember(route.itemId) { mutableStateOf<String?>(null) }
                    var detailReloadVersion by remember(route.itemId) { mutableStateOf(0) }
                    var isFavoriteUpdating by remember(route.itemId) { mutableStateOf(false) }
                    var favoriteStatusMessage by remember(route.itemId) { mutableStateOf<String?>(null) }
                    var isFavoriteActionError by remember(route.itemId) { mutableStateOf(false) }
                    val activeServer = defaultServer()
                    val session = currentSession()

                    LaunchedEffect(
                        route.itemId,
                        activeServer?.id,
                        activeServer?.baseUrl,
                        session?.serverId,
                        session?.accessToken,
                        favoriteRefreshVersion,
                        detailReloadVersion,
                    ) {
                        isFavoriteUpdating = false
                        favoriteStatusMessage = null
                        isFavoriteActionError = false
                        val previousDetail = detail
                        isDetailLoading = true
                        detailRefreshIssueMessage = null
                        val loadedDetail = runCatching {
                            detailRepository.loadDetail(
                                itemId = route.itemId,
                                defaultServer = activeServer,
                                session = session,
                            )
                        }.getOrElse { error ->
                            AppLogger.error(
                                tag = "DetailRoute",
                                message = "loadDetail failed unexpectedly; preserving previous detail when available.",
                                throwable = error,
                            )
                            null
                        }?.let(::applyFavoriteOverride)

                        when {
                            loadedDetail == null && previousDetail != null -> {
                                detail = previousDetail
                                isShowingStaleDetail = true
                                detailRefreshIssueMessage = "内容还在更新中，当前先展示刚才可用的版本。"
                            }

                            loadedDetail == null -> {
                                detail = null
                                isShowingStaleDetail = false
                                detailRefreshIssueMessage = "这个内容暂时还没准备好，稍后再试一次即可。"
                            }

                            loadedDetail.loadIssue != null && previousDetail != null -> {
                                detail = previousDetail.copy(
                                    isFavorite = loadedDetail.isFavorite,
                                ).also(::rememberDetail)
                                isShowingStaleDetail = true
                                detailRefreshIssueMessage = appendActionHint(
                                    message = "这次没有拿到完整内容，当前先展示可用版本。",
                                    errorCode = loadedDetail.loadIssue.errorCode,
                                    retryable = loadedDetail.loadIssue.retryable,
                                )
                            }

                            else -> {
                                detail = loadedDetail.also(::rememberDetail)
                                isShowingStaleDetail = false
                            }
                        }
                        isDetailLoading = false
                    }

                    val resolvedDetail = detail
                    if (resolvedDetail == null) {
                        MissingContentScreen(
                            isLoading = isDetailLoading,
                            message = detailRefreshIssueMessage,
                            onRetry = {
                                if (!isDetailLoading) {
                                    detailReloadVersion += 1
                                }
                            },
                            onBack = { navController.popBackStack() },
                            onOpenLibrary = { navigateToRootTab(RootTab.LIBRARY) },
                        )
                    } else {
                        DetailScreen(
                            detail = resolvedDetail,
                            isLoading = isDetailLoading,
                            isShowingStaleData = isShowingStaleDetail,
                            refreshIssueMessage = detailRefreshIssueMessage,
                            onNavigateBack = { navController.popBackStack() },
                            onOpenLibrary = { navigateToRootTab(RootTab.LIBRARY) },
                            onPlayContinue = {
                                val continueEpisode = resolvedDetail.episodes.firstOrNull {
                                    it.id == resolvedDetail.continueEpisodeId
                                }
                                navController.navigate(
                                    PlayerRoute(
                                        itemId = resolvedDetail.itemId,
                                        episodeId = resolvedDetail.continueEpisodeId,
                                        playbackItemId = continueEpisode?.playbackItemId,
                                    )
                                )
                            },
                            onPlayFromStart = {
                                val firstEpisode = resolvedDetail.episodes.firstOrNull()
                                navController.navigate(
                                    PlayerRoute(
                                        itemId = resolvedDetail.itemId,
                                        episodeId = firstEpisode?.id ?: resolvedDetail.continueEpisodeId,
                                        playbackItemId = firstEpisode?.playbackItemId,
                                    )
                                )
                            },
                            onToggleFavorite = {
                                if (isFavoriteUpdating) return@DetailScreen
                                scope.launch {
                                    val currentDetail = detail ?: return@launch
                                    val targetFavorite = !currentDetail.isFavorite
                                    isFavoriteUpdating = true
                                    try {
                                        when (
                                            val result = favoritesRepository.updateFavorite(
                                                itemId = currentDetail.itemId,
                                                favorite = targetFavorite,
                                                defaultServer = activeServer,
                                                session = session,
                                            )
                                        ) {
                                            is FavoriteMutationResult.Success -> {
                                                favoriteOverrides[currentDetail.itemId] = result.isFavorite
                                                favoriteRefreshVersion += 1
                                                detail = currentDetail.copy(isFavorite = result.isFavorite)
                                                    .also(::rememberDetail)
                                                favoriteStatusMessage = result.message
                                                isFavoriteActionError = false
                                            }

                                            is FavoriteMutationResult.Failure -> {
                                                favoriteStatusMessage = appendActionHint(
                                                    message = result.loadIssue.message,
                                                    errorCode = result.loadIssue.errorCode,
                                                    retryable = result.loadIssue.retryable,
                                                )
                                                isFavoriteActionError = true
                                            }
                                        }
                                    } catch (error: Throwable) {
                                        AppLogger.error(
                                            tag = "DetailFavorite",
                                            message = "updateFavorite failed unexpectedly.",
                                            throwable = error,
                                        )
                                        favoriteStatusMessage = "收藏状态这次没有更新成功，稍后再试一次即可。"
                                        isFavoriteActionError = true
                                    } finally {
                                        isFavoriteUpdating = false
                                    }
                                }
                            },
                            onEpisodeClick = { episodeId ->
                                val episode = resolvedDetail.episodes.firstOrNull { it.id == episodeId }
                                navController.navigate(
                                    PlayerRoute(
                                        itemId = resolvedDetail.itemId,
                                        episodeId = episodeId,
                                        playbackItemId = episode?.playbackItemId,
                                    )
                                )
                            },
                            onRetryLoad = {
                                if (!isDetailLoading) {
                                    detailReloadVersion += 1
                                }
                            },
                            isFavoriteUpdating = isFavoriteUpdating,
                            favoriteStatusMessage = favoriteStatusMessage,
                            isFavoriteActionError = isFavoriteActionError,
                        )
                    }
                }

                composable<PlayerRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<PlayerRoute>()
                    var playbackContext by remember(
                        route.itemId,
                        route.episodeId,
                        route.playbackItemId,
                    ) {
                        mutableStateOf(
                            PlaybackContext.placeholder(
                                itemId = route.itemId,
                                episodeId = route.episodeId,
                            )
                        )
                    }
                    var isLoading by remember(route.itemId, route.episodeId, route.playbackItemId) {
                        mutableStateOf(true)
                    }
                    var reloadTick by remember(route.itemId, route.episodeId, route.playbackItemId) {
                        mutableStateOf(0)
                    }
                    val activeServer = defaultServer()
                    val session = currentSession()

                    LaunchedEffect(
                        route.itemId,
                        route.episodeId,
                        route.playbackItemId,
                        reloadTick,
                        activeServer?.id,
                        activeServer?.baseUrl,
                        session?.serverId,
                        session?.accessToken,
                    ) {
                        isLoading = true
                        playbackContext = playbackRepository.loadPlaybackContext(
                            itemId = route.itemId,
                            episodeId = route.episodeId,
                            playbackItemId = route.playbackItemId,
                            defaultServer = activeServer,
                            session = session,
                        )
                        isLoading = false
                    }

                    PlayerScreen(
                        playbackContext = playbackContext,
                        isLoading = isLoading,
                        canStartPlayback = !isLoading &&
                            playbackContext.loadIssue == null &&
                            playbackContext.streamUrl != "N/A",
                        onStartPlayback = { onOpenPlayback(playbackContext) },
                        onRetryLoad = {
                            if (!isLoading) {
                                reloadTick += 1
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
                }
            }
        }
    }
}

@Composable
private fun RootTopBar(
    tab: RootTab,
    activeServerName: String?,
    onOpenSearch: () -> Unit,
    onOpenServerConfig: () -> Unit,
) {
    val subtitle = when (tab) {
        RootTab.HOME -> "为你继续播放"
        RootTab.LIBRARY -> "浏览与发现"
        RootTab.UPDATES -> "追更与动态"
        RootTab.ACCOUNT -> activeServerName?.takeIf { it.isNotBlank() }?.let { "已连接 $it" } ?: "尚未连接服务器"
    }

    Surface(
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MaFei",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                ChromeBadge(
                    text = when (tab) {
                        RootTab.HOME -> "今日片单"
                        RootTab.LIBRARY -> "内容浏览"
                        RootTab.UPDATES -> "追更提醒"
                        RootTab.ACCOUNT -> "账号中心"
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (tab) {
                        RootTab.HOME,
                        RootTab.LIBRARY,
                        RootTab.UPDATES -> {
                            ChromeActionButton(onClick = onOpenSearch) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                )
                            }
                        }

                        RootTab.ACCOUNT -> {
                            ChromeActionButton(onClick = onOpenServerConfig) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ChromeActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        IconButton(onClick = onClick) {
            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

@Composable
private fun RootBottomBar(
    activeRootTab: RootTab?,
    onSelectTab: (RootTab) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RootTab.entries.forEach { tab ->
                    val selected = activeRootTab == tab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelectTab(tab) },
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            Color.Transparent
                        },
                    )
                    {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RootTabIcon(
                                tab = tab,
                                selected = selected,
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RootTabIcon(
    tab: RootTab,
    selected: Boolean,
) {
    val icon = when (tab) {
        RootTab.HOME -> Icons.Filled.Home
        RootTab.LIBRARY -> Icons.AutoMirrored.Filled.List
        RootTab.UPDATES -> Icons.Filled.Notifications
        RootTab.ACCOUNT -> Icons.Filled.Person
    }
    Icon(
        imageVector = icon,
        contentDescription = tab.label,
        tint = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun ChromeBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun MissingContentScreen(
    isLoading: Boolean,
    message: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val normalizedIssueMessage = message
        ?.lineSequence()
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    val primaryTitle = if (isLoading) "内容准备中" else "暂时无法打开这个内容"
    val primarySubtitle = if (isLoading) {
        "我们正在同步详情与剧集信息。"
    } else {
        "这通常是临时同步延迟，稍后重试即可恢复。"
    }
    val statusMessage = when {
        !normalizedIssueMessage.isNullOrBlank() &&
            !normalizedIssueMessage.contains("Exception", ignoreCase = true) &&
            !normalizedIssueMessage.contains("Stack", ignoreCase = true) -> normalizedIssueMessage
        isLoading -> "正在获取详情信息，请稍候。"
        else -> "暂时没能打开这个内容，请稍后再试。"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppSectionCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppHeaderRow(
                    title = primaryTitle,
                    subtitle = primarySubtitle,
                    leadingIcon = if (isLoading) Icons.Filled.Notifications else Icons.Filled.Warning,
                    leadingEmphasized = false,
                )
                AppInlineTip(
                    message = if (isLoading) {
                        "同步完成后会自动回到可浏览状态。"
                    } else {
                        "你可以先返回媒体库继续浏览，稍后再尝试打开。"
                    },
                    tone = if (isLoading) AppStatusTone.Progress else AppStatusTone.Neutral,
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                )
            }
        }

        AppStatusCard(
            title = if (isLoading) "正在同步内容" else "打开未完成",
            message = statusMessage,
            supportingText = if (isLoading) {
                "同步完成后会自动恢复可浏览状态。"
            } else {
                "建议先回到媒体库继续浏览，稍后再试。"
            },
            tone = if (isLoading) AppStatusTone.Progress else AppStatusTone.Warning,
            leading = {
                Icon(
                    imageVector = if (isLoading) Icons.Filled.Notifications else Icons.Filled.Warning,
                    contentDescription = null,
                )
            },
        )

        AppSectionCard(
            modifier = Modifier.fillMaxWidth(),
            secondary = true,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!isLoading) {
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Text("重试打开")
                    }
                }
                OutlinedButton(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth()) {
                    Text("前往媒体库")
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("返回上一页")
                }
            }
        }

        if (isLoading) {
            Text(
                text = "如果等待时间较长，可先前往媒体库继续浏览。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
