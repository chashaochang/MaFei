package cn.mafei.cmp.player.core

import cn.mafei.cmp.player.api.*
import kotlin.test.*

class PlayerStateMachineTest {
    @Test
    fun loadCarriesResumePositionToRuntime() {
        val machine = PlayerStateMachine(PlayerCapabilities())

        val load = (machine.load(listOf(resource()), 0, 12_000, true).single() as PlayerEffect.Engine)
            .command as RuntimeCommand.Load

        assertEquals(12_000, load.startPositionMs)
    }

    private fun resource(id: String = "one") = PlayableResource(id, "https://media.invalid/$id")
    private fun machine() = PlayerStateMachine(PlayerCapabilities())
    private fun ready(m: PlayerStateMachine): PlaybackGeneration {
        m.load(listOf(resource()), 0, 1200, true)
        val generation = m.state.generation!!
        m.accept(RuntimeEvent.Ready(generation, 10000), 0)
        return generation
    }

    @Test fun loadNeverPretendsReadyAndResumePrecedesAutoplay() {
        val m = machine()
        val effects = m.load(listOf(resource()), 0, 1200, true)
        assertEquals(PlayerPhase.Loading, m.state.phase)
        assertIs<PlayerEffect.Engine>(effects.single())
        val result = m.accept(RuntimeEvent.Ready(m.state.generation!!, 10000), 0)
        assertEquals(PlayerPhase.Ready, m.state.phase)
        assertIs<RuntimeCommand.Seek>((result[0] as PlayerEffect.Engine).command)
        assertIs<RuntimeCommand.Play>((result[1] as PlayerEffect.Engine).command)
        assertEquals(1200, m.state.positionMs)
    }

    @Test fun lateCallbacksCannotMutateReplacementEvenWithSameResourceId() {
        val m = machine(); val old = ready(m)
        m.load(listOf(resource()), 0, 0, false)
        val fresh = m.state
        assertTrue(m.accept(RuntimeEvent.Failed(old, PlayerFailure.Network), 1).isEmpty())
        assertEquals(fresh, m.state)
    }

    @Test fun duplicateReadyCannotSeekBackOrAutoplayAgain() {
        val m = machine(); val id = ready(m)
        assertTrue(m.accept(RuntimeEvent.Ready(id, 10000), 1).isEmpty())
    }

    @Test fun invalidQueueDoesNotDestroyCurrentPlayback() {
        val m = machine(); ready(m); val before = m.state
        assertFailsWith<IllegalArgumentException> { m.load(emptyList(), 0, 0, false) }
        assertEquals(before, m.state)
    }

    @Test fun endedMovesOnceToNextResourceAndFinalEndStaysEnded() {
        val m = machine(); m.load(listOf(resource("a"), resource("b")), 0, 0, true)
        val a = m.state.generation!!
        m.accept(RuntimeEvent.Ready(a, 5000), 0)
        m.accept(RuntimeEvent.Ended(a), 1)
        val b = m.state.generation!!
        assertEquals("b", m.state.resourceId)
        assertTrue(m.accept(RuntimeEvent.Ended(a), 2).isEmpty())
        m.accept(RuntimeEvent.Ready(b, 5000), 3)
        m.accept(RuntimeEvent.Ended(b), 4)
        assertEquals(PlayerPhase.Ended, m.state.phase)
    }

    @Test fun progressIsThrottledButTerminalReportIsNot() {
        val m = machine(); val id = ready(m)
        assertEquals(1, m.accept(RuntimeEvent.Playing(id), 100).filterIsInstance<PlayerEffect.Report>().size)
        assertTrue(m.accept(RuntimeEvent.Progress(id, 2000, 5000), 200).filterIsInstance<PlayerEffect.Report>().isEmpty())
        assertEquals(1, m.accept(RuntimeEvent.Progress(id, 3000, 6000), 10100).filterIsInstance<PlayerEffect.Report>().size)
        assertEquals(1, m.stop().filterIsInstance<PlayerEffect.Report>().size)
        assertTrue(m.stop().isEmpty())
    }

    @Test fun pauseDuringLoadingCancelsAutoplay() {
        val m = machine(); m.load(listOf(resource()), 0, 0, true); m.pause()
        val effects = m.accept(RuntimeEvent.Ready(m.state.generation!!, null), 0)
        assertTrue(effects.none { it is PlayerEffect.Engine && it.command is RuntimeCommand.Play })
    }

    @Test fun backgroundResumesOnlyPlaybackItPaused() {
        val m = machine(); val id = ready(m); m.accept(RuntimeEvent.Playing(id), 0)
        assertEquals(1, m.background().size)
        m.accept(RuntimeEvent.Paused(id), 1)
        assertIs<RuntimeCommand.Play>((m.foreground().single() as PlayerEffect.Engine).command)
        m.pause(); m.background()
        assertTrue(m.foreground().isEmpty())
    }

    @Test fun staleSurfaceDetachCannotDetachNewSurface() {
        val m = machine(); ready(m)
        val a = PlayerSurfaceHandle(); val b = PlayerSurfaceHandle()
        m.attachSurface(a); m.attachSurface(b)
        assertTrue(m.detachSurface(a).isEmpty())
        assertEquals(1, m.detachSurface(b).size)
    }

    @Test fun releaseIsTerminalAndIdempotent() {
        val m = machine(); val id = ready(m)
        assertTrue(m.release().any { it is PlayerEffect.Engine && it.command is RuntimeCommand.Release })
        assertTrue(m.release().isEmpty())
        assertTrue(m.accept(RuntimeEvent.Playing(id), 1).isEmpty())
        assertFailsWith<IllegalStateException> { m.load(listOf(resource()), 0, 0, true) }
        assertEquals(PlayerPhase.Released, m.state.phase)
    }

    @Test fun cancellationTokenCannotCancelNewLoad() {
        val m = machine(); val old = ready(m)
        m.load(listOf(resource("new")), 0, 0, false)
        assertTrue(m.cancelLoad(old).isEmpty())
        assertEquals(PlayerPhase.Loading, m.state.phase)
        m.cancelLoad(m.state.generation!!)
        assertEquals(PlayerPhase.Idle, m.state.phase)
    }

    @Test fun unsupportedCommandsAreExplicitAndDoNotMutateState() {
        val m = PlayerStateMachine(PlayerCapabilities(speed = false, trackSelection = false)); ready(m)
        val before = m.state
        assertFailsWith<UnsupportedPlayerCapability> { m.speed(2f) }
        assertFailsWith<UnsupportedPlayerCapability> { m.selectTrack(TrackKind.Audio, "x") }
        assertEquals(before, m.state)
    }

    @Test fun errorsDoNotLeakNetworkDetailsAndStopReportsOnce() {
        val m = machine(); val id = ready(m); m.accept(RuntimeEvent.Playing(id), 0)
        val effects = m.accept(RuntimeEvent.Failed(id, PlayerFailure.Network), 1)
        assertEquals(PlayerPhase.Error, m.state.phase)
        assertEquals(1, effects.filterIsInstance<PlayerEffect.Report>().size)
        assertTrue(m.accept(RuntimeEvent.Failed(id, PlayerFailure.Network), 2).isEmpty())
    }

    @Test fun resourceToStringRedactsLinksAndHeaders() {
        val resource = PlayableResource("safe", "https://media.invalid/?token=SECRET", mapOf("Authorization" to "SECRET"))
        assertFalse(resource.toString().contains("SECRET"))
    }

    @Test fun seekIsValidatedAndClampedWithoutPretendingNativePositionChanged() {
        val m = machine(); ready(m)
        val command = (m.seek(20_000).single() as PlayerEffect.Engine).command as RuntimeCommand.Seek
        assertEquals(10_000, command.positionMs)
        assertEquals(1200, m.state.positionMs)
        assertFailsWith<IllegalArgumentException> { m.seek(-1) }
    }

    @Test fun backgroundDuringLoadResumesOnReturnAndExplicitPauseOverridesIt() {
        val m = machine(); m.load(listOf(resource()), 0, 0, true)
        m.background()
        assertTrue(m.accept(RuntimeEvent.Ready(m.state.generation!!, null), 0).isEmpty())
        assertIs<RuntimeCommand.Play>((m.foreground().single() as PlayerEffect.Engine).command)
        m.background(); m.pause()
        assertTrue(m.foreground().isEmpty())
    }

    @Test fun invalidNativeProgressAndUnpreparedPlayingAreIgnored() {
        val m = machine(); m.load(listOf(resource()), 0, 0, true); val id = m.state.generation!!
        assertTrue(m.accept(RuntimeEvent.Playing(id), 0).isEmpty())
        assertEquals(PlayerPhase.Loading, m.state.phase)
        m.accept(RuntimeEvent.Ready(id, 1000), 0)
        m.accept(RuntimeEvent.Progress(id, -1, 1000), 1)
        assertEquals(0, m.state.positionMs)
        m.accept(RuntimeEvent.Progress(id, 2000, 3000), 1)
        assertEquals(1000, m.state.positionMs)
        assertEquals(1000, m.state.bufferedPositionMs)
    }

    @Test fun progressFillsDurationDiscoveredAfterReady() {
        val m = machine(); m.load(listOf(resource()), 0, 0, true); val id = m.state.generation!!
        m.accept(RuntimeEvent.Ready(id, null), 0)

        m.accept(RuntimeEvent.Progress(id, 2_000, 8_000, 6_000), 1)

        assertEquals(6_000, m.state.durationMs)
        assertEquals(2_000, m.state.positionMs)
        assertEquals(6_000, m.state.bufferedPositionMs)
    }

    @Test fun selectingKnownTracksAndDisablingSubtitlesIsExplicit() {
        val m = machine(); val id = ready(m)
        m.accept(RuntimeEvent.Tracks(id, listOf(PlayerTrack("en", TrackKind.Audio, "English"))), 0)
        assertEquals(1, m.selectTrack(TrackKind.Audio, "en").size)
        assertEquals(1, m.selectTrack(TrackKind.Subtitle, null).size)
        assertFailsWith<IllegalArgumentException> { m.selectTrack(TrackKind.Audio, null) }
        assertFailsWith<IllegalArgumentException> { m.selectTrack(TrackKind.Audio, "missing") }
    }

    @Test fun latePlayingAfterUserPauseIsCountermandedWithoutClaimingPlaying() {
        val m = machine(); val id = ready(m); m.pause()
        val effect = m.accept(RuntimeEvent.Playing(id), 0).single() as PlayerEffect.Engine
        assertIs<RuntimeCommand.Pause>(effect.command)
        assertEquals(PlayerPhase.Ready, m.state.phase)
    }

    @Test fun pausedCannotBeOverwrittenByLateBuffering() {
        val m = machine(); val id = ready(m); m.pause(); m.accept(RuntimeEvent.Paused(id), 0)
        m.accept(RuntimeEvent.Buffering(id), 1)
        assertEquals(PlayerPhase.Paused, m.state.phase)
    }

    @Test fun loadingWhileAlreadyBackgroundedPreservesAutoplayForForeground() {
        val m = machine(); m.background(); m.load(listOf(resource()), 0, 0, true)
        assertTrue(m.accept(RuntimeEvent.Ready(m.state.generation!!, null), 0).isEmpty())
        assertIs<RuntimeCommand.Play>((m.foreground().single() as PlayerEffect.Engine).command)
    }

    @Test fun explicitPlayWhileBackgroundedIsDeferredUntilForeground() {
        val m = machine(); m.load(listOf(resource()), 0, 0, false)
        m.accept(RuntimeEvent.Ready(m.state.generation!!, null), 0)
        m.background(); assertTrue(m.play().isEmpty())
        assertIs<RuntimeCommand.Play>((m.foreground().single() as PlayerEffect.Engine).command)
    }

    @Test fun backgroundAutomaticNextPreservesIntentButManualPauseCancelsIt() {
        val m = machine(); m.load(listOf(resource("a"), resource("b")), 0, 0, true)
        val a = m.state.generation!!; m.accept(RuntimeEvent.Ready(a, null), 0)
        m.background(); m.accept(RuntimeEvent.Ended(a), 1)
        m.accept(RuntimeEvent.Ready(m.state.generation!!, null), 2)
        assertIs<RuntimeCommand.Play>((m.foreground().single() as PlayerEffect.Engine).command)
        m.background(); m.play(); m.pause(); assertTrue(m.foreground().isEmpty())
    }

    @Test fun backgroundLoadDoesNotInventAutoplayWhenDisabledOrCancelled() {
        for (autoplay in listOf(false, true)) {
            val m = machine(); m.background(); m.load(listOf(resource()), 0, 0, autoplay)
            if (autoplay) m.pause()
            m.accept(RuntimeEvent.Ready(m.state.generation!!, null), 0)
            assertTrue(m.foreground().isEmpty())
        }
    }

    @Test fun pauseAndResumeConfirmationsReportImmediatelyWithoutProgressCallbacks() {
        val m = machine(); val id = ready(m); m.accept(RuntimeEvent.Playing(id), 0)
        m.pause()
        val paused = m.accept(RuntimeEvent.Paused(id), 1).filterIsInstance<PlayerEffect.Report>().single().value
        assertEquals(PlayerReportKind.Progress, paused.kind); assertTrue(paused.paused)
        assertTrue(m.accept(RuntimeEvent.Paused(id), 2).isEmpty())
        m.play()
        val resumed = m.accept(RuntimeEvent.Playing(id), 3).filterIsInstance<PlayerEffect.Report>().single().value
        assertEquals(PlayerReportKind.Progress, resumed.kind); assertFalse(resumed.paused)
        assertTrue(m.accept(RuntimeEvent.Playing(id), 4).isEmpty())
    }

    @Test fun bufferingAndProgressCannotReportUnpausedBeforeNativePlayingConfirmation() {
        val m = machine(); val id = ready(m); m.accept(RuntimeEvent.Playing(id), 0)
        m.pause(); m.accept(RuntimeEvent.Paused(id), 1); m.play()
        m.accept(RuntimeEvent.Buffering(id), 2)
        val whileWaiting = m.accept(RuntimeEvent.Progress(id, 1200, 5000), 20_000).filterIsInstance<PlayerEffect.Report>().single().value
        assertTrue(whileWaiting.paused)
        val confirmed = m.accept(RuntimeEvent.Playing(id), 20_001).filterIsInstance<PlayerEffect.Report>().single().value
        assertFalse(confirmed.paused)
    }

    @Test fun contentModeIsRefusedWhenTheSurfaceCannotApplyIt() {
        val m = machine()
        assertFailsWith<UnsupportedPlayerCapability> { m.contentMode(PlayerContentMode.Ratio16x9) }
        assertEquals(PlayerContentMode.Fit, m.state.contentMode)
    }

    @Test fun contentModeEmitsOnlyOnARealChangeAndNeverNeedsPreparation() {
        val m = PlayerStateMachine(PlayerCapabilities(contentMode = true))
        // 尚未加载任何资源时也能设：画面比例属于画面，不属于某次加载。
        val effects = m.contentMode(PlayerContentMode.Ratio21x9)
        assertEquals(
            PlayerContentMode.Ratio21x9,
            (effects.single() as PlayerEffect.Engine).command.let { (it as RuntimeCommand.ContentMode).mode },
        )
        assertEquals(PlayerContentMode.Ratio21x9, m.state.contentMode)
        assertTrue(m.contentMode(PlayerContentMode.Ratio21x9).isEmpty())
    }

    @Test fun contentModeSurvivesQueueAdvanceAndStopResetsIt() {
        val m = PlayerStateMachine(PlayerCapabilities(contentMode = true))
        m.load(listOf(resource("a"), resource("b")), 0, 0, true)
        m.contentMode(PlayerContentMode.Fill)
        val first = m.state.generation!!
        m.accept(RuntimeEvent.Ready(first, 1000), 0)
        // 播完自动接下一集：新代仍是同一个观看者的选择。
        m.accept(RuntimeEvent.Ended(first), 1)
        assertEquals(PlayerContentMode.Fill, m.state.contentMode)
        assertNotEquals(first, m.state.generation)
        m.stop()
        assertEquals(PlayerContentMode.Fit, m.state.contentMode)
    }

    @Test fun contentModeIsGenerationLessSoAnExpiredGenerationCannotSmuggleIt() {
        val m = PlayerStateMachine(PlayerCapabilities(contentMode = true))
        m.load(listOf(resource()), 0, 0, true)
        val command = (m.contentMode(PlayerContentMode.Fill).single() as PlayerEffect.Engine).command
        // 与 AttachSurface 同类：它不携带 generation，运行时不得按代过滤。
        assertIs<RuntimeCommand.ContentMode>(command)
    }
}
