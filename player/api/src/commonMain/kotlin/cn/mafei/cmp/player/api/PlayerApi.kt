package cn.mafei.cmp.player.api

import kotlinx.coroutines.flow.StateFlow

/** One negotiated, already trust-checked stream. Never persist URLs or headers. */
class PlayableResource(
    val id: String,
    val url: String,
    headers: Map<String, String> = emptyMap(),
    val subtitleUrl: String? = null,
    val allowInsecureTls: Boolean = false,
    val multiConnectionAllowed: Boolean = true,
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

/**
 * Legacy ArkUI 「画面比例」 (`PlayerContentMode`). The picture is fitted in two
 * places, and they never overlap:
 *
 * - The host (Compose) owns the geometry of the video AREA — [Original] takes
 *   the source's own pixel size at 1:1, the `Ratio*` entries reshape the area
 *   into a fixed frame, [Fit]/[Fill] keep the whole area.
 * - The native surface owns only how the picture inside that area is scaled:
 *   [fills] picks crop-to-fill, everything else letterboxes.
 *
 * Keeping the split here means the three ratio modes work on every backend
 * without provider-specific negotiation.
 */
enum class PlayerContentMode {
    /** 适应 — whole picture inside the area (letterbox). */
    Fit,

    /** 填满 — crop the picture to fill the area. */
    Fill,

    /** 原始比例 — source pixels at 1:1, centred; the overflow is clipped. */
    Original,

    Ratio16x9,
    Ratio4x3,
    Ratio21x9,
    ;

    /** True when the surface must crop instead of letterboxing. */
    val fills: Boolean get() = this == Fill

    /** Fixed area shape in width/height; null keeps the natural area. */
    val forcedAspectRatio: Float? get() = when (this) {
        Ratio16x9 -> 16f / 9f
        Ratio4x3 -> 4f / 3f
        Ratio21x9 -> 21f / 9f
        else -> null
    }
}

data class PlayerTrack(
    val id: String,
    val kind: TrackKind,
    val label: String,
    val language: String? = null,
    val selected: Boolean = false,
)
data class PlayerCapabilities(
    val seek: Boolean = true,
    val speed: Boolean = true,
    val trackSelection: Boolean = true,
    val backgroundPlayback: Boolean = false,
    /**
     * The surface can apply [PlayerContentMode.fills] natively (crop vs
     * letterbox). False keeps the 「画面比例」 menu hidden: the ratio-only half
     * would render while 「填满」 silently letterboxed, which is worse than not
     * offering the command.
     */
    val contentMode: Boolean = false,
)
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
    /** Host-selected picture fitting; survives generation changes like the old page-local state. */
    val contentMode: PlayerContentMode = PlayerContentMode.Fit,
)

/** Runtime callbacks must retain the generation of the originating native player/load. */
sealed interface RuntimeEvent {
    val generation: PlaybackGeneration
    data class Ready(override val generation: PlaybackGeneration, val durationMs: Long?) : RuntimeEvent
    data class Playing(override val generation: PlaybackGeneration) : RuntimeEvent
    data class Paused(override val generation: PlaybackGeneration) : RuntimeEvent
    data class Buffering(override val generation: PlaybackGeneration) : RuntimeEvent
    data class Progress(
        override val generation: PlaybackGeneration,
        val positionMs: Long,
        val bufferedPositionMs: Long,
        /** Duration can become known after Ready; carry it without replaying Ready side effects. */
        val durationMs: Long? = null,
    ) : RuntimeEvent
    data class Tracks(override val generation: PlaybackGeneration, val tracks: List<PlayerTrack>) : RuntimeEvent
    data class Ended(override val generation: PlaybackGeneration) : RuntimeEvent
    data class Failed(override val generation: PlaybackGeneration, val failure: PlayerFailure) : RuntimeEvent
}

/** Commands acknowledge submission, not playback success. Native runtime emits confirmation events. */
sealed interface RuntimeCommand {
    data class Load(
        val generation: PlaybackGeneration,
        val resource: PlayableResource,
        val startPositionMs: Long = 0L,
    ) : RuntimeCommand
    data class Play(val generation: PlaybackGeneration) : RuntimeCommand
    data class Pause(val generation: PlaybackGeneration) : RuntimeCommand
    data class Seek(val generation: PlaybackGeneration, val positionMs: Long) : RuntimeCommand
    data class Stop(val generation: PlaybackGeneration) : RuntimeCommand
    data class Speed(val generation: PlaybackGeneration, val value: Float) : RuntimeCommand
    data class SelectTrack(val generation: PlaybackGeneration, val kind: TrackKind, val id: String?) : RuntimeCommand

    /**
     * Surface-scoped, generation-less like [AttachSurface]: the picture fitting
     * belongs to the video surface, which outlives individual loads. Runtimes
     * must re-apply it to every native player they create (Android releases
     * and rebuilds its `MediaPlayer` per load).
     */
    data class ContentMode(val mode: PlayerContentMode) : RuntimeCommand

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
 * Platform implementations live in the corresponding `player:runtime-*`
 * modules; this API intentionally stays free of native engine and UI types.
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
    suspend fun setContentMode(mode: PlayerContentMode)
    suspend fun attachSurface(handle: PlayerSurfaceHandle)
    suspend fun detachSurface(handle: PlayerSurfaceHandle)
    suspend fun background()
    suspend fun foreground()
    suspend fun cancelLoad(generation: PlaybackGeneration)
}
