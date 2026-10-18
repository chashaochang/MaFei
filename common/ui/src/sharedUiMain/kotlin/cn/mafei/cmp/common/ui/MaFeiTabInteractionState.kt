package cn.mafei.cmp.common.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first

/** Session-owned coordination for deferred navigation and automatic data refresh. */
@Stable
class MaFeiTabInteractionState {
    private var pendingSelection by mutableStateOf(false)
    private var animating by mutableStateOf(false)
    private var changeVersion by mutableLongStateOf(0L)

    val isBusy: Boolean get() = pendingSelection || animating

    internal fun selectionPending(pending: Boolean) {
        if (pendingSelection != pending) {
            pendingSelection = pending
            changeVersion++
        }
    }
    internal fun animationRunning(running: Boolean) {
        if (animating != running) {
            animating = running
            changeVersion++
        }
    }
    internal fun clear() {
        selectionPending(false)
        animationRunning(false)
    }

    suspend fun awaitIdle() {
        while (true) {
            snapshotFlow { isBusy }.first { !it }
            val idleVersion = changeVersion
            // Animation cancellation/restart can briefly report false. Let the
            // idle frame draw and reject even a complete busy pulse in between.
            withFrameNanos { }
            if (!isBusy && changeVersion == idleVersion) return
        }
    }
}

val LocalMaFeiTabInteractionState = staticCompositionLocalOf<MaFeiTabInteractionState?> { null }
