package cn.mafei.cmp.player.runtime

import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerEngineType
import cn.mafei.cmp.player.api.PlayerFailure
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.PlayerSurfaceHandle
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import cn.mafei.cmp.player.api.TrackKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.mfplayer.cmp.CommandResult
import org.mfplayer.cmp.PlayerContentMode
import org.mfplayer.cmp.PlayerEngine
import org.mfplayer.cmp.PlayerEvent
import org.mfplayer.cmp.PlayerOptions
import org.mfplayer.cmp.PlayerPhase
import org.mfplayer.cmp.PlaybackGeneration as SdkGeneration

/**
 * Adapts one SDK engine to one MaFei controller. Commands and SDK events are serialized on the
 * host thread: an event sent synchronously by load cannot run before the generation is recorded.
 */
internal class MfPlayerRuntime(
    override val capabilities: PlayerCapabilities,
    private val createEngine: () -> PlayerEngine,
    private val surface: (PlayerEngine, PlayerSurfaceHandle, Boolean) -> Unit = { _, _, _ -> },
    private val options: (RuntimeCommand.Load) -> PlayerOptions = {
        PlayerOptions(startPositionMs = it.startPositionMs, playWhenReady = false, audioOnly = it.resource.audioOnly)
    },
    hostDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : PlayerRuntime {
    private sealed interface Message {
        data class Bind(val sink: (RuntimeEvent) -> Unit) : Message
        data class Command(val value: RuntimeCommand) : Message
        data class Event(val value: PlayerEvent) : Message
    }

    private data class LoadBinding(
        val generation: PlaybackGeneration,
        val sdkGeneration: SdkGeneration,
    )

    private val inbox = Channel<Message>(Channel.UNLIMITED)
    private var sink: ((RuntimeEvent) -> Unit)? = null
    private var engine: PlayerEngine? = null
    private var active: LoadBinding? = null
    private var attachedSurface: PlayerSurfaceHandle? = null
    private var contentMode = PlayerContentMode.FIT

    init {
        CoroutineScope(hostDispatcher).launch {
            try {
                for (message in inbox) when (message) {
                    is Message.Bind -> sink = message.sink
                    is Message.Event -> accept(message.value)
                    is Message.Command -> {
                        if (message.value == RuntimeCommand.Release) break
                        apply(message.value, this)
                    }
                }
            } finally {
                inbox.cancel()
                try {
                    engine?.let { current ->
                        try {
                            attachedSurface?.let { surface(current, it, false) }
                        } finally {
                            current.release()
                        }
                    }
                } finally {
                    attachedSurface = null
                    active = null
                    engine = null
                    sink = null
                }
            }
        }
    }

    override fun bind(eventSink: (RuntimeEvent) -> Unit) {
        inbox.trySend(Message.Bind(eventSink))
    }

    override fun execute(command: RuntimeCommand) {
        inbox.trySend(Message.Command(command))
    }

    private fun apply(command: RuntimeCommand, scope: CoroutineScope) {
        try {
            when (command) {
                is RuntimeCommand.Load -> load(command, scope)
                is RuntimeCommand.AttachSurface -> {
                    attachedSurface?.let { previous -> engine?.let { surface(it, previous, false) } }
                    attachedSurface = command.handle
                    engine?.let { surface(it, command.handle, true) }
                }
                is RuntimeCommand.DetachSurface -> if (attachedSurface === command.handle) {
                    attachedSurface = null
                    engine?.let { surface(it, command.handle, false) }
                }
                is RuntimeCommand.ContentMode -> {
                    contentMode = if (command.mode.fills) PlayerContentMode.FILL else PlayerContentMode.FIT
                    engine?.setContentMode(contentMode)
                }
                is RuntimeCommand.Play -> forActive(command.generation) { it.play() }
                is RuntimeCommand.Pause -> forActive(command.generation) { it.pause() }
                is RuntimeCommand.Seek -> forActive(command.generation) { it.seekTo(command.positionMs) }
                is RuntimeCommand.Speed -> forActive(command.generation) { it.setPlaybackSpeed(command.value.toDouble()) }
                is RuntimeCommand.SelectTrack -> forActive(command.generation) {
                    when (command.kind) {
                        TrackKind.Audio -> it.selectAudioTrack(checkNotNull(command.id))
                        TrackKind.Subtitle -> it.selectSubtitleTrack(command.id)
                        TrackKind.Video -> return@forActive null
                    }
                }
                is RuntimeCommand.Stop -> if (active?.generation === command.generation) {
                    active = null
                    engine?.stop()
                }
                RuntimeCommand.Release -> error("Release is handled by the worker")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val generation = (command as? RuntimeCommand.Load)?.generation ?: active?.generation ?: return
            emit(RuntimeEvent.Failed(generation, PlayerFailure.Engine))
        }
    }

    private fun load(command: RuntimeCommand.Load, scope: CoroutineScope) {
        val current = engine ?: createEngine().also { created ->
            engine = created
            scope.launch { created.events.collect { inbox.trySend(Message.Event(it)) } }
            attachedSurface?.let { surface(created, it, true) }
        }
        active = null
        val result = current.load(command.resource.toSdkSource(), options(command).copy(contentMode = contentMode))
        when (result) {
            is CommandResult.Accepted -> {
                active = LoadBinding(command.generation, current.snapshot.value.generation)
                emit(RuntimeEvent.EngineChanged(command.generation, PlayerEngineType.Mpv))
            }
            is CommandResult.Rejected -> {
                current.stop()
                emit(RuntimeEvent.Failed(command.generation, result.failure.toMaFeiFailure()))
            }
        }
    }

    private inline fun forActive(generation: PlaybackGeneration, command: (PlayerEngine) -> CommandResult?) {
        val current = engine ?: return
        if (active?.generation !== generation) return
        val result = command(current)
        if (result is CommandResult.Rejected) emit(RuntimeEvent.Failed(generation, result.failure.toMaFeiFailure()))
    }

    private fun accept(event: PlayerEvent) {
        val binding = active ?: return
        if (binding.sdkGeneration != event.generation) return
        val generation = binding.generation
        val snapshot = engine?.snapshot?.value?.takeIf { it.generation == event.generation }
        val mapped = when (event) {
            is PlayerEvent.Ready -> RuntimeEvent.Ready(generation, event.durationMs)
            is PlayerEvent.Playing -> RuntimeEvent.Playing(generation)
            is PlayerEvent.Paused -> RuntimeEvent.Paused(generation)
            is PlayerEvent.Buffering -> when {
                event.isBuffering -> RuntimeEvent.Buffering(generation)
                snapshot?.phase == PlayerPhase.PLAYING -> RuntimeEvent.Playing(generation)
                snapshot?.phase == PlayerPhase.PAUSED -> RuntimeEvent.Paused(generation)
                else -> null
            }
            is PlayerEvent.Progress -> RuntimeEvent.Progress(
                generation, event.positionMs, event.bufferedPositionMs, event.durationMs,
            )
            is PlayerEvent.Tracks -> RuntimeEvent.Tracks(generation, event.tracks.map { it.toMaFeiTrack() })
            is PlayerEvent.FirstFrame, is PlayerEvent.VideoInfo, is PlayerEvent.Stats, is PlayerEvent.Fallback ->
                snapshot?.let { RuntimeEvent.Diagnostics(generation, it.toMaFeiDiagnostics()) }
            is PlayerEvent.Ended -> RuntimeEvent.Ended(generation)
            is PlayerEvent.Failed -> RuntimeEvent.Failed(generation, event.failure.toMaFeiFailure())
            is PlayerEvent.LoadRejected -> RuntimeEvent.Failed(generation, event.failure.toMaFeiFailure())
            is PlayerEvent.Chapters, is PlayerEvent.Released -> null
        }
        mapped?.let(::emit)
    }

    private fun emit(event: RuntimeEvent) {
        sink?.invoke(event)
    }
}
