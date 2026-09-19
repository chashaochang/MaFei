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

fun MainViewController() = ComposeUIViewController {
    val runtime = ProcessApplication.getOrCreate {
        ApplicationRuntime(createIosSettings(), IosKeychainCredentialStore("cn.mafei.credentials.v1"), createIosHttpTransport())
    }
    val navigationOwner = rememberNavigationEventDispatcherOwner(parent = null)
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navigationOwner) {
        App(runtime)
    }
}
