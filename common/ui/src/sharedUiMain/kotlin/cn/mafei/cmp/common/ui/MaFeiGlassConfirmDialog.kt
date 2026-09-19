package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidAlertDialog

/**
 * Modal confirmation in the upstream liquid material — see [LiquidAlertDialog],
 * whose panel this places in the calling window.
 *
 * A separate dialog window has no capture source, so the material can only be
 * real inside the window that owns [backdrop] (the mine/overview sheets do the
 * same). This wrapper owns what the app adds to the upstream panel: theme colors,
 * a full-window dim, dismissal by scrim/Escape, and the tap that must not fall
 * through the panel.
 */
@Composable
fun MaFeiGlassConfirmDialog(
    title: String,
    content: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val isLightTheme = scheme.background.luminance() > .5f
    // Upstream values; only the accent follows the product's palette.
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = .60f)
        else Color(0xFF121212).copy(alpha = .40f)
    val dimColor = if (isLightTheme) Color(0xFF29293A).copy(alpha = .23f)
        else Color(0xFF121212).copy(alpha = .56f)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    Box(
        modifier.fillMaxSize()
            .background(dimColor)
            .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .testTag("mafeiGlassConfirmScrim"),
        contentAlignment = Alignment.Center,
    ) {
        LiquidAlertDialog(
            backdrop = backdrop,
            title = title,
            message = content,
            confirmText = confirmText,
            dismissText = dismissText,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            contentColor = scheme.onSurface,
            accentColor = if (destructive) scheme.error else scheme.primary,
            confirmContentColor = if (destructive) scheme.onError else scheme.onPrimary,
            containerColor = containerColor,
            isLightTheme = isLightTheme,
            // The panel swallows its own taps so pressing inside never dismisses.
            modifier = Modifier
                .pointerInput(Unit) { detectTapGestures { } }
                .testTag("mafeiGlassConfirmCard"),
        )
    }
}
