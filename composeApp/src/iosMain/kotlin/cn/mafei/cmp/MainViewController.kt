package cn.mafei.cmp

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import cn.mafei.cmp.session.ApplicationRuntime
import cn.mafei.cmp.session.ProcessApplication
import cn.mafei.cmp.storage.createIosSettings
import cn.mafei.cmp.platform.secure_storage_ios.IosKeychainCredentialStore
import cn.mafei.cmp.platform.http_ios.createIosHttpTransport

private var processPlayerHost: IosPlayerHost? = null

/** SceneDelegate aggregates all visible scenes before forwarding to the process controller. */
fun SetApplicationForeground(foreground: Boolean) = ProcessApplication.setForeground(foreground)

fun MainViewController(
    onPlayerScreenChanged: (Boolean) -> Unit,
    onPlayerFullscreenChanged: (Boolean) -> Unit,
    playerBridge: cn.mafei.cmp.player.runtime_ios.IosAvPlayerBridge,
) : platform.UIKit.UIViewController {
    // ProcessApplication owns the player runtime; every scene must use its SAME surface registry.
    val player = processPlayerHost ?: IosPlayerHost(playerBridge).also { processPlayerHost = it }
    return ComposeUIViewController {
    val runtime = ProcessApplication.getOrCreate {
        ApplicationRuntime(createIosSettings(), IosKeychainCredentialStore("cn.mafei.credentials.v1"), createIosHttpTransport(),
            playerRuntimeFactory = { player.runtime() }, photoSaver = IosPhotoSaver, externalSubtitleOverlay = true)
    }
    val navigationOwner = rememberNavigationEventDispatcherOwner(parent = null)
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navigationOwner,
        LocalEmbeddedWebHost provides IosEmbeddedWebHost) {
        App(
            runtime = runtime,
            onPlayerScreenChanged = onPlayerScreenChanged,
            onPlayerFullscreenChanged = onPlayerFullscreenChanged,
            playerSurface = { controller, _, _, _, modifier -> player.Surface(controller, modifier) },
        )
    }
    }
}
