package cn.mafei.cmp.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.MaFeiInsetsDp
import cn.mafei.cmp.common.design_system.MaFeiWindowFeatures
import cn.mafei.cmp.player.api.*
import kotlin.test.*

class PlayerMenuLayoutTest {
    @get:org.junit.Rule val rule = createComposeRule()
    private fun show(
        width: Int,
        height: Int,
        fullscreen: Boolean,
        features: MaFeiWindowFeatures = MaFeiWindowFeatures(),
        playerState: MutableState<PlayerState>? = null,
    ) {
        rule.setContent { MaFeiTheme {
            CompositionLocalProvider(LocalMaFeiWindowFeatures provides features) {
                Box(Modifier.requiredSize(width.dp, height.dp).testTag("playerTestViewport")) {
                    NowPlayingScreen(NowPlayingMedia("v", "影片", ""), playerState?.value ?: PlayerState(phase = PlayerPhase.Paused),
                        capabilities = PlayerCapabilities(contentMode = true, speed = true),
                        onBack = {}, onTogglePlay = {}, onRetry = {}, onSeek = {}, onSpeed = {},
                        onSelectTrack = { _, _ -> }, fullscreen = fullscreen,
                        onPictureInPicture = {}, playbackFacts = PlayerPlaybackFacts("影片"),
                        videoSurface = { Box(it.background(Color(0xFF35556B))) })
                }
            }
        } }
    }

    @Test fun wideMoreMenuRetainsLegacyEdgeGeometryWithoutLargeFloatingCardInsets() {
        show(900, 600, true)
        rule.onNodeWithContentDescription("更多设置").performClick()
        val panel = rule.onNodeWithTag("playerMenuPanel").fetchSemanticsNode().boundsInRoot
        assertEquals(270f, panel.width, .5f)
        assertEquals(600f, panel.height, .5f)
        assertEquals(900f, panel.right, .5f)
        rule.onNodeWithText("画面比例 · 适应").assertExists()
    }

    @Test fun phoneMoreMenuRetainsTheLegacy84PercentWidth() {
        show(360, 780, false)
        rule.onNodeWithContentDescription("更多设置").performClick()
        val panel = rule.onNodeWithTag("playerMenuPanel").fetchSemanticsNode().boundsInRoot
        assertEquals(360f * .84f, panel.width, 1f)
        assertEquals(780f, panel.height, .5f)
    }

    @Test fun phoneMenuContentAvoidsTheStatusBar() {
        show(360, 780, false, MaFeiWindowFeatures(safeInsets = MaFeiInsetsDp(top = 32f, bottom = 24f)))
        rule.onNodeWithContentDescription("更多设置").performClick()
        rule.mainClock.advanceTimeBy(1000)
        val firstOption = rule.onNodeWithText("画面比例 · 适应").fetchSemanticsNode().boundsInRoot
        val viewport = rule.onNodeWithTag("playerTestViewport").fetchSemanticsNode().boundsInRoot
        assertTrue(firstOption.top - viewport.top >= 40f, "Menu content starts in the status bar: $firstOption")
        rule.onNodeWithText("画面比例 · 适应").performClick()
        rule.mainClock.advanceTimeBy(1000)
        val ratio = rule.onNodeWithText("原始比例").fetchSemanticsNode().boundsInRoot
        assertTrue(ratio.top - viewport.top >= 40f, "The ratio submenu must use the same safe area")
    }

    @Test fun wideMenuContentAvoidsWindowButtonsAndSystemBars() {
        show(900, 600, true, MaFeiWindowFeatures(
            safeInsets = MaFeiInsetsDp(top = 12f, bottom = 16f),
            windowedHost = true,
            windowControlTopInsetDp = 40f,
        ))
        rule.onNodeWithContentDescription("更多设置").performClick()
        rule.mainClock.advanceTimeBy(1000)
        val firstOption = rule.onNodeWithText("画面比例 · 适应").fetchSemanticsNode().boundsInRoot
        val viewport = rule.onNodeWithTag("playerTestViewport").fetchSemanticsNode().boundsInRoot
        assertTrue(firstOption.top - viewport.top >= 70f, "Menu content overlaps the native window controls: $firstOption")
    }

    @Test fun phonePlaybackInfoUsesTheSameSafeAreaAsTheMenu() {
        show(360, 780, false, MaFeiWindowFeatures(safeInsets = MaFeiInsetsDp(top = 48f, bottom = 24f)))
        rule.onNodeWithContentDescription("更多设置").performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("播放信息").performClick()
        rule.mainClock.advanceTimeBy(1500)
        val title = rule.onNodeWithText("视频信息").fetchSemanticsNode().boundsInRoot
        val viewport = rule.onNodeWithTag("playerTestViewport").fetchSemanticsNode().boundsInRoot
        assertTrue(title.top - viewport.top >= 48f, "Playback info overlaps the status bar: $title")
    }

    @Test fun pictureInPictureOnlyKeepsVideoAndDoesNotRestoreAnOldMenu() {
        val state = mutableStateOf(PlayerState(phase = PlayerPhase.Paused))
        show(360, 780, false, playerState = state)
        rule.onNodeWithContentDescription("更多设置").performClick()
        rule.onNodeWithTag("playerMenuPanel").assertExists()
        rule.runOnIdle { state.value = state.value.copy(pictureInPicture = true) }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithTag("playerMenuPanel").assertDoesNotExist()
        rule.onNodeWithContentDescription("更多设置").assertDoesNotExist()
        rule.onNodeWithContentDescription("画中画").assertDoesNotExist()
        rule.onNodeWithContentDescription("播放").assertDoesNotExist()
        rule.runOnIdle { state.value = state.value.copy(pictureInPicture = false) }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithContentDescription("更多设置").assertExists()
        rule.onNodeWithContentDescription("画中画").assertExists()
        rule.onNodeWithTag("playerMenuPanel").assertDoesNotExist()
    }
}
