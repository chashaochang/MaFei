package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackEventKind
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
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

private class StubAdapter : MediaProviderAdapter {
    val reports = mutableListOf<Pair<PlaybackPreparation.Ready, PlaybackEvent>>()
    var failReports = false

    override suspend fun preparePlayback(item: MediaId): PlaybackPreparation = PlaybackPreparation.Ready(
        streamUrl = "https://stream.test/session-a.m3u8",
        headers = mapOf("Authorization" to "tok"),
        reportingContext = "{\"item\":\"${item.value}\"}",
    )
    override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) {
        if (failReports) throw cn.mafei.cmp.core.domain.ClientConfigError("engine gone")
        reports += stream to event
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

private class CountingRuntime : PlayerRuntime {
    val commands = mutableListOf<RuntimeCommand>()
    private var sink: ((RuntimeEvent) -> Unit)? = null
    override val capabilities = PlayerCapabilities()
    override fun bind(eventSink: (RuntimeEvent) -> Unit) { sink = eventSink }
    override fun execute(command: RuntimeCommand) { commands += command }
    fun emitReady(generation: PlaybackGeneration) { sink?.invoke(RuntimeEvent.Ready(generation, 60_000)) }
    fun emitPlaying(generation: PlaybackGeneration) { sink?.invoke(RuntimeEvent.Playing(generation)) }
}

class SessionPlaybackRuntimeTest {

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
