package cn.mafei.cmp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
    val windowState = rememberWindowState(
        width = (System.getProperty("mafei.desktop.windowWidth")?.toIntOrNull()
            ?.coerceIn(PcMinimumWindowWidth.value.toInt(), 2400) ?: 1180).dp,
        height = (System.getProperty("mafei.desktop.windowHeight")?.toIntOrNull()
            ?.coerceIn(PcMinimumWindowHeight.value.toInt(), 1600) ?: 780).dp,
    )
    Window(
        icon = painterResource(Res.drawable.app_launcher_icon),
        state = windowState,
        onCloseRequest = { runtime.requestClose { exitApplication() } },
        title = if (usesDesktopDevelopmentStorage()) "马飞 · 开发测试（本地登录态）" else "马飞",
    ) {
        val density = LocalDensity.current
        DisposableEffect(density) {
            // Compose Desktop exposes the native frame through FrameWindowScope.
            // Enforce the product's supported PC range at the host boundary so
            // the shared shell never silently turns into a phone/tablet drawer
            // while the user is resizing a desktop window.
            window.minimumSize = Dimension(
                with(density) { PcMinimumWindowWidth.toPx().roundToInt() },
                with(density) { PcMinimumWindowHeight.toPx().roundToInt() },
            )
            onDispose { }
        }
        CompositionLocalProvider(
            LocalMaFeiWindowFeatures provides MaFeiWindowFeatures(desktopWindow = true),
        ) {
            DesktopNavigationOwner {
                App(
                    runtime = runtime,
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
