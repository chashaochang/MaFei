package cn.mafei.cmp.common.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported

/** Opaque product handle: vendor graphics layers never cross the glass boundary. */
@Stable
class GlassBackdrop internal constructor(internal val layer: LayerBackdrop)

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberLayerBackdrop()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Capture background siblings only; never record a consumer into its own source. */
fun Modifier.captureGlassBackdrop(backdrop: GlassBackdrop): Modifier = layerBackdrop(backdrop.layer)

/** Effective capability, including the shell's latched effect failure. */
@Composable
fun currentGlassCapability(): GlassCapability {
    val failed = LocalGlassRenderHealth.current?.effectFailed == true
    return GlassCapability(
        backdrop = true,
        blur = !failed && isRenderEffectSupported(),
        lens = !failed && isRuntimeShaderSupported(),
        highlight = !failed && isRenderEffectSupported(),
        runtimeShader = !failed && isRuntimeShaderSupported(),
    )
}

/** The same layout and hit area are retained for every resolved level. */
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop?,
    spec: GlassSurfaceSpec,
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Standalone components also fail closed when no shell environment is installed.
    val health = LocalGlassRenderHealth.current ?: rememberGlassRenderHealth()
    val level = if (health.effectFailed) GlassLevel.Opaque
        else resolveGlassLevel(spec.level, currentGlassCapability(), backdrop != null)
    val shape = RoundedCornerShape(spec.cornerRadiusDp.coerceAtLeast(0f).dp)
    val material = if (level == GlassLevel.Opaque || backdrop == null) {
        Modifier.background(tint.copy(alpha = 1f), shape)
    } else {
        Modifier.drawBackdrop(
            backdrop = backdrop.layer,
            shape = { shape },
            effects = {
                val applied = health.guard.run {
                    blur(12.dp.toPx() * spec.intensity.coerceIn(0f, 1f))
                    if (level == GlassLevel.Full) {
                        lens(16.dp.toPx(), 8.dp.toPx() * spec.intensity.coerceIn(0f, 1f))
                    }
                }
                if (!applied) {
                    // Remove even partially constructed effects in this draw.
                    renderEffect = null
                    padding = 0f
                }
            },
            shadow = null,
            onDrawSurface = {
                // Do not wait for recomposition to make the failed frame readable.
                drawRect(tint.copy(alpha = if (health.effectFailed) 1f else spec.tintAlpha.coerceIn(0f, 1f)))
            },
        )
    }
    Box(modifier.clip(shape).then(material)) { content() }
}
