package cn.mafei.cmp.common.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.shapes.Capsule

/** Opaque product handle: vendor graphics layers never cross the glass boundary. */
@Stable
class GlassBackdrop internal constructor(
    internal val layer: Backdrop,
    internal val captureLayer: LayerBackdrop?,
)

/**
 * `drawBackdrop` 的 `highlight` 参数**默认是 `Highlight.Default`**（一圈高光）。液态玻璃
 * 需要它；纯高斯模糊（贴边面板）必须显式给 null —— 否则会在形状的每条边上画出一条亮线，
 * 一整屏高度的侧边栏就成了"四周一圈边框"。
 */
private val LiquidHighlight: () -> Highlight? = { Highlight.Default }
private val NoHighlight: () -> Highlight? = { null }

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberLayerBackdrop()
    return remember(layer) { GlassBackdrop(layer, layer) }
}

/** Capture background siblings only; never record a consumer into its own source. */
fun Modifier.captureGlassBackdrop(backdrop: GlassBackdrop): Modifier =
    layerBackdrop(requireNotNull(backdrop.captureLayer) { "A combined glass backdrop cannot be used as a recorder" })

/**
 * Shared liquid navigation track. The vendor backdrop remains inside
 * common:glass; UI shells only provide the page backdrop, tint and optional
 * pressed transform. The caller can put [captureGlassBackdrop] before this
 * modifier on a sibling row to record the track for a selection droplet.
 */
fun Modifier.liquidTrackSurface(
    backdrop: GlassBackdrop,
    tint: Color,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier =
    // Do not put a fixed-size clip outside drawBackdrop. Its layerBlock is the
    // real pressed transform; an outer clip would cut off the enlarged capsule
    // and its highlight, leaving only the sampled content visibly expanded.
    // drawBackdrop already owns the capsule mask, matching LiquidBottomTabs.
    drawBackdrop(
        backdrop = backdrop.layer,
        shape = { Capsule() },
        effects = {
            blur(12.dp.toPx() * .9f)
            lens(16.dp.toPx(), 8.dp.toPx() * .9f)
        },
        layerBlock = layerBlock,
        onDrawSurface = { drawRect(tint) },
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
    /**
     * Records this surface's own material into that layer while it draws. Glass
     * children of this surface then sample page + surface
     * ([rememberCombinedGlassBackdrop]) instead of the page alone.
     */
    exportedBackdrop: GlassBackdrop? = null,
    content: @Composable () -> Unit,
) {
    // Standalone components also fail closed when no shell environment is installed.
    val health = LocalGlassRenderHealth.current ?: rememberGlassRenderHealth()
    val level = if (health.effectFailed) GlassLevel.Opaque
        else resolveGlassLevel(spec.level, currentGlassCapability(), backdrop != null)
    val shape = RoundedCornerShape(spec.cornerRadiusDp.coerceAtLeast(0f).dp)
    val export = exportedBackdrop?.let {
        requireNotNull(it.captureLayer) { "A combined glass backdrop cannot be used as a recorder" }
    }
    val liquid = spec.material == GlassMaterial.Liquid
    val material = if (level == GlassLevel.Opaque || backdrop == null) {
        Modifier.background(tint.copy(alpha = 1f), shape)
    } else {
        Modifier
            // 投影只属于"浮在页面上的液态玻璃"。贴边通栏的面板加投影只会让朝内容的那条边
            // 多一道晕，而那一侧本来就由调用方画分界线。
            .then(
                if (liquid) {
                    Modifier.shadow(
                        elevation = if (spec.role == GlassRole.Navigation || spec.role == GlassRole.Overlay) 14.dp else 8.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = .28f),
                        spotColor = Color.Black.copy(alpha = .34f),
                    )
                } else {
                    Modifier
                },
            )
            .drawBackdrop(
            backdrop = backdrop.layer,
            shape = { shape },
            effects = {
                val applied = health.guard.run {
                    blur(12.dp.toPx() * spec.intensity.coerceIn(0f, 1f))
                    // 纯高斯模糊（Frosted）不折射：折射在贴边面板的四条边上会画出可见的
                    // 边（"上下左右都有线"），而整屏高度的折射没有视觉收益。
                    if (level == GlassLevel.Full && liquid) {
                        lens(16.dp.toPx(), 8.dp.toPx() * spec.intensity.coerceIn(0f, 1f))
                    }
                }
                if (!applied) {
                    // Remove even partially constructed effects in this draw.
                    renderEffect = null
                    padding = 0f
                }
            },
            exportedBackdrop = export,
            // 只有液态玻璃要那圈高光；高斯模糊面板显式关掉（默认值是 Highlight.Default）。
            highlight = if (liquid) LiquidHighlight else NoHighlight,
            shadow = null,
            onDrawSurface = {
                // Do not wait for recomposition to make the failed frame readable.
                drawRect(tint.copy(alpha = if (health.effectFailed) 1f else spec.tintAlpha.coerceIn(0f, 1f)))
                if (!health.effectFailed && level != GlassLevel.Opaque && liquid) {
                    val radius = spec.cornerRadiusDp.coerceAtLeast(0f).dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (level == GlassLevel.Full) .22f else .14f),
                        cornerRadius = CornerRadius(radius, radius),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            },
        )
    }
    Box(modifier.clip(shape).then(material)) { content() }
}
