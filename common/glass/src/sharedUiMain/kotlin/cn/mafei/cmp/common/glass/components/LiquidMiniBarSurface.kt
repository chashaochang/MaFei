package cn.mafei.cmp.common.glass.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.GlassBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/** Optical surface ported from AndroidLiquidGlass' verified MiniBar sample. */
@Composable
fun LiquidMiniBarSurface(
    backdrop: GlassBackdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .drawBackdrop(
                backdrop = backdrop.layer,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(16.dp.toPx(), 28.dp.toPx(), chromaticAberration = true)
                },
                shadow = { Shadow(alpha = .18f) },
                innerShadow = { InnerShadow(radius = 5.dp, alpha = .25f) },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = .2f))
                },
            )
            .clip(Capsule())
            .clickable(role = Role.Button, onClickLabel = "打开播放器", onClick = onClick)
            .padding(6.dp),
        content = content,
    )
}
