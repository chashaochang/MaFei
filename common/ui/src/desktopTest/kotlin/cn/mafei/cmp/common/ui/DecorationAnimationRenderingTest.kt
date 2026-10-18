package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.RuntimeShaderCache
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import kotlin.test.Test
import kotlin.test.assertTrue

class DecorationAnimationRenderingTest {
    @get:org.junit.Rule
    val compose = createComposeRule()

    @Test
    fun cachedHighlightStillUpdatesOpacityBlendModeAndReappearsAfterZero() {
        val opacity = mutableStateOf(.25f)
        val blendMode = mutableStateOf(BlendMode.SrcOver)
        compose.setContent {
            Box(Modifier.size(48.dp).testTag("highlight").drawBackdrop(
                SolidBackdrop(Color(.25f, .25f, .25f)), shape = { RectangleShape }, effects = {},
                highlight = {
                    Highlight(width = 4.dp, blurRadius = 0.dp, alpha = opacity.value,
                        style = HighlightStyle.Plain(Color.White, blendMode.value))
                }, shadow = null,
            ))
        }
        fun edge(): Float {
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("highlight").captureToImage().toPixelMap()
            return pixels[(pixels.width / 24).coerceAtLeast(1), pixels.height / 2].red
        }
        val faint = edge()
        compose.runOnIdle { opacity.value = .75f }
        val bright = edge()
        assertTrue(bright > faint + .25f, "Layer opacity must update even when paint is reused")
        compose.runOnIdle { opacity.value = .5f }
        val sourceOver = edge()
        compose.runOnIdle { blendMode.value = BlendMode.Plus }
        assertTrue(edge() > sourceOver + .05f, "Layer blend mode must remain live")
        compose.runOnIdle { opacity.value = 0f }
        val hidden = edge()
        compose.runOnIdle { opacity.value = 1f; blendMode.value = BlendMode.SrcOver }
        assertTrue(edge() > hidden + .5f, "A cached highlight must reappear after the zero-alpha fast path")
    }

    @Test
    fun cachedShadowStillFadesAndReappearsAfterZero() {
        val opacity = mutableStateOf(.2f)
        val blendMode = mutableStateOf(BlendMode.SrcOver)
        compose.setContent {
            Box(Modifier.size(80.dp).background(Color.White).testTag("shadow")) {
                Box(Modifier.offset(x = 16.dp, y = 16.dp).size(48.dp).drawBackdrop(
                    SolidBackdrop(Color.Red), shape = { RectangleShape }, effects = {},
                    highlight = null,
                    shadow = { Shadow(radius = 4.dp, offset = DpOffset(0.dp, 4.dp),
                        color = Color.Black, alpha = opacity.value, blendMode = blendMode.value) },
                ))
            }
        }
        fun darknessBelow(): Float {
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("shadow").captureToImage().toPixelMap()
            // The surface ends at 64/80 of the parent; sample only its outer shadow.
            return 1f - pixels[pixels.width / 2, pixels.height * 85 / 100].red
        }
        val faint = darknessBelow()
        compose.runOnIdle { opacity.value = .9f }
        assertTrue(darknessBelow() > faint + .1f, "Reusing the mask must not freeze the shadow's opacity")
        compose.runOnIdle { blendMode.value = BlendMode.Plus }
        assertTrue(darknessBelow() < .02f, "The cached shadow must still update its layer blend mode")
        compose.runOnIdle { blendMode.value = BlendMode.SrcOver }
        compose.runOnIdle { opacity.value = 0f }
        assertTrue(darknessBelow() < .02f)
        compose.runOnIdle { opacity.value = 1f }
        assertTrue(darknessBelow() > faint + .1f)
    }

    @Test
    fun customHighlightShaderContinuesReadingAnimatedState() {
        val red = mutableStateOf(1f)
        val dynamicStyle = object : HighlightStyle {
            override val color = Color.White
            override val blendMode = BlendMode.SrcOver
            override fun DrawScope.createShader(shape: Shape, runtimeShaderCache: RuntimeShaderCache): RuntimeShader =
                runtimeShaderCache.obtainRuntimeShader("animated-test-highlight", """
                    uniform float red;
                    half4 main(float2 coord) { return half4(red, 0.0, 1.0 - red, 1.0); }
                """.trimIndent()).apply { setFloatUniform("red", red.value) }
        }
        compose.setContent {
            Box(Modifier.size(48.dp).testTag("dynamic").drawBackdrop(
                SolidBackdrop(Color.Black), shape = { RectangleShape }, effects = {},
                highlight = { Highlight(width = 4.dp, blurRadius = 0.dp, style = dynamicStyle) },
                shadow = null,
            ))
        }
        fun edge(): Color {
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("dynamic").captureToImage().toPixelMap()
            return pixels[(pixels.width / 24).coerceAtLeast(1), pixels.height / 2]
        }
        val initial = edge()
        assertTrue(initial.red > .8f && initial.blue < .2f)
        compose.runOnIdle { red.value = 0f }
        val changed = edge()
        assertTrue(changed.blue > .8f && changed.red < .2f,
            "A custom style may animate uniforms without changing its object identity")
    }
}

private class SolidBackdrop(private val color: Color) : Backdrop {
    override val isCoordinatesDependent = false
    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        drawRect(color)
    }
}
