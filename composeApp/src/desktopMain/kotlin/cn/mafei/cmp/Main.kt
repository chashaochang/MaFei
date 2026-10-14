package cn.mafei.cmp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.hoverable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.MaFeiWindowFeatures
import cn.mafei.cmp.session.ApplicationRuntime
import cn.mafei.cmp.session.ProcessApplication
import cn.mafei.cmp.storage.createDesktopSettings
import cn.mafei.cmp.storage.createDesktopCredentialStore
import cn.mafei.cmp.storage.usesDesktopDevelopmentStorage
import cn.mafei.cmp.platform.http_macos.createMacOsHttpTransport
import cn.mafei.cmp.player.runtime_macos.createMacOsPlayerRuntime
import mafei.composeapp.generated.resources.Res
import mafei.composeapp.generated.resources.app_launcher_icon
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension
import kotlin.math.roundToInt

private val PcMinimumWindowWidth = 1000.dp
private val PcMinimumWindowHeight = 640.dp
private val PcWindowCornerRadius = 14.dp

private data class DesktopWorkArea(val width: Int, val height: Int)

private fun desktopWorkArea(): DesktopWorkArea? = runCatching {
    val graphics = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
    if (graphics.isHeadlessInstance) return@runCatching null
    val configuration = graphics.defaultScreenDevice.defaultConfiguration
    val bounds = configuration.bounds
    val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    DesktopWorkArea(
        width = (bounds.width - insets.left - insets.right).coerceAtLeast(1),
        height = (bounds.height - insets.top - insets.bottom).coerceAtLeast(1),
    )
}.getOrNull()

/**
 * 唯一的桌面入口：真应用 + 真实数据。
 *
 * 曾经还有一个 `-Pmafei.desktop.visualShowcase=true` 的只读渲染入口（假数据 + 渐变占位图），
 * 用来在不需要服务器的情况下截桌面图。它在 2026-09-18 被移除：合成数据既复现不了真机上
 * 的问题，也容易让人误以为"这一版看过了"。视觉验收改为**在真机 / 真账号上跑再截图**。
 */
fun main() = application {
    val player = remember { MacPlayerHost() }
    val runtime = ProcessApplication.getOrCreate {
        ApplicationRuntime(
            createDesktopSettings(),
            createDesktopCredentialStore(),
            createMacOsHttpTransport(),
            playerRuntimeFactory = { player.runtime() },
            photoSaver = DesktopPhotoSaver,
            externalSubtitleOverlay = true,
        )
    }
    val workArea = remember { desktopWorkArea() }
    val initialWidth = workArea?.width
        ?.minus(48)
        ?.coerceAtLeast(PcMinimumWindowWidth.value.toInt())
        ?.coerceAtMost(1180)
        ?: 1180
    val initialHeight = workArea?.height
        ?.minus(48)
        ?.coerceAtLeast(PcMinimumWindowHeight.value.toInt())
        ?.coerceAtMost(780)
        ?: 780
    val windowState = rememberWindowState(
        width = (System.getProperty("mafei.desktop.windowWidth")?.toIntOrNull()
            ?.coerceIn(PcMinimumWindowWidth.value.toInt(), 2400) ?: initialWidth).dp,
        height = (System.getProperty("mafei.desktop.windowHeight")?.toIntOrNull()
            ?.coerceIn(PcMinimumWindowHeight.value.toInt(), 1600) ?: initialHeight).dp,
    )
    val playerWindowMode = remember { DesktopPlayerWindowMode() }
    val requestPlayerFullscreen: (Boolean) -> Unit = remember(windowState) {
        { requested -> windowState.placement = playerWindowMode.request(windowState.placement, requested) }
    }
    val playerScreenChanged: (Boolean) -> Unit = remember(windowState) {
        { visible -> if (!visible) windowState.placement = playerWindowMode.playerClosed(windowState.placement) }
    }
    Window(
        icon = painterResource(Res.drawable.app_launcher_icon),
        state = windowState,
        undecorated = true,
        transparent = true,
        onCloseRequest = { runtime.requestClose { exitApplication() } },
        title = if (usesDesktopDevelopmentStorage()) "马飞 · 开发测试（本地登录态）" else "马飞",
    ) {
        DisposableEffect(Unit) {
            // Compose Desktop exposes the native frame through FrameWindowScope.
            // Enforce the product's supported PC range at the host boundary so
            // the shared shell never silently turns into a phone/tablet drawer
            // while the user is resizing a desktop window. AWT's frame dimensions
            // are already expressed in the desktop's logical coordinate space;
            // multiplying by Compose density makes a Retina Mac's minimum window
            // twice as wide and high.
            window.minimumSize = Dimension(
                PcMinimumWindowWidth.value.roundToInt(),
                PcMinimumWindowHeight.value.roundToInt(),
            )
            onDispose { }
        }
        CompositionLocalProvider(
            LocalMaFeiWindowFeatures provides MaFeiWindowFeatures(
                desktopWindow = true,
                windowedHost = true,
                windowFullscreen = windowState.placement == WindowPlacement.Fullscreen,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(if (windowState.placement == WindowPlacement.Fullscreen) 0.dp else PcWindowCornerRadius)),
            ) {
                DesktopNavigationOwner {
                    App(
                        runtime = runtime,
                        playerSurface = { controller, _, _, _, modifier -> player.Surface(controller, modifier) },
                        windowControls = {
                            val fullscreen = windowState.placement == WindowPlacement.Fullscreen
                            MacWindowControls(
                                fullscreen = fullscreen,
                                onMinimize = { window.extendedState = window.extendedState or java.awt.Frame.ICONIFIED },
                                onToggleFullscreen = {
                                    if (fullscreen) requestPlayerFullscreen(false)
                                    else windowState.placement = WindowPlacement.Fullscreen
                                },
                                onClose = { runtime.requestClose { exitApplication() } },
                            )
                        },
                        windowDragArea = { content ->
                            WindowDraggableArea(content = content)
                        },
                        onPlayerScreenChanged = playerScreenChanged,
                        onPlayerFullscreenChanged = requestPlayerFullscreen,
                    )
                }
            }
        }
    }
}

/** Native-looking traffic-light controls for the undecorated Mac window. */
@Composable
private fun RowScope.MacWindowControls(
    fullscreen: Boolean,
    onMinimize: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onClose: () -> Unit,
) {
    val groupSource = remember { MutableInteractionSource() }
    val groupHovered by groupSource.collectIsHoveredAsState()
    val active = LocalWindowInfo.current.isWindowFocused
    val controls = listOf(
        Triple(Color(0xFFFF5F57), "关闭", "×") to (true to onClose),
        Triple(Color(0xFFFFBD2E), "最小化", "−") to (!fullscreen to onMinimize),
        Triple(Color(0xFF28C840), if (fullscreen) "退出全屏" else "进入全屏", if (fullscreen) "⤡" else "⤢") to
            (true to onToggleFullscreen),
    )
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(60.dp).hoverable(groupSource),
    ) {
        controls.forEachIndexed { index, (control, action) ->
            val (color, label, symbol) = control
            val (enabled, onClick) = action
            val source = remember(index) { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val displayColor = when {
                !active || !enabled -> Color(0xFF9A9A9A)
                else -> color
            }
            Box(
                Modifier
                    .size(16.dp)
                    .mafeiInteractive(
                        onClick = onClick,
                        enabled = enabled,
                        radiusDp = 8f,
                        label = label,
                        interactionSource = source,
                        // macOS traffic lights reveal their glyphs as one group;
                        // the standard Compose hover wash would make each dot
                        // behave like an unrelated Material button.
                        visualFeedback = false,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Spacer(
                    Modifier.size(12.dp).background(
                        displayColor.copy(alpha = if (pressed) .72f else 1f),
                        CircleShape,
                    ),
                )
                if (groupHovered && active && enabled) {
                    Text(
                        text = symbol,
                        color = Color.Black.copy(alpha = .78f),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}
