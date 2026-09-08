package cn.mafei.cmp.player.api

import kotlinx.coroutines.flow.StateFlow

/** One negotiated, already trust-checked stream. Never persist URLs or headers. */
class PlayableResource(
    val id: String,
    val url: String,
    headers: Map<String, String> = emptyMap(),
    val subtitleUrl: String? = null,
) {
    val headers: Map<String, String> = headers.toMap()
    init { require(id.isNotBlank()); require(url.isNotBlank()) }
    override fun toString() = "PlayableResource(id=$id, stream=<redacted>)"
}

/** Reference identity, deliberately not a numeric or forgeable data-class generation. */
class PlaybackGeneration

/** Platform keeps Surface/UIView/window in its own registry keyed by this opaque identity. */
class PlayerSurfaceHandle

enum class PlayerPhase { Idle, Loading, Ready, Playing, Paused, Buffering, Ended, Error, Released }
enum class TrackKind { Video, Audio, Subtitle }
data class PlayerTrack(val id: String, val kind: TrackKind, val label: String, val language: String? = null)
data class PlayerCapabilities(val seek: Boolean = true, val speed: Boolean = true, val trackSelection: Boolean = true, val backgroundPlayback: Boolean = false)
class UnsupportedPlayerCapability(val capability: String) : IllegalStateException("Unsupported player capability: $capability")
enum class PlayerFailure { Network, Decode, UnsupportedMedia, Engine, RuntimeClosed }

data class PlayerState(
    val phase: PlayerPhase = PlayerPhase.Idle,
    val generation: PlaybackGeneration? = null,
    val resourceId: String? = null,
    val queueIndex: Int = -1,
    val queueSize: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val bufferedPositionMs: Long = 0,
    val tracks: List<PlayerTrack> = emptyList(),
    val speed: Float = 1f,
    val failure: PlayerFailure? = null,
)

/** Runtime callbacks must retain the generation of the originating native player/load. */
sealed interface RuntimeEvent {
    val generation: PlaybackGeneration
    data class Ready(override val generation: PlaybackGeneration, val durationMs: Long?) : RuntimeEvent
    data class Playing(override val generation: PlaybackGeneration) : RuntimeEvent
    data class Paused(override val generation: PlaybackGeneration) : RuntimeEvent
    data class Buffering(override val generation: PlaybackGeneration) : RuntimeEvent
    data class Progress(override val generation: PlaybackGeneration, val positionMs: Long, val bufferedPositionMs: Long) : RuntimeEvent
    data class Tracks(override val generation: PlaybackGeneration, val tracks: List<PlayerTrack>) : RuntimeEvent
    data class Ended(override val generation: PlaybackGeneration) : RuntimeEvent
    data class Failed(override val generation: PlaybackGeneration, val failure: PlayerFailure) : RuntimeEvent
}

/** Commands acknowledge submission, not playback success. Native runtime emits confirmation events. */
sealed interface RuntimeCommand {
    data class Load(val generation: PlaybackGeneration, val resource: PlayableResource) : RuntimeCommand
    data class Play(val generation: PlaybackGeneration) : RuntimeCommand
    data class Pause(val generation: PlaybackGeneration) : RuntimeCommand
    data class Seek(val generation: PlaybackGeneration, val positionMs: Long) : RuntimeCommand
    data class Stop(val generation: PlaybackGeneration) : RuntimeCommand
    data class Speed(val generation: PlaybackGeneration, val value: Float) : RuntimeCommand
    data class SelectTrack(val generation: PlaybackGeneration, val kind: TrackKind, val id: String?) : RuntimeCommand
    data class AttachSurface(val handle: PlayerSurfaceHandle) : RuntimeCommand
    data class DetachSurface(val handle: PlayerSurfaceHandle) : RuntimeCommand
    data object Release : RuntimeCommand
}

/**
 * One runtime per controller. bind is called once before any command. execute must be
 * non-blocking and cannot suspend; enqueue native work on the engine's required thread.
 * Preserve command order, ignore commands for retired generations and stop the previous
 * resource on Load. Event sink is thread-safe, may be invoked synchronously, and must not
 * be retained after Release. Release must be idempotent even after an engine failure.
 * No runtime implementation exists in this first core batch.
 */
interface PlayerRuntime {
    val capabilities: PlayerCapabilities
    fun bind(eventSink: (RuntimeEvent) -> Unit)
    fun execute(command: RuntimeCommand)
}

enum class PlayerReportKind { Start, Progress, Stop }
data class PlayerReport(val generation: PlaybackGeneration, val resourceId: String, val kind: PlayerReportKind, val positionMs: Long, val durationMs: Long?, val paused: Boolean)

/**
 * All methods are command acknowledgement only; observe state for native confirmation.
 * Canceling a coroutine before command admission has no effect. Once admitted, commands
 * complete atomically; cancelLoad explicitly stops an asynchronous preparation by identity.
 * A controller owns exactly one runtime and must be released when its owner scope ends.
 */
interface PlayerController {
    val state: StateFlow<PlayerState>
    val capabilities: PlayerCapabilities
    suspend fun load(queue: List<PlayableResource>, index: Int = 0, resumePositionMs: Long = 0, autoplay: Boolean = true): PlaybackGeneration
    suspend fun play()
    suspend fun pause()
    suspend fun seek(positionMs: Long)
    suspend fun stop()
    suspend fun release()
    suspend fun speed(value: Float)
    suspend fun selectTrack(kind: TrackKind, id: String?)
    suspend fun attachSurface(handle: PlayerSurfaceHandle)
    suspend fun detachSurface(handle: PlayerSurfaceHandle)
    suspend fun background()
    suspend fun foreground()
    suspend fun cancelLoad(generation: PlaybackGeneration)
}
