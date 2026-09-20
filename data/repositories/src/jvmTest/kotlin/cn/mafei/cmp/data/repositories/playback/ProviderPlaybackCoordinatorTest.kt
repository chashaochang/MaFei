package cn.mafei.cmp.data.repositories.playback

import cn.mafei.cmp.core.domain.AccountId
import java.util.concurrent.atomic.AtomicInteger
import cn.mafei.cmp.core.domain.AuthenticationError
import cn.mafei.cmp.core.domain.AuthFailureKind
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackEventKind
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.data.adapter_contract.RefusalReason
import cn.mafei.cmp.player.api.PlayableResource
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerReport
import cn.mafei.cmp.player.api.PlayerReportKind
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import cn.mafei.cmp.player.facade.DefaultPlayerController
import cn.mafei.cmp.player.testkit.FakePlayerRuntime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

private val ITEM = MediaId("item-0001")

/**
 * Adapter fake exposing only the playback surface; every other contract
 * method throws so no test can depend on it by accident.
 */
private class PlaybackOnlyAdapter : MediaProviderAdapter {
    val preparedUrls = mutableListOf<String>()
    val reports = mutableListOf<Pair<PlaybackPreparation.Ready, PlaybackEvent>>()
    val preparedReadies = mutableListOf<PlaybackPreparation.Ready>()
    val stubs = ArrayDeque<StubOutcome>()
    private val preparedCallCount = AtomicInteger(0)
    private val gateCallNumbers = mutableSetOf<Int>()
    private val gates = mutableListOf<CompletableDeferred<Unit>>()
    var reportFailure: ((PlaybackPreparation.Ready, PlaybackEvent) -> Unit)? = null

    /** Parks the Nth (1-based) prepare call until it is explicitly opened. */
    fun gateCall(number: Int) {
        gateCallNumbers += number
    }

    fun openGates() {
        val toOpen = gates.toList()
        gates.clear()
        toOpen.forEach { it.complete(Unit) }
    }

    sealed interface StubOutcome {
        data class ReadyStub(val url: String, val headers: Map<String, String>) : StubOutcome
        data object AuthError : StubOutcome
        data object Cancelled : StubOutcome
        data object Refused : StubOutcome
    }


    fun queueReady(streamUrl: String, headers: Map<String, String> = emptyMap()) {
        stubs += StubOutcome.ReadyStub(streamUrl, headers)
    }

    fun queueAuthError() {
        stubs += StubOutcome.AuthError
    }



    private fun defaultStub(): StubOutcome = StubOutcome.ReadyStub("https://stream.test/default", emptyMap())

    override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation {
        val callNumber = preparedCallCount.incrementAndGet()
        val gate = if (callNumber in gateCallNumbers) CompletableDeferred<Unit>().also { gates += it } else null
        gate?.await()
        when (val stub = stubs.removeFirstOrNull() ?: defaultStub()) {
            is StubOutcome.ReadyStub -> {
                val ready = PlaybackPreparation.Ready(
                    streamUrl = stub.url,
                    subtitleUrl = null,
                    headers = stub.headers,
                    playSessionId = null,
                    reportingContext = "{\"item\":${preparedReadies.size}}",
                )
                preparedUrls += ready.streamUrl
                preparedReadies += ready
                return ready
            }
            StubOutcome.AuthError -> throw AuthenticationError(AuthFailureKind.TokenExpired, "expired")
            StubOutcome.Cancelled -> throw CancellationException("scope cancelled during prepare")
            StubOutcome.Refused -> return PlaybackPreparation.Refused(RefusalReason.NoPlayableSource)
        }
    }

    override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) {
        reportFailure?.invoke(stream, event)
        reports += stream to event
    }

    override val provider = ProviderKind.Jellyfin
    override val serverId = ServerId("srv-test")
    override val accountId = AccountId("acc-test")
    override val capabilities = AdapterCapabilities(playbackReporting = true)
    override suspend fun collections(request: PageRequest) = throw ClientConfigError("unused")
    override suspend fun items(collectionId: CollectionId?, request: PageRequest) = throw ClientConfigError("unused")
    override suspend fun search(term: String, request: PageRequest) = throw ClientConfigError("unused")
    override suspend fun item(id: MediaId) = throw ClientConfigError("unused")
    override suspend fun seasons(seriesId: MediaId): Page<cn.mafei.cmp.core.domain.MediaItem> = throw ClientConfigError("unused")
    override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest) = throw ClientConfigError("unused")
    override suspend fun setFavorite(id: MediaId, favorite: Boolean) = throw ClientConfigError("unused")
    override suspend fun setWatched(id: MediaId, watched: Boolean) = throw ClientConfigError("unused")
}

private class Harness {
    val adapter = PlaybackOnlyAdapter()
    val runtime = FakePlayerRuntime(PlayerCapabilities(trackSelection = false))
    val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    val failures = mutableListOf<PlaybackReportFailure>()
    val coordinator = ProviderPlaybackCoordinator(
        adapterFor = { adapter },
        scope = scope,
        maxRenegotiationsPerGeneration = 1,
    )
    val controller: DefaultPlayerController

    init {
        coordinator.onFailureObserved = { failures += it }
        controller = DefaultPlayerController(
            runtime = runtime,
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            nowMs = { 0L },
            reportSink = coordinator.reportSink,
        )
        coordinator.attachController(controller)
    }

    suspend fun dispose() {
        controller.release()
        coordinator.release()
        scope.cancel()
    }
}

class ProviderPlaybackCoordinatorTest {

    private fun harness(block: suspend (Harness) -> Unit) = runBlocking {
        // A hung coordinator must fail the test with a stack, never park CI.
        kotlinx.coroutines.withTimeout(10_000) {
            val harness = Harness()
            try {
                block(harness)
            } finally {
                harness.dispose()
            }
        }
    }

    private fun loadedResources(harness: Harness): List<PlayableResource> =
        harness.runtime.commands.filterIsInstance<RuntimeCommand.Load>().map { it.resource }

    private fun currentGeneration(harness: Harness): PlaybackGeneration =
        checkNotNull(harness.controller.state.value.generation)

    private fun emitReadyAndPlaying(harness: Harness, durationMs: Long = 60_000) {
        val generation = currentGeneration(harness)
        harness.runtime.emit(RuntimeEvent.Ready(generation, durationMs))
        harness.runtime.emit(RuntimeEvent.Playing(generation))
    }

    private fun progressReport(harness: Harness, positionMs: Long = 1_000) =
        PlayerReport(currentGeneration(harness), "item-0001", PlayerReportKind.Progress, positionMs, 60_000, false)

    // ------------------------------------------------------- normal path

    @Test
    fun normalPrepareLoadsResourceAndReportsStartWithTheOriginalReady() = harness { harness ->
        harness.adapter.queueReady("https://stream.test/a.m3u8", mapOf("Authorization" to "tok"))
        val generation = harness.coordinator.play(ProviderKind.Jellyfin, ITEM)
        assertEquals(ITEM.value, loadedResources(harness).single().id)

        emitReadyAndPlaying(harness)
        val (ready, event) = harness.adapter.reports.single()
        // The report carries the EXACT Ready instance this coordinator prepared.
        assertTrue(ready === harness.adapter.preparedReadies.single())
        assertEquals("https://stream.test/a.m3u8", ready.streamUrl)
        assertEquals(PlaybackEventKind.Start, event.kind)
        assertEquals(generation, harness.controller.state.value.generation)
    }

    @Test
    fun cookiesAndHeadersSurviveTheTranslation() = harness { harness ->
        harness.adapter.queueReady(
            "https://stream.test/fn.m3u8",
            mapOf("Authorization" to "fn-token", "Cookie" to "Trim-MC-token=fn-token; mode=relay"),
        )
        harness.coordinator.play(ProviderKind.Feiniu, ITEM)
        val loaded = loadedResources(harness).single()
        assertEquals("fn-token", loaded.headers["Authorization"])
        assertEquals("Trim-MC-token=fn-token; mode=relay", loaded.headers["Cookie"])
    }

    // ----------------------------------------------------- renegotiation

    @Test
    fun expiredLinkRenegotiatesOnceWithTheSameAdapterAndReloads() = harness { harness ->
        harness.adapter.queueReady("https://stream.test/expired")
        harness.adapter.queueReady("https://stream.test/refreshed")
        harness.coordinator.play(ProviderKind.Jellyfin, ITEM)
        emitReadyAndPlaying(harness)
        harness.adapter.reportFailure = { _, _ -> throw AuthenticationError(AuthFailureKind.TokenExpired, "expired") }

        harness.coordinator.reportSink(progressReport(harness))
        assertEquals(
            listOf("https://stream.test/expired", "https://stream.test/refreshed"),
            harness.adapter.preparedUrls,
        )
        assertEquals(2, loadedResources(harness).size)
        assertEquals(1, harness.coordinator.completedRenegotiations.value)
    }

    @Test
    fun renegotiationIsBoundedPerGeneration() = harness { harness ->
        harness.adapter.queueReady("https://stream.test/expired")
        harness.adapter.queueReady("https://stream.test/refreshed")
        harness.coordinator.play(ProviderKind.Jellyfin, ITEM)
        emitReadyAndPlaying(harness)
        harness.adapter.reportFailure = { _, _ -> throw AuthenticationError(AuthFailureKind.TokenExpired, "expired") }

        repeat(5) { harness.coordinator.reportSink(progressReport(harness)) }
        assertEquals(1, harness.coordinator.completedRenegotiations.value)
        assertTrue(harness.failures.isNotEmpty()) // excess failures are observable
    }

    // --------------------------------------------- cancellation / races

    @Test
    fun stopDuringRenegotiationDiscardsTheStaleResult() = harness { harness ->
        harness.adapter.queueReady("https://stream.test/expired")
        harness.adapter.queueReady("https://stream.test/refreshed")
        harness.adapter.gateCall(2) // parks the renegotiation prepare
        harness.coordinator.play(ProviderKind.Jellyfin, ITEM)
        emitReadyAndPlaying(harness)
        harness.adapter.reportFailure = { _, _ -> throw AuthenticationError(AuthFailureKind.TokenExpired, "expired") }
        harness.coordinator.reportSink(progressReport(harness))

        harness.coordinator.stop()
        harness.adapter.openGates()
        assertEquals(1, loadedResources(harness).size) // no reload of the stale result
    }

    @Test
    fun newerPlayDiscardsTheStaleRenegotiation() = harness { harness ->
        harness.adapter.queueReady("https://stream.test/expired")
        harness.adapter.queueReady("https://stream.test/refreshed")
        harness.adapter.queueReady("https://stream.test/b")
        harness.coordinator.play(ProviderKind.Jellyfin, ITEM)
        emitReadyAndPlaying(harness)
        harness.adapter.reportFailure = { _, _ -> throw AuthenticationError(AuthFailureKind.TokenExpired, "expired") }
        // The gate parks the RENEGOTIATION prepare, which runs after this line.
        harness.adapter.gateCall(2)
        harness.coordinator.reportSink(progressReport(harness))

        // Quick item switch wins before the stale renegotiation completes.
        harness.coordinator.play(ProviderKind.Jellyfin, MediaId("item-0002"))
        harness.adapter.openGates()
        // The switch loads the next available stub; the stale renegotiation's
        // result is discarded and never loaded afterwards.
        assertEquals(
            listOf("https://stream.test/expired", "https://stream.test/refreshed"),
            loadedResources(harness).map { it.url },
        )
    }

    @Test
    fun cancellationDuringPreparePropagates() = harness { harness ->
        harness.adapter.stubs += PlaybackOnlyAdapter.StubOutcome.Cancelled
        assertFailsWith<CancellationException> {
            harness.coordinator.play(ProviderKind.Jellyfin, ITEM)
        }
    }

    // ------------------------------------------------- failure isolation

    @Test
    fun reportFailureIsObservedWithoutBreakingTheStateMachine() = harness { harness ->
        harness.adapter.queueReady("https://stream.test/a")
        harness.coordinator.play(ProviderKind.Jellyfin, ITEM)
        emitReadyAndPlaying(harness)
        harness.adapter.reportFailure = { _, _ -> throw cn.mafei.cmp.core.domain.ServerError(statusCode = 503, message = "report failed") }

        harness.coordinator.reportSink(progressReport(harness))
        assertEquals(1, harness.failures.size)
        assertEquals(503, (harness.failures.single().error as cn.mafei.cmp.core.domain.ServerError).statusCode)
        assertEquals(PlayerReportKind.Progress, harness.failures.single().kind)

        // The state machine stays usable: a later report still flows through.
        harness.coordinator.reportSink(
            PlayerReport(currentGeneration(harness), "item-0001", PlayerReportKind.Stop, 1_000, 60_000, false),
        )
        assertEquals(2, harness.failures.size)
        assertEquals(PlayerReportKind.Stop, harness.failures.last().kind)
    }

    @Test
    fun refusedPreparationIsObservableWithoutLoading() = harness { harness ->
        harness.adapter.stubs.clear()
        harness.adapter.stubs.addLast(PlaybackOnlyAdapter.StubOutcome.Refused)
        val generation = harness.coordinator.play(ProviderKind.Jellyfin, ITEM)
        assertNull(generation)
        assertEquals(emptyList(), loadedResources(harness))
        assertEquals("prepare refused: NoPlayableSource", harness.failures.single().message)
    }

    @Test
    fun releaseStopsAllReporting() = harness { harness ->
        harness.adapter.queueReady("https://stream.test/a")
        harness.coordinator.play(ProviderKind.Jellyfin, ITEM)
        emitReadyAndPlaying(harness)
        harness.coordinator.release()
        val before = harness.adapter.reports.size
        harness.coordinator.reportSink(
            PlayerReport(PlaybackGeneration(), "item-0001", PlayerReportKind.Progress, 1_000, 60_000, false),
        )
        assertEquals(before, harness.adapter.reports.size)
    }
}
