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
import cn.mafei.cmp.data.adapter_contract.PlaybackTrack
import cn.mafei.cmp.data.adapter_contract.PlaybackTrackKind
import cn.mafei.cmp.data.adapter_contract.RefusalReason
import cn.mafei.cmp.player.api.PlayableResource
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerReport
import cn.mafei.cmp.player.api.PlayerReportKind
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.TrackKind
import cn.mafei.cmp.player.api.PlayerTrack
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineStart

private val ITEM = MediaId("item-0001")

/**
 * Adapter fake exposing only the playback surface; every other contract
 * method throws so no test can depend on it by accident.
 */
private class PlaybackOnlyAdapter : MediaProviderAdapter {
    val preparedUrls = mutableListOf<String>()
    val releasedReadies = mutableListOf<PlaybackPreparation.Ready>()
    val reports = mutableListOf<Pair<PlaybackPreparation.Ready, PlaybackEvent>>()
    val preparedReadies = mutableListOf<PlaybackPreparation.Ready>()
    val preparedOptions = mutableListOf<PlaybackOptions>()
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
        data class ReadyStub(
            val url: String,
            val headers: Map<String, String>,
            val mediaSourceId: String? = null,
            val tracks: List<PlaybackTrack> = emptyList(),
        ) : StubOutcome
        data object AuthError : StubOutcome
        data object Cancelled : StubOutcome
        data object Refused : StubOutcome
    }


    fun queueReady(
        streamUrl: String,
        headers: Map<String, String> = emptyMap(),
        mediaSourceId: String? = null,
        tracks: List<PlaybackTrack> = emptyList(),
    ) {
        stubs += StubOutcome.ReadyStub(streamUrl, headers, mediaSourceId, tracks)
    }

    fun queueAuthError() {
        stubs += StubOutcome.AuthError
    }



    private fun defaultStub(): StubOutcome = StubOutcome.ReadyStub("https://stream.test/default", emptyMap())

    override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation {
        preparedOptions += options
        val callNumber = preparedCallCount.incrementAndGet()
        val gate = if (callNumber in gateCallNumbers) CompletableDeferred<Unit>().also { gates += it } else null
        gate?.await()
        when (val stub = stubs.removeFirstOrNull() ?: defaultStub()) {
            is StubOutcome.ReadyStub -> {
                val ready = PlaybackPreparation.Ready(
                    streamUrl = stub.url,
                    subtitleUrl = null,
                    mediaSourceId = stub.mediaSourceId,
                    tracks = stub.tracks,
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
    override suspend fun releasePlayback(stream: PlaybackPreparation.Ready) { releasedReadies += stream }

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

private class Harness(trackSelection: Boolean = false, isoDirectPlay: Boolean = false) {
    val adapter = PlaybackOnlyAdapter()
    val runtime = FakePlayerRuntime(PlayerCapabilities(trackSelection = trackSelection, isoDirectPlay = isoDirectPlay))
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
    @Test fun stoppingDuringSlowPrepareDoesNotWaitForTheServerAndReleasesTheLateCandidate() = harness { h ->
        h.adapter.gateCall(1)
        coroutineScope {
            val preparing = async(start = CoroutineStart.UNDISPATCHED) { h.coordinator.play(ProviderKind.Jellyfin, ITEM) }
            try {
                kotlinx.coroutines.withTimeout(300) { h.coordinator.stop() }
            } finally { h.adapter.openGates() }
            assertNull(preparing.await())
        }
        assertTrue(loadedResources(h).isEmpty())
        assertTrue(h.adapter.releasedReadies.single() === h.adapter.preparedReadies.single())
    }

    @Test fun stopReportsFinalPositionAndClosesTheExactStreamOnce() = harness { h ->
        h.coordinator.play(ProviderKind.Jellyfin, ITEM)
        emitReadyAndPlaying(h)
        h.runtime.emit(RuntimeEvent.Progress(currentGeneration(h), 14000, 15000))
        val prepared = h.adapter.preparedReadies.single()
        h.coordinator.stop()
        assertTrue(h.adapter.reports.last().first === prepared)
        assertEquals(PlaybackEventKind.Stop, h.adapter.reports.last().second.kind)
        assertEquals(14000, h.adapter.reports.last().second.positionMs)
        assertTrue(h.adapter.releasedReadies.single() === prepared)
        h.coordinator.stop(); assertEquals(1, h.adapter.releasedReadies.size)
    }

    @Test fun replacingAnUnstartedStreamStillReleasesItAndDoesNotCloseTheNewStream() = harness { h ->
        h.coordinator.play(ProviderKind.Jellyfin, ITEM)
        val first = h.adapter.preparedReadies.single()
        h.coordinator.play(ProviderKind.Jellyfin, MediaId("next"))
        assertTrue(h.adapter.releasedReadies.single() === first)
        assertTrue(h.adapter.releasedReadies.none { it === h.adapter.preparedReadies.last() })
        h.coordinator.release()
        assertEquals(2, h.adapter.releasedReadies.size)
    }

    private fun harness(
        trackSelection: Boolean = false,
        isoDirectPlay: Boolean = false,
        block: suspend (Harness) -> Unit,
    ) = runBlocking {
        // A hung coordinator must fail the test with a stack, never park CI.
        kotlinx.coroutines.withTimeout(10_000) {
            val harness = Harness(trackSelection, isoDirectPlay)
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

    /**
     * The preselection补选 runs on the coordinator's scope:它不是 load 的同步结果，
     * 所以要等它落地，而不是假设 emit 之后就立刻生效。
     */
    private suspend fun awaitNativeSelections(harness: Harness, atLeast: Int = 1) =
        kotlinx.coroutines.withTimeout(2_000) {
            var found: List<RuntimeCommand.SelectTrack> = emptyList()
            while (found.size < atLeast) {
                found = harness.runtime.commands.filterIsInstance<RuntimeCommand.SelectTrack>()
                if (found.size < atLeast) kotlinx.coroutines.delay(5)
            }
            found
        }

    private fun progressReport(harness: Harness, positionMs: Long = 1_000) =
        PlayerReport(currentGeneration(harness), "item-0001", PlayerReportKind.Progress, positionMs, 60_000, false)

    // ------------------------------------------------------- normal path

    @Test
    fun isoSupportComesFromTheRuntimeAndSurvivesRenegotiation() = harness(isoDirectPlay = true) { harness ->
        harness.coordinator.play(ProviderKind.Jellyfin, ITEM, resumePositionMs = 90_000)
        assertEquals(true, harness.adapter.preparedOptions.single().isoDirectPlay)
        assertEquals(90_000, harness.adapter.preparedOptions.single().startPositionMs)
        emitReadyAndPlaying(harness)
        harness.coordinator.selectQuality(8_000_000)
        assertEquals(true, harness.adapter.preparedOptions.last().isoDirectPlay)
    }

    @Test
    fun callerCannotEnableIsoOnAnEngineThatDoesNotSupportIt() = harness { harness ->
        harness.coordinator.play(ProviderKind.Jellyfin, ITEM, options = PlaybackOptions(isoDirectPlay = true))
        assertEquals(false, harness.adapter.preparedOptions.single().isoDirectPlay)
    }

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

    @Test
    fun providerTrackSelectionRenegotiatesAtCurrentPositionAndKeepsTheSource() = harness { harness ->
        harness.adapter.queueReady(
            streamUrl = "https://stream.test/original",
            mediaSourceId = "source-a",
            tracks = listOf(
                PlaybackTrack(1, PlaybackTrackKind.Audio, displayTitle = "国语", selected = true),
                PlaybackTrack(2, PlaybackTrackKind.Audio, displayTitle = "英语"),
                PlaybackTrack(3, PlaybackTrackKind.Subtitle, displayTitle = "中文字幕", selected = true),
            ),
        )
        harness.adapter.queueReady(
            streamUrl = "https://stream.test/english",
            mediaSourceId = "source-a",
            tracks = listOf(
                PlaybackTrack(1, PlaybackTrackKind.Audio, displayTitle = "国语"),
                PlaybackTrack(2, PlaybackTrackKind.Audio, displayTitle = "英语", selected = true),
                PlaybackTrack(3, PlaybackTrackKind.Subtitle, displayTitle = "中文字幕", selected = true),
            ),
        )
        harness.coordinator.play(ProviderKind.Jellyfin, ITEM)
        val initialGeneration = currentGeneration(harness)
        harness.runtime.emit(RuntimeEvent.Ready(initialGeneration, 60_000))
        harness.runtime.emit(RuntimeEvent.Progress(initialGeneration, 12_345, 20_000, 60_000))

        harness.coordinator.selectTrack(PlaybackTrackKind.Audio, "2")

        assertEquals(
            PlaybackOptions(
                mediaSourceId = "source-a",
                audioStreamIndex = 2,
                subtitleStreamIndex = 3,
                startPositionMs = 12_345,
            ),
            harness.adapter.preparedOptions.last(),
        )
        val reload = harness.runtime.commands.filterIsInstance<RuntimeCommand.Load>().last()
        assertEquals(12_345, reload.startPositionMs)
        assertEquals("https://stream.test/english", reload.resource.url)
        assertEquals(
            "英语",
            harness.coordinator.tracks.value.single {
                it.selected && it.kind == PlaybackTrackKind.Audio
            }.displayTitle,
        )
    }

    @Test
    fun nativeTrackSwitchUpdatesThePublishedSelectionAndLaterNegotiations() = harness { harness ->
        // 直连流的内嵌音轨只能由播放器自己换（renegotiate = false），provider 不会重新协商。
        // 但界面信的是 provider 的选中态，后续协商也要带上新索引 —— 两个都得跟上。
        harness.adapter.queueReady(
            streamUrl = "https://stream.test/direct",
            tracks = listOf(
                PlaybackTrack(1, PlaybackTrackKind.Audio, displayTitle = "国语", selected = true),
                PlaybackTrack(2, PlaybackTrackKind.Audio, displayTitle = "英语"),
                PlaybackTrack(3, PlaybackTrackKind.Subtitle, displayTitle = "中文字幕", selected = true),
            ),
        )
        harness.coordinator.play(ProviderKind.Feiniu, ITEM)
        emitReadyAndPlaying(harness)

        harness.coordinator.selectTrackLocally(PlaybackTrackKind.Audio, 2)

        val tracks = harness.coordinator.tracks.value
        assertEquals("英语", tracks.single { it.selected && it.kind == PlaybackTrackKind.Audio }.displayTitle)
        assertTrue(tracks.none { it.kind == PlaybackTrackKind.Audio && it.index == 1 && it.selected })
        // 另一类轨道不受影响。
        assertEquals("中文字幕", tracks.single { it.selected && it.kind == PlaybackTrackKind.Subtitle }.displayTitle)

        // 下一次重新协商（换清晰度）必须带上新音轨，而不是把旧索引带回去。
        harness.adapter.queueReady("https://stream.test/direct-720p")
        harness.coordinator.selectQuality(2_000_000)
        val options = harness.adapter.preparedOptions.last()
        assertEquals(2, options.audioStreamIndex)
        assertEquals(3, options.subtitleStreamIndex)
        assertEquals(2_000_000, options.maxStreamingBitrate)
    }

    @Test
    fun nativeSubtitleSwitchOffClearsThePublishedSelection() = harness { harness ->
        harness.adapter.queueReady(
            streamUrl = "https://stream.test/direct",
            tracks = listOf(
                PlaybackTrack(1, PlaybackTrackKind.Audio, displayTitle = "国语", selected = true),
                PlaybackTrack(3, PlaybackTrackKind.Subtitle, displayTitle = "中文字幕", selected = true),
            ),
        )
        harness.coordinator.play(ProviderKind.Feiniu, ITEM)
        emitReadyAndPlaying(harness)

        harness.coordinator.selectTrackLocally(PlaybackTrackKind.Subtitle, -1)

        assertTrue(harness.coordinator.tracks.value.none { it.kind == PlaybackTrackKind.Subtitle && it.selected })
        harness.adapter.queueReady("https://stream.test/direct-720p")
        harness.coordinator.selectQuality(2_000_000)
        assertEquals(-1, harness.adapter.preparedOptions.last().subtitleStreamIndex)
    }

    // -------------------------------------- preselected tracks at load time

    @Test
    fun preselectedDirectPlayTracksAreAppliedNativelyOnceReady() = harness(trackSelection = true) { harness ->
        harness.adapter.queueReady(
            streamUrl = "https://stream.test/direct",
            tracks = listOf(
                // 直连流的内嵌轨：provider 无法靠重新协商换掉它们。
                PlaybackTrack(1, PlaybackTrackKind.Audio, displayTitle = "国语", renegotiate = false),
                PlaybackTrack(
                    2,
                    PlaybackTrackKind.Audio,
                    displayTitle = "英语",
                    selected = true,
                    renegotiate = false,
                ),
                PlaybackTrack(3, PlaybackTrackKind.Subtitle, displayTitle = "中文字幕", renegotiate = false),
            ),
        )

        harness.coordinator.play(
            ProviderKind.Jellyfin,
            ITEM,
            options = PlaybackOptions(audioStreamIndex = 2, subtitleStreamIndex = -1),
        )
        val generation = currentGeneration(harness)
        // 鸿蒙桥的真实顺序是 Ready 在前、Tracks 在后。补选必须等轨道目录真正到达，
        // 不能在 Ready 时读到空列表就永久放弃。
        harness.runtime.emit(RuntimeEvent.Ready(generation, 60_000))
        harness.runtime.emit(RuntimeEvent.Playing(generation))
        harness.runtime.emit(
            RuntimeEvent.Tracks(
                generation,
                listOf(
                    PlayerTrack("1", TrackKind.Audio, "国语", language = "chi"),
                    PlayerTrack("2", TrackKind.Audio, "英语", language = "eng"),
                    PlayerTrack("9", TrackKind.Subtitle, "中文字幕"),
                ),
            ),
        )

        // 直连流的内嵌轨只能在播放器里换：provider 的选中态对了，播放器还得被告知。
        val selections = awaitNativeSelections(harness, atLeast = 2)
        assertEquals(listOf("2"), selections.filter { it.kind == TrackKind.Audio }.map { it.id })
        // 明确的关字幕同样要落到播放器上。
        assertEquals(listOf(null), selections.filter { it.kind == TrackKind.Subtitle }.map { it.id })
    }

    @Test
    fun providerDefaultEmbeddedSubtitleIsAppliedNativelyWithoutExplicitSelection() =
        harness(trackSelection = true) { harness ->
            harness.adapter.queueReady(
                streamUrl = "https://stream.test/direct",
                tracks = listOf(
                    PlaybackTrack(
                        1,
                        PlaybackTrackKind.Audio,
                        displayTitle = "国语",
                        selected = true,
                        renegotiate = false,
                    ),
                    PlaybackTrack(
                        3,
                        PlaybackTrackKind.Subtitle,
                        displayTitle = "中文字幕",
                        selected = true,
                        renegotiate = false,
                    ),
                ),
            )

            // No explicit audio/subtitle options: these are the provider's
            // server defaults and must still be applied to the native player.
            harness.coordinator.play(ProviderKind.Jellyfin, ITEM)
            val generation = currentGeneration(harness)
            harness.runtime.emit(
                RuntimeEvent.Tracks(
                    generation,
                    listOf(
                        PlayerTrack("1", TrackKind.Audio, "国语"),
                        PlayerTrack("3", TrackKind.Subtitle, "中文字幕"),
                    ),
                ),
            )
            harness.runtime.emit(RuntimeEvent.Ready(generation, 60_000))

            val selections = awaitNativeSelections(harness, atLeast = 2)
            assertEquals(
                listOf("1"),
                selections.filter { it.kind == TrackKind.Audio }.map { it.id },
            )
            assertEquals(
                listOf("3"),
                selections.filter { it.kind == TrackKind.Subtitle }.map { it.id },
            )
        }

    @Test
    fun preselectedTracksTheProviderNegotiatesAreNotSwitchedNatively() = harness(trackSelection = true) { harness ->
        harness.adapter.queueReady(
            streamUrl = "https://stream.test/hls/master.m3u8",
            tracks = listOf(
                PlaybackTrack(1, PlaybackTrackKind.Audio, displayTitle = "国语", renegotiate = true),
            ),
        )

        harness.coordinator.play(
            ProviderKind.Jellyfin,
            ITEM,
            options = PlaybackOptions(audioStreamIndex = 1),
        )
        val generation = currentGeneration(harness)
        harness.runtime.emit(
            RuntimeEvent.Tracks(generation, listOf(PlayerTrack("1", TrackKind.Audio, "国语"))),
        )
        emitReadyAndPlaying(harness)

        // 转码/外挂轨由 provider 重新协商换：原生再切一次就会与选中态打架。
        kotlinx.coroutines.delay(100)
        assertEquals(emptyList(), harness.runtime.commands.filterIsInstance<RuntimeCommand.SelectTrack>())
    }

    @Test
    fun aSupersededLoadDropsItsPendingNativeSelection() = harness(trackSelection = true) { harness ->
        harness.adapter.queueReady(
            streamUrl = "https://stream.test/first",
            tracks = listOf(
                PlaybackTrack(
                    2,
                    PlaybackTrackKind.Audio,
                    displayTitle = "英语",
                    selected = true,
                    renegotiate = false,
                ),
            ),
        )
        harness.coordinator.play(
            ProviderKind.Jellyfin,
            ITEM,
            options = PlaybackOptions(audioStreamIndex = 2),
        )
        val first = currentGeneration(harness)

        // 还没进入 Ready 就换了条目：上一代的补选必须失效。
        harness.adapter.queueReady(
            streamUrl = "https://stream.test/second",
            tracks = listOf(
                PlaybackTrack(
                    2,
                    PlaybackTrackKind.Audio,
                    displayTitle = "英语",
                    selected = true,
                    renegotiate = false,
                ),
            ),
        )
        harness.coordinator.play(
            ProviderKind.Jellyfin,
            ITEM,
            options = PlaybackOptions(audioStreamIndex = 2),
        )
        val second = currentGeneration(harness)

        harness.runtime.emit(
            RuntimeEvent.Tracks(second, listOf(PlayerTrack("2", TrackKind.Audio, "英语", language = "eng"))),
        )
        harness.runtime.emit(RuntimeEvent.Ready(second, 60_000))
        harness.runtime.emit(RuntimeEvent.Playing(second))
        // 第一代已经作废：即便它此刻才"就绪"，也不该再动播放器。
        harness.runtime.emit(RuntimeEvent.Ready(first, 60_000))

        val selections = awaitNativeSelections(harness)
        kotlinx.coroutines.delay(100)
        assertEquals(1, selections.size)
        assertEquals(listOf(second), harness.runtime.commands.filterIsInstance<RuntimeCommand.SelectTrack>().map { it.generation })
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
        assertTrue(harness.adapter.releasedReadies.any { it === harness.adapter.preparedReadies.last() })
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
    fun refusedNextTrackKeepsCurrentNegotiationAndReporting() = harness { harness ->
        harness.adapter.queueReady("https://stream.test/current", mediaSourceId = "source-current",
            tracks = listOf(PlaybackTrack(1, PlaybackTrackKind.Audio, title = "Original audio")))
        harness.coordinator.play(ProviderKind.Jellyfin, ITEM)
        emitReadyAndPlaying(harness)
        val originalTracks = harness.coordinator.tracks.value
        val originalFacts = harness.coordinator.prepared.value
        harness.adapter.stubs.addLast(PlaybackOnlyAdapter.StubOutcome.Refused)
        assertNull(harness.coordinator.play(ProviderKind.Jellyfin, MediaId("next-track")))
        assertEquals(originalTracks, harness.coordinator.tracks.value)
        assertEquals(originalFacts, harness.coordinator.prepared.value)
        val reports = harness.adapter.reports.size
        harness.coordinator.reportSink(progressReport(harness))
        assertEquals(reports + 1, harness.adapter.reports.size)
        assertEquals("source-current", harness.adapter.reports.last().first.mediaSourceId)
        assertEquals(1, loadedResources(harness).size)
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
