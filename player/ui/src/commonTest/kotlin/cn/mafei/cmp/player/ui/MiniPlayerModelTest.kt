package cn.mafei.cmp.player.ui

import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiniPlayerModelTest {
    @Test
    fun idleAndReleasedStatesStayHidden() {
        assertFalse(PlayerState().toMiniPlayerPresentation().isVisible)
        assertFalse(PlayerState(phase = PlayerPhase.Released).toMiniPlayerPresentation().isVisible)
    }

    @Test
    fun playbackProgressIsBoundedAndFormatted() {
        val presentation = PlayerState(
            phase = PlayerPhase.Playing,
            positionMs = 125_000,
            durationMs = 100_000,
        ).toMiniPlayerPresentation()

        assertTrue(presentation.isVisible)
        assertTrue(presentation.isPlaying)
        assertEquals(1f, presentation.progress)
        assertEquals("2:05 / 1:40", presentation.status)
    }

    @Test
    fun pausedStateKeepsPositionContext() {
        val presentation = PlayerState(
            phase = PlayerPhase.Paused,
            positionMs = 3_661_000,
            durationMs = 7_200_000,
        ).toMiniPlayerPresentation()

        assertFalse(presentation.isPlaying)
        assertEquals("已暂停 · 1:01:01 / 2:00:00", presentation.status)
    }
}
