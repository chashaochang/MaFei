package cn.mafei.cmp

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.ArkUIView2
import androidx.compose.ui.napi.nApiValue
import cn.mafei.cmp.session.WebPageRequest
import kotlinx.cinterop.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.experimental.ExperimentalNativeApi

@Serializable
private data class WebHostEvent(val handle: Long, val action: String, val value: String = "", val canBack: Boolean = false)

private object OhosWebEvents {
    val json = Json { ignoreUnknownKeys = true }
    var next = 0L
    val listeners = mutableMapOf<Long, (WebHostEvent) -> Unit>()
}

@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@CName("UpdateOhosWebState")
fun UpdateOhosWebState(raw: CPointer<ByteVar>?): Boolean {
    val text = raw?.toKString() ?: return false
    if (text.length > 64 * 1024) return false
    val payload = runCatching { OhosWebEvents.json.decodeFromString(WebHostEvent.serializer(), text) }.getOrNull() ?: return false
    val listener = OhosWebEvents.listeners[payload.handle] ?: return false
    listener(payload); return true
}

internal object OhosEmbeddedWebHost : EmbeddedWebHost {
    override val nativePlaybackSupported = true
    @OptIn(ExperimentalForeignApi::class)
    @Composable override fun Content(request: WebPageRequest, callbacks: EmbeddedWebCallbacks, modifier: Modifier) {
        val handle = remember(request) { ++OhosWebEvents.next }
        var revision by remember(request) { mutableLongStateOf(0) }
        var canBack by remember(request) { mutableStateOf(false) }
        val latestCallbacks by rememberUpdatedState(callbacks)
        DisposableEffect(handle) {
            OhosWebEvents.listeners[handle] = { event ->
                if (event.action == "state") canBack = event.canBack
                else if (event.action == "error") latestCallbacks.onFailure()
                else latestCallbacks.onNativeAction(event.action, event.value)
            }
            callbacks.registerBack { if (canBack) { revision++; true } else false }
            onDispose { OhosWebEvents.listeners.remove(handle); callbacks.registerBack(null) }
        }
        val args = androidx.compose.ui.napi.js {
            "handle"(handle.nApiValue())
            "revision"(revision.nApiValue())
            "url"(request.url)
            "origin"(request.origin)
            "script"(request.bootstrapScript)
            "deviceInformation"(request.deviceInformation)
        }
        ArkUIView2(name = "MaFeiEmbeddedWeb", parameter = args, modifier = modifier, interactive = true)
    }
}
