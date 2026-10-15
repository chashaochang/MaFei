package cn.mafei.cmp.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.MaFeiInsetsDp
import cn.mafei.cmp.common.design_system.MaFeiWindowFeatures
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.glass.GlassStyleScope
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
        tracks: List<PlayerTrack> = emptyList(),
        videoColor: Color = Color(0xFF35556B),
        style: GlassStyle = GlassStyle.Liquid,
    ) {
        rule.setContent { MaFeiTheme {
            CompositionLocalProvider(LocalMaFeiWindowFeatures provides features) {
                GlassStyleScope(style) {
                Box(Modifier.requiredSize(width.dp, height.dp).testTag("playerTestViewport")) {
                    NowPlayingScreen(NowPlayingMedia("v", "影片", ""), playerState?.value ?: PlayerState(phase = PlayerPhase.Paused),
                        capabilities = PlayerCapabilities(contentMode = true, speed = true),
                        onBack = {}, onTogglePlay = {}, onRetry = {}, onSeek = {}, onSpeed = {},
                        onSelectTrack = { _, _ -> }, fullscreen = fullscreen,
                        tracks = tracks, onFullscreenRequest = {},
                        onPictureInPicture = {}, playbackFacts = PlayerPlaybackFacts("影片"),
                        videoSurface = { Box(it.background(videoColor)) })
                }
                }
            }
        } }
    }

    @Test fun tabletMoreMenuOpensBelowItsButtonInsteadOfFillingTheScreenEdge() {
        show(900, 600, true)
        val button = rule.onNodeWithContentDescription("更多设置").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithContentDescription("更多设置").performClick()
        rule.mainClock.advanceTimeBy(1000)
        val panel = rule.onNodeWithTag("playerMenuPanel").fetchSemanticsNode().boundsInRoot
        assertEquals(296f, panel.width, .5f)
        assertTrue(panel.height < 200f, "A two-action menu should fit its content")
        assertEquals(button.bottom + 8f, panel.top, 1f)
        rule.onNodeWithText("画面比例 · 适应").assertExists()
    }

    @Test fun tabletSpeedMenuOpensAboveTheBottomSpeedControlWithReadableType() {
        show(900, 600, true)
        val button = rule.onNodeWithText("倍速").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithText("倍速").performClick()
        rule.mainClock.advanceTimeBy(1000)
        val panel = rule.onNodeWithTag("playerMenuPanel").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.bottom <= button.top - 7f, "Speed options must open beside their bottom control")
        assertTrue(button.top - panel.bottom < 32f, "The menu must not be detached at the top right")
        rule.onNodeWithText("倍速").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("1.5x").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().layoutInput.style.fontSize.value >= 16f)
    }

    @Test fun tabletMenuRemainsDistinctOnAnEntirelyBlackVideo() {
        show(900, 600, true, videoColor = Color.Black)
        rule.onNodeWithContentDescription("更多设置").performClick()
        rule.mainClock.advanceTimeBy(1000)
        val pixels = rule.onNodeWithTag("playerMenuPanel").captureToImage().toPixelMap()
        assertTrue(pixels[8, pixels.height / 2].luminance() > .015f,
            "A tablet menu needs a visible surface even when blur samples pure black")
    }

    @Test fun tabletTranslucentMenuIsAlsoVisibleAgainstBlack() {
        show(900, 600, true, videoColor = Color.Black, style = GlassStyle.Translucent)
        rule.onNodeWithContentDescription("更多设置").performClick()
        rule.mainClock.advanceTimeBy(1000)
        val pixels = rule.onNodeWithTag("playerMenuPanel").captureToImage().toPixelMap()
        assertTrue(pixels[8, pixels.height / 2].luminance() > .015f)
    }

    @Test fun phoneLandscapeKeepsTheExistingEdgeMenuInsteadOfATabletPopover() {
        show(900, 400, true)
        rule.onNodeWithText("倍速").performClick()
        rule.mainClock.advanceTimeBy(1000)
        val panel = rule.onNodeWithTag("playerMenuPanel").fetchSemanticsNode().boundsInRoot
        assertEquals(138f, panel.width, .5f)
        assertEquals(400f, panel.height, .5f)
    }

    @Test fun tabletAspectRatioSubmenuKeepsTheMoreButtonAnchor() {
        show(900, 600, true)
        val button = rule.onNodeWithContentDescription("更多设置").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithContentDescription("更多设置").performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("画面比例 · 适应").performClick()
        rule.mainClock.advanceTimeBy(1000)
        val panel = rule.onNodeWithTag("playerMenuPanel").fetchSemanticsNode().boundsInRoot
        assertEquals(button.bottom + 8f, panel.top, 1f)
        rule.onNodeWithText("原始比例").assertIsDisplayed()
    }

    @Test fun phoneMoreMenuRetainsTheLegacy84PercentWidth() {
        show(360, 780, false)
        rule.onNodeWithContentDescription("更多设置").performClick()
        val panel = rule.onNodeWithTag("playerMenuPanel").fetchSemanticsNode().boundsInRoot
        assertEquals(360f * .84f, panel.width, 1f)
        assertEquals(780f, panel.height, .5f)
    }

    @Test fun portraitTabletKeepsTrackAndSpeedControlsInsideTheSafeAreaWithoutRequiringFullscreen() {
        show(600, 740, false,
            features = MaFeiWindowFeatures(safeInsets = MaFeiInsetsDp(top = 32f, bottom = 24f)),
            tracks = listOf(
                PlayerTrack("a1", TrackKind.Audio, "国语", selected = true),
                PlayerTrack("a2", TrackKind.Audio, "英语"),
                PlayerTrack("s1", TrackKind.Subtitle, "简体中文"),
            ),
        )
        val viewport = rule.onNodeWithTag("playerTestViewport").fetchSemanticsNode().boundsInRoot
        for (label in listOf("音轨", "字幕", "倍速")) {
            val control = rule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            assertTrue(control.left >= viewport.left && control.right <= viewport.right)
            assertTrue(control.bottom <= viewport.bottom - 24f, "$label overlaps the tablet navigation safe area")
        }
        rule.onNodeWithContentDescription("横屏").assertExists()
        rule.onNodeWithContentDescription("更多设置").performClick()
        rule.mainClock.advanceTimeBy(1000)
        val panel = rule.onNodeWithTag("playerMenuPanel").fetchSemanticsNode().boundsInRoot
        assertEquals(296f, panel.width, .5f, "Tablet portrait must use the anchored menu")
        val firstOption = rule.onNodeWithText("画面比例 · 适应").fetchSemanticsNode().boundsInRoot
        assertTrue(firstOption.top >= viewport.top + 32f)
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
