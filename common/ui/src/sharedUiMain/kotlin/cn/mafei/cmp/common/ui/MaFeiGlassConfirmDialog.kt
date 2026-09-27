package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.glass.GlassBackdrop

/**
 * Modal confirmation panel. The panel intentionally uses an opaque surface:
 * application dialogs must stay readable and must not add a sampled-glass edge.
 *
 * [backdrop] remains in the signature for source compatibility with existing
 * callers; it is deliberately not consumed by the dialog background.
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
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA) else Color(0xFF242428)
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
        Column(
            Modifier
                .widthIn(min = 280.dp, max = 360.dp)
                .fillMaxWidth()
                .background(containerColor, RoundedCornerShape(24.dp))
                // The panel swallows its own taps so pressing inside never dismisses.
                .pointerInput(Unit) { detectTapGestures { } }
                .testTag("mafeiGlassConfirmCard"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                title,
                color = scheme.onSurface,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp),
            )
            Text(
                content,
                color = scheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    dismissText,
                    Modifier.height(44.dp).mafeiInteractive(
                        onDismiss,
                        radiusDp = 22f,
                        label = dismissText,
                    ).padding(horizontal = 18.dp, vertical = 12.dp),
                    color = scheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    confirmText,
                    Modifier.height(44.dp).mafeiInteractive(
                        onConfirm,
                        radiusDp = 22f,
                        label = confirmText,
                    ).padding(horizontal = 18.dp, vertical = 12.dp),
                    color = if (destructive) scheme.error else scheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
