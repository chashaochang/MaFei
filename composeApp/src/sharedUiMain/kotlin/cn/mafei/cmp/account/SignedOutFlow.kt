package cn.mafei.cmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiWindowEnvironment
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.currentMaFeiSafeArea
import cn.mafei.cmp.common.design_system.mafeiAdaptiveWindow
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiGlassConfirmDialog
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.feature.connect.ConnectAtmosphere
import cn.mafei.cmp.feature.connect.AccountHistoryScreen
import cn.mafei.cmp.feature.connect.ConnectScreen
import cn.mafei.cmp.feature.connect.ConnectionHistoryItem
import cn.mafei.cmp.feature.connect.ServerDiscoveryScreen
import cn.mafei.cmp.feature.connect.rememberConnectFormState
import cn.mafei.cmp.session.*
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.map
import cn.mafei.cmp.session.AccountAccess

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SignedOutFlow(
    accounts: AccountAccess,
    busy: Boolean,
    error: String?,
    onBusyChange: (Boolean) -> Unit,
    onErrorChange: (String?) -> Unit,
    windowControls: @Composable RowScope.() -> Unit,
    windowDragArea: @Composable (@Composable () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf<SignedOutRoute>(ConnectRoute) }
    val busyState = rememberUpdatedState(busy)
    val busyChange = rememberUpdatedState(onBusyChange)
    val errorChange = rememberUpdatedState(onErrorChange)
    val history = remember(accounts) {
        SignedOutAccountState(accounts, scope, { busyState.value },
            { busyChange.value(it) }, { errorChange.value(it) })
    }
    // 窗口背景层的源：影视墙铺在窗口层、两个面板共享，所以它在整窗里是同一面墙。
    // 建在这里是因为两个面板的 lambda 都要引用它。
    var pendingDeleteAccount by remember(accounts) { mutableStateOf<ConnectionHistoryItem?>(null) }
    val pageBackdrop = rememberGlassBackdrop()
    val connectFormState = rememberConnectFormState()
    // Signed out means no account is in use, but the history panel must never
    // offer to delete whatever account the runtime still holds.
    val activeSelection = accounts.activeSelection
    val historyItems = history.historyConnections.map { connection ->
        ConnectionHistoryItem(
            key = connection.credentialKey,
            provider = connection.server.provider,
            serverId = connection.server.id,
            displayName = connection.account.displayName,
            serverName = connection.server.name,
            address = connection.server.baseUrl,
            hasRememberedPassword = connection.credentialKey in history.rememberedPasswordKeys,
            isCurrent = connection.selection == activeSelection,
        )
    }
    val connectContent: @Composable () -> Unit = {
        ConnectScreen(
            busy = busy,
            error = error,
            onOpenHistory = {
                if (backStack.lastOrNull() != AccountHistoryRoute) {
                    backStack.add(AccountHistoryRoute)
                }
                history.loadHistory()
            },
            onOpenServerDiscovery = {
                if (backStack.lastOrNull() != ServerDiscoveryRoute) {
                    backStack.add(ServerDiscoveryRoute)
                }
            },
            onSubmit = history::connect,
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
            loading = history.historyLoading && historyItems.isEmpty(),
            error = history.historyError,
            busy = busy,
            onBack = { backStack.removeLastOrNull() },
            onRetry = history::loadHistory,
            onSelected = { item ->
                history.select(item) { connection, savedPassword ->
                    connectFormState.provider = connection.server.provider
                    connectFormState.baseUrl = connection.server.baseUrl
                    connectFormState.username = connection.account.displayName
                    connectFormState.password = savedPassword.orEmpty()
                    connectFormState.rememberPassword = savedPassword != null
                    backStack.removeLastOrNull()
                }
            },
            deletingKey = history.deletingAccountKey,
            onRequestDelete = { pendingDeleteAccount = it },
            modifier = Modifier.fillMaxSize(),
            pageBackdrop = pageBackdrop,
        )
    }
    val discoveryContent: @Composable () -> Unit = {
        ServerDiscoveryScreen(
            loadSavedServers = { savedJellyfinServerSuggestions(accounts) },
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
                    history.deleteAccount(item)
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
