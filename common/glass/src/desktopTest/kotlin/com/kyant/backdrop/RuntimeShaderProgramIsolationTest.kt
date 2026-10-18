package com.kyant.backdrop

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RuntimeShaderProgramIsolationTest {
    @Test
    fun separateNodeCachesReuseProgramButNeverShareUniformBuilders() {
        val firstNode = RuntimeShaderCacheImpl()
        val secondNode = RuntimeShaderCacheImpl()
        val first = firstNode.obtainRuntimeShader("color", ColorShader) as SkikoRuntimeShader
        val second = secondNode.obtainRuntimeShader("color", ColorShader) as SkikoRuntimeShader
        try {
            assertSame(first.compiledProgram, second.compiledProgram)
            assertNotSame(first.shader, second.shader)
            first.setFloatUniform("color", 1f, 0f, 0f, 1f)
            second.setFloatUniform("color", 0f, 1f, 0f, 1f)
            assertEquals(0xffff0000.toInt(), pixel(first))
            assertEquals(0xff00ff00.toInt(), pixel(second))
            assertEquals(0xffff0000.toInt(), pixel(first))

            firstNode.clear()
            val reattached = firstNode.obtainRuntimeShader("color", ColorShader) as SkikoRuntimeShader
            try {
                assertSame(first.compiledProgram, reattached.compiledProgram)
                assertNotSame(first.shader, reattached.shader)
            } finally {
                reattached.shader.close()
            }
        } finally {
            first.shader.close()
            second.shader.close()
        }
    }

    @Test
    fun activeBuilderRetainsProgramAfterGlobalCacheEviction() {
        val active = RuntimeShader(ColorShader) as SkikoRuntimeShader
        try {
            active.setFloatUniform("color", 1f, 0f, 0f, 1f)
            repeat(33) { index ->
                val other = RuntimeShader("$ColorShader\n// distinct program $index") as SkikoRuntimeShader
                other.shader.close()
            }
            assertEquals(0xffff0000.toInt(), pixel(active))
        } finally {
            active.shader.close()
        }
    }

    private fun pixel(shader: SkikoRuntimeShader): Int = Surface.makeRasterN32Premul(1, 1).use { surface ->
        shader.shader.makeShader().use { instance ->
            Paint().use { paint ->
                paint.shader = instance
                surface.canvas.drawRect(Rect.makeWH(1f, 1f), paint)
            }
        }
        Bitmap().use { bitmap ->
            assertTrue(bitmap.allocN32Pixels(1, 1))
            assertTrue(surface.readPixels(bitmap, 0, 0))
            bitmap.getColor(0, 0)
        }
    }

    private companion object {
        const val ColorShader = "uniform half4 color; half4 main(float2 p) { return color; }"
    }
}
