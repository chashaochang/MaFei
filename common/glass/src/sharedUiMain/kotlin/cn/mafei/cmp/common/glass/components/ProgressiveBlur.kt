package cn.mafei.cmp.common.glass.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.withSaveLayer
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.drawFrostedBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

/** Apply the fade after the blur, on a material-only sibling of the toolbar content. */
internal fun Modifier.progressiveBlur(
    backdrop: GlassBackdrop,
    tint: Color,
    tintIntensity: Float,
    gradientStartFraction: Float,
    opacity: Float,
    exportedBackdrop: LayerBackdrop? = null,
): Modifier {
    if (opacity <= 0f) return this
    return then(if (exportedBackdrop == null) Modifier else Modifier.layerBackdrop(exportedBackdrop))
        .drawWithCache {
            val opaque = Color.Black.copy(alpha = opacity.coerceAtMost(1f))
            val fade = Brush.verticalGradient(
                0f to opaque,
                gradientStartFraction.coerceIn(0f, .999f) to opaque,
                1f to Color.Transparent,
            )
            val paint = Paint()
            onDrawWithContent {
                drawContext.canvas.withSaveLayer(Rect(Offset.Zero, size), paint) {
                    drawContent()
                    drawRect(fade, blendMode = BlendMode.DstIn)
                }
            }
        }
        .drawFrostedBackdrop(
            backdrop = backdrop.layer,
            shape = { RectangleShape },
            fallbackTint = tint,
            onDrawSurface = { drawRect(tint.copy(alpha = tintIntensity.coerceIn(0f, 1f))) },
        )
}

fun Modifier.mafeiProgressiveBlur(
    backdrop: GlassBackdrop,
    tint: Color,
    tintIntensity: Float,
    gradientStartFraction: Float,
    opacity: Float,
): Modifier = progressiveBlur(backdrop, tint, tintIntensity, gradientStartFraction, opacity)
