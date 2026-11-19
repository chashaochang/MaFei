package cn.mafei.cmp.session

import cn.mafei.cmp.ShortDramaPlatform
import cn.mafei.cmp.ShortDramaRequest
import cn.mafei.cmp.ShortDramaSession
import cn.mafei.cmp.shortDramaMode
import cn.mafei.cmp.supportsShortDrama
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackEventKind
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

private class StubAdapter : MediaProviderAdapter {
    val reports = mutableListOf<Pair<PlaybackPreparation.Ready, PlaybackEvent>>()
    val released = mutableListOf<PlaybackPreparation.Ready>()
    var failReports = false
    var beforePrepare: suspend (MediaId) -> Unit = {}

    override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation {
        beforePrepare(item)
        return PlaybackPreparation.Ready(
            streamUrl = "https://stream.test/session-a.m3u8",
            headers = mapOf("Authorization" to "tok"),
            reportingContext = "{\"item\":\"${item.value}\"}",
        )
    }
    override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) {
        if (failReports) throw cn.mafei.cmp.core.domain.ClientConfigError("engine gone")
        reports += stream to event
    }
    override suspend fun releasePlayback(stream: PlaybackPreparation.Ready) {
        released += stream
    }

    override val provider = ProviderKind.Jellyfin
    override val serverId = cn.mafei.cmp.core.domain.ServerId("srv")
    override val accountId = cn.mafei.cmp.core.domain.AccountId("acc")
    override val capabilities = AdapterCapabilities(playbackReporting = true)
    override suspend fun collections(request: PageRequest) = throw ClientConfigError("unused")
    override suspend fun items(collectionId: cn.mafei.cmp.core.domain.CollectionId?, request: PageRequest) = throw ClientConfigError("unused")
    override suspend fun search(term: String, request: PageRequest) = throw ClientConfigError("unused")
    override suspend fun item(id: MediaId) = throw ClientConfigError("unused")
    override suspend fun seasons(seriesId: MediaId): Page<cn.mafei.cmp.core.domain.MediaItem> = throw ClientConfigError("unused")
    override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest) = throw ClientConfigError("unused")
    override suspend fun setFavorite(id: MediaId, favorite: Boolean) = throw ClientConfigError("unused")
    override suspend fun setWatched(id: MediaId, watched: Boolean) = throw ClientConfigError("unused")
}

class ShortDramaSessionTest {
    private val adapter = StubAdapter()
    private val episodes = List(5) { index ->
        MediaItem(MediaId("episode-$index"), adapter.serverId, adapter.provider, "Episode $index", MediaType.Episode)
    }

    @Test
    fun keepsAdjacentGenerationsAndOnlyStartsTheSettledEpisode() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val session = buildSession(scope)
        try {
            session.select(episodes, 1)
            val previous = engine(session, 0)
            val current = engine(session, 1)
            val next = engine(session, 2)
            assertNull(session.slot(episodes[3]))
            previous.emitReady(previous.generation())
            next.emitReady(next.generation())
            current.emitReady(current.generation())
            current.emitPlaying(current.generation())
            assertFalse(previous.commands.any { it is RuntimeCommand.Play })
            assertFalse(next.commands.any { it is RuntimeCommand.Play })
            assertEquals(1, adapter.reports.count { it.second.kind == PlaybackEventKind.Start })

            session.select(episodes, 2)
            assertSame(next, engine(session, 2))
            assertEquals(1, next.commands.count { it is RuntimeCommand.Load })
            assertTrue(next.commands.last() is RuntimeCommand.Play)
            assertTrue(current.commands.last() is RuntimeCommand.Pause)
            assertTrue(previous.commands.last() is RuntimeCommand.Release)
            assertEquals("{\"item\":\"episode-0\"}", adapter.released.single().reportingContext)
            assertNull(session.slot(episodes[0]))
            assertTrue(engine(session, 3).commands.single() is RuntimeCommand.Load)
            session.close()
            assertTrue(next.commands.last() is RuntimeCommand.Release)
        } finally {
            session.close()
            scope.cancel()
        }
    }

    @Test
    fun slowPreparationDoesNotStartAnEpisodeThatIsNoLongerSelected() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val delayed = CompletableDeferred<Unit>()
        adapter.beforePrepare = { if (it == episodes[1].id) delayed.await() }
        val session = buildSession(scope)
        try {
            session.select(episodes, 1)
            val slow = engine(session, 1)
            session.select(episodes, 2)
            delayed.complete(Unit)
            slow.emitReady(slow.generation())
            assertFalse(slow.commands.any { it is RuntimeCommand.Play })
            session.select(episodes, 1)
            assertTrue(slow.commands.last() is RuntimeCommand.Play)
        } finally {
            session.close()
            scope.cancel()
        }
    }

    @Test
    fun backgroundPreparesWithoutStartingAndForegroundResumesOnlyTheActiveSlot() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val session = buildSession(scope)
        try {
            session.setForeground(false)
            session.select(episodes, 0)
            val current = engine(session, 0)
            val next = engine(session, 1)
            current.emitReady(current.generation())
            next.emitReady(next.generation())
            assertFalse(current.commands.any { it is RuntimeCommand.Play })
            session.setForeground(true)
            assertTrue(current.commands.last() is RuntimeCommand.Play)
            assertFalse(next.commands.any { it is RuntimeCommand.Play })
        } finally {
            session.close()
            scope.cancel()
        }
    }

    @Test
    fun canLeaveAnEndedEpisodeWithoutPausingItsTerminalGeneration() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val session = buildSession(scope)
        try {
            session.select(episodes, 0)
            val current = engine(session, 0)
            val next = engine(session, 1)
            current.emitReady(current.generation())
            current.emitPlaying(current.generation())
            next.emitReady(next.generation())
            current.emitEnded(current.generation())
            session.select(episodes, 1)
            assertTrue(next.commands.last() is RuntimeCommand.Play)
            assertFalse(current.commands.any { it is RuntimeCommand.Pause })
        } finally {
            session.close()
            scope.cancel()
        }
    }

    @Test
    fun normalVideoDoesNotRouteToShortDrama() {
        assertTrue(episodes.first().supportsShortDrama)
        assertTrue(episodes.first().copy(mediaType = MediaType.Movie).supportsShortDrama)
        assertFalse(episodes.first().copy(mediaType = MediaType.Audio).supportsShortDrama)
        assertFalse(shortDramaMode(null, null, "影视"))
    }

    @Test
    fun detailsKeepsTheSamePlaybackGenerationAndRestoresIntent() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val session = buildSession(scope)
        try {
            session.select(episodes, 1)
            val current = engine(session, 1)
            val generation = current.generation()
            current.emitReady(generation)
            current.emitPlaying(generation)
            session.openDetails()
            assertTrue(current.commands.last() is RuntimeCommand.Pause)
            current.emitPaused(generation)
            session.closeDetails(play = false)
            assertTrue(current.commands.last() is RuntimeCommand.Play)
            current.emitPlaying(generation)
            session.toggle()
            current.emitPaused(generation)
            session.openDetails()
            val pausedCount = current.commands.size
            session.closeDetails(play = false)
            assertEquals(pausedCount, current.commands.size)
            session.openDetails()
            session.closeDetails(play = true)
            assertTrue(current.commands.last() is RuntimeCommand.Play)
            assertSame(generation, current.generation())
        } finally {
            session.close()
            scope.cancel()
        }
    }

    private fun buildSession(scope: CoroutineScope): ShortDramaSession = ShortDramaSession(
        ShortDramaRequest(episodes[1], PlaybackOptions()),
        scope,
        ShortDramaPlatform({ CountingRuntime() }, { _, _ -> }),
    ) { runtime -> SessionPlaybackRuntime.create(adapter.provider, adapter, runtime, scope, { 0L }) }

    private fun engine(session: ShortDramaSession, index: Int): CountingRuntime =
        checkNotNull(session.slot(episodes[index])).runtime as CountingRuntime

    private fun CountingRuntime.generation() = commands.filterIsInstance<RuntimeCommand.Load>().single().generation
}

class SessionPlaybackRuntimeTest {

    @Test
    fun acceptedStopReachesTheSessionCallback() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val adapter = StubAdapter()
        val engine = CountingRuntime()
        var stoppedReports = 0
        val playback = SessionPlaybackRuntime.create(
            provider = ProviderKind.Jellyfin,
            adapter = adapter,
            runtime = engine,
            scope = scope,
            nowMs = { 0L },
            onStopReported = {
                assertEquals(PlaybackEventKind.Stop, adapter.reports.last().second.kind)
                stoppedReports++
            },
        )
        try {
            val generation = checkNotNull(playback.play(MediaId("item-9")))
            engine.emitReady(generation)
            engine.emitPlaying(generation)
            assertEquals(0, stoppedReports)
            playback.stop()
            assertEquals(1, stoppedReports)
        } finally {
            playback.release()
            scope.cancel()
        }
    }

    @Test
    fun sessionPlaybackBindsAdapterAndReportsThroughIt() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val adapter = StubAdapter()
        val runtime = CountingRuntime()
        val sessionPlayback = SessionPlaybackRuntime.create(
            provider = ProviderKind.Jellyfin,
            adapter = adapter,
            runtime = runtime,
            scope = scope,
            nowMs = { 0L },
        )
        try {
            val generation: cn.mafei.cmp.player.api.PlaybackGeneration? = sessionPlayback.play(MediaId("item-9"))
            val load = runtime.commands.filterIsInstance<RuntimeCommand.Load>().single()
            assertEquals("item-9", load.resource.id)
            assertEquals("https://stream.test/session-a.m3u8", load.resource.url)
            assertEquals("tok", load.resource.headers["Authorization"])

            runtime.emitReady(checkNotNull(generation))
            runtime.emitPlaying(checkNotNull(generation))
            val reportPair = adapter.reports.single()
            val ready: PlaybackPreparation.Ready = reportPair.first
            val event: PlaybackEvent = reportPair.second
            assertEquals("https://stream.test/session-a.m3u8", ready.streamUrl)
            assertTrue(event.kind == PlaybackEventKind.Start)
        } finally {
            sessionPlayback.release()
            scope.cancel()
        }
    }

    @Test
    fun reportFailureIsObservableAndReleaseStopsReporting() = runBlocking<Unit> {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val adapter = StubAdapter().apply { failReports = true }
        val runtime = CountingRuntime()
        val sessionPlayback = SessionPlaybackRuntime.create(
            provider = ProviderKind.Jellyfin,
            adapter = adapter,
            runtime = runtime,
            scope = scope,
            nowMs = { 0L },
        )
        val observed = mutableListOf<cn.mafei.cmp.data.repositories.playback.PlaybackReportFailure>()
        sessionPlayback.onFailureObserved = { observed += it }
        try {
            val generation = checkNotNull(sessionPlayback.play(MediaId("item-9")))
            runtime.emitReady(generation)
            runtime.emitPlaying(generation)
            assertEquals(1, observed.size) // visible, never crashing the state machine
            sessionPlayback.release()
            val reportsBefore = adapter.reports.size
            assertFailsWith<IllegalStateException> {
                sessionPlayback.play(MediaId("item-9")) // released -> coordinator refuses
            }
            assertEquals(reportsBefore, adapter.reports.size)
        } finally {
            sessionPlayback.release()
            scope.cancel()
        }
    }
}
