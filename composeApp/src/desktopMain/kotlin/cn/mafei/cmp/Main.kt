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

fun main() = application {
    val runtime = ProcessApplication.getOrCreate {
        ApplicationRuntime(createDesktopSettings(),
            createDesktopCredentialStore(), createMacOsHttpTransport())
    }
    Window(
        state = rememberWindowState(
            width = (System.getProperty("mafei.desktop.windowWidth")?.toIntOrNull()?.coerceIn(320, 2400) ?: 1180).dp,
            height = (System.getProperty("mafei.desktop.windowHeight")?.toIntOrNull()?.coerceIn(400, 1600) ?: 780).dp,
        ),
        onCloseRequest = { runtime.requestClose { exitApplication() } },
        title = if (usesDesktopDevelopmentStorage()) "MaFei · 开发测试（本地登录态）" else "MaFei",
    ) {
        App(runtime)
    }
}
