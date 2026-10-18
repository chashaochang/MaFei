package cn.mafei.cmp.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.mafei.cmp.common.glass.GlassBackdrop

/**
 * Operation feedback belongs above the page, outside its backdrop recorder.
 * Dismissal only consumes presentation: pagination/request errors must remain
 * in their owner until a new request, otherwise auto-load can retry in a loop.
 */
@Composable
fun MaFeiErrorDialog(
    message: String?,
    backdrop: GlassBackdrop,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    busy: Boolean = false,
    title: String = "操作失败",
) {
    var dismissed by remember(message, busy) { mutableStateOf(false) }
    if (message.isNullOrBlank() || busy || dismissed) return
    val dismiss = { dismissed = true; onDismiss?.invoke(); Unit }
    MaFeiBackHandler(enabled = true, onBack = dismiss)
    MaFeiGlassConfirmDialog(
        title = title,
        content = message,
        confirmText = if (onRetry == null) "知道了" else "重试",
        dismissText = if (onRetry == null) null else "关闭",
        onConfirm = {
            if (onRetry == null) dismiss() else {
                // The request's busy/null-error transition hides the dialog.
                // Do not consume it first: an immediate same-message failure
                // can be coalesced into one frame and still needs feedback.
                onRetry()
            }
        },
        onDismiss = dismiss,
        backdrop = backdrop,
    )
}
