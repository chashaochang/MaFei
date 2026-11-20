package cn.mafei.cmp.player.runtime

import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import cn.mafei.cmp.player.runtime_macos.MacSurfaceRegistry
import org.mfplayer.cmp.desktop.MacPlayerAdapter
import java.awt.Component
import javax.swing.JPanel

val usesSdkMacVideoSurface: Boolean get() = true

fun createPlayerRuntime(surfaces: MacSurfaceRegistry): PlayerRuntime {
    var mounted: Pair<JPanel, Component>? = null
    val video = MfPlayerRuntime(
        capabilities = PlayerCapabilities(contentMode = true),
        createEngine = { MacPlayerAdapter.create() },
        // The rc.1 bridge reports file-loaded directly as Paused when autoplay is disabled.
        pausedLoadIsReady = true,
        surface = { engine, handle, attach ->
            val adapter = engine as MacPlayerAdapter
            if (attach) {
                surfaces.containerOf(handle)?.let { panel ->
                    val canvas = adapter.createVideoCanvas().apply { isFocusable = false }
                    mounted = panel to canvas
                    panel.add(canvas)
                    panel.revalidate()
                }
            } else {
                mounted?.let { (panel, canvas) ->
                    adapter.detachVideoCanvas(canvas)
                    panel.remove(canvas)
                    panel.revalidate()
                    panel.repaint()
                }
                mounted = null
            }
        },
    )
    return MacPreviewPlayerRuntime(video, createMacSystemPlayerRuntime(surfaces))
}

/** rc.1's mpv adapter explicitly rejects audio-only sources. */
private class MacPreviewPlayerRuntime(
    private val video: PlayerRuntime,
    private val music: PlayerRuntime,
) : PlayerRuntime {
    override val capabilities = video.capabilities
    private var selected = video
    private var generation: PlaybackGeneration? = null

    override fun bind(eventSink: (RuntimeEvent) -> Unit) {
        video.bind(eventSink)
        music.bind(eventSink)
    }

    override fun execute(command: RuntimeCommand) {
        when (command) {
            is RuntimeCommand.AttachSurface, is RuntimeCommand.DetachSurface -> video.execute(command)
            RuntimeCommand.Release -> try {
                video.execute(command)
            } finally {
                music.execute(command)
            }
            is RuntimeCommand.Load -> {
                val next = if (command.resource.audioOnly) music else video
                if (selected !== next) {
                    generation?.let { selected.execute(RuntimeCommand.Stop(it)) }
                    selected = next
                }
                generation = command.generation
                selected.execute(command)
            }
            is RuntimeCommand.ContentMode -> video.execute(command)
            else -> selected.execute(command)
        }
    }
}
