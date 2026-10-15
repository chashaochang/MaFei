package cn.mafei.cmp.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassDecorationRecordingTest {
    @get:org.junit.Rule
    val compose = createComposeRule()

    @Test
    fun reusedDecorationMatchesFreshLayersAndDoesNotFreezeBackdrop() {
        val source = mutableStateOf(Color.Red)
        val alpha = mutableStateOf(1f)
        val extent = mutableStateOf(64.dp)
        val radius = mutableStateOf(4.dp)
        val tint = mutableStateOf(Color.Black)
        val highlightStyle = mutableStateOf<HighlightStyle>(HighlightStyle.Default())
        val generation = mutableStateOf(0)
        compose.setContent {
            Row {
                Sample("reused", { source.value }, { alpha.value }, extent.value, radius.value, tint.value, highlightStyle.value)
                key(generation.value) {
                    Sample("fresh", { source.value }, { alpha.value }, extent.value, radius.value, tint.value, highlightStyle.value)
                }
            }
        }
        fun assertPixelsMatch() {
            compose.waitForIdle()
            val cached = compose.onNodeWithTag("reused").captureToImage().toPixelMap()
            val fresh = compose.onNodeWithTag("fresh").captureToImage().toPixelMap()
            assertEquals(cached.width, fresh.width)
            assertEquals(cached.height, fresh.height)
            for (y in 0 until cached.height step 2) {
                for (x in 0 until cached.width step 2) {
                    val a = cached[x, y]
                    val b = fresh[x, y]
                    assertTrue(abs(a.red - b.red) < .01f && abs(a.green - b.green) < .01f &&
                        abs(a.blue - b.blue) < .01f && abs(a.alpha - b.alpha) < .01f,
                        "Cached decoration differs from fresh at $x,$y: $a / $b")
                }
            }
            val center = cached[cached.width / 2, cached.height / 2]
            assertEquals(source.value, center, "Backdrop center must remain live and unmasked")
        }
        assertPixelsMatch()
        compose.runOnIdle { source.value = Color.Cyan; generation.value++ }
        assertPixelsMatch()
        compose.runOnIdle { alpha.value = 0f; generation.value++ }
        assertPixelsMatch()
        compose.runOnIdle { alpha.value = .6f; generation.value++ }
        assertPixelsMatch()
        compose.runOnIdle { extent.value = 80.dp; radius.value = 6.dp; tint.value = Color.White; generation.value++ }
        assertPixelsMatch()
        compose.runOnIdle { highlightStyle.value = HighlightStyle.Default(angle = 100f); generation.value++ }
        assertPixelsMatch()
        compose.runOnIdle { highlightStyle.value = HighlightStyle.Ambient(); generation.value++ }
        assertPixelsMatch()
        compose.runOnIdle { highlightStyle.value = HighlightStyle.Plain(); generation.value++ }
        assertPixelsMatch()
    }

    @Composable
    private fun Sample(tag: String, source: () -> Color, alpha: () -> Float, extent: Dp, radius: Dp, tint: Color, style: HighlightStyle) {
        val backdrop = rememberLayerBackdrop()
        Box(Modifier.testTag(tag).padding(16.dp)) {
            Box(Modifier.size(extent).layerBackdrop(backdrop).drawBehind { drawRect(source()) })
            Box(Modifier.size(extent).drawBackdrop(
                backdrop, shape = { RoundedCornerShape(12.dp) }, effects = {},
                highlight = { Highlight(alpha = alpha(), style = style) },
                shadow = { Shadow(radius, DpOffset(0.dp, 2.dp), tint.copy(alpha = .2f), alpha()) },
                innerShadow = { InnerShadow(radius, DpOffset(0.dp, 2.dp), tint.copy(alpha = .2f), alpha()) },
            ))
        }
    }
}
