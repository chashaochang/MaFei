package cn.mafei.cmp

import androidx.compose.ui.window.ComposeArkUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.initMainHandler
import kotlin.experimental.ExperimentalNativeApi
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_env
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_value
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_throw_error
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toKString
import cn.mafei.cmp.session.ApplicationRuntime
import cn.mafei.cmp.session.ProcessApplication
import cn.mafei.cmp.storage.createOhosSettings
import cn.mafei.cmp.platform.secure_storage_ohos.OhosAssetCredentialStore
import cn.mafei.cmp.platform.http_ohos.createOhosHttpTransport

@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@CName("MainArkUIViewController")
fun MainArkUIViewController(env: napi_env, filesDirectory: CPointer<ByteVar>?): napi_value? = try {
    initMainHandler(env)
    val directory = requireNotNull(filesDirectory).toKString()
    val runtime = ProcessApplication.getOrCreate {
        ApplicationRuntime(createOhosSettings(directory), OhosAssetCredentialStore(directory), createOhosHttpTransport())
    }
    ComposeArkUIViewController(env) {
        App(runtime)
    }
} catch (_: Exception) {
    // A Kotlin exception must not escape the C callback and terminate the process.
    napi_throw_error(env, null, "Application runtime initialization failed")
    null
}
