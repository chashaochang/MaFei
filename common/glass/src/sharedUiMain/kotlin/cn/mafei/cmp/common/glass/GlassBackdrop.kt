package cn.mafei.cmp.common.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize

/** Opaque handle for sampling one or more background siblings. */
@Stable
class GlassBackdrop internal constructor(
    internal val layer: BackdropSample,
    internal val captureLayer: BackdropCapture?,
)

private val GaussianRadius = 16.dp
internal fun frostedAlpha(requested: Float): Float = requested.coerceIn(.42f, 1f)

/** Blurs the sampled background, never the foreground content. */
internal fun Modifier.drawFrostedBackdrop(
    backdrop: BackdropSample,
    shape: () -> Shape,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: BackdropCapture? = null,
    fallbackTint: Color? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = { drawBackdrop -> drawBackdrop() },
    onDrawSurface: (DrawScope.() -> Unit)? = null,
): Modifier = composed {
    val health = LocalGlassRenderHealth.current ?: rememberGlassRenderHealth()
    val supported = supportsFrostedSampling() && !health.effectFailed
    val surface = MaterialTheme.colorScheme.surface
    val sampledLayer = if (supported) rememberGraphicsLayer() else null
    val placement = remember { MaterialPlacement() }
    DisposableEffect(exportedBackdrop) {
        onDispose { exportedBackdrop?.coordinates = null }
    }
    val transform = if (supported && layerBlock != null) Modifier.graphicsLayer(layerBlock) else Modifier
    transform.clip(shape())
        .onPlaced {
            placement.coordinates = it
            exportedBackdrop?.coordinates = it
        }
        .drawWithCache {
            val radius = GaussianRadius.toPx()
            if (sampledLayer != null) {
                health.guard.run {
                    sampledLayer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                }
            }
            val materialDensity = Density(density, fontScale)
            onDrawWithContent {
                val target = placement.coordinates
                val sampled = sampledLayer != null && !health.effectFailed &&
                    target != null && backdrop.available(target)
                if (sampledLayer != null && target != null && sampled) {
                    sampledLayer.record(materialDensity, layoutDirection, size.toIntSize()) {
                        onDrawBackdrop { backdrop.draw(this, target) }
                    }
                }
                fun DrawScope.material() {
                    if (sampled) drawLayer(sampledLayer)
                    else drawRect((fallbackTint ?: surface).copy(alpha = 1f))
                    if (onDrawSurface == null) drawRect(surface.copy(alpha = frostedAlpha(0f)))
                    else onDrawSurface()
                }
                val export = exportedBackdrop?.layer
                if (export != null && supported) {
                    export.record(materialDensity, layoutDirection, size.toIntSize()) { material() }
                    drawLayer(export)
                } else {
                    material()
                }
                drawContent()
            }
        }
}

private class MaterialPlacement {
    var coordinates: LayoutCoordinates? = null
}

@Composable
fun rememberGlassBackdrop(onDraw: (ContentDrawScope.() -> Unit)? = null): GlassBackdrop {
    val layer = rememberBackdropCapture(onDraw)
    return remember(layer) { GlassBackdrop(layer, layer) }
}

/** Capture background siblings only; never record a consumer into its own source. */
fun Modifier.captureGlassBackdrop(backdrop: GlassBackdrop): Modifier =
    captureBackdrop(requireNotNull(backdrop.captureLayer) { "A combined glass backdrop cannot be used as a recorder" })

/**
 * Shared frosted navigation track. UI shells provide the page backdrop, tint and optional
 * pressed transform. The caller can put [captureGlassBackdrop] before this
 * modifier on a sibling row to record the track for a selection droplet.
 */
fun Modifier.liquidTrackSurface(
    backdrop: GlassBackdrop,
    tint: Color,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier = drawFrostedBackdrop(
    backdrop = backdrop.layer,
    shape = { CircleShape },
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
    val combined = rememberCombinedSamples(*backdrops.map { it.layer }.toTypedArray())
    return remember(combined) { GlassBackdrop(combined, captureLayer = null) }
}

/** Effective capability, including the shell's latched effect failure. */
@Composable
fun currentGlassCapability(): GlassCapability {
    val failed = LocalGlassRenderHealth.current?.effectFailed == true
    return GlassCapability(
        backdrop = supportsFrostedSampling(),
        blur = !failed && supportsFrostedSampling(),
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
