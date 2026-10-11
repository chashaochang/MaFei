package cn.mafei.cmp.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.player.api.*
import kotlin.test.*

class PlayerNativeBackdropTest {
    @get:org.junit.Rule val rule = createComposeRule()

    private fun frame(color: Color): ImageBitmap = ImageBitmap(180, 120).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.drawRect(Rect(0f, 0f, 180f, 120f), Paint().apply { this.color = color })
    }

    @Test fun nativeFrameUpdatesTheMaterialWithoutReplacingTheVisibleVideo() {
        val source = PlayerVideoBackdropSource().apply { frame = frame(Color.Red) }
        rule.setContent {
            CompositionLocalProvider(LocalPlayerVideoBackdropSource provides source) { MaFeiTheme {
                Box(Modifier.requiredSize(900.dp, 600.dp)) {
                    NowPlayingScreen(NowPlayingMedia("v", "影片", ""), PlayerState(phase = PlayerPhase.Paused),
                        capabilities = PlayerCapabilities(contentMode = true), fullscreen = true,
                        onBack = {}, onTogglePlay = {}, onRetry = {}, onSeek = {}, onSpeed = {},
                        onSelectTrack = { _, _ -> }, videoSurface = { Box(it.background(Color.Black)) })
                }
            } }
        }
        rule.waitForIdle()
        assertTrue(source.sampling)
        val red = rule.onNodeWithContentDescription("更多设置").captureToImage().toPixelMap()
        rule.runOnIdle { source.frame = frame(Color.Green) }
        val green = rule.onNodeWithContentDescription("更多设置").captureToImage().toPixelMap()
        // Sample the empty centre-left of the round button, not its white glyph or highlight edge.
        val x = red.width / 4
        val y = red.height / 2
        assertTrue(red[x, y].red > red[x, y].green + .2f)
        assertTrue(green[x, y].green > green[x, y].red + .2f)
        val visibleVideo = rule.onRoot().captureToImage().toPixelMap()
        assertEquals(Color.Black, visibleVideo[80, 200], "The sample must never be painted over the native video")
    }
}
