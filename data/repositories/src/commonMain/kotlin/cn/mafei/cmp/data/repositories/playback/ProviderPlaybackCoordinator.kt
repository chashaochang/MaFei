package cn.mafei.cmp.data.repositories.playback

import cn.mafei.cmp.core.domain.AuthenticationError
import cn.mafei.cmp.core.domain.AuthorizationError
import cn.mafei.cmp.core.domain.DomainError
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackEventKind
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.data.adapter_contract.PlaybackTrack
import cn.mafei.cmp.data.adapter_contract.PlaybackTrackKind
import cn.mafei.cmp.player.api.PlayableResource
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerReport
import cn.mafei.cmp.player.api.PlayerReportKind
import cn.mafei.cmp.player.api.TrackKind
import cn.mafei.cmp.player.api.UnsupportedPlayerCapability
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One observed report failure. Never thrown into the player state machine:
 * playback keeps running and the UI can surface this entry.
 */
data class PlaybackReportFailure(
    val generation: PlaybackGeneration,
    val resourceId: String,
    val kind: PlayerReportKind,
    val renegotiated: Boolean,
    val error: DomainError?,
    val message: String?,
) {
    override fun toString() =
        "PlaybackReportFailure(generation=$generation, resourceId=$resourceId, kind=$kind, " +
            "renegotiated=$renegotiated, error=${error?.let { it::class.simpleName }}, message=$message)"
}

/**
 * Provider->player closure: turns `MediaProviderAdapter.preparePlayback`
 * outcomes into `player:api` [PlayableResource]s, drives [PlayerController],
 * and translates `PlayerReport`s back into the originating adapter's
 * `reportPlayback` using the EXACT `PlaybackPreparation.Ready` this
 * coordinator received from that adapter instance.
 *
 * Frozen semantics:
 * - The validated stream URL, headers/cookies, media id and subtitle URL are
 *   carried over verbatim. The provider reporting context stays inside the
 *   held `Ready` value in memory only — never persisted, never re-parsed
 *   from the stream URL.
 * - Expired resources / auth failures on reporting trigger a re-negotiation
 *   with the SAME adapter instance, bounded by [maxRenegotiationsPerGeneration]
 *   so an expiring token cannot loop forever.
 * - A stale renegotiation result (after Stop, Release or a newer play) is
 *   released and never loaded into the new session. Superseded Start/Progress
 *   reports are dropped; the last Stop and resource cleanup are drained.
 * - Report failures are published on [reportFailures] and never crash the
 *   player state machine; CancellationException always propagates.
 * - [release] drains already queued Stop/cleanup before stopping the worker.
 */
class ProviderPlaybackCoordinator(
    private val adapterFor: (ProviderKind) -> MediaProviderAdapter,
    private val scope: CoroutineScope,
    private val maxRenegotiationsPerGeneration: Int = 1,
) {
    init {
        require(maxRenegotiationsPerGeneration >= 0)
    }

    /** Wire this into `DefaultPlayerController(reportSink = coordinator.reportSink, ...)`. */
    val reportSink: (PlayerReport) -> Unit = ::onPlayerReport

    private val mutex = Mutex()
    private var controller: PlayerController? = null
    private val bindingState = MutableStateFlow(Binding())
    private var binding: Binding
        get() = bindingState.value
        set(value) {
            val previous = bindingState.value
            bindingState.value = value
            if (previous.ready != null && previous.ready !== value.ready) {
                reportTasks.trySend(ReportTask(previous, null, previous.itemId!!.value))
            }
        }
    private var requestSeq = 0L
    private var released = false

    private val reportTasks = Channel<ReportTask>(Channel.UNLIMITED)
    private var worker: Job? = null

    /**
     * Applies a track preselection that must happen INSIDE the native player
     * (see [schedulePendingNativeTracks]). Exactly one is alive at a time;
     * every new load, stop and release cancels the previous one.
     */
    private var nativeSelection: Job? = null

    private val reportFailuresFlow = MutableSharedFlow<PlaybackReportFailure>(extraBufferCapacity = 64)
    val reportFailures: SharedFlow<PlaybackReportFailure> = reportFailuresFlow.asSharedFlow()

    /** Synchronous observation hook (tests/diagnostics); failures also flow on [reportFailures]. */
    var onFailureObserved: ((PlaybackReportFailure) -> Unit)? = null

    /** Total completed re-negotiations; observable for tests and diagnostics. */
    val completedRenegotiations: StateFlow<Int> get() = renegotiationsFlow
    private val renegotiationsFlow = MutableStateFlow(0)

    /** Provider-authoritative track metadata for the current negotiated source. */
    val tracks: StateFlow<List<PlaybackTrack>> get() = tracksFlow
    private val tracksFlow = MutableStateFlow<List<PlaybackTrack>>(emptyList())

    /**
     * Provider facts about the stream currently bound to the player: which
     * media source, how it is served, what ceilings it can serve, and which
     * ceiling is actually in effect.
     *
     * Published next to [tracks] so the chrome REPORTS the negotiation instead
     * of inferring it from its own last click. Deliberately a subset of
     * [PlaybackPreparation.Ready]: stream URLs, headers and reporting contexts
     * must never reach the UI layer.
     */
    val prepared: StateFlow<PreparedStreamFacts?> get() = preparedFlow
    private val preparedFlow = MutableStateFlow<PreparedStreamFacts?>(null)

    private data class Binding(
        val generation: PlaybackGeneration? = null,
        val adapter: MediaProviderAdapter? = null,
        val ready: PlaybackPreparation.Ready? = null,
        val itemId: MediaId? = null,
        val options: PlaybackOptions = PlaybackOptions(),
        val renegotiations: Int = 0,
    ) {
        fun handles(candidate: PlaybackGeneration?): Boolean = candidate != null && candidate === generation
    }

    private data class ReportTask(
        val binding: Binding,
        val event: PlaybackEvent?,
        val resourceId: String,
    )

    private data class LoadRequest(
        val sequence: Long,
        val controller: PlayerController,
        val adapter: MediaProviderAdapter,
        val itemId: MediaId,
        val options: PlaybackOptions,
        val autoplay: Boolean,
        val renegotiations: Int,
    )

    /** Attaches the controller this coordinator's [reportSink] was wired into and starts the report worker. */
    fun attachController(target: PlayerController) {
        controller = target
        if (worker == null) {
            worker = scope.launch {
                for (task in reportTasks) {
                    val event = task.event
                    if (event == null) {
                        try {
                            task.binding.adapter?.releasePlayback(checkNotNull(task.binding.ready))
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { /* A server cleanup failure cannot stop the new resource. */ }
                        continue
                    }
                    // Tasks queued for a superseded binding are dropped: a
                    // renegotiated load is never chased by stale reports.
                    if (task.binding !== bindingState.value && event.kind != PlaybackEventKind.Stop) continue
                    try {
                        task.binding.adapter?.reportPlayback(checkNotNull(task.binding.ready), event)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (failure: DomainError) {
                        onFailure(task, failure, failure.message)
                    } catch (failure: UnsupportedOperationException) {
                        onFailure(task, null, failure.message)
                    } catch (failure: IllegalStateException) {
                        onFailure(task, null, failure.message)
                    }
                }
            }
        }
    }

    /**
     * Prepares via the provider adapter (honoring user-directed [options])
     * and loads the resulting resource. Returns the accepted generation, or
     * null when the request was refused or superseded before loading.
     */
    suspend fun play(
        provider: ProviderKind,
        itemId: MediaId,
        resumePositionMs: Long = 0,
        autoplay: Boolean = true,
        options: PlaybackOptions = PlaybackOptions(),
    ): PlaybackGeneration? {
        val request = mutex.withLock {
        println("[MaFeiPlayback] coordinator prepare start provider=$provider item=${itemId.value}")
        val target = checkNotNull(controller) { "attachController before play" }
        check(!released) { "Playback coordinator released" }
        val seq = ++requestSeq
        cancelPendingNativeTracks()
        val adapter = adapterFor(provider)
        // CancellationException propagates untouched (adapter contract).
        val prepareOptions = options.copy(
            startPositionMs = resumePositionMs.coerceAtLeast(0L),
            isoDirectPlay = target.capabilities.isoDirectPlay,
        )
            LoadRequest(seq, target, adapter, itemId, prepareOptions, autoplay, 0)
        }
        return prepareAndLoad(request)
    }

    private suspend fun prepareAndLoad(request: LoadRequest): PlaybackGeneration? {
        // Server negotiation must not hold the command mutex: Back/Stop must
        // finish immediately even when a server hasn't answered yet.
        val outcome = request.adapter.preparePlayback(request.itemId, request.options)
        println("[MaFeiPlayback] coordinator prepare completed outcome=${outcome::class.simpleName}")
        val ready = outcome as? PlaybackPreparation.Ready
        if (ready == null) {
            val refused = outcome as? PlaybackPreparation.Refused
            emitFailure(
                PlaybackReportFailure(
                    generation = PlaybackGeneration(),
                    resourceId = request.itemId.value,
                    kind = PlayerReportKind.Start,
                    renegotiated = false,
                    error = null,
                    message = "prepare refused: ${refused?.reason}" + (refused?.detail?.let { " ($it)" } ?: ""),
                ),
            )
            return null
        }
        var installed = false
        try {
            return mutex.withLock {
                if (request.sequence != requestSeq || released) return@withLock null
                val generation = request.controller.load(listOf(ready.toPlayableResource(request.itemId)), index = 0,
                    resumePositionMs = request.options.startPositionMs ?: 0, autoplay = request.autoplay)
        binding = Binding(
            generation = generation,
            adapter = request.adapter,
            ready = ready,
            itemId = request.itemId,
            options = request.options.withNegotiatedDefaults(ready),
            renegotiations = request.renegotiations,
        )
                installed = true
        tracksFlow.value = ready.tracks
        preparedFlow.value = ready.toPreparedFacts()
        schedulePendingNativeTracks(generation, ready, request.options)
        generation
            }
        } finally {
            if (!installed) releaseCandidate(request.adapter, ready)
        }
    }

    private suspend fun releaseCandidate(adapter: MediaProviderAdapter, ready: PlaybackPreparation.Ready) {
        // Candidates never published to binding have no queued cleanup owner.
        withContext(NonCancellable) {
            withTimeoutOrNull(5_000) {
                try { adapter.releasePlayback(ready) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Cleanup cannot replace a prepare/load failure. */ }
            }
        }
    }

    /**
     * Re-runs provider negotiation for a user-selected provider track and
     * reloads at the current position. Provider indexes are never passed to a
     * native runtime as though they were native track ids.
     */
    suspend fun selectTrack(kind: PlaybackTrackKind, id: String?): PlaybackGeneration? {
        val request = mutex.withLock {
        val snapshot = binding
        val adapter = snapshot.adapter ?: return@withLock null
        val itemId = snapshot.itemId ?: return@withLock null
        val target = controller ?: return@withLock null
        val selectedIndex = when (kind) {
            PlaybackTrackKind.Audio -> id?.toIntOrNull() ?: return@withLock null
            PlaybackTrackKind.Subtitle -> id?.toIntOrNull() ?: -1
        }
        val state = target.state.value
        val options = when (kind) {
            PlaybackTrackKind.Audio -> snapshot.options.copy(
                audioStreamIndex = selectedIndex,
                startPositionMs = state.positionMs,
            )
            PlaybackTrackKind.Subtitle -> snapshot.options.copy(
                subtitleStreamIndex = selectedIndex,
                startPositionMs = state.positionMs,
            )
        }
        val autoplay = state.phase != cn.mafei.cmp.player.api.PlayerPhase.Paused
            LoadRequest(++requestSeq, target, adapter, itemId, options, autoplay, snapshot.renegotiations)
        } ?: return null
        return prepareAndLoad(request)
    }

    /**
     * Re-runs provider negotiation for a user-selected quality ceiling
     * ([PlaybackOptions.maxStreamingBitrate], null = auto/direct) and reloads
     * at the current position. Mirrors [selectTrack]'s stale-result guards.
     */
    suspend fun selectQuality(maxStreamingBitrate: Int?): PlaybackGeneration? {
        val request = mutex.withLock {
        val snapshot = binding
        val adapter = snapshot.adapter ?: return@withLock null
        val itemId = snapshot.itemId ?: return@withLock null
        val target = controller ?: return@withLock null
        val state = target.state.value
        val options = snapshot.options.copy(
            maxStreamingBitrate = maxStreamingBitrate,
            startPositionMs = state.positionMs,
        )
        val autoplay = state.phase != cn.mafei.cmp.player.api.PlayerPhase.Paused
            LoadRequest(++requestSeq, target, adapter, itemId, options, autoplay, snapshot.renegotiations)
        } ?: return null
        return prepareAndLoad(request)
    }

    /**
     * Applies a preselection the provider CANNOT deliver by negotiation.
     *
     * [PlaybackPreparation.Ready.tracks] already marks the chosen track selected
     * (the adapter read the same [PlaybackOptions]), but that is only the
     * provider's view: a track muxed into a byte-for-byte direct stream is chosen
     * by whoever opens the file, and the runtime starts on the file's DEFAULT
     * track. Nothing has told it otherwise, so a direct play would silently use
     * the default audio and show the default embedded subtitle. The legacy page
     * closed exactly this gap (MFPlayerView applied the chosen track once the
     * native track list arrived); without it a preselection is only half honoured
     * — the chrome shows the picked track while a different one plays.
     *
     * Waits for the generation to be prepared, because a track switch before that
     * is refused by the state machine, and is dropped as soon as a newer load
     * supersedes it. Platforms that cannot switch tracks simply keep playing: the
     * switch is best-effort, never a reason to fail a started session.
     */
    private fun schedulePendingNativeTracks(
        generation: PlaybackGeneration,
        ready: PlaybackPreparation.Ready,
        requested: PlaybackOptions,
    ) {
        cancelPendingNativeTracks()
        val requestedSubtitle = requested.subtitleStreamIndex
        // The provider's selected flag is authoritative even when the caller
        // did not pass an explicit track index. This is the server's default
        // track (often an embedded subtitle); without this initial native
        // sync, it is only applied after the user opens the menu and selects it
        // again. Explicit indexes still resolve to the provider-selected
        // result after negotiation, so a provider may normalize an index.
        val pending = ready.tracks.filter { track ->
            !track.renegotiate && track.selected
        }
        // 明确的「关字幕」：内嵌字幕只能由播放器关掉，外挂字幕走 provider 协商。
        val subtitlesOff = requestedSubtitle != null && requestedSubtitle < 0 &&
            ready.tracks.none { it.kind == PlaybackTrackKind.Subtitle && it.renegotiate }
        if (pending.isEmpty() && !subtitlesOff) return
        nativeSelection = scope.launch {
            val target = controller ?: return@launch
            val settled = target.state.first { state ->
                state.generation === generation &&
                    state.phase.canSelectTracks() &&
                    pending.all { track ->
                        val kind = track.kind.toPlayerTrackKind()
                        resolveNativeTrackId(track, state.tracks.filter { it.kind == kind }) != null
                    }
            }
            // 期间可能已经换了条目/停了播放：那就不属于这一代了。
            if (!bindingState.value.handles(settled.generation)) return@launch
            pending.forEach { track ->
                val kind = track.kind.toPlayerTrackKind()
                val nativeId = resolveNativeTrackId(
                    providerTrack = track,
                    nativeTracks = settled.tracks.filter { it.kind == kind },
                ) ?: return@forEach
                try {
                    target.selectTrack(kind, nativeId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: UnsupportedPlayerCapability) {
                    // 平台不支持换轨：保持默认轨继续播，不因此中断。
                } catch (_: IllegalStateException) {
                    // 播放器又回到未就绪（例如重新加载）：交给下一次调度。
                }
            }
            if (subtitlesOff) {
                try {
                    target.selectTrack(TrackKind.Subtitle, null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: UnsupportedPlayerCapability) {
                } catch (_: IllegalStateException) {
                }
            }
        }
    }

    private fun cancelPendingNativeTracks() {
        nativeSelection?.cancel()
        nativeSelection = null
    }

    private fun PlaybackTrackKind.toPlayerTrackKind(): TrackKind = when (this) {
        PlaybackTrackKind.Audio -> TrackKind.Audio
        PlaybackTrackKind.Subtitle -> TrackKind.Subtitle
    }

    private fun PlayerPhase.canSelectTracks(): Boolean = when (this) {
        PlayerPhase.Ready,
        PlayerPhase.Playing,
        PlayerPhase.Paused,
        PlayerPhase.Buffering,
        PlayerPhase.Ended -> true
        PlayerPhase.Idle,
        PlayerPhase.Loading,
        PlayerPhase.Error,
        PlayerPhase.Released -> false
    }

    /**
     * Records a track switch that happened INSIDE the native player.
     *
     * Direct-play / direct-stream tracks are muxed into the file we already
     * have, so re-requesting the same URL cannot change them and the switch is
     * performed by the runtime. The provider's published selection must follow
     * that switch, otherwise two lies appear: the chrome (which trusts the
     * published list) keeps its checkmark on the old track, and the next
     * re-negotiation — a quality change, a subtitle-off — re-sends the OLD
     * index and silently restores the old track.
     *
     * [providerIndex] is the provider's own stream index; `-1` means subtitles
     * off and clears the subtitle selection.
     */
    suspend fun selectTrackLocally(kind: PlaybackTrackKind, providerIndex: Int) = mutex.withLock {
        val snapshot = binding
        if (snapshot.itemId == null) return@withLock
        val selected = { track: PlaybackTrack ->
            when (kind) {
                PlaybackTrackKind.Audio -> if (track.kind == kind) track.index == providerIndex else track.selected
                PlaybackTrackKind.Subtitle ->
                    if (track.kind == kind) providerIndex >= 0 && track.index == providerIndex else track.selected
            }
        }
        val current = tracksFlow.value
        val updated = current.map { it.copy(selected = selected(it)) }
        if (updated != current) tracksFlow.value = updated
        binding = snapshot.copy(
            options = when (kind) {
                PlaybackTrackKind.Audio -> snapshot.options.copy(audioStreamIndex = providerIndex)
                PlaybackTrackKind.Subtitle -> snapshot.options.copy(subtitleStreamIndex = providerIndex)
            },
        )
    }

    /** Stops immediately; queued final Stop and cleanup retain the old binding. */
    suspend fun stop() = mutex.withLock {
        ++requestSeq
        cancelPendingNativeTracks()
        controller?.stop()
        binding = Binding()
        tracksFlow.value = emptyList()
        preparedFlow.value = null
    }

    /** Drains final Stop/cleanup, then releases the report worker. */
    suspend fun release() = mutex.withLock {
        ++requestSeq
        cancelPendingNativeTracks()
        released = true
        controller?.release()
        binding = Binding()
        tracksFlow.value = emptyList()
        preparedFlow.value = null
        reportTasks.close()
        // Drain final stop/cleanup, but never hang app/account shutdown on a server.
        withTimeoutOrNull(5_000) { worker?.join() }
        worker?.cancelAndJoin()
        worker = null
        controller = null
    }

    // ------------------------------------------------------------- internals

    private fun onPlayerReport(report: PlayerReport) {
        val snapshot = binding
        // A retired generation's report is never replayed onto a new adapter session.
        if (!snapshot.handles(report.generation)) return
        val event = when (report.kind) {
            PlayerReportKind.Start -> PlaybackEvent(PlaybackEventKind.Start, positionMs = report.positionMs)
            PlayerReportKind.Progress -> PlaybackEvent(
                PlaybackEventKind.Progress,
                positionMs = report.positionMs,
                durationMs = report.durationMs,
                isPaused = report.paused,
            )
            PlayerReportKind.Stop -> PlaybackEvent(PlaybackEventKind.Stop, positionMs = report.positionMs, durationMs = report.durationMs)
        }
        // Serialized onto the worker; an expired outcome renegotiates there.
        reportTasks.trySend(ReportTask(snapshot, event, report.resourceId))
    }

    private suspend fun onFailure(task: ReportTask, error: DomainError?, message: String?) {
        val event = task.event ?: return
        val snapshot = task.binding
        val expiryFailure = error is AuthenticationError || error is AuthorizationError
        if (expiryFailure && event.kind != PlaybackEventKind.Stop && task.binding === binding &&
            snapshot.renegotiations < maxRenegotiationsPerGeneration && !released) {
            renegotiate(snapshot, task.resourceId, event, resumePositionMs = event.positionMs)
            return
        }
        emitFailure(
            PlaybackReportFailure(
                generation = checkNotNull(snapshot.generation),
                resourceId = task.resourceId,
                kind = event.kind.toReportKind(),
                renegotiated = false,
                error = error,
                message = message,
            ),
        )
    }

    /**
     * Re-negotiates with the SAME adapter instance and, when this binding is
     * still current (not stopped/released/superseded), reloads the fresh
     * resource as a new generation carrying the last known position.
     */
    private suspend fun renegotiate(
        snapshot: Binding,
        resourceId: String,
        event: PlaybackEvent,
        resumePositionMs: Long,
    ) {
        val adapter = snapshot.adapter ?: return
        val itemId = snapshot.itemId ?: return
        val options = snapshot.options.copy(startPositionMs = resumePositionMs.coerceAtLeast(0L))
        val outcome = try {
            adapter.preparePlayback(itemId, options)
        } catch (e: CancellationException) {
            throw e
        } catch (failure: DomainError) {
            emitFailure(
                PlaybackReportFailure(
                    generation = checkNotNull(snapshot.generation),
                    resourceId = resourceId,
                    kind = event.kind.toReportKind(),
                    renegotiated = true,
                    error = failure,
                    message = failure.message,
                ),
            )
            return
        }
        // The stale result never loads into a newer session.
        val stillCurrent = binding === snapshot && binding.generation === snapshot.generation
        if (!stillCurrent || outcome !is PlaybackPreparation.Ready) {
            if (outcome is PlaybackPreparation.Ready) releaseCandidate(adapter, outcome)
            emitFailure(
                PlaybackReportFailure(
                    generation = checkNotNull(snapshot.generation),
                    resourceId = resourceId,
                    kind = event.kind.toReportKind(),
                    renegotiated = false,
                    error = null,
                    message = if (outcome is PlaybackPreparation.Ready) "renegotiation discarded (stale)" else "renegotiation refused",
                ),
            )
            return
        }
        val target = controller ?: return
        val resource = outcome.toPlayableResource(itemId)
        val newGeneration = target.load(
            listOf(resource),
            index = 0,
            resumePositionMs = resumePositionMs,
            autoplay = true,
        )
        binding = Binding(
            generation = newGeneration,
            adapter = adapter,
            ready = outcome,
            itemId = itemId,
            options = options.withNegotiatedDefaults(outcome),
            renegotiations = snapshot.renegotiations + 1,
        )
        tracksFlow.value = outcome.tracks
        preparedFlow.value = outcome.toPreparedFacts()
        schedulePendingNativeTracks(newGeneration, outcome, options)
        renegotiationsFlow.value += 1
    }

    private fun emitFailure(failure: PlaybackReportFailure) {
        onFailureObserved?.invoke(failure)
        reportFailuresFlow.tryEmit(failure)
    }

    private fun PlaybackEventKind.toReportKind(): PlayerReportKind = when (this) {
        PlaybackEventKind.Start -> PlayerReportKind.Start
        PlaybackEventKind.Progress -> PlayerReportKind.Progress
        PlaybackEventKind.Stop -> PlayerReportKind.Stop
    }

    private fun PlaybackPreparation.Ready.toPlayableResource(itemId: MediaId) = PlayableResource(
        id = itemId.value,
        audioOnly = audioOnly,
        url = streamUrl,
        headers = headers,
        subtitleUrl = subtitleUrl,
        allowInsecureTls = allowInsecureTls,
        multiConnectionAllowed = multiConnectionAllowed,
        engineFacts = engineSource?.let { source ->
            cn.mafei.cmp.player.api.PlayerEngineSourceFacts(
                container = source.container, videoCodec = source.videoStream?.codec,
                pixelFormat = source.videoStream?.pixelFormat, videoRangeType = source.videoStream?.videoRangeType,
                dolbyVisionProfile = source.videoStream?.dolbyVisionProfile,
                audioCodecs = source.audioStreams.map { it.codec.orEmpty() },
                externalSubtitleCodecs = tracks.filter { it.isExternal }.map { it.codec.orEmpty() },
            )
        },
    )

    /**
     * Everything the player chrome may know about the bound stream. Kept apart
     * from [PlaybackPreparation.Ready] so URLs, headers and reporting contexts
     * cannot leak into UI state through an accidental `copy`.
     */
    private fun PlaybackPreparation.Ready.toPreparedFacts() = PreparedStreamFacts(
        mediaSourceId = mediaSourceId,
        transport = transport,
        qualities = qualities,
        appliedBitrate = appliedBitrate,
        protocol = streamUrl.substringBefore("://", missingDelimiterValue = "")
            .trim()
            .takeIf(String::isNotEmpty),
        multiConnectionAllowed = multiConnectionAllowed,
    )

    private fun PlaybackOptions.withNegotiatedDefaults(ready: PlaybackPreparation.Ready): PlaybackOptions = copy(
        // 用户选的清晰度档位必须跨重新协商保留（媒体源/轨道同理）。
        maxStreamingBitrate = maxStreamingBitrate,
        mediaSourceId = ready.mediaSourceId ?: mediaSourceId,
        audioStreamIndex = ready.tracks.firstOrNull {
            it.kind == PlaybackTrackKind.Audio && it.selected
        }?.index ?: audioStreamIndex,
        subtitleStreamIndex = when {
            subtitleStreamIndex == -1 -> -1
            else -> ready.tracks.firstOrNull {
                it.kind == PlaybackTrackKind.Subtitle && it.selected
            }?.index ?: subtitleStreamIndex
        },
    )
}
