package cn.mafei.cmp.common.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role

/**
 * One event-driven behavior on every target, including phones with a mouse.
 * Clickable owns hover, focus, keyboard activation and press cancellation. Effects
 * never change measurement or hit geometry. Focus and hover remain independent.
 */
fun Modifier.mafeiInteractive(
    onClick: () -> Unit,
    enabled: Boolean = true,
    radiusDp: Float = 12f,
    label: String? = null,
    role: Role = Role.Button,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val active = enabled && LocalWindowInfo.current.isWindowFocused
    val edge by animateFloatAsState(
        if (!active) 0f else if (focused) 1f else if (hovered) .55f else 0f,
        tween(120), label = "card edge",
    )
    val wash by animateFloatAsState(
        if (!active) 0f else if (pressed) .12f else if (hovered) .045f else 0f,
        tween(100), label = "card feedback",
    )
    val scale by animateFloatAsState(
        if (!active) 1f else if (pressed) .985f else if (hovered) 1.012f else 1f,
        tween(if (pressed) 80 else 140), label = "card scale",
    )
    val accent = MaterialTheme.colorScheme.primary
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(source, LocalIndication.current, enabled, label, role, onClick)
        .drawWithContent {
            drawContent()
            val corner = CornerRadius(radiusDp * density)
            if (wash > 0f) drawRoundRect(accent.copy(alpha = wash), cornerRadius = corner)
            if (edge > 0f) drawRoundRect(accent.copy(alpha = edge), cornerRadius = corner,
                style = Stroke((if (focused) 2f else 1f) * density))
        }
}
