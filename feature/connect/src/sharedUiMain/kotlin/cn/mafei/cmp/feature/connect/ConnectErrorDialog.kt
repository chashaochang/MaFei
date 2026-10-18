package cn.mafei.cmp.feature.connect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiBackHandler
import cn.mafei.cmp.common.ui.MaFeiGlassConfirmDialog

/** Flow-level feedback: keep this outside the captured form and navigation host. */
@Composable
fun ConnectErrorDialog(
    error: String?,
    onDismiss: () -> Unit,
    backdrop: GlassBackdrop,
) {
    if (error == null) return
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(error) { keyboard?.hide() }
    MaFeiBackHandler(enabled = true, onBack = onDismiss)
    MaFeiGlassConfirmDialog(
        title = "连接提示",
        content = error,
        confirmText = "知道了",
        dismissText = null,
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        backdrop = backdrop,
    )
}
