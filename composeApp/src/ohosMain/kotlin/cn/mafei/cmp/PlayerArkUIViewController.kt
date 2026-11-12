@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package cn.mafei.cmp

import androidx.compose.runtime.*
import androidx.compose.ui.window.ComposeArkUIViewController
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.MaFeiInsetsDp
import cn.mafei.cmp.common.design_system.MaFeiWindowFeatures
import kotlinx.cinterop.*
import platform.ArkTS.ArkTS_Napi_NativeModule.*

internal val ohosPlayerWindowState = PlayerWindowState()
private var playerWindowFeatures by mutableStateOf(MaFeiWindowFeatures(desktopWindow = true, windowedHost = true))
private var playerWindowBackInput: OhosSystemBackInput? = null

@CName("SetOhosPlayerWindowEnabled")
fun SetOhosPlayerWindowEnabled(enabled: Boolean) = ohosPlayerWindowState.enable(enabled)

@CName("GetOhosPlayerWindowRequestId")
fun GetOhosPlayerWindowRequestId(): Long = ohosPlayerWindowState.request?.id ?: 0L

@CName("GetOhosPlayerWindowRevision")
fun GetOhosPlayerWindowRevision(): Long = ohosPlayerWindowState.revision

@CName("GetOhosPlayerWindowFullscreenRevision")
fun GetOhosPlayerWindowFullscreenRevision(): Long = ohosPlayerWindowState.fullscreenRevision

@CName("IsOhosPlayerWindowFullscreenRequested")
fun IsOhosPlayerWindowFullscreenRequested(): Boolean = ohosPlayerWindowState.fullscreenRequested

@CName("CloseOhosPlayerWindow")
fun CloseOhosPlayerWindow(id: Long) = ohosPlayerWindowState.close(id)

@CName("FailOhosPlayerWindow")
fun FailOhosPlayerWindow(id: Long) = ohosPlayerWindowState.failed(id)

@CName("GetOhosPlayerWindowTitle")
fun GetOhosPlayerWindowTitle(env: napi_env): napi_value? = memScoped {
    val title = ohosPlayerWindowState.request?.title.orEmpty()
    val result = alloc<napi_valueVar>()
    if (napi_create_string_utf8(env, title, title.encodeToByteArray().size.toULong(), result.ptr) != napi_ok) null
    else result.value
}

@CName("UpdateOhosPlayerWindowMetrics")
fun UpdateOhosPlayerWindowMetrics(
    left: Double, top: Double, right: Double, bottom: Double,
    controlsEnd: Double, controlsTop: Double, fullscreen: Boolean,
) {
    fun Double.inset(): Float = takeIf { it.isFinite() }?.coerceAtLeast(0.0)?.toFloat() ?: 0f
    playerWindowFeatures = MaFeiWindowFeatures(
        desktopWindow = true,
        windowedHost = true,
        safeInsets = MaFeiInsetsDp(left.inset(), top.inset(), right.inset(), bottom.inset()),
        windowControlEndInsetDp = controlsEnd.inset(),
        windowControlTopInsetDp = controlsTop.inset(),
        windowFullscreen = fullscreen,
    )
}

@CName("HandleOhosPlayerWindowBack")
fun HandleOhosPlayerWindowBack(): Boolean = playerWindowBackInput?.dispatchBack() == true

/** A second view of the existing player. This entry point never creates App or a runtime. */
@CName("PlayerArkUIViewController")
fun PlayerArkUIViewController(env: napi_env): napi_value? = try {
    ComposeArkUIViewController(env) {
        val owner = rememberNavigationEventDispatcherOwner(parent = null)
        val input = remember(owner) { OhosSystemBackInput() }
        DisposableEffect(owner, input) {
            owner.navigationEventDispatcher.addInput(input)
            playerWindowBackInput = input
            onDispose {
                if (playerWindowBackInput === input) playerWindowBackInput = null
                owner.navigationEventDispatcher.removeInput(input)
            }
        }
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides owner,
            LocalMaFeiWindowFeatures provides playerWindowFeatures,
        ) {
            ohosPlayerWindowState.request?.let { request ->
                key(request.id) { request.content() }
            }
        }
    }
} catch (_: Exception) {
    napi_throw_error(env, null, "Player window initialization failed")
    null
}
