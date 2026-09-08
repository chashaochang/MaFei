package cn.mafei.cmp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import cn.mafei.cmp.session.ApplicationRuntime
import cn.mafei.cmp.session.ProcessApplication
import cn.mafei.cmp.storage.createDesktopSettings
import cn.mafei.cmp.platform.secure_storage_macos.MacOSSecureCredentialStore
import cn.mafei.cmp.platform.http_macos.createMacOsHttpTransport

fun main() = application {
    val runtime = ProcessApplication.getOrCreate {
        ApplicationRuntime(createDesktopSettings(),
            MacOSSecureCredentialStore(), createMacOsHttpTransport())
    }
    Window(
        onCloseRequest = { runtime.requestClose { exitApplication() } },
        title = "MaFei",
    ) {
        App(runtime)
    }
}
