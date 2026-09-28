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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
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
    val runtime = ProcessApplication.getOrCreate {
        ApplicationRuntime(
            createDesktopSettings(),
            createDesktopCredentialStore(),
            createMacOsHttpTransport(),
            playerRuntimeFactory = { createMacOsPlayerRuntime() },
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
            LocalMaFeiWindowFeatures provides MaFeiWindowFeatures(desktopWindow = true),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(PcWindowCornerRadius)),
            ) {
                DesktopNavigationOwner {
                    var maximized by remember {
                        mutableStateOf(window.extendedState and java.awt.Frame.MAXIMIZED_BOTH != 0)
                    }
                    App(
                        runtime = runtime,
                        windowControls = {
                            MacWindowControls(
                                onMinimize = { window.extendedState = window.extendedState or java.awt.Frame.ICONIFIED },
                                onToggleMaximize = {
                                    window.extendedState = if (maximized) {
                                        java.awt.Frame.NORMAL
                                    } else {
                                        java.awt.Frame.MAXIMIZED_BOTH
                                    }
                                    maximized = !maximized
                                },
                                onClose = { runtime.requestClose { exitApplication() } },
                            )
                        },
                        windowDragArea = { content ->
                            WindowDraggableArea(content = content)
                        },
                        onPlayerFullscreenChanged = { fullscreen ->
                            windowState.placement = if (fullscreen) {
                                WindowPlacement.Fullscreen
                            } else {
                                WindowPlacement.Floating
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Native-looking traffic-light controls for the undecorated Mac window. */
@Composable
private fun RowScope.MacWindowControls(
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    val controls = listOf(
        Color(0xFFFF5F57) to ("关闭" to onClose),
        Color(0xFFFFBD2E) to ("最小化" to onMinimize),
        Color(0xFF28C840) to ("最大化" to onToggleMaximize),
    )
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(60.dp),
    ) {
        controls.forEach { (color, action) ->
            Box(
                Modifier
                    .size(16.dp)
                    .mafeiInteractive(
                        onClick = action.second,
                        radiusDp = 8f,
                        label = action.first,
                        // Keep the traffic lights as real interactive controls:
                        // hover/press wash is the only visual affordance in the
                        // undecorated frame, and also confirms that the pointer
                        // is not inside the neighbouring drag surface.
                        visualFeedback = true,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Spacer(Modifier.size(12.dp).background(color, CircleShape))
            }
        }
    }
}
