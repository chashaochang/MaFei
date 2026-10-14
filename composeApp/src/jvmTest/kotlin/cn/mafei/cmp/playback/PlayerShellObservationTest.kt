package cn.mafei.cmp

import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class PlayerShellObservationTest {
    @Test
    fun progressBufferAndSubtitleUpdatesDoNotInvalidateBrowseChrome() =
        runBlocking<Unit> {
            val playing = PlayerState(phase = PlayerPhase.Playing, resourceId = "one")
            val ticks = (0L..100L).map { tick -> playing.copy(positionMs = tick * 250, bufferedPositionMs = tick * 500, subtitleText = "caption $tick") }
            val observed = ticks.asFlow().shellStates().toList()
            assertEquals(listOf(playing.toPlayerShellState()), observed)
        }

    @Test
    fun phaseAndResourceTransitionsStillReachTheShellInOrder() =
        runBlocking<Unit> {
            val initial = PlayerState(phase = PlayerPhase.Playing, resourceId = "one")
            val states =
                listOf(
                    initial,
                    initial.copy(positionMs = 1000),
                    initial.copy(phase = PlayerPhase.Ended),
                    initial.copy(phase = PlayerPhase.Loading, resourceId = "two"),
                    initial.copy(resourceId = "two"),
                )
            assertEquals(listOf(states[0], states[2], states[3], states[4]).map { it.toPlayerShellState() }, states.asFlow().shellStates().toList())
        }
}
