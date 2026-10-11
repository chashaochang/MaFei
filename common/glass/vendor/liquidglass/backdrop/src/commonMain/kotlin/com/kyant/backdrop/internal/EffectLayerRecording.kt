package com.kyant.backdrop.internal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

// Only decorative layers may use this cache. Backdrop content must stay live.
internal class EffectLayerRecording {
    private var previous: Key? = null

    fun recordIfChanged(
        size: Size,
        density: Density,
        layoutDirection: LayoutDirection,
        outline: Outline,
        parameters: Any,
        canReuse: Boolean = true,
        record: () -> Unit
    ) {
        val key = Key(size, density.density, density.fontScale, layoutDirection, outline, parameters)
        if (canReuse && key == previous) return
        record()
        previous = if (canReuse) key else null
    }

    fun clear() {
        previous = null
    }

    private data class Key(
        val size: Size,
        val density: Float,
        val fontScale: Float,
        val layoutDirection: LayoutDirection,
        val outline: Outline,
        val parameters: Any
    )
}

internal fun isInvisibleEffect(alpha: Float, blendMode: BlendMode): Boolean =
    alpha <= 0f && (blendMode == BlendMode.SrcOver || blendMode == BlendMode.Plus)
