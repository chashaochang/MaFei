package cn.mafei.cmp.player.ui

import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerFailure
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingModelTest {
    @Test
    fun playingStateProducesClampedProgressAndReadableTime() {
        val presentation = PlayerState(
            phase = PlayerPhase.Playing,
            positionMs = 3_725_000,
            bufferedPositionMs = 4_000_000,
            durationMs = 3_800_000,
        ).toNowPlayingPresentation(PlayerCapabilities())

        assertTrue(presentation.isPlaying)
        assertFalse(presentation.isBusy)
        assertTrue(presentation.canSeek)
        assertEquals("1:02:05", presentation.elapsed)
        assertEquals("1:03:20", presentation.duration)
        assertEquals(1f, presentation.bufferedProgress)
    }

    @Test
    fun loadingWithoutDurationCannotSeek() {
        val presentation = PlayerState(
            phase = PlayerPhase.Loading,
            positionMs = 8_000,
        ).toNowPlayingPresentation(PlayerCapabilities(seek = true))

        assertTrue(presentation.isBusy)
        assertFalse(presentation.canSeek)
        assertEquals(0f, presentation.progress)
        assertEquals("--:--", presentation.duration)
    }

    @Test
    fun errorExposesRetryWithoutPretendingPlayback() {
        val presentation = PlayerState(
            phase = PlayerPhase.Error,
            durationMs = 10_000,
            failure = PlayerFailure.Network,
        ).toNowPlayingPresentation(PlayerCapabilities())

        assertTrue(presentation.showRetry)
        assertFalse(presentation.isPlaying)
        assertEquals("播放出现问题", presentation.status)
    }

    @Test
    fun layoutSwitchesForWideAndLandscapeForms() {
        assertFalse(useWideNowPlayingLayout(430f, 860f))
        assertTrue(useWideNowPlayingLayout(720f, 1_000f))
        assertTrue(useWideNowPlayingLayout(600f, 960f))
        assertFalse(useWideNowPlayingLayout(599f, 960f))
        assertTrue(useWideNowPlayingLayout(700f, 400f))
        assertTrue(useWideNowPlayingLayout(1_180f, 780f))
    }
}
