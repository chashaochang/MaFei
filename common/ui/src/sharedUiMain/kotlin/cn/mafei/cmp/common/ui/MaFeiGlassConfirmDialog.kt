package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidAlertDialog

/**
 * Same-layer modal host for the standard Liquid Glass Demo confirmation.
 * The caller records page content into [backdrop], outside this modal subtree.
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
    val dimColor = if (isLightTheme) Color(0xFF29293A).copy(alpha = .23f)
        else Color(0xFF121212).copy(alpha = .56f)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    Box(
        modifier.fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(dimColor)
                .mafeiDismissOnOutsidePointer(onDismiss)
                .testTag("mafeiGlassConfirmScrim"),
        )
        MaFeiConfirmDialogPanel(
            title = title,
            content = content,
            confirmText = confirmText,
            dismissText = dismissText,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            destructive = destructive,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                // The panel swallows its own taps so pressing inside never dismisses.
                .pointerInput(Unit) { detectTapGestures { } }
                .testTag("mafeiGlassConfirmCard"),
        )
    }
}

/** Shared product colors and semantics; layout/material belong to LiquidAlertDialog. */
@Composable
internal fun MaFeiConfirmDialogPanel(
    title: String,
    content: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val isLightTheme = scheme.background.luminance() > .5f
    LiquidAlertDialog(
        backdrop = backdrop,
        title = title,
        message = content,
        confirmText = confirmText,
        dismissText = dismissText,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        contentColor = if (isLightTheme) Color.Black else Color.White,
        accentColor = if (destructive) scheme.error
            else if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF),
        containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = .6f)
            else Color(0xFF121212).copy(alpha = .4f),
        isLightTheme = isLightTheme,
        modifier = modifier,
    )
}

/**
 * Dismiss a same-layer modal from the scrim without relying on the final
 * pointer-up event. CPF/OHOS can let a child consume the up event before a
 * regular clickable or tap detector receives it; observing the Initial pass
 * keeps the scrim reliable while the panel remains a separate hit target.
 */
fun Modifier.mafeiDismissOnOutsidePointer(onDismiss: () -> Unit): Modifier =
    pointerInput(onDismiss) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    onDismiss()
                    break
                }
            }
        }
    }
