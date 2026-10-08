package com.kyant.backdrop.backdrops

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import com.kyant.backdrop.internal.recordLayer

fun Modifier.layerBackdrop(backdrop: LayerBackdrop): Modifier =
    this then LayerBackdropElement(backdrop)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        return LayerBackdropNode(backdrop)
    }

    override fun update(node: LayerBackdropNode) {
        if (node.backdrop != backdrop) {
            node.backdrop.layerCoordinates = null
            node.backdrop = backdrop
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layerBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerBackdropElement) return false

        if (backdrop != other.backdrop) return false

        return true
    }

    override fun hashCode(): Int {
        return backdrop.hashCode()
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    override fun ContentDrawScope.draw() {
        if (backdrop.recordsContentOnly) {
            // Reuse the live display list for both the page and its consumers.
            // Drawing content separately doubles traversal at every nested recorder.
            recordLayer(backdrop.graphicsLayer) { this@draw.drawContent() }
            drawLayer(backdrop.graphicsLayer)
        } else {
            // A custom recorder may intentionally differ from the visible content.
            drawContent()
            recordLayer(backdrop.graphicsLayer) { backdrop.onDraw(this@draw) }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
        }
    }

    override fun onDetach() {
        backdrop.layerCoordinates = null
    }
}
