package cn.mafei.cmp.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.player.api.*
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class PlayerWindowControllerTest {
    @get:org.junit.Rule val rule = createComposeRule()
    private val fullscreen = mutableStateOf(false)
    private val requests = mutableListOf<Boolean>()
    private var toggles = 0
    private var backs = 0
    private var seek = -1L

    private fun show(width: Int = 1000, height: Int = 640, windowed: Boolean = true,
        dark: Boolean = true, phase: PlayerPhase = PlayerPhase.Paused,
        volume: PlayerDeviceControls = PlayerDeviceControls.Unsupported,
        features: MaFeiWindowFeatures = MaFeiWindowFeatures(),
        size: MutableState<Pair<Int, Int>>? = null) {
        rule.setContent {
            MaFeiTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalMaFeiWindowFeatures provides features.copy(
                    windowedHost = windowed, windowFullscreen = fullscreen.value,
                )) {
                    NowPlayingScreen(
                        media = NowPlayingMedia("movie", "测试影片", ""),
                        state = PlayerState(phase = phase, durationMs = 120_000, positionMs = 30_000),
                        tracks = listOf(PlayerTrack("a1", TrackKind.Audio, "国语"),
                            PlayerTrack("a2", TrackKind.Audio, "英语"),
                            PlayerTrack("s1", TrackKind.Subtitle, "简体中文")),
                        capabilities = PlayerCapabilities(contentMode = true),
                        fullscreen = fullscreen.value,
                        onFullscreenRequest = { requests += it; fullscreen.value = it },
                        onBack = { backs++ }, onTogglePlay = { toggles++ }, onRetry = {},
                        onSeek = { seek = it }, onSpeed = {}, onSelectTrack = { _, _ -> },
                        deviceControls = volume, onPrevious = {}, onNext = {},
                        videoSurface = { Box(it.background(Color(0xFF35556B)).testTag("video")) },
                        modifier = Modifier.requiredSize((size?.value?.first ?: width).dp,
                            (size?.value?.second ?: height).dp).testTag("viewport"),
                    )
                }
            }
        }
    }

    @Test fun systemAndWindowFullscreenAreIndependentAndInTheRequestedOrder() {
        show()
        val window = rule.onNodeWithContentDescription("窗口内全屏").fetchSemanticsNode().boundsInRoot
        val system = rule.onNodeWithContentDescription("系统全屏").fetchSemanticsNode().boundsInRoot
        assertTrue(window.right <= system.left)
        rule.onNodeWithTag("playerWindowHeader").assertExists()
        rule.onNodeWithContentDescription("窗口内全屏").performClick()
        rule.onNodeWithTag("playerWindowHeader").assertDoesNotExist()
        assertTrue(requests.isEmpty(), "Window fill must not call the system host")
        rule.onNodeWithContentDescription("系统全屏").performClick()
        assertEquals(listOf(true), requests)
        rule.onNodeWithContentDescription("退出系统全屏").assertExists()
    }

    @Test fun escapeUnwindsSystemThenWindowFillThenThePlayer() {
        show()
        rule.onNodeWithContentDescription("窗口内全屏").performClick()
        rule.onNodeWithContentDescription("系统全屏").performClick()
        rule.onNodeWithTag("video").performKeyInput { pressKey(Key.Escape) }
        rule.runOnIdle { assertFalse(fullscreen.value) }
        rule.onNodeWithTag("viewport").performKeyInput { pressKey(Key.Escape) }
        rule.onNodeWithTag("playerWindowHeader").assertExists()
        assertEquals(0, backs)
        rule.onNodeWithTag("viewport").performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, backs)
    }

    @Test fun freeformTabletUsesWindowControlsEvenInANarrowPortraitWindow() {
        show(500, 720)
        val viewport = rule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        for (label in listOf("窗口内全屏", "系统全屏", "上一集", "下一集")) {
            val bounds = rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= viewport.left && bounds.right <= viewport.right, label)
        }
        for (label in listOf("音轨", "字幕", "倍速")) rule.onNodeWithText(label).assertIsDisplayed()
        rule.onNodeWithContentDescription("横屏").assertDoesNotExist()
    }

    @Test fun normalTabletAndPhoneKeepTheirExistingOrientationControls() {
        show(600, 740, windowed = false)
        rule.onNodeWithContentDescription("横屏").assertExists()
        rule.onNodeWithContentDescription("窗口内全屏").assertDoesNotExist()
        rule.onNodeWithTag("playerWindowController").assertDoesNotExist()
    }

    @Test fun keyboardControlsAreAvailableWithoutAMouseOrADeviceName() {
        show()
        rule.onNodeWithTag("playerInputSurface").assertIsFocused()
        rule.onNodeWithTag("viewport").performKeyInput { pressKey(Key.Spacebar) }
        assertEquals(1, toggles)
        rule.onNodeWithTag("viewport").performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(40_000L, seek)
        rule.onNodeWithTag("viewport").performKeyInput { pressKey(Key.F) }
        assertEquals(listOf(true), requests)
        rule.onNodeWithTag("viewport").performKeyInput { pressKey(Key.W) }
        assertEquals(listOf(true, false), requests)
        rule.onNodeWithContentDescription("退出窗口内全屏").assertExists()
    }

    @Test fun doubleClickOnTheVideoTogglesSystemFullscreenButTouchDoubleTapTogglesPlayback() {
        show()
        rule.onNodeWithTag("video").performMouseInput { doubleClick(center) }
        assertEquals(listOf(true), requests)
        assertEquals(0, toggles)
        rule.onNodeWithTag("video").performMouseInput { doubleClick(center) }
        assertEquals(listOf(true, false), requests)
        rule.onNodeWithTag("video").performTouchInput { doubleClick(center) }
        assertEquals(1, toggles)
    }

    @Test fun pointerHoverKeepsTheControlBarVisibleDuringPlayback() {
        show(phase = PlayerPhase.Playing)
        rule.onNodeWithContentDescription("系统全屏").performMouseInput { enter(center) }
        rule.mainClock.advanceTimeBy(6_000)
        rule.onNodeWithContentDescription("系统全屏").assertIsDisplayed()
        rule.onNodeWithTag("video").performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(6_000)
        rule.onNodeWithContentDescription("系统全屏").assertDoesNotExist()
    }

    @Test fun windowHeaderMoreMenuIsAnchoredInsideTheVideoViewport() {
        show()
        rule.onNodeWithContentDescription("更多设置").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        val menu = rule.onNodeWithTag("playerMenuPanel").fetchSemanticsNode().boundsInRoot
        val video = rule.onNodeWithTag("video").fetchSemanticsNode().boundsInRoot
        assertTrue(menu.top >= video.top)
        assertTrue(menu.right <= video.right)
    }

    @Test fun supportedVolumeIsARealSliderAndUnsupportedControlsStayAbsent() {
        val controls = object : PlayerDeviceControls {
            var steps = 8
            override fun brightness(): Float? = null
            override fun setBrightness(value: Float) = Unit
            override fun volume(): Int = steps
            override fun maxVolume(): Int = 16
            override fun setVolume(steps: Int) { this.steps = steps }
        }
        show(volume = controls)
        rule.onNodeWithContentDescription("音量").performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        assertEquals(12, controls.steps)
        rule.onNodeWithContentDescription("静音").performClick()
        assertEquals(0, controls.steps)
        rule.onNodeWithContentDescription("取消静音").performClick()
        assertEquals(12, controls.steps)
    }

    @Test fun keyboardSeekingOnTheFocusedSliderDoesNotAlsoTriggerTheSceneShortcut() {
        show()
        rule.onNodeWithContentDescription("播放进度").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        rule.onNodeWithContentDescription("播放进度").performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(35_000L, seek)
    }

    @Test fun menuEscapeDoesNotExitTheSystemFullscreenOrActivateTheFocusedOption() {
        show()
        rule.onNodeWithContentDescription("系统全屏").performClick()
        rule.onNodeWithContentDescription("更多设置").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("viewport").performKeyInput { pressKey(Key.Escape) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("playerMenuPanel").assertDoesNotExist()
        assertTrue(fullscreen.value)
        assertEquals(0, backs)
    }

    @Test fun windowResizePreservesWindowFillAndUsesTheNarrowControllerArrangement() {
        val size = mutableStateOf(1000 to 640)
        show(size = size)
        rule.onNodeWithContentDescription("窗口内全屏").performClick()
        rule.runOnIdle { size.value = 500 to 720 }
        rule.onNodeWithTag("playerWindowHeader").assertDoesNotExist()
        rule.onNodeWithContentDescription("退出窗口内全屏").assertIsDisplayed()
        rule.onNodeWithText("音轨").assertIsDisplayed()
        assertTrue(requests.isEmpty())
    }

    @Test fun narrowFreeformNativeTitleButtonsStayAboveBothNormalAndWindowFillChrome() {
        show(360, 720, features = MaFeiWindowFeatures(windowControlTopInsetDp = 40f, windowControlEndInsetDp = 140f))
        val viewport = rule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        val back = rule.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
        assertTrue(back.top >= viewport.top + 40f)
        val more = rule.onNodeWithContentDescription("更多设置").fetchSemanticsNode().boundsInRoot
        assertTrue(more.right <= viewport.right)
        rule.onNodeWithContentDescription("窗口内全屏").performClick()
        val fillBack = rule.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
        assertTrue(fillBack.top >= viewport.top + 40f)
    }

    @Test fun windowDarkPreview() = preview(true)
    @Test fun windowLightPreview() = preview(false)

    private fun preview(dark: Boolean) {
        show(dark = dark)
        val directory = File("/private/tmp/mafei-player-window-preview").apply { mkdirs() }
        val prefix = if (dark) "window-dark" else "window-light"
        fun save(suffix: String) = ImageIO.write(rule.onNodeWithTag("viewport").captureToImage().toAwtImage(),
            "png", File(directory, "$prefix-$suffix.png"))
        save("normal")
        rule.onNodeWithContentDescription("窗口内全屏").performClick()
        save("fill")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("更多设置").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(96)
        save("pressed")
        rule.onNodeWithContentDescription("更多设置").performTouchInput { up() }
        rule.mainClock.autoAdvance = true
    }
}
