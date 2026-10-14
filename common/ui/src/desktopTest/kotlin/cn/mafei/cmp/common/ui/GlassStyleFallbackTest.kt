package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.glass.GlassStyleScope
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class GlassStyleFallbackTest {
    @get:org.junit.Rule val compose = createComposeRule()

    @Test fun uncoloredButtonsRemainVisibleInBothFallbackStylesAndThemes() = verifyButton(Color.Unspecified)

    @Test fun explicitlyTransparentButtonsAlsoReceiveFallbackMaterial() = verifyButton(Color.Transparent)

    @Test fun translucentPanelsDoNotClampTheirSurfaceBelowReadableOpacity() {
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = Color.Black)) {
                GlassStyleScope(GlassStyle.Translucent) {
                    val backdrop = rememberGlassBackdrop()
                    Box(Modifier.size(80.dp).background(Color.Magenta)) {
                        GlassSurface(backdrop, GlassSurfaceSpec(GlassRole.Navigation, GlassLevel.Full, tintAlpha = .12f),
                            Color.Black, Modifier.size(64.dp).testTag("panel")) {}
                    }
                }
            }
        }
        val center = sample("panel")
        assertTrue(center.red <= .45f && center.blue <= .45f,
            "Translucent panel must retain a visible material over artwork: $center")
    }

    private fun verifyButton(surface: Color) {
        val style = mutableStateOf(GlassStyle.Translucent)
        val dark = mutableStateOf(true)
        val source = mutableStateOf(Color.Magenta)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme(surface = Color.Black)
                else lightColorScheme(surface = Color.White)) {
                GlassStyleScope(style.value) {
                    val backdrop = rememberGlassBackdrop()
                    Box(Modifier.size(120.dp, 80.dp)) {
                        Box(Modifier.size(120.dp, 80.dp).captureGlassBackdrop(backdrop).background(source.value))
                        LiquidButton({}, backdrop, Modifier.size(64.dp, 48.dp).testTag("button"),
                            surfaceColor = surface, horizontalPadding = 0.dp) {}
                    }
                }
            }
        }
        for (material in listOf(GlassStyle.Translucent, GlassStyle.GaussianBlur)) {
            for (isDark in listOf(true, false)) {
                compose.runOnIdle { style.value = material; dark.value = isDark }
                val before = sample("button")
                val contrast = if (isDark) 1f - before.red else before.green
                assertTrue(contrast >= .35f, "$material/$isDark button must have a material, not bare artwork: $before")
                compose.runOnIdle { source.value = Color.Cyan }
                val after = sample("button")
                assertTrue(abs(before.red - after.red) > .15f,
                    "Fallback must remain transparent/live, not replace the button with an opaque surface")
                compose.runOnIdle { source.value = Color.Magenta }
            }
        }
    }

    private fun sample(tag: String): Color {
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }
}
