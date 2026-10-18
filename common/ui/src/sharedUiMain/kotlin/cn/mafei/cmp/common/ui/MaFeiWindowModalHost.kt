package cn.mafei.cmp.common.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.glass.*

internal data class MaFeiWindowModalEntry(
    val owner: Any,
    val content: @Composable BoxScope.(GlassBackdrop) -> Unit,
)

/**
 * Window-level modal portal shared by pages hosted inside a navigation pane.
 * Pages publish modal content here; [MaFeiAdaptiveShell] renders it after the
 * sidebar/top bar so a large-screen dialog is centered in the whole window.
 */
class MaFeiWindowModalHostState internal constructor() {
    internal var entry by mutableStateOf<MaFeiWindowModalEntry?>(null)
        private set

    internal fun present(
        owner: Any,
        content: @Composable BoxScope.(GlassBackdrop) -> Unit,
    ) {
        entry = MaFeiWindowModalEntry(owner, content)
    }

    internal fun dismiss(owner: Any) {
        if (entry?.owner === owner) entry = null
    }
}

val LocalMaFeiWindowModalHost = staticCompositionLocalOf<MaFeiWindowModalHostState?> { null }

/** Publishes [content] while [visible] and its retained page is active. */
@Composable
fun MaFeiWindowModalPortal(
    visible: Boolean,
    content: @Composable BoxScope.(GlassBackdrop) -> Unit,
) {
    val host = LocalMaFeiWindowModalHost.current
    val presented = visible && LocalMaFeiPageActive.current
    val owner = remember { Any() }
    val latestContent = rememberUpdatedState(content)
    DisposableEffect(host, owner, presented) {
        if (host != null && presented) {
            host.present(owner) { backdrop -> latestContent.value(this, backdrop) }
        }
        onDispose { host?.dismiss(owner) }
    }
}
