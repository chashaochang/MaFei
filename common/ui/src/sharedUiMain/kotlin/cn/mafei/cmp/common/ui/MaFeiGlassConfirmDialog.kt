package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassMaterial
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassSurfaceSpec

/**
 * Modal confirmation panel with an opaque dialog surface. Application dialogs
 * stay readable without liquid-glass refraction or page bleed-through.
 *
 * [backdrop] remains in the signature for source compatibility with existing
 * callers; the opaque dialog intentionally does not sample it.
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
    // Dialogs are their own dark canvas; nested cards/list groups use `surface`.
    // Keeping those semantic layers distinct prevents popup content from
    // visually merging into one flat block.
    val containerColor = scheme.background
    val dimColor = if (isLightTheme) Color(0xFF29293A).copy(alpha = .23f)
        else Color(0xFF121212).copy(alpha = .56f)
    val focusRequester = remember { FocusRequester() }
    val scrimInteraction = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }
    val surfaceSpec = GlassSurfaceSpec(
        role = GlassRole.Dialog,
        level = GlassLevel.Opaque,
        tintAlpha = 1f,
        intensity = 0f,
        cornerRadiusDp = 24f,
        material = GlassMaterial.Frosted,
    )
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    Box(
        modifier.fillMaxSize()
            .background(dimColor)
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onDismiss,
            )
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
        GlassSurface(
            backdrop = backdrop,
            spec = surfaceSpec,
            tint = containerColor,
            modifier = Modifier
                .widthIn(min = 280.dp, max = 360.dp)
                .fillMaxWidth()
                // The panel swallows its own taps so pressing inside never dismisses.
                .clickable(
                    interactionSource = panelInteraction,
                    indication = null,
                    onClick = {},
                )
                .testTag("mafeiGlassConfirmCard"),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title,
                        color = scheme.onSurface,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
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
}
