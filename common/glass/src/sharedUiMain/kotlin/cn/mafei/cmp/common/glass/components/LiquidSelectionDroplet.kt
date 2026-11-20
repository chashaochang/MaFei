package cn.mafei.cmp.common.glass.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.utils.DampedDragAnimation
import cn.mafei.cmp.common.glass.drawFrostedBackdrop
import com.kyant.shapes.Capsule

/** Animated selection surface; place below the visible labels. */
@Composable
fun LiquidSelectionDroplet(
    backdrop: GlassBackdrop,
    animation: DampedDragAnimation,
    /** 相邻两级之间液滴要走的距离（等宽轨道的默认值）。 */
    travelPerStep: Dp,
    /** Optional pixel-position mapping for tracks whose tabs have different widths. */
    translationXInPx: ((Float) -> Float)? = null,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() >= .5f
    val travelPx = with(LocalDensity.current) { travelPerStep.toPx() }
    Box(
        modifier
            // Translation is a placement transform. Keep it outside the
            // backdrop layer so variable-width tracks can move the droplet
            // without changing the source coordinate system.
            .graphicsLayer {
                translationX = translationXInPx?.invoke(animation.value)
                    ?: animation.value * travelPx
            }
            .drawFrostedBackdrop(
                backdrop = backdrop.layer,
                shape = { Capsule() },
                // Scale must be applied through drawBackdrop's layerBlock,
                // block to inverse-transform the sampled source; applying it
                // and visibly pushes text toward the lower-right corner.
                layerBlock = {
                    // 速度拉伸：横向按速度放大、纵向相应收紧，液滴才有"被甩出去"的感觉。
                    val velocity = animation.velocity / 10f
                    scaleX = animation.scaleX
                    scaleY = animation.scaleY
                    scaleX /= 1f - (velocity * .75f).coerceIn(-.2f, .2f)
                    scaleY *= 1f - (velocity * .25f).coerceIn(-.2f, .2f)
                },
                onDrawSurface = {
                    // 与 `LiquidBottomTabs` 的选中液滴一致：静止时是一层 10% 的黑/白，
                    drawRect(
                        if (isLightTheme) Color.Black.copy(alpha = .10f)
                        else Color.White.copy(alpha = .10f),
                    )
                    // Match the standard LiquidBottomTabs selection layer:
                    // pressing adds the same subtle dark depth wash beneath
                    // the highlight instead of using a separate top-bar tint.
                    drawRect(Color.Black.copy(alpha = .03f * animation.pressProgress))
                },
            ),
    )
}
