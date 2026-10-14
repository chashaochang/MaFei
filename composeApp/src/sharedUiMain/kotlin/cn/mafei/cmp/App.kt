package cn.mafei.cmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.glass.GlassRenderingEnvironment
import cn.mafei.cmp.common.glass.rememberGlassRenderHealth
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.session.*

val LocalApplicationRuntime = staticCompositionLocalOf<ApplicationRuntime> { error("Application runtime is not installed") }

@Composable
fun App(
    runtime: ApplicationRuntime,
    onThemeChanged: (Boolean) -> Unit = {},
    playerSurface: @Composable (PlayerController, onBack: () -> Unit, onExitFullscreen: () -> Unit, fullscreen: Boolean, Modifier) -> Unit =
        { _, _, _, _, modifier ->
            Box(modifier.background(Color.Black))
        },
    onPlayerScreenChanged: (Boolean) -> Unit = {},
    onPlayerFullscreenChanged: (Boolean) -> Unit = {},
    windowControls: @Composable RowScope.() -> Unit = {},
    windowDragArea: @Composable (@Composable () -> Unit) -> Unit = { content -> content() },
    playerRenderModeSupported: Boolean = false,
) {
    val sessionState by runtime.sessions.state.collectAsState()
    val appearance by runtime.appearance.state.collectAsState()
    val glassStyle by runtime.glassStyle.state.collectAsState()
    val darkAppearance = appearance.dark(androidx.compose.foundation.isSystemInDarkTheme())
    LaunchedEffect(runtime) { runtime.appearance.load() }
    LaunchedEffect(runtime) { runtime.glassStyle.load() }
    var probe by remember { mutableStateOf(false) }
    val accounts = remember(runtime) { runtime.accountAccess() }
    val startup = rememberApplicationStartup(runtime)
    val preferences = rememberApplicationPreferences(runtime)
    CompositionLocalProvider(LocalApplicationRuntime provides runtime) {
        if (probe) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.safeDrawingPadding().padding(horizontal = 12.dp).mafeiInteractive(onClick = { probe = false }, label = "返回映光影院"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaFeiIcon(MaFeiGlyph.Back, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("返回映光影院")
                }
                Box(Modifier.weight(1f)) { FoundationProbeScreen(onThemeChanged) }
            }
        } else {
            SideEffect { onThemeChanged(darkAppearance) }
            MaFeiTheme(darkTheme = darkAppearance) {
                val health = rememberGlassRenderHealth()
                GlassRenderingEnvironment(health, intensity = preferences.liquidGlassIntensity, style = glassStyle) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (startup.starting) {
                            StaticAppCanvas { if (startup.showStartupProgress) PonyStartupLoading(exiting = startup.startupExiting) }
                        } else
                            when (sessionState.status) {
                                ApplicationSessionStatus.NotStarted,
                                ApplicationSessionStatus.Restoring -> StaticAppCanvas {}

                                ApplicationSessionStatus.SignedOut ->
                                    SignedOutFlow(
                                        accounts = accounts,
                                        busy = startup.busy,
                                        error = startup.error,
                                        onBusyChange = { startup.busy = it },
                                        onErrorChange = { startup.error = it },
                                        windowControls = windowControls,
                                        windowDragArea = windowDragArea,
                                    )

                                ApplicationSessionStatus.Ready -> {
                                    val session = sessionState.session
                                    if (session == null) {
                                        StaticAppCanvas { SessionMessage("账号会话尚未就绪，请稍后重试") }
                                    } else {
                                        key(session) {
                                            BrowseApplication(
                                                runtime = runtime,
                                                session = session,
                                                busy = startup.busy,
                                                liquidGlassIntensity = preferences.liquidGlassIntensity,
                                                onLiquidGlassIntensityChanged = preferences::setGlassIntensity,
                                                homeCarouselVisible = preferences.homeCarouselVisibleState,
                                                onHomeCarouselVisibleChanged = preferences::setHomeCarouselVisible,
                                                playerRenderMode = preferences.playerRenderMode,
                                                playerRenderModeSupported = playerRenderModeSupported,
                                                onPlayerRenderModeChanged = preferences::setRenderMode,
                                                playerSurface = playerSurface,
                                                onPlayerScreenChanged = onPlayerScreenChanged,
                                                onPlayerFullscreenChanged = onPlayerFullscreenChanged,
                                                windowControls = windowControls,
                                                windowDragArea = windowDragArea,
                                                onLogout = { startup.logout(session) },
                                            )
                                        }
                                    }
                                }

                                ApplicationSessionStatus.NeedsAttention ->
                                    StaticAppCanvas {
                                        SessionMessage(
                                            message = startup.error ?: "账号恢复或保存未完成。可以重试恢复，或先完成退出再登录。",
                                            busy = startup.busy,
                                            onRetry = startup::retry,
                                            onCleanup = startup::cleanup,
                                        )
                                    }

                                ApplicationSessionStatus.Closed -> StaticAppCanvas { SessionMessage("应用会话已关闭，请重新启动") }
                            }
                    }
                }
            }
        }
    }
}
