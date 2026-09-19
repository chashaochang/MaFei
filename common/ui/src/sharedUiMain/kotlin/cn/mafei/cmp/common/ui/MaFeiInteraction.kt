package cn.mafei.cmp.common.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
 * [onLongClick] is opt-in (legacy 管理员长按多选入口)；带长按的调用走
 * `combinedClickable`，其余仍是纯 `clickable`，行为不变。
 */
fun Modifier.mafeiInteractive(
    onClick: () -> Unit,
    enabled: Boolean = true,
    radiusDp: Float = 12f,
    label: String? = null,
    role: Role = Role.Button,
    visualFeedback: Boolean = true,
    onLongClick: (() -> Unit)? = null,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val active = enabled && LocalWindowInfo.current.isWindowFocused
    // 描边只留给「键盘聚焦」：悬停时再画一圈主色描边在深色界面上像一圈蓝边（侧边栏
    // 展开按钮上最明显），很脏。鼠标停在按钮上时也不需要焦点环 —— 指针本身就是指示。
    // 悬停反馈保留蓝色薄染（wash）+ 微放大，见下。
    val edge by animateFloatAsState(
        if (!active || !visualFeedback) 0f else if (focused && !hovered) 1f else 0f,
        tween(120), label = "card edge",
    )
    val wash by animateFloatAsState(
        if (!active || !visualFeedback) 0f else if (pressed) .12f else if (hovered) .045f else 0f,
        tween(100), label = "card feedback",
    )
    val scale by animateFloatAsState(
        if (!active || !visualFeedback) 1f else if (pressed) .985f else if (hovered) 1.012f else 1f,
        tween(if (pressed) 80 else 140), label = "card scale",
    )
    val accent = MaterialTheme.colorScheme.primary
    val clickableModifier = if (onLongClick != null) {
        @OptIn(ExperimentalFoundationApi::class)
        Modifier.combinedClickable(
            interactionSource = source,
            indication = if (visualFeedback) LocalIndication.current else null,
            enabled = enabled,
            onClickLabel = label,
            onLongClickLabel = null,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    } else {
        Modifier.clickable(
            source,
            // visualFeedback = false 的调用方（全宽轮播、整页背景点击区）连按压
            // ripple 也不要：radiusDp = 0 的矩形涟漪在无边框大面上就是「一个框」。
            indication = if (visualFeedback) LocalIndication.current else null,
            enabled = enabled,
            onClickLabel = label,
            role = role,
            onClick = onClick,
        )
    }
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
        .then(clickableModifier)
        .drawWithContent {
            drawContent()
            val corner = CornerRadius(radiusDp * density)
            if (wash > 0f) drawRoundRect(accent.copy(alpha = wash), cornerRadius = corner)
            if (edge > 0f) drawRoundRect(accent.copy(alpha = edge), cornerRadius = corner,
                style = Stroke((if (focused) 2f else 1f) * density))
        }
}
