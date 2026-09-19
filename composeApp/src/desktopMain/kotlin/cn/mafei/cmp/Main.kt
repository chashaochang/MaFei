package cn.mafei.cmp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.session.ApplicationRuntime
import cn.mafei.cmp.session.ProcessApplication
import cn.mafei.cmp.storage.createDesktopSettings
import cn.mafei.cmp.storage.createDesktopCredentialStore
import cn.mafei.cmp.storage.usesDesktopDevelopmentStorage
import cn.mafei.cmp.platform.http_macos.createMacOsHttpTransport
import cn.mafei.cmp.player.runtime_macos.createMacOsPlayerRuntime

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
    Window(
        state = rememberWindowState(
            width = (System.getProperty("mafei.desktop.windowWidth")?.toIntOrNull()?.coerceIn(320, 2400) ?: 1180).dp,
            height = (System.getProperty("mafei.desktop.windowHeight")?.toIntOrNull()?.coerceIn(400, 1600) ?: 780).dp,
        ),
        onCloseRequest = { runtime.requestClose { exitApplication() } },
        title = if (usesDesktopDevelopmentStorage()) "MaFei · 开发测试（本地登录态）" else "MaFei",
    ) {
        DesktopNavigationOwner { App(runtime) }
    }
}
