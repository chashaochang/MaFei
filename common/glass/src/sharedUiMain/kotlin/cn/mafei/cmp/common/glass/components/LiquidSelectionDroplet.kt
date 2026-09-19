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
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/**
 * 选中指示器"液滴"：不是画一个色块，而是一层**真玻璃** —— 对身后内容做折射（带色散），
 * 并按下的力度浮现高光、阴影与内阴影。
 *
 * 位移与缩放全部由 [animation]（`DampedDragAnimation`）驱动，也就是底部导航
 * (`LiquidBottomTabs`) 选中态用的同一套：切换时先按一下（放大到 `pressedScale`）、再用阻尼
 * 弹簧滑过去，拖动时按速度做拉伸/压缩。只做 `animateDpAsState` 的平移是没有"液态"感的。
 *
 * 静止态**必须**有可见的表面色（`onDrawSurface` 的 10% 黑/白，按下时淡出）——否则
 * `pressProgress = 0` 时折射半径 ≈ 0、高光 alpha = 0，选中项在静止时完全看不见。
 *
 * 分层要求：调用方必须把液滴画在标签**下面**。`drawBackdrop` 是把折射后的背景铺满整个
 * 形状，画在标签上层会把选中项的文字整个盖掉（只剩一颗空胶囊）。因此拖拽指示器的手势
 * 拿不到事件（标签在上面），按压反馈改由调用方把 [DampedDragAnimation.modifier] 挂到
 * Tab 自己身上触发：按下 `press()`，松手 `release()`。
 */
@Composable
fun LiquidSelectionDroplet(
    backdrop: GlassBackdrop,
    animation: DampedDragAnimation,
    /** 相邻两级之间液滴要走的距离（Tab 的等宽）。 */
    travelPerStep: Dp,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() >= .5f
    val travelPx = with(LocalDensity.current) { travelPerStep.toPx() }
    Box(
        modifier
            .graphicsLayer {
                translationX = animation.value * travelPx
                // 速度拉伸：横向按速度放大、纵向相应收紧，液滴才有"被甩出去"的感觉。
                val velocity = animation.velocity / 10f
                scaleX = animation.scaleX
                scaleY = animation.scaleY
                scaleX /= 1f - (velocity * .75f).coerceIn(-.2f, .2f)
                scaleY *= 1f - (velocity * .25f).coerceIn(-.2f, .2f)
            }
            .drawBackdrop(
                backdrop = backdrop.layer,
                shape = { Capsule() },
                effects = {
                    val progress = animation.pressProgress.coerceAtLeast(.001f)
                    lens(
                        10.dp.toPx() * progress,
                        14.dp.toPx() * progress,
                        chromaticAberration = true,
                    )
                },
                highlight = { Highlight.Default.copy(alpha = animation.pressProgress) },
                shadow = { Shadow(alpha = animation.pressProgress) },
                innerShadow = {
                    InnerShadow(
                        radius = 8.dp * animation.pressProgress,
                        alpha = animation.pressProgress,
                    )
                },
                onDrawSurface = {
                    // 与 `LiquidBottomTabs` 的选中液滴一致：静止时是一层 10% 的黑/白，
                    // 按下过程中淡出，把"实心块"让位给折射 + 高光。
                    drawRect(
                        if (isLightTheme) Color.Black.copy(alpha = .10f)
                        else Color.White.copy(alpha = .10f),
                        alpha = 1f - animation.pressProgress,
                    )
                },
            ),
    )
}
