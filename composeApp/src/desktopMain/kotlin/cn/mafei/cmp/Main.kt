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

fun main() = application {
    val visualShowcase = System.getProperty("mafei.desktop.visualShowcase") == "true"
    val runtime = if (visualShowcase) null else ProcessApplication.getOrCreate {
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
        onCloseRequest = {
            if (runtime == null) exitApplication()
            else runtime.requestClose { exitApplication() }
        },
        title = when {
            visualShowcase -> "MaFei · 视觉验收"
            usesDesktopDevelopmentStorage() -> "MaFei · 开发测试（本地登录态）"
            else -> "MaFei"
        },
    ) {
        if (runtime == null) DesktopVisualShowcase() else App(runtime)
    }
}
