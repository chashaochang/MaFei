package com.kyant.backdrop.internal

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import java.lang.reflect.Proxy

class OutlineClipCacheTest {
    private val canvas = stub<Canvas>()
    private var paths = 0
    private var rewinds = 0
    private val cache = OutlineClipCache {
        paths++
        stub<Path> { method -> if (method == "rewind") rewinds++ }
    }

    private fun rounded(width: Float = 32f, radius: Float = 8f) =
        Outline.Rounded(RoundRect(0f, 0f, width, 32f, CornerRadius(radius)))

    private fun clip(outline: Outline) {
        canvas.save()
        cache.clip(canvas, outline)
        canvas.restore()
    }

    @Test
    fun unchangedRoundedGeometryBuildsPathOnceAcrossBackgroundFrames() {
        repeat(120) { clip(rounded()) }
        assertEquals(1, paths)
        assertEquals(1, rewinds)
    }

    @Test
    fun sizeAndAnimatedCornerChangesRebuildTheSamePath() {
        clip(rounded())
        clip(rounded(width = 24f))
        clip(rounded(width = 24f, radius = 4f))
        clip(rounded(width = 24f, radius = 4f))
        assertEquals(1, paths)
        assertEquals(3, rewinds)
    }

    @Test
    fun detachClearsBothPathAndGeometry() {
        clip(rounded())
        cache.clear()
        clip(rounded())
        assertEquals(2, paths)
        assertEquals(2, rewinds)
    }

    @Test
    fun rectanglesAndMutableGenericPathsDoNotBuildARoundedPath() {
        clip(Outline.Rectangle(Rect(0f, 0f, 32f, 32f)))
        val generic = stub<Path>().apply { addRect(Rect(0f, 0f, 24f, 24f)) }
        clip(Outline.Generic(generic))
        generic.reset()
        generic.addRect(Rect(8f, 8f, 32f, 32f))
        clip(Outline.Generic(generic))
        assertEquals(0, paths)
        assertEquals(0, rewinds)
    }

    // Count native path-building requests without loading a GPU/native runtime.
    private inline fun <reified T> stub(crossinline onCall: (String) -> Unit = {}): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            onCall(method.name)
            null
        } as T
}
