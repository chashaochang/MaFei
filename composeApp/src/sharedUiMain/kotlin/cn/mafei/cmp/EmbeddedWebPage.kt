package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiActionBarScaffold
import cn.mafei.cmp.common.ui.LegacyEmptyView
import cn.mafei.cmp.session.WebPageRequest

internal class EmbeddedWebCallbacks(
    val onNativeAction: (String, String) -> Unit,
    val onBack: () -> Unit,
    val registerBack: ((() -> Boolean)?) -> Unit,
    val onFailure: () -> Unit = {},
)

internal val LocalEmbeddedWebHost = staticCompositionLocalOf<EmbeddedWebHost> { UnsupportedEmbeddedWebHost }

internal class EmbeddedWebPageState {
    var request: WebPageRequest? = null
    var location: String? = null
}

/** Platform renderer only; navigation, title and the native playback queue stay in CMP. */
internal interface EmbeddedWebHost {
    val nativePlaybackSupported: Boolean get() = false
    @Composable fun Content(request: WebPageRequest, callbacks: EmbeddedWebCallbacks, modifier: Modifier)
}

internal object UnsupportedEmbeddedWebHost : EmbeddedWebHost {
    @Composable override fun Content(request: WebPageRequest, callbacks: EmbeddedWebCallbacks, modifier: Modifier) {
        LegacyEmptyView(title = "此平台暂不支持内嵌浏览", modifier = modifier)
    }
}

@Composable
internal fun EmbeddedWebPage(host: EmbeddedWebHost, request: WebPageRequest?, title: String,
    error: String?, onBack: () -> Unit, onRetry: () -> Unit, onNativeAction: (String, String) -> Unit,
    modifier: Modifier = Modifier) {
    var browserBack by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var failed by remember(request) { mutableStateOf(false) }
    var attempt by remember(request) { mutableIntStateOf(0) }
    val back = { if (browserBack?.invoke() != true) onBack() }
    cn.mafei.cmp.common.ui.MaFeiBackHandler(enabled = true) { back() }
    MaFeiActionBarScaffold(
        title = title, onBack = back,
        modifier = modifier,
    ) {
        if (request == null) {
            if (error != null) Column(Modifier.fillMaxSize()) {
                LegacyEmptyView(title = error, modifier = Modifier.weight(1f))
                cn.mafei.cmp.common.ui.LegacyListGroup(listOf(cn.mafei.cmp.common.ui.LegacyListGroupItem("重试")),
                    onItemClick = { onRetry() }, modifier = Modifier.padding(24.dp))
            }
            else cn.mafei.cmp.common.ui.MaFeiLoadingIndicator(modifier = Modifier.fillMaxSize())
        } else if (failed) {
            Column(Modifier.fillMaxSize()) {
                LegacyEmptyView(title = "网页加载失败", modifier = Modifier.weight(1f))
                cn.mafei.cmp.common.ui.LegacyListGroup(listOf(cn.mafei.cmp.common.ui.LegacyListGroupItem("重试")),
                    onItemClick = { failed = false; attempt++ }, modifier = Modifier.padding(24.dp))
            }
        } else key(request, attempt) {
            val latestAction by rememberUpdatedState(onNativeAction)
            val latestBack by rememberUpdatedState(onBack)
            val callbacks = remember(request) { EmbeddedWebCallbacks({ action, value -> latestAction(action, value) },
                { latestBack() }, { browserBack = it }, { failed = true }) }
            host.Content(request, callbacks, Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun JellyfinEmbeddedPage(session: cn.mafei.cmp.session.ApplicationSession, memory: EmbeddedWebPageState, onBack: () -> Unit,
    onNativeAction: (String, String) -> Unit) {
    var request by remember(session) { mutableStateOf(memory.request?.let { page ->
        memory.location?.takeIf(page::accepts)?.let(page::atUrl) ?: page
    }) }
    var error by remember(session) { mutableStateOf<String?>(null) }
    var revision by remember(session) { mutableIntStateOf(0) }
    LaunchedEffect(session, revision) {
        error = null
        try {
            if (request == null || revision > 0) {
                val page = session.binding.webPage() ?: error("Web not supported")
                request = memory.location?.takeIf(page::accepts)?.let(page::atUrl) ?: page
                memory.request = page
            }
        }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "网页加载失败" }
    }
    EmbeddedWebPage(LocalEmbeddedWebHost.current, request, "网(Web)易通", error, onBack,
        { revision++ }, { action, value ->
            if (action == "location") {
                if (request?.accepts(value) == true) memory.location = value
            } else onNativeAction(action, value)
        }, Modifier.fillMaxSize())
}
