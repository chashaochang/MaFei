package cn.mafei.cmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.glass.*
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.feature.connect.ConnectScreen
import cn.mafei.cmp.feature.home.HomeScreen
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.detail.DetailScreen
import cn.mafei.cmp.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

val LocalApplicationRuntime = staticCompositionLocalOf<ApplicationRuntime> { error("Application runtime is not installed") }

@Composable
fun App(runtime: ApplicationRuntime, onThemeChanged: (Boolean) -> Unit = {}) {
    val sessionState by runtime.sessions.state.collectAsState()
    val scope = rememberCoroutineScope()
    var dark by rememberSaveable { mutableStateOf(true) }
    var probe by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(runtime) { runtime.sessions.start() }
    SideEffect { onThemeChanged(dark) }
    CompositionLocalProvider(LocalApplicationRuntime provides runtime) {
        MaFeiTheme(dark) {
            if (probe) {
                Column(Modifier.fillMaxSize()) {
                    TextButton(onClick = { probe = false }, modifier = Modifier.safeDrawingPadding()) { Text("返回 MaFei") }
                    Box(Modifier.weight(1f)) { FoundationProbeScreen(onThemeChanged) }
                }
            } else {
                val health = rememberGlassRenderHealth()
                val backdrop = rememberGlassBackdrop()
                GlassRenderingEnvironment(health) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            // Background sibling only: never capture the bar into its own source.
                            Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop).background(Brush.verticalGradient(
                                listOf(MaterialTheme.colorScheme.primary.copy(alpha = .12f), MaterialTheme.colorScheme.background))))
                            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                                AppGlassBar(backdrop, Modifier.fillMaxWidth()) {
                                    Text("MaFei", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                    TextButton(onClick = { dark = !dark }) { Text(if (dark) "浅色" else "深色") }
                                    TextButton(onClick = { probe = true }, enabled = !busy) { Text("实验室") }
                                }
                                Box(Modifier.weight(1f)) {
                                    when (sessionState.status) {
                                        ApplicationSessionStatus.NotStarted, ApplicationSessionStatus.Restoring ->
                                            SessionMessage("正在恢复账号…", busy = true)
                                        ApplicationSessionStatus.SignedOut -> ConnectScreen(busy, error, onSubmit = { provider, address, name, password ->
                                            if (!busy) {
                                                busy = true; error = null
                                                scope.launch {
                                                    try { runtime.connect(provider, address, name, password) }
                                                    catch (cancelled: CancellationException) { throw cancelled }
                                                    catch (failure: Exception) { error = connectError(failure) }
                                                    finally { busy = false }
                                                }
                                            }
                                        })
                                        ApplicationSessionStatus.Ready -> sessionState.session?.let { session ->
                                            key(session) {
                                                BrowseContent(runtime, session, onLogout = {
                                                    if (!busy) {
                                                        busy = true; error = null
                                                        scope.launch {
                                                            try { runtime.sessions.logout(session) }
                                                            catch (cancelled: CancellationException) { throw cancelled }
                                                            catch (_: Exception) { error = "安全凭证清理未完成，请重试" }
                                                            finally { busy = false }
                                                        }
                                                    }
                                                })
                                            }
                                        }
                                        ApplicationSessionStatus.NeedsAttention -> SessionMessage(
                                            error ?: "账号恢复或保存未完成。可以重试恢复，或先完成退出再登录。", busy,
                                            onRetry = { scope.launch { runtime.sessions.start() } },
                                            onCleanup = {
                                                if (!busy) {
                                                    busy = true
                                                    scope.launch {
                                                        try { runtime.sessions.retryCleanup(); error = null }
                                                        catch (cancelled: CancellationException) { throw cancelled }
                                                        catch (_: Exception) { error = "安全凭证清理仍未完成，请稍后重试" }
                                                        finally { busy = false }
                                                    }
                                                }
                                            },
                                        )
                                        ApplicationSessionStatus.Closed -> SessionMessage("应用会话已关闭，请重新启动")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseContent(runtime: ApplicationRuntime, session: ApplicationSession, onLogout: () -> Unit) {
    val browser = remember(session) { MediaBrowser(runtime.sessions.media, session.media) }
    val state by browser.state.collectAsState()
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    LaunchedEffect(state.selectedCollection) { gridState.scrollToItem(0) }
    LaunchedEffect(browser) { browser.start() }
    if (state.detailId != null) {
        DetailScreen(state.detail, state.detailLoading, state.detailError, browser::back,
            onRetry = { state.detailId?.let { id -> scope.launch { browser.openDetail(id) } } })
    } else HomeScreen(state.collections, state.items, state.selectedCollection, state.loading,
        state.error, state.stale, state.nextCursor != null,
        onCollection = { collection -> scope.launch { browser.select(collection.id) } },
        onItem = { item -> scope.launch { browser.openDetail(item.id) } },
        onRetry = { scope.launch { browser.refresh() } },
        onLoadMore = { scope.launch { browser.loadMore() } }, onLogout = onLogout, gridState = gridState)
}

@Composable
private fun SessionMessage(message: String, busy: Boolean = false, onRetry: (() -> Unit)? = null, onCleanup: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            onRetry?.let { OutlinedButton(it, enabled = !busy) { Text("重试恢复") } }
            onCleanup?.let { TextButton(it, enabled = !busy) { Text("完成退出，重新登录") } }
        }
    }
}

// All network/storage diagnostics are deliberately replaced, not interpolated.
private fun connectError(error: Exception): String = when (error) {
    is AuthenticationError -> "登录未通过，请检查账号和密码"
    is AuthorizationError -> "此账号没有访问权限"
    is ClientConfigError -> "请检查服务器地址和用户名；地址须包含 http:// 或 https://"
    is NetworkUnreachableError, is TimeoutError -> "无法连接服务器，请检查地址和网络"
    is ProtocolDecodingError -> "服务器登录响应不完整或不受支持"
    else -> "登录或安全保存失败，请检查服务器和存储后重试"
}
