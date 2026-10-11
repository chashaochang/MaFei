package cn.mafei.cmp.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.player.api.*
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/** Render the shipping controls offscreen; never capture or activate the user's desktop. */
class PlayerMaterialPreviewTest {
    @get:org.junit.Rule val rule = createComposeRule()
    @Test fun phoneDark() = preview(360, 780, true)
    @Test fun phoneLight() = preview(360, 780, false)
    @Test fun wideDark() = preview(960, 640, true)
    @Test fun wideLight() = preview(960, 640, false)

    private fun preview(width: Int, height: Int, dark: Boolean) {
        val frame = ImageBitmap(width, height)
        val canvas = Canvas(frame)
        val colors = listOf(Color(0xFF254656), Color(0xFFDBB67B), Color(0xFF386875), Color(0xFF3D2B37))
        repeat(16) { index ->
            canvas.drawRect(Rect(width * index / 16f, 0f, width * (index + 1) / 16f, height.toFloat()),
                Paint().apply { color = colors[index % colors.size] })
        }
        val source = PlayerVideoBackdropSource().apply { this.frame = frame }
        rule.setContent { MaFeiTheme(darkTheme = dark) {
            CompositionLocalProvider(LocalPlayerVideoBackdropSource provides source) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    NowPlayingScreen(NowPlayingMedia("fixture", "影片", ""),
                        PlayerState(phase = PlayerPhase.Paused, positionMs = 45_000, durationMs = 120_000),
                        capabilities = PlayerCapabilities(contentMode = true, speed = true),
                        fullscreen = width > height, onBack = {}, onTogglePlay = {}, onRetry = {},
                        onSeek = {}, onSpeed = {}, onSelectTrack = { _, _ -> }, onCasting = {}, onPictureInPicture = {},
                        videoSurface = { Image(bitmap = frame, contentDescription = null, modifier = it, contentScale = ContentScale.FillBounds) })
                }
            }
        } }
        val prefix = "${if (width > height) "wide" else "phone"}-${if (dark) "dark" else "light"}"
        save("$prefix-idle")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("更多设置").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(96)
        save("$prefix-pressed")
        rule.onNodeWithContentDescription("更多设置").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("playerMenuPanel").assertExists()
        save("$prefix-menu")
        rule.mainClock.autoAdvance = true
    }

    private fun save(name: String) {
        val directory = File("/tmp/mafei-player-ui-preview").apply { mkdirs() }
        ImageIO.write(rule.onRoot().captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }
}
