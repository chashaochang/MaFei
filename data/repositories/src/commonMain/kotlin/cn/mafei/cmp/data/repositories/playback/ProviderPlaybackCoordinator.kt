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
 *   discarded and never loaded into the new session; reports queued for a
 *   superseded binding are dropped the same way.
 * - Report failures are published on [reportFailures] and never crash the
 *   player state machine; CancellationException always propagates.
 * - After [release] no report is forwarded anymore.
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
        set(value) { bindingState.value = value }
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
        val event: PlaybackEvent,
        val resourceId: String,
    )

    /** Attaches the controller this coordinator's [reportSink] was wired into and starts the report worker. */
    fun attachController(target: PlayerController) {
        controller = target
        if (worker == null) {
            worker = scope.launch {
                for (task in reportTasks) {
                    // Tasks queued for a superseded binding are dropped: a
                    // renegotiated load is never chased by stale reports.
                    if (task.binding !== bindingState.value) continue
                    try {
                        task.binding.adapter?.reportPlayback(checkNotNull(task.binding.ready), task.event)
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
    ): PlaybackGeneration? = mutex.withLock {
        println("[MaFeiPlayback] coordinator prepare start provider=$provider item=${itemId.value}")
        val target = checkNotNull(controller) { "attachController before play" }
        check(!released) { "Playback coordinator released" }
        val seq = ++requestSeq
        cancelPendingNativeTracks()
        binding = Binding()
        tracksFlow.value = emptyList()
        preparedFlow.value = null
        val adapter = adapterFor(provider)
        // CancellationException propagates untouched (adapter contract).
        val outcome = adapter.preparePlayback(itemId, options)
        println("[MaFeiPlayback] coordinator prepare completed outcome=${outcome::class.simpleName}")
        if (seq != requestSeq || released) return@withLock null // superseded while preparing
        val ready = outcome as? PlaybackPreparation.Ready
        if (ready == null) {
            val refused = outcome as? PlaybackPreparation.Refused
            emitFailure(
                PlaybackReportFailure(
                    generation = PlaybackGeneration(),
                    resourceId = itemId.value,
                    kind = PlayerReportKind.Start,
                    renegotiated = false,
                    error = null,
                    message = "prepare refused: ${refused?.reason}" + (refused?.detail?.let { " ($it)" } ?: ""),
                ),
            )
            return@withLock null
        }
        val resource = PlayableResource(
            id = itemId.value,
            url = ready.streamUrl,
            headers = ready.headers,
            subtitleUrl = ready.subtitleUrl,
            allowInsecureTls = ready.allowInsecureTls,
            multiConnectionAllowed = ready.multiConnectionAllowed,
        )
        val generation = target.load(listOf(resource), index = 0, resumePositionMs = resumePositionMs, autoplay = autoplay)
        println("[MaFeiPlayback] coordinator target.load invoked item=${itemId.value}")
        binding = Binding(
            generation = generation,
            adapter = adapter,
            ready = ready,
            itemId = itemId,
            options = options.withNegotiatedDefaults(ready),
            renegotiations = 0,
        )
        tracksFlow.value = ready.tracks
        preparedFlow.value = ready.toPreparedFacts()
        schedulePendingNativeTracks(generation, ready, options)
        generation
    }

    /**
     * Re-runs provider negotiation for a user-selected provider track and
     * reloads at the current position. Provider indexes are never passed to a
     * native runtime as though they were native track ids.
     */
    suspend fun selectTrack(kind: PlaybackTrackKind, id: String?): PlaybackGeneration? = mutex.withLock {
        val snapshot = binding
        val adapter = snapshot.adapter ?: return@withLock null
        val itemId = snapshot.itemId ?: return@withLock null
        val target = controller ?: return@withLock null
        val selectedIndex = when (kind) {
            PlaybackTrackKind.Audio -> id?.toIntOrNull() ?: return@withLock null
            PlaybackTrackKind.Subtitle -> id?.toIntOrNull() ?: -1
        }
        val options = when (kind) {
            PlaybackTrackKind.Audio -> snapshot.options.copy(audioStreamIndex = selectedIndex)
            PlaybackTrackKind.Subtitle -> snapshot.options.copy(subtitleStreamIndex = selectedIndex)
        }
        val outcome = adapter.preparePlayback(itemId, options)
        val ready = outcome as? PlaybackPreparation.Ready ?: return@withLock null
        val state = target.state.value
        val autoplay = state.phase != cn.mafei.cmp.player.api.PlayerPhase.Paused
        val resource = ready.toPlayableResource(itemId)
        val generation = target.load(
            listOf(resource),
            index = 0,
            resumePositionMs = state.positionMs,
            autoplay = autoplay,
        )
        binding = Binding(
            generation = generation,
            adapter = adapter,
            ready = ready,
            itemId = itemId,
            options = options.withNegotiatedDefaults(ready),
            renegotiations = snapshot.renegotiations,
        )
        tracksFlow.value = ready.tracks
        preparedFlow.value = ready.toPreparedFacts()
        schedulePendingNativeTracks(generation, ready, options)
        generation
    }

    /**
     * Re-runs provider negotiation for a user-selected quality ceiling
     * ([PlaybackOptions.maxStreamingBitrate], null = auto/direct) and reloads
     * at the current position. Mirrors [selectTrack]'s stale-result guards.
     */
    suspend fun selectQuality(maxStreamingBitrate: Int?): PlaybackGeneration? = mutex.withLock {
        val snapshot = binding
        val adapter = snapshot.adapter ?: return@withLock null
        val itemId = snapshot.itemId ?: return@withLock null
        val target = controller ?: return@withLock null
        val options = snapshot.options.copy(maxStreamingBitrate = maxStreamingBitrate)
        val outcome = adapter.preparePlayback(itemId, options)
        val ready = outcome as? PlaybackPreparation.Ready ?: return@withLock null
        val state = target.state.value
        val autoplay = state.phase != cn.mafei.cmp.player.api.PlayerPhase.Paused
        val generation = target.load(
            listOf(ready.toPlayableResource(itemId)),
            index = 0,
            resumePositionMs = state.positionMs,
            autoplay = autoplay,
        )
        binding = Binding(
            generation = generation,
            adapter = adapter,
            ready = ready,
            itemId = itemId,
            options = options.withNegotiatedDefaults(ready),
            renegotiations = snapshot.renegotiations,
        )
        tracksFlow.value = ready.tracks
        preparedFlow.value = ready.toPreparedFacts()
        schedulePendingNativeTracks(generation, ready, options)
        generation
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
        val pending = ready.tracks.filter { track ->
            !track.renegotiate && when (track.kind) {
                PlaybackTrackKind.Audio -> track.index == requested.audioStreamIndex
                PlaybackTrackKind.Subtitle -> track.index == requestedSubtitle
            }
        }
        // 明确的「关字幕」：内嵌字幕只能由播放器关掉，外挂字幕走 provider 协商。
        val subtitlesOff = requestedSubtitle != null && requestedSubtitle < 0 &&
            ready.tracks.none { it.kind == PlaybackTrackKind.Subtitle && it.renegotiate }
        if (pending.isEmpty() && !subtitlesOff) return
        nativeSelection = scope.launch {
            val target = controller ?: return@launch
            val settled = target.state.first { state ->
                state.generation === generation && state.phase.canSelectTracks()
            }
            // 期间可能已经换了条目/停了播放：那就不属于这一代了。
            if (!bindingState.value.handles(settled.generation)) return@launch
            pending.forEach { track ->
                val kind = track.kind.toPlayerTrackKind()
                val nativeId = resolveNativeTrackId(
                    providerTrack = track,
                    nativeTracks = target.state.value.tracks.filter { it.kind == kind },
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

    /** Stops playback and discards the current binding; queued reports for the old binding are dropped. */
    suspend fun stop() = mutex.withLock {
        ++requestSeq
        cancelPendingNativeTracks()
        binding = Binding()
        tracksFlow.value = emptyList()
        preparedFlow.value = null
        controller?.stop()
    }

    /** Releases the controller and this coordinator; no report is forwarded afterwards. */
    suspend fun release() = mutex.withLock {
        ++requestSeq
        cancelPendingNativeTracks()
        binding = Binding()
        tracksFlow.value = emptyList()
        preparedFlow.value = null
        released = true
        reportTasks.close()
        worker?.cancelAndJoin()
        worker = null
        controller?.release()
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
        val snapshot = task.binding
        val expiryFailure = error is AuthenticationError || error is AuthorizationError
        if (expiryFailure && snapshot.renegotiations < maxRenegotiationsPerGeneration && !released) {
            renegotiate(snapshot, task.resourceId, task.event, resumePositionMs = task.event.positionMs)
            return
        }
        emitFailure(
            PlaybackReportFailure(
                generation = checkNotNull(snapshot.generation),
                resourceId = task.resourceId,
                kind = task.event.kind.toReportKind(),
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
        val outcome = try {
            adapter.preparePlayback(itemId, snapshot.options)
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
        val resource = PlayableResource(
            id = itemId.value,
            url = outcome.streamUrl,
            headers = outcome.headers,
            subtitleUrl = outcome.subtitleUrl,
            allowInsecureTls = outcome.allowInsecureTls,
            multiConnectionAllowed = outcome.multiConnectionAllowed,
        )
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
            options = snapshot.options.withNegotiatedDefaults(outcome),
            renegotiations = snapshot.renegotiations + 1,
        )
        tracksFlow.value = outcome.tracks
        preparedFlow.value = outcome.toPreparedFacts()
        schedulePendingNativeTracks(newGeneration, outcome, snapshot.options)
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
        url = streamUrl,
        headers = headers,
        subtitleUrl = subtitleUrl,
        allowInsecureTls = allowInsecureTls,
        multiConnectionAllowed = multiConnectionAllowed,
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
