package cn.mafei.cmp.player.runtime

import cn.mafei.cmp.player.api.PlayableResource
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerSurfaceHandle
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.mfplayer.cmp.CommandResult
import org.mfplayer.cmp.PlayerCommandId
import org.mfplayer.cmp.PlayerContentMode
import org.mfplayer.cmp.PlayerEngine
import org.mfplayer.cmp.PlayerEvent
import org.mfplayer.cmp.PlayerExternalSubtitle
import org.mfplayer.cmp.PlayerOptions
import org.mfplayer.cmp.PlayerScreenshotRequest
import org.mfplayer.cmp.PlayerSnapshot
import org.mfplayer.cmp.PlayerSource
import org.mfplayer.cmp.ScreenshotOutcome
import org.mfplayer.cmp.PlayerCapabilities as SdkCapabilities

@OptIn(ExperimentalCoroutinesApi::class)
class MfPlayerRuntimeTest {
    @kotlin.test.Test
    fun mapsEarlyReadyAndDoesNotReportRetiredEvents() = runTest {
        val engine = Engine()
        val received = mutableListOf<RuntimeEvent>()
        val runtime = MfPlayerRuntime(PlayerCapabilities(), { engine }, hostDispatcher = StandardTestDispatcher(testScheduler))
        runtime.bind(received::add)
        val first = PlaybackGeneration()
        val second = PlaybackGeneration()
        val stream = PlayableResource("item", "https://media.invalid/movie",
            headers = mapOf("X-Test" to "value"), allowInsecureTls = true)
        runtime.execute(RuntimeCommand.Load(first, stream, 15))
        runCurrent()
        val old = engine.snapshot.value.generation
        runtime.execute(RuntimeCommand.Load(second, stream, 30))
        runtime.execute(RuntimeCommand.Play(first))
        runCurrent()
        engine.post(PlayerEvent.Ended(old))
        engine.post(PlayerEvent.Progress(engine.snapshot.value.generation, 5_000, 6_000, 10_000))
        runtime.execute(RuntimeCommand.Play(second))
        runCurrent()
        kotlin.test.assertEquals(listOf(first, second), received.filterIsInstance<RuntimeEvent.Ready>().map { it.generation })
        kotlin.test.assertEquals(RuntimeEvent.Progress(second, 5_000, 6_000, 10_000), received.last())
        kotlin.test.assertEquals(1, engine.playCalls)
        kotlin.test.assertEquals(stream.headers, engine.source?.headers)
        kotlin.test.assertTrue(engine.source?.allowInsecureTls == true)
        kotlin.test.assertEquals(30L, engine.options?.startPositionMs)
        kotlin.test.assertFalse(engine.options?.playWhenReady ?: true)
        runtime.execute(RuntimeCommand.Release)
        runCurrent()
    }

    @kotlin.test.Test
    fun detachesOnlyOwnedSurfaceAndReleasesOnce() = runTest {
        val calls = mutableListOf<String>()
        val engine = Engine { calls += "release" }
        val first = PlayerSurfaceHandle()
        val second = PlayerSurfaceHandle()
        val runtime = MfPlayerRuntime(
            PlayerCapabilities(),
            { engine },
            surface = { _, handle, attach -> calls += "${if (attach) "attach" else "detach"}:${if (handle === first) 1 else 2}" },
            hostDispatcher = StandardTestDispatcher(testScheduler),
        )
        runtime.bind {}
        runtime.execute(RuntimeCommand.AttachSurface(first))
        runtime.execute(RuntimeCommand.Load(PlaybackGeneration(), PlayableResource("id", "file:///fixture.mp4")))
        runtime.execute(RuntimeCommand.AttachSurface(second))
        runtime.execute(RuntimeCommand.DetachSurface(first))
        runtime.execute(RuntimeCommand.Release)
        runtime.execute(RuntimeCommand.Release)
        runCurrent()
        kotlin.test.assertEquals(listOf("attach:1", "detach:1", "attach:2", "detach:2", "release"), calls)
    }

    @kotlin.test.Test
    fun confirmedPausedLoadCanBeReadyWithoutDuplicateReady() = runTest {
        val engine = Engine(loadPaused = true)
        val received = mutableListOf<RuntimeEvent>()
        val current = PlaybackGeneration()
        val runtime = MfPlayerRuntime(PlayerCapabilities(), { engine }, pausedLoadIsReady = true,
            hostDispatcher = StandardTestDispatcher(testScheduler))
        runtime.bind(received::add)
        runtime.execute(RuntimeCommand.Load(current, PlayableResource("id", "fixture.mp4")))
        runCurrent()
        engine.post(PlayerEvent.Ready(engine.snapshot.value.generation, 10_000))
        runCurrent()
        kotlin.test.assertEquals(listOf(RuntimeEvent.Ready(current, 10_000), RuntimeEvent.Paused(current)), received.drop(1))
        runtime.execute(RuntimeCommand.Release)
        runCurrent()
    }

    @kotlin.test.Test
    fun sidecarIsNotGivenToNativeEngineWhenAppOverlayOwnsIt() = runTest {
        val engine = Engine()
        val runtime = MfPlayerRuntime(PlayerCapabilities(), { engine }, nativeExternalSubtitles = false,
            hostDispatcher = StandardTestDispatcher(testScheduler))
        val resource = PlayableResource("id", "https://media.invalid/movie", subtitleUrl = "https://media.invalid/en.srt")
        runtime.execute(RuntimeCommand.Load(PlaybackGeneration(), resource))
        runCurrent()
        kotlin.test.assertTrue(checkNotNull(engine.source).externalSubtitles.isEmpty())
        kotlin.test.assertEquals(listOf(PlayerExternalSubtitle(resource.subtitleUrl!!, isDefault = true)),
            resource.toSdkSource().externalSubtitles)
        runtime.execute(RuntimeCommand.Release)
        runCurrent()
    }

    @kotlin.test.Test
    fun androidRc1WaitsForMediaAcrossDecoderRebuildAndDropsRetiredEvents() = runTest {
        val engine = Engine()
        val received = mutableListOf<RuntimeEvent>()
        val first = PlaybackGeneration()
        val second = PlaybackGeneration()
        val resource = PlayableResource("id", "https://media.invalid/movie")
        val runtime = MfPlayerRuntime(PlayerCapabilities(), { engine }, androidRc1LoadCompatibility = true,
            hostDispatcher = StandardTestDispatcher(testScheduler))
        runtime.bind(received::add)
        runtime.execute(RuntimeCommand.Load(first, resource))
        runCurrent()
        kotlin.test.assertTrue(received.none { it is RuntimeEvent.Ready })
        val beforeRebuild = engine.snapshot.value.generation
        val rebuilt = beforeRebuild.next()
        engine.snapshot.value = engine.snapshot.value.copy(generation = rebuilt, durationMs = null)
        engine.post(PlayerEvent.Paused(beforeRebuild))
        engine.post(PlayerEvent.Ready(rebuilt, null))
        engine.post(PlayerEvent.Paused(rebuilt))
        runCurrent()
        runtime.execute(RuntimeCommand.Play(first))
        runtime.execute(RuntimeCommand.Load(second, PlayableResource("next", "https://media.invalid/next")))
        runCurrent()
        val next = engine.snapshot.value.generation
        engine.post(PlayerEvent.Ended(rebuilt))
        engine.post(PlayerEvent.Paused(next))
        engine.post(PlayerEvent.Ready(next, 10_000))
        runCurrent()
        kotlin.test.assertEquals(
            listOf(RuntimeEvent.Ready(first, null), RuntimeEvent.Ready(second, 10_000)),
            received.filterIsInstance<RuntimeEvent.Ready>(),
        )
        kotlin.test.assertEquals(listOf(RuntimeEvent.Paused(first), RuntimeEvent.Paused(second)),
            received.filterIsInstance<RuntimeEvent.Paused>())
        kotlin.test.assertTrue(received.none { it is RuntimeEvent.Ended })
        kotlin.test.assertEquals(1, engine.playCalls)
        runtime.execute(RuntimeCommand.Release)
        runCurrent()
    }

    private class Engine(private val loadPaused: Boolean = false, private val onRelease: () -> Unit = {}) : PlayerEngine {
        private val channel = Channel<PlayerEvent>(Channel.UNLIMITED)
        override val events = channel.receiveAsFlow()
        override val snapshot = MutableStateFlow(PlayerSnapshot.Empty)
        override val capabilities = SdkCapabilities(
            true, true, true, false, true, false, false, false, false, true, true, false, false,
        )
        var source: PlayerSource? = null
        var options: PlayerOptions? = null
        var playCalls = 0
        private val accepted = CommandResult.Accepted(PlayerCommandId(1))

        fun post(event: PlayerEvent) { check(channel.trySend(event).isSuccess) }
        override fun load(source: PlayerSource, options: PlayerOptions): CommandResult {
            this.source = source
            this.options = options
            val generation = snapshot.value.generation.next()
            snapshot.value = snapshot.value.copy(generation = generation, durationMs = 10_000)
            post(if (loadPaused) PlayerEvent.Paused(generation) else PlayerEvent.Ready(generation, 10_000))
            return accepted
        }
        override fun play(): CommandResult { playCalls++; return accepted }
        override fun pause() = accepted
        override fun stop() = accepted
        override fun seekTo(positionMs: Long, exact: Boolean) = accepted
        override fun setPlaybackSpeed(speed: Double) = accepted
        override fun setContentMode(mode: PlayerContentMode) = accepted
        override fun selectAudioTrack(id: String) = accepted
        override fun selectSubtitleTrack(id: String?) = accepted
        override fun addExternalSubtitle(subtitle: PlayerExternalSubtitle) = accepted
        override fun seekToChapter(index: Int) = accepted
        override fun takeScreenshot(request: PlayerScreenshotRequest) = emptyFlow<ScreenshotOutcome>()
        override fun onHostBackground() = accepted
        override fun onHostForeground() = accepted
        override fun release() { channel.close(); onRelease() }
    }
}
