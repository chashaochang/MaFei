package cn.mafei.cmp.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog

/** Platform-window host; shares the standard panel, with no cross-window sampling. */
@Composable
fun LegacyConfirmDialog(
    title: String,
    content: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss) {
        MaFeiConfirmDialogPanel(
            title = title,
            content = content,
            confirmText = confirmText,
            dismissText = dismissText,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            destructive = destructive,
            backdrop = null,
        )
    }
}
