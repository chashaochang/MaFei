package cn.mafei.cmp.common.glass.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.GlassBackdrop
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported

/**
 * The catalog's progressive material with a defined SkSL/AGSL gradient and a
 * readable non-shader fallback. The rendered material can be exported so the
 * action buttons refract both the page and the bar beneath them.
 */
internal fun Modifier.progressiveBlur(
    backdrop: Backdrop,
    tint: Color,
    blurRadius: Dp = 4.dp,
    tintIntensity: Float = 0.8f,
    gradientStartFraction: Float = 0.5f,
    opacity: Float = 1f,
    exportedBackdrop: LayerBackdrop? = null,
): Modifier {
    val resolvedTintIntensity = tintIntensity.coerceIn(0f, 1f)
    val resolvedStart = gradientStartFraction.coerceIn(0f, .999f)
    val resolvedOpacity = opacity.coerceIn(0f, 1f)
    if (!isRenderEffectSupported() || !isRuntimeShaderSupported()) {
        return drawWithCache {
            val material = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to tint.copy(alpha = resolvedTintIntensity * resolvedOpacity),
                    resolvedStart to tint.copy(alpha = resolvedTintIntensity * resolvedOpacity),
                    1f to Color.Transparent,
                ),
            )
            onDrawBehind { drawRect(material) }
        }
    }
    return drawPlainBackdrop(
        backdrop = backdrop,
        shape = { RectangleShape },
        effects = {
            blur(blurRadius.toPx())
            runtimeShaderEffect(
                key = "VerticalProgressiveBlur",
                shaderString = """
                    uniform shader content;

                    uniform float2 size;
                    layout(color) uniform half4 tint;
                    uniform float tintIntensity;
                    uniform float gradientStartFraction;
                    uniform float opacity;

                    half4 main(float2 coord) {
                        float alpha = (1.0 - smoothstep(
                            size.y * gradientStartFraction,
                            size.y,
                            coord.y
                        )) * opacity;
                        return mix(content.eval(coord) * alpha, tint * alpha, tintIntensity);
                    }
                """.trimIndent(),
                uniformShaderName = "content",
            ) {
                setFloatUniform("size", size.width, size.height)
                setColorUniform("tint", tint)
                setFloatUniform("tintIntensity", resolvedTintIntensity)
                setFloatUniform("gradientStartFraction", resolvedStart)
                setFloatUniform("opacity", resolvedOpacity)
            }
        },
        exportedBackdrop = exportedBackdrop,
    )
}

/**
 * 公开入口：应用层（common/ui、各 feature）只有 [GlassBackdrop]，拿不到模块内部的
 * vendored `Backdrop`，所以渐进模糊必须从这里转发出去。
 *
 * 用途见导航重构：一级/二级页面顶部导航在内容滚动后，要在状态栏区域浮现一条渐进
 * 模糊（`opacity = if (已滚动) 1f else 0f`），不要用普通的半透明色块冒充。
 */
fun Modifier.mafeiProgressiveBlur(
    backdrop: GlassBackdrop,
    tint: Color,
    blurRadius: Dp = 4.dp,
    tintIntensity: Float = 0.8f,
    gradientStartFraction: Float = 0.5f,
    opacity: Float = 1f,
): Modifier = progressiveBlur(
    backdrop = backdrop.layer,
    tint = tint,
    blurRadius = blurRadius,
    tintIntensity = tintIntensity,
    gradientStartFraction = gradientStartFraction,
    opacity = opacity,
)
