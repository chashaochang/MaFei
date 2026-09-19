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
import cn.mafei.cmp.player.api.PlayableResource
import cn.mafei.cmp.player.api.PlayerController
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerReport
import cn.mafei.cmp.player.api.PlayerReportKind
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

    private val reportFailuresFlow = MutableSharedFlow<PlaybackReportFailure>(extraBufferCapacity = 64)
    val reportFailures: SharedFlow<PlaybackReportFailure> = reportFailuresFlow.asSharedFlow()

    /** Synchronous observation hook (tests/diagnostics); failures also flow on [reportFailures]. */
    var onFailureObserved: ((PlaybackReportFailure) -> Unit)? = null

    /** Total completed re-negotiations; observable for tests and diagnostics. */
    val completedRenegotiations: StateFlow<Int> get() = renegotiationsFlow
    private val renegotiationsFlow = MutableStateFlow(0)

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
        val target = checkNotNull(controller) { "attachController before play" }
        check(!released) { "Playback coordinator released" }
        val seq = ++requestSeq
        binding = Binding()
        val adapter = adapterFor(provider)
        // CancellationException propagates untouched (adapter contract).
        val outcome = adapter.preparePlayback(itemId, options)
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
        )
        val generation = target.load(listOf(resource), index = 0, resumePositionMs = resumePositionMs, autoplay = autoplay)
        binding = Binding(
            generation = generation,
            adapter = adapter,
            ready = ready,
            itemId = itemId,
            options = options,
            renegotiations = 0,
        )
        generation
    }

    /** Stops playback and discards the current binding; queued reports for the old binding are dropped. */
    suspend fun stop() = mutex.withLock {
        ++requestSeq
        binding = Binding()
        controller?.stop()
    }

    /** Releases the controller and this coordinator; no report is forwarded afterwards. */
    suspend fun release() = mutex.withLock {
        ++requestSeq
        binding = Binding()
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
            options = snapshot.options,
            renegotiations = snapshot.renegotiations + 1,
        )
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
}
