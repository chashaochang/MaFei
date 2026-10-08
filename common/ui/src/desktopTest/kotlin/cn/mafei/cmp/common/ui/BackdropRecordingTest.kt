package cn.mafei.cmp.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.kyant.backdrop.effects.blur
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackdropRecordingTest {
    @get:org.junit.Rule
    val compose = createComposeRule()

    @Test
    fun exportedMaterialRecordsItsSourceOncePerDraw() {
        val tint = mutableStateOf(Color.Red)
        var recordings = 0
        compose.setContent {
            val backdrop = rememberLayerBackdrop()
            val exported = rememberLayerBackdrop()
            Box(Modifier.size(48.dp)) {
                Box(Modifier.size(48.dp).layerBackdrop(backdrop))
                Box(Modifier.size(48.dp).drawPlainBackdrop(
                    backdrop, shape = { RectangleShape }, effects = {},
                    exportedBackdrop = exported,
                    onDrawBackdrop = { drawBackdrop ->
                        recordings++
                        drawBackdrop()
                    },
                    onDrawSurface = { drawRect(tint.value) },
                ))
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            recordings = 0
            tint.value = Color.Blue
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, recordings) }
    }

    @Test
    fun exportedMaterialKeepsLiveSourceAndOverlayOrderWithoutChildContent() {
        val color = mutableStateOf(Color.Red)
        compose.setContent {
            val backdrop = rememberLayerBackdrop()
            val exported = rememberLayerBackdrop()
            Box(Modifier.size(96.dp, 48.dp)) {
                Box(Modifier.size(48.dp).layerBackdrop(backdrop).drawBehind { drawRect(color.value) })
                Box(Modifier.size(48.dp).testTag("material").drawPlainBackdrop(
                    backdrop, shape = { RectangleShape }, effects = {},
                    exportedBackdrop = exported,
                    onDrawBehind = { drawRect(Color.Black) },
                    onDrawSurface = { drawRect(Color.Blue, size = Size(size.width / 2, size.height)) },
                    onDrawFront = { drawRect(Color.Yellow, size = Size(size.width / 4, size.height / 4)) },
                ).drawBehind {
                    drawRect(Color.Green, Offset(0f, size.height / 2), Size(size.width, size.height / 2))
                })
                // Put the consumer elsewhere on screen, but sample the export at its origin.
                Box(Modifier.offset(48.dp).size(48.dp).testTag("export").drawPlainBackdrop(
                    exported, shape = { RectangleShape }, effects = {},
                    onDrawBackdrop = { drawBackdrop ->
                        drawContext.canvas.translate(48.dp.toPx(), 0f)
                        drawBackdrop()
                        drawContext.canvas.translate(-48.dp.toPx(), 0f)
                    },
                ))
            }
        }
        compose.waitForIdle()
        fun assertMaterial(source: Color) {
            val visible = compose.onNodeWithTag("material").captureToImage().toPixelMap()
            val sample = compose.onNodeWithTag("export").captureToImage().toPixelMap()
            assertEquals(Color.Green, visible[visible.width * 3 / 4, visible.height * 3 / 4])
            assertEquals(source, sample[sample.width * 3 / 4, sample.height * 3 / 4])
            assertEquals(Color.Blue, sample[sample.width / 4, sample.height * 3 / 4])
            assertEquals(Color.Yellow, sample[2, 2])
        }
        assertMaterial(Color.Red)
        compose.runOnIdle { color.value = Color.Cyan }
        compose.waitForIdle()
        assertMaterial(Color.Cyan)
    }

    @Test
    fun exportMatchesVisibleBlurAfterSizeAndEffectChanges() {
        val extent = mutableStateOf(48.dp)
        val radius = mutableStateOf(6.dp)
        compose.setContent {
            val backdrop = rememberLayerBackdrop()
            val exported = rememberLayerBackdrop()
            val width = extent.value
            Box(Modifier.size(width * 2, width)) {
                Box(Modifier.size(width).layerBackdrop(backdrop).drawBehind {
                    drawRect(Color.Red)
                    drawRect(Color.Blue, size = Size(size.width / 2, size.height))
                })
                Box(Modifier.size(width).testTag("blurred").drawPlainBackdrop(
                    backdrop, shape = { RectangleShape }, effects = { blur(radius.value.toPx()) },
                    exportedBackdrop = exported,
                ))
                Box(Modifier.offset(width).size(width).testTag("blur-export").drawPlainBackdrop(
                    exported, shape = { RectangleShape }, effects = {},
                    onDrawBackdrop = { drawBackdrop ->
                        drawContext.canvas.translate(width.toPx(), 0f)
                        drawBackdrop()
                        drawContext.canvas.translate(-width.toPx(), 0f)
                    },
                ))
            }
        }
        fun assertSamePixels() {
            compose.waitForIdle()
            val visible = compose.onNodeWithTag("blurred").captureToImage().toPixelMap()
            val sample = compose.onNodeWithTag("blur-export").captureToImage().toPixelMap()
            assertEquals(visible.width, sample.width)
            for (y in 0 until visible.height step 3) {
                for (x in 0 until visible.width step 3) {
                    assertEquals(visible[x, y], sample[x, y], "Export differs at $x,$y")
                }
            }
            val middle = sample[sample.width / 2, sample.height / 2]
            assertTrue(middle.red > .2f && middle.blue > .2f, "The export must retain the blur effect")
        }
        assertSamePixels()
        compose.runOnIdle { extent.value = 64.dp; radius.value = 10.dp }
        assertSamePixels()
    }

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
