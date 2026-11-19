@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.ArkUIView2
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerFailure
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toKString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.compose.ui.napi.js
import androidx.compose.ui.napi.nApiValue

internal object OhosShortDramaHost {
    private val surfaces = mutableMapOf<Int, OhosShortDramaRuntime>()
    private var lastId = 0
    private var lastPlay = 0
    var platform by mutableStateOf<ShortDramaPlatform?>(null)
        private set
    var foreground by mutableStateOf(true)

    fun nextPlay(): Int = ++lastPlay

    fun receive(raw: String): Boolean {
        val event = runCatching { Json.decodeFromString<ShortDramaEvent>(raw) }.getOrNull() ?: return false
        if (event.available) {
            platform = ShortDramaPlatform(
                createRuntime = { OhosShortDramaRuntime(++lastId, event.advancedSource) },
                surface = { runtime, modifier -> Surface(runtime as OhosShortDramaRuntime, modifier) },
                setSelected = { runtime, selected -> (runtime as OhosShortDramaRuntime).setSelected(selected) },
            )
        } else {
            surfaces[event.slotId]?.receive(event)
        }
        return true
    }

    @Composable
    private fun Surface(runtime: OhosShortDramaRuntime, modifier: Modifier) {
        DisposableEffect(runtime) {
            surfaces[runtime.slotId] = runtime
            onDispose { surfaces.remove(runtime.slotId) }
        }
        val intent = runtime.intent
        ArkUIView2(
            name = "MaFeiShortDramaSurface",
            modifier = modifier,
            parameter = js {
                "slotId"(runtime.slotId.nApiValue())
                "loadId"(intent.loadId.nApiValue())
                "url"(intent.url.nApiValue())
                "httpHeaders"(intent.httpHeaders.nApiValue())
                "allowInsecureTls"(intent.allowInsecureTls.nApiValue())
                "startPositionMs"(intent.startMs.toDouble().nApiValue())
                "playRevision"(intent.playRevision.nApiValue())
                "seekRevision"(intent.seekRevision.nApiValue())
                "seekPositionMs"(intent.seekMs.toDouble().nApiValue())
                "speed"(intent.speed.toDouble().nApiValue())
            },
            background = Color.Black,
            interactive = false,
        )
    }
}

@CName("UpdateOhosShortDramaState")
fun UpdateOhosShortDramaState(payload: CPointer<ByteVar>?): Boolean =
    payload?.toKString()?.let(OhosShortDramaHost::receive) == true

@Serializable
private data class ShortDramaEvent(
    val available: Boolean = false,
    val advancedSource: Boolean = false,
    val slotId: Int = 0,
    val loadId: Int = 0,
    val phase: String = "",
    val firstFrame: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

private data class ShortDramaIntent(
    val loadId: Int = 0,
    val url: String = "",
    val httpHeaders: String = "[]",
    val allowInsecureTls: Boolean = false,
    val startMs: Long = 0,
    val playRevision: Int = 0,
    val seekRevision: Int = 0,
    val seekMs: Long = 0,
    val speed: Float = 1f,
)

private class OhosShortDramaRuntime(val slotId: Int, private val advancedSource: Boolean) : PlayerRuntime {
    override val capabilities = PlayerCapabilities(speed = advancedSource, trackSelection = false)
    private var generation: PlaybackGeneration? = null
    private var eventSink: ((RuntimeEvent) -> Unit)? = null
    private var ready = false
    private var phase = ""
    private var selected = false
    var intent by mutableStateOf(ShortDramaIntent())
        private set

    override fun bind(eventSink: (RuntimeEvent) -> Unit) {
        this.eventSink = eventSink
    }

    fun setSelected(value: Boolean) {
        selected = value
        if (!value) intent = intent.copy(playRevision = 0)
    }

    override fun execute(command: RuntimeCommand) {
        when (command) {
            is RuntimeCommand.Load -> {
                val stream = command.resource
                generation = command.generation
                ready = false
                phase = ""
                intent = ShortDramaIntent(loadId = intent.loadId + 1)
                if ((!advancedSource && (stream.headers.isNotEmpty() || stream.allowInsecureTls)) || stream.audioOnly ||
                    stream.subtitleUrl != null || stream.url.endsWith("#bdiso") ||
                    !stream.url.startsWith("http://") && !stream.url.startsWith("https://")
                ) {
                    eventSink?.invoke(RuntimeEvent.Failed(command.generation, PlayerFailure.UnsupportedMedia))
                } else {
                    intent = intent.copy(
                        url = stream.url,
                        httpHeaders = Json.encodeToString(stream.headers.map { (key, value) -> "$key: $value" }),
                        allowInsecureTls = stream.allowInsecureTls,
                        startMs = command.startPositionMs,
                    )
                }
            }
            is RuntimeCommand.Play -> if (selected && generation === command.generation) {
                intent = intent.copy(playRevision = OhosShortDramaHost.nextPlay())
            }
            is RuntimeCommand.Pause -> if (generation === command.generation) {
                intent = intent.copy(playRevision = 0)
            }
            is RuntimeCommand.Seek -> if (generation === command.generation) {
                intent = intent.copy(seekRevision = intent.seekRevision + 1, seekMs = command.positionMs)
            }
            is RuntimeCommand.Speed -> if (generation === command.generation && capabilities.speed) {
                intent = intent.copy(speed = command.value)
            }
            is RuntimeCommand.Stop -> if (generation === command.generation) reset()
            RuntimeCommand.Release -> {
                reset()
                eventSink = null
            }
            else -> Unit
        }
    }

    private fun reset() {
        generation = null
        intent = ShortDramaIntent(loadId = intent.loadId + 1)
    }

    fun receive(event: ShortDramaEvent) {
        val gen = generation ?: return
        val sink = eventSink ?: return
        if (event.loadId != intent.loadId || event.positionMs < 0 || event.durationMs < 0) return
        val duration = event.durationMs.takeIf { it > 0 }
        var firstReady = false
        if (event.firstFrame && !ready) {
            ready = true
            firstReady = true
            sink(RuntimeEvent.Ready(gen, duration))
        }
        if (ready) sink(RuntimeEvent.Progress(gen, event.positionMs, event.positionMs, duration))
        if (!firstReady && phase == event.phase) return
        phase = event.phase
        when (event.phase) {
            "playing" -> if (ready && intent.playRevision != 0) sink(RuntimeEvent.Playing(gen))
            "paused" -> if (ready) sink(RuntimeEvent.Paused(gen))
            "ended" -> sink(RuntimeEvent.Ended(gen))
            "error" -> sink(RuntimeEvent.Failed(gen, PlayerFailure.Engine))
        }
    }
}
