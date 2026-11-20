package cn.mafei.cmp.common.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.shapes.Capsule

/** Opaque product handle: vendor graphics layers never cross the glass boundary. */
@Stable
class GlassBackdrop internal constructor(
    internal val layer: Backdrop,
    internal val captureLayer: LayerBackdrop?,
)

private val GaussianRadius = 16.dp
internal fun frostedAlpha(requested: Float): Float = requested.coerceIn(.42f, 1f)

/** Blurs the sampled background, never the foreground content. */
internal fun Modifier.drawFrostedBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    fallbackTint: Color? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = { drawBackdrop -> drawBackdrop() },
    onDrawSurface: (DrawScope.() -> Unit)? = null,
): Modifier = composed {
    val health = LocalGlassRenderHealth.current ?: rememberGlassRenderHealth()
    val supported = isRenderEffectSupported()
    val canBlur = supported && !health.effectFailed
    val surface = MaterialTheme.colorScheme.surface
    val base = if (supported) Modifier else Modifier.clip(shape())
    base.drawPlainBackdrop(
        backdrop = backdrop,
        shape = shape,
        effects = {
            if (canBlur && !health.guard.run { blur(GaussianRadius.toPx()) }) {
                renderEffect = null
                padding = 0f
            }
        },
        layerBlock = layerBlock,
        exportedBackdrop = exportedBackdrop,
        onDrawBackdrop = { sample ->
            if (canBlur && !health.effectFailed) onDrawBackdrop(sample)
        },
        onDrawSurface = {
            if (!canBlur || health.effectFailed) {
                drawRect((fallbackTint ?: surface).copy(alpha = 1f))
            }
            if (onDrawSurface != null) onDrawSurface()
            else drawRect(surface.copy(alpha = frostedAlpha(0f)))
        },
    )
}

@Composable
fun rememberGlassBackdrop(onDraw: (ContentDrawScope.() -> Unit)? = null): GlassBackdrop {
    val layer = if (onDraw == null) rememberLayerBackdrop() else rememberLayerBackdrop(onDraw = onDraw)
    return remember(layer) { GlassBackdrop(layer, layer) }
}

/** Capture background siblings only; never record a consumer into its own source. */
fun Modifier.captureGlassBackdrop(backdrop: GlassBackdrop): Modifier =
    layerBackdrop(requireNotNull(backdrop.captureLayer) { "A combined glass backdrop cannot be used as a recorder" })

/**
 * Shared frosted navigation track. The vendor backdrop remains inside
 * common:glass; UI shells only provide the page backdrop, tint and optional
 * pressed transform. The caller can put [captureGlassBackdrop] before this
 * modifier on a sibling row to record the track for a selection droplet.
 */
fun Modifier.liquidTrackSurface(
    backdrop: GlassBackdrop,
    tint: Color,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier = drawFrostedBackdrop(
    backdrop = backdrop.layer,
    shape = { Capsule() },
    layerBlock = layerBlock,
    fallbackTint = tint,
    onDrawSurface = { drawRect(tint.copy(alpha = frostedAlpha(tint.alpha))) },
)

/**
 * One backdrop holding every [backdrops] material, for a glass child that must
 * sample the panels it sits on instead of only the page behind them.
 *
 * A child's look is decided entirely by the source it samples: hand it the page
 * backdrop and it reproduces the page, missing the panel it is drawn on. Pair this
 * with the exporting side — the panel passes the same layer as `exportedBackdrop`
 * when it draws ([GlassSurface]) — and the child gets page + panel.
 */
@Composable
fun rememberCombinedGlassBackdrop(vararg backdrops: GlassBackdrop): GlassBackdrop {
    val combined = rememberCombinedBackdrop(*backdrops.map { it.layer }.toTypedArray())
    return remember(combined) { GlassBackdrop(combined, captureLayer = null) }
}

/** Effective capability, including the shell's latched effect failure. */
@Composable
fun currentGlassCapability(): GlassCapability {
    val failed = LocalGlassRenderHealth.current?.effectFailed == true
    return GlassCapability(
        backdrop = true,
        blur = !failed && isRenderEffectSupported(),
    )
}

/** The same layout and hit area are retained for every resolved level. */
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop?,
    spec: GlassSurfaceSpec,
    tint: Color,
    modifier: Modifier = Modifier,
    /**
     * Records this surface's own material into that layer while it draws. Glass
     * children of this surface then sample page + surface
     * ([rememberCombinedGlassBackdrop]) instead of the page alone.
     */
    exportedBackdrop: GlassBackdrop? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(spec.cornerRadiusDp.coerceAtLeast(0f).dp)
    val export = exportedBackdrop?.let {
        requireNotNull(it.captureLayer) { "A combined glass backdrop cannot be used as a recorder" }
    }
    val material = if (spec.level == GlassLevel.Opaque || backdrop == null || !currentGlassCapability().blur) {
        Modifier.background(tint.copy(alpha = 1f), shape)
    } else {
        Modifier.drawFrostedBackdrop(
            backdrop = backdrop.layer, shape = { shape },
            exportedBackdrop = export,
            fallbackTint = tint,
            onDrawSurface = { drawRect(tint.copy(alpha = frostedAlpha(spec.tintAlpha))) },
        )
    }
    Box(modifier.clip(shape).then(material)) { content() }
}
