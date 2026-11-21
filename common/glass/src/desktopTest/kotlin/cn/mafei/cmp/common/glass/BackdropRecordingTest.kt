package cn.mafei.cmp.common.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackdropRecordingTest {
    @get:org.junit.Rule val compose = createComposeRule()

    @Test
    fun nestedRecordersDrawContentOnceAndKeepLiveUpdates() {
        val color = mutableStateOf(Color.Red)
        var draws = 0
        compose.setContent {
            val outer = rememberGlassBackdrop()
            val middle = rememberGlassBackdrop()
            val inner = rememberGlassBackdrop()
            Box(Modifier.size(48.dp).testTag("scene").captureGlassBackdrop(outer)) {
                Box(Modifier.size(48.dp).captureGlassBackdrop(middle)) {
                    Box(Modifier.size(48.dp).captureGlassBackdrop(inner).drawBehind {
                        draws++
                        drawRect(color.value)
                    })
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            draws = 0
            color.value = Color.Blue
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, draws) }
        assertEquals(Color.Blue, center("scene"))
    }

    @Test
    fun exportedMaterialDoesNotCaptureForegroundAndTracksSourceUpdates() {
        val color = mutableStateOf(Color.Red)
        compose.setContent {
            val source = rememberBackdropCapture()
            val export = rememberBackdropCapture()
            Box(Modifier.size(256.dp, 128.dp)) {
                Box(Modifier.size(128.dp).captureBackdrop(source).drawBehind { drawRect(color.value) })
                Box(Modifier.size(128.dp).testTag("foreground").drawFrostedBackdrop(
                    source, shape = { RectangleShape }, exportedBackdrop = export, onDrawSurface = {},
                ).drawBehind { drawRect(Color.Green) })
                Box(Modifier.offset(128.dp).size(128.dp).testTag("export").drawFrostedBackdrop(
                    export, shape = { RectangleShape },
                    onDrawBackdrop = { sample -> translate(128.dp.toPx(), 0f) { sample() } },
                    onDrawSurface = {},
                ))
            }
        }
        assertEquals(Color.Green, center("foreground"))
        val before = center("export")
        assertTrue(before.red > .8f && before.green < .2f)
        compose.runOnIdle { color.value = Color.Blue }
        val after = center("export")
        assertTrue(after.blue > .8f && after.red < .2f)
    }

    @Test
    fun unrecordedBackdropUsesOpaqueTintWithoutHidingContent() {
        compose.setContent {
            val source = rememberBackdropCapture()
            Box(Modifier.size(96.dp).testTag("fallback").drawFrostedBackdrop(
                source, shape = { RectangleShape }, fallbackTint = Color.Red, onDrawSurface = {},
            ).drawBehind { drawCircle(Color.Green, radius = 6.dp.toPx()) })
        }
        val pixels = compose.onNodeWithTag("fallback").captureToImage().toPixelMap()
        assertEquals(Color.Red, pixels[4, 4])
        assertEquals(Color.Green, pixels[pixels.width / 2, pixels.height / 2])
    }

    private fun center(tag: String): Color {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }
}
