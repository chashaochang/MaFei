package cn.mafei.cmp.player.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** 返回优先级：旧版 PlayerPage.onBackPress 的三层（菜单 → 全屏 → 退出播放页）。 */
class PlayerBackLadderTest {

    @Test fun windowFillIsUnwoundAfterSystemFullscreenAndBeforeLeavingThePlayer() {
        assertEquals(PlayerBackStep.ExitFullscreen, playerBackStep(false, false, true, true, true))
        assertEquals(PlayerBackStep.ExitWindowFullscreen, playerBackStep(false, false, false, true, true))
        assertEquals(PlayerBackStep.ClosePanel, playerBackStep(true, false, true, true, true))
    }

    @Test
    fun panelWinsOverEverythingElse() {
        assertEquals(
            PlayerBackStep.ClosePanel,
            playerBackStep(panelOpen = true, playbackInfoVisible = true, fullscreen = true, fullscreenToggleAvailable = true),
        )
    }

    @Test
    fun playbackInfoCardIsTheSecondLayer() {
        // 旧版把播放信息卡也算「菜单可见」，所以它夹在面板与全屏之间。
        assertEquals(
            PlayerBackStep.ClosePlaybackInfo,
            playerBackStep(panelOpen = false, playbackInfoVisible = true, fullscreen = true, fullscreenToggleAvailable = true),
        )
    }

    @Test
    fun fullscreenIsTheThirdLayerAndLeavesFullscreen() {
        assertEquals(
            PlayerBackStep.ExitFullscreen,
            playerBackStep(panelOpen = false, playbackInfoVisible = false, fullscreen = true, fullscreenToggleAvailable = true),
        )
    }

    @Test
    fun windowedPlaybackLeavesThePlayer() {
        assertEquals(
            PlayerBackStep.LeavePlayer,
            playerBackStep(panelOpen = false, playbackInfoVisible = false, fullscreen = false, fullscreenToggleAvailable = true),
        )
    }

    @Test
    fun aHostWithoutFullscreenSupportGoesStraightToLeaving() {
        // 没有全屏能力时不能停在一个什么都不做的一步上。
        assertEquals(
            PlayerBackStep.LeavePlayer,
            playerBackStep(panelOpen = false, playbackInfoVisible = false, fullscreen = true, fullscreenToggleAvailable = false),
        )
    }
}
