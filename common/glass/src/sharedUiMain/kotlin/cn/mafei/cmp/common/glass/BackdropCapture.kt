package cn.mafei.cmp.common.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toIntSize

internal interface BackdropSample {
    fun available(target: LayoutCoordinates): Boolean
    fun draw(scope: DrawScope, target: LayoutCoordinates)
}

internal class BackdropCapture(
    val layer: GraphicsLayer?,
    val drawSample: (ContentDrawScope.() -> Unit)?,
) : BackdropSample {
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    private val matrix = Matrix()

    override fun available(target: LayoutCoordinates): Boolean {
        val source = coordinates ?: return false
        return layer != null && layer.size.width > 0 && layer.size.height > 0 &&
            source.isAttached && target.isAttached && source !== target &&
            source.findRootCoordinates() === target.findRootCoordinates()
    }

    override fun draw(scope: DrawScope, target: LayoutCoordinates) {
        if (!available(target)) return
        val source = coordinates ?: return
        val recorded = layer ?: return
        matrix.reset()
        target.transformFrom(source, matrix)
        scope.withTransform({ transform(matrix) }) { drawLayer(recorded) }
    }
}

@Composable
internal fun rememberBackdropCapture(drawSample: (ContentDrawScope.() -> Unit)? = null): BackdropCapture {
    val layer = if (supportsFrostedSampling()) rememberGraphicsLayer() else null
    return remember(layer, drawSample) { BackdropCapture(layer, drawSample) }
}

internal fun Modifier.captureBackdrop(capture: BackdropCapture): Modifier =
    if (capture.layer == null) this else this then CaptureModifier(capture)

private data class CaptureModifier(val capture: BackdropCapture) : ModifierNodeElement<CaptureNode>() {
    override fun create() = CaptureNode(capture)
    override fun update(node: CaptureNode) = node.replace(capture)
}

private class CaptureNode(private var capture: BackdropCapture) :
    Modifier.Node(), DrawModifierNode, LayoutAwareModifierNode {
    override fun onPlaced(coordinates: LayoutCoordinates) {
        capture.coordinates = coordinates
    }

    override fun ContentDrawScope.draw() {
        val layer = capture.layer ?: return drawContent()
        val custom = capture.drawSample
        if (custom != null) drawContent()
        val canvasScope = this
        val density = Density(density, fontScale)
        layer.record(density, layoutDirection, size.toIntSize()) {
            // Redirect drawContent to the capture without letting Compose use its live scope as density.
            canvasScope.draw(density, layoutDirection, drawContext.canvas, size, drawContext.graphicsLayer) {
                if (custom == null) canvasScope.drawContent() else canvasScope.custom()
            }
        }
        if (custom == null) drawLayer(layer)
    }

    fun replace(next: BackdropCapture) {
        next.coordinates = capture.coordinates
        capture.coordinates = null
        capture = next
    }

    override fun onDetach() {
        capture.coordinates = null
    }
}

@Composable
internal fun rememberCombinedSamples(vararg samples: BackdropSample): BackdropSample =
    remember(*samples) {
        object : BackdropSample {
            override fun available(target: LayoutCoordinates) = samples.any { it.available(target) }
            override fun draw(scope: DrawScope, target: LayoutCoordinates) {
                samples.forEach { it.draw(scope, target) }
            }
        }
    }

@Composable
internal fun rememberTransformedSample(
    source: BackdropSample,
    transform: DrawScope.(sample: () -> Unit) -> Unit,
): BackdropSample =
    remember(source, transform) {
        object : BackdropSample by source {
            override fun draw(scope: DrawScope, target: LayoutCoordinates) {
                scope.transform { source.draw(scope, target) }
            }
        }
    }
