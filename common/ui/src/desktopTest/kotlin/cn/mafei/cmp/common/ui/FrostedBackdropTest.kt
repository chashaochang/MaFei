package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRenderingEnvironment
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassRenderHealth
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class FrostedBackdropTest {
    @get:org.junit.Rule val compose = createComposeRule()

    @Test fun blurSoftensEdgesAndSamplesLiveArtworkInBothThemes() = verify(latchFailure = false)

    @Test fun failedBlurBecomesOpaqueWithoutMovingTheSurface() = verify(latchFailure = true)

    private fun verify(latchFailure: Boolean) {
        val dark = mutableStateOf(true)
        val artwork = mutableStateOf(Color.Red)
        lateinit var fail: () -> Unit
        compose.setContent {
            val tint = if (dark.value) Color.Black else Color.White
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                val health = rememberGlassRenderHealth()
                fail = health::reportEffectFailure
                GlassRenderingEnvironment(health) {
                    val backdrop = rememberGlassBackdrop()
                    Box(Modifier.size(160.dp)) {
                        Canvas(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
                            drawRect(Color.Blue)
                            drawRect(artwork.value, Offset.Zero, Size(size.width / 2, size.height))
                        }
                        GlassSurface(backdrop, GlassSurfaceSpec(GlassRole.Navigation, GlassLevel.Frosted, tintAlpha = .42f),
                            tint, Modifier.align(Alignment.Center).size(112.dp).testTag("panel")) {}
                    }
                }
            }
        }
        val panel = compose.onNodeWithTag("panel")
        if (latchFailure) {
            val bounds = panel.fetchSemanticsNode().boundsInRoot
            compose.runOnIdle { fail() }
            for (isDark in listOf(true, false)) {
                compose.runOnIdle { dark.value = isDark }
                val pixels = panel.captureToImage().toPixelMap()
                val center = pixels[pixels.width / 2, pixels.height / 2]
                val endpoint = if (isDark) 0f else 1f
                assertTrue(listOf(center.red, center.green, center.blue).all { abs(it - endpoint) < .02f }, "$center")
                assertTrue(bounds == panel.fetchSemanticsNode().boundsInRoot)
            }
        } else {
            for (isDark in listOf(true, false)) {
                compose.runOnIdle { dark.value = isDark; artwork.value = Color.Red }
                val before = panel.captureToImage().toPixelMap()
                val x = before.width / 2
                val y = before.height / 2
                val edge = before[x, y]
                val right = before[before.width * 4 / 5, y]
                assertTrue(edge.red > right.red + .12f && edge.blue < right.blue - .12f,
                    "Gaussian blur should mix red into the blue side: $edge vs $right")
                compose.runOnIdle { artwork.value = Color.Green }
                val after = panel.captureToImage().toPixelMap()[x, y]
                assertTrue(edge.red > after.red + .12f && after.green > edge.green + .12f,
                    "The blurred sample should follow the changing artwork: $edge -> $after")
            }
        }
    }
}
