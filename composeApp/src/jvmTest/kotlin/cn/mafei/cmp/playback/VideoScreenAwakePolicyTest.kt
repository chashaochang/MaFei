package cn.mafei.cmp

import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoScreenAwakePolicyTest {
    @Test fun localVideoStaysAwakeWhilePreparingPlayingOrBuffering() {
        for (phase in listOf(PlayerPhase.Loading, PlayerPhase.Ready, PlayerPhase.Playing, PlayerPhase.Buffering)) {
            assertTrue(PlayerState(phase = phase).keepVideoScreenAwake(), phase.name)
        }
    }

    @Test fun pauseEndFailureAndReleaseAllowAutomaticScreenOff() {
        for (phase in listOf(PlayerPhase.Idle, PlayerPhase.Paused, PlayerPhase.Ended, PlayerPhase.Error, PlayerPhase.Released)) {
            assertFalse(PlayerState(phase = phase).keepVideoScreenAwake(), phase.name)
        }
    }

    @Test fun castingAndSystemPictureInPictureDoNotHoldTheApplicationWindowAwake() {
        for (phase in PlayerPhase.entries) {
            assertFalse(PlayerState(phase = phase, casting = true).keepVideoScreenAwake(), phase.name)
            assertFalse(PlayerState(phase = phase, pictureInPicture = true).keepVideoScreenAwake(), phase.name)
        }
    }

    @Test fun progressTrackAndFullscreenIndependentUpdatesDoNotDropTheRequest() {
        val playing = PlayerState(phase = PlayerPhase.Playing)
        for (position in 0L..100L) {
            assertTrue(playing.copy(positionMs = position * 1_000, subtitleText = "$position").keepVideoScreenAwake())
        }
        assertTrue(playing.copy(phase = PlayerPhase.Buffering).keepVideoScreenAwake())
        assertTrue(playing.copy(phase = PlayerPhase.Playing).keepVideoScreenAwake())
    }
}
