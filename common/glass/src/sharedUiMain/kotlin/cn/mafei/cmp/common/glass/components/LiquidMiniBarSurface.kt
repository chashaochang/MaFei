package cn.mafei.cmp.common.glass.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.drawFrostedBackdrop
import com.kyant.shapes.Capsule

/** Optical surface ported from AndroidLiquidGlass' verified MiniBar sample. */
@Composable
fun LiquidMiniBarSurface(
    backdrop: GlassBackdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier
            .drawFrostedBackdrop(
                backdrop = backdrop.layer,
                shape = { Capsule() },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = .2f))
                },
            )
            .clip(Capsule())
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "打开播放器",
                onClick = onClick,
            )
            .drawWithContent {
                drawContent()
                if (hovered) drawRect(Color.White.copy(alpha = .045f))
            }
            .padding(6.dp),
        content = content,
    )
}
