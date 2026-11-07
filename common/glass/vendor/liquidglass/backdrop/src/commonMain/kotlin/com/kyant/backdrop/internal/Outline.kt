// Modified by MaFei: cache rounded clipping geometry while keeping generic paths live.
package com.kyant.backdrop.internal

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path

// Cache geometry only. Clipping still runs every draw and generic paths stay live.
internal class OutlineClipCache(private val createPath: () -> Path = { Path() }) {
    private var path: Path? = null
    private var roundRect: RoundRect? = null

    fun clip(canvas: Canvas, outline: Outline) {
        when (outline) {
            is Outline.Rectangle -> canvas.clipRect(outline.rect)
            is Outline.Rounded -> {
                val path = path ?: createPath().also { path = it }
                if (roundRect != outline.roundRect) {
                    path.rewind()
                    path.addRoundRect(outline.roundRect)
                    roundRect = outline.roundRect
                }
                canvas.clipPath(path)
            }
            is Outline.Generic -> canvas.clipPath(outline.path)
        }
    }

    fun clear() {
        path = null
        roundRect = null
    }
}
