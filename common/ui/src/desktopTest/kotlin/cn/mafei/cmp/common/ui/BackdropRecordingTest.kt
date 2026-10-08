package cn.mafei.cmp.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackdropRecordingTest {
    @get:org.junit.Rule
    val compose = createComposeRule()

    @Test
    fun glassConsumerSamplesUpdatedRecordingAcrossAnOpaqueSibling() {
        val color = mutableStateOf(Color.Red)
        compose.setContent {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.size(48.dp).testTag("sample")) {
                Box(Modifier.size(48.dp).layerBackdrop(backdrop).drawBehind { drawRect(color.value) })
                Box(Modifier.size(48.dp).drawBehind { drawRect(Color.Black) })
                Box(Modifier.size(48.dp).drawPlainBackdrop(backdrop, shape = { RectangleShape }, effects = {}))
            }
        }
        compose.waitForIdle()
        assertEquals(Color.Red, compose.onNodeWithTag("sample").captureToImage().toPixelMap()[12, 12])
        compose.runOnIdle { color.value = Color.Blue }
        compose.waitForIdle()
        assertEquals(Color.Blue, compose.onNodeWithTag("sample").captureToImage().toPixelMap()[12, 12])
    }

    @Test
    fun customRecordingStillDiffersFromVisibleContent() {
        compose.setContent {
            val backdrop = rememberLayerBackdrop(onDraw = { drawRect(Color.Green) })
            Box(Modifier.size(48.dp)) {
                Box(Modifier.size(48.dp).testTag("original").layerBackdrop(backdrop)
                    .drawBehind { drawRect(Color.Red) })
                Box(Modifier.size(24.dp).testTag("custom").drawPlainBackdrop(
                    backdrop, shape = { RectangleShape }, effects = {},
                ))
            }
        }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("original").captureToImage().toPixelMap()
        assertEquals(Color.Red, pixels[pixels.width - 2, pixels.height - 2])
        assertEquals(Color.Green, compose.onNodeWithTag("custom").captureToImage().toPixelMap()[6, 6])
    }

    @Test
    fun nestedRecordersDrawContentOnceAndKeepLiveUpdates() {
        val color = mutableStateOf(Color.Red)
        var draws = 0
        compose.setContent {
            val outer = rememberGlassBackdrop()
            val middle = rememberGlassBackdrop()
            val inner = rememberGlassBackdrop()
            Box(Modifier.size(48.dp).testTag("scene")) {
                Box(Modifier.size(48.dp).captureGlassBackdrop(outer)) {
                    Box(Modifier.size(48.dp).captureGlassBackdrop(middle)) {
                        Box(Modifier.size(48.dp).captureGlassBackdrop(inner).drawBehind {
                            draws++
                            drawRect(color.value)
                        })
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            draws = 0
            color.value = Color.Blue
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, draws, "One invalidated frame must record content once, even with nested captures")
        }
        val pixel = compose.onNodeWithTag("scene").captureToImage().toPixelMap()[12, 12]
        assertTrue(pixel.blue > .95f && pixel.red < .05f, "The recorded frame must remain visible and current")
    }
}
