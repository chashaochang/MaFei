package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalPlayerWindowState = staticCompositionLocalOf<PlayerWindowState?> { null }

/** Owns presentation only. Playback, queue and provider state remain session-owned. */
internal class PlayerWindowState {
    private var sequence = 0L
    private var failedOwner by mutableStateOf<Any?>(null)
    var enabled by mutableStateOf(false)
        private set
    var request by mutableStateOf<PlayerWindowRequest?>(null)
        private set
    var revision = 0L
        private set
    var fullscreenRequested = false
        private set
    var fullscreenRevision = 0L
        private set

    fun accepts(owner: Any): Boolean = enabled && failedOwner !== owner

    fun enable(value: Boolean) {
        if (enabled == value) return
        enabled = value
        failedOwner = null
        if (!value) request?.let { close(it.id) }
    }

    fun present(owner: Any, mediaKey: String, title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
        if (!accepts(owner)) return
        val previous = request
        val id = if (previous?.owner === owner) previous.id else ++sequence
        if (previous?.id != id) {
            fullscreenRequested = false
            fullscreenRevision++
        }
        if (previous?.id != id || previous?.mediaKey != mediaKey) revision++
        request = PlayerWindowRequest(id, owner, mediaKey, title, onClose, content)
    }

    fun dismiss(owner: Any) {
        if (request?.owner === owner) {
            request = null
            fullscreenRequested = false
        }
    }

    fun close(id: Long) {
        val active = request?.takeIf { it.id == id } ?: return
        request = null
        fullscreenRequested = false
        active.onClose()
    }

    /** Fall back to the existing inline player for this session, preserving playback. */
    fun failed(id: Long) {
        val active = request?.takeIf { it.id == id } ?: return
        failedOwner = active.owner
        request = null
        fullscreenRequested = false
    }

    fun requestFullscreen(value: Boolean) {
        fullscreenRequested = value
        fullscreenRevision++
    }
}

internal class PlayerWindowRequest(
    val id: Long,
    val owner: Any,
    val mediaKey: String,
    val title: String,
    val onClose: () -> Unit,
    val content: @Composable () -> Unit,
)
