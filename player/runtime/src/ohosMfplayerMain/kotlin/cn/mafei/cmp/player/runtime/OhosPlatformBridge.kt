package cn.mafei.cmp.player.runtime

import cn.mafei.cmp.player.api.PlayerContentMode
import cn.mafei.cmp.player.api.PlayerFailure
import cn.mafei.cmp.player.api.PlayerPlaybackPreferences
import cn.mafei.cmp.player.api.PlayerRenderMode
import cn.mafei.cmp.player.api.TrackKind
import cn.mafei.cmp.player.runtime_ohos.OhosNativePlayerBridge
import cn.mafei.cmp.player.runtime_ohos.OhosNativePlayerEvent
import org.mfplayer.cmp.host.OhosEventDisposition
import org.mfplayer.cmp.host.OhosPlayerCommandAction
import org.mfplayer.cmp.host.OhosPlayerEvent
import org.mfplayer.cmp.host.OhosPlayerEventKind
import org.mfplayer.cmp.host.OhosPlayerExternalSubtitle
import org.mfplayer.cmp.host.OhosPlayerFailure
import org.mfplayer.cmp.host.OhosPlayerLoadOptions
import org.mfplayer.cmp.host.OhosPlayerProtocol
import org.mfplayer.cmp.host.OhosPlayerSource
import org.mfplayer.cmp.host.OhosPlayerTrack
import org.mfplayer.cmp.host.PlayerHostBridge

internal fun platformOhosBridge(bridge: OhosNativePlayerBridge): OhosNativePlayerBridge =
    OhosCmpPlayerBridge(bridge)

/** Use the SDK wire session without replacing the ArkTS host that owns the two native engines. */
private class OhosCmpPlayerBridge(private val host: OhosNativePlayerBridge) : OhosNativePlayerBridge by host {
    override val newHandlePerLoad: Boolean = true

    private var handle = 0L
    private var live = false
    private var mode = PlayerRenderMode.Buffer
    private var preferences = PlayerPlaybackPreferences()
    private var playFrom: Long? = null
    private var listener: ((OhosNativePlayerEvent) -> Unit)? = null
    private val receive: (OhosNativePlayerEvent) -> Unit = ::receive
    private val sdk = PlayerHostBridge(transport = ::dispatch)

    override fun create(): Long = host.create().also { handle = it }

    override fun bind(eventSink: (OhosNativePlayerEvent) -> Unit) {
        listener = eventSink
        host.bind(receive)
    }

    override fun unbind(eventSink: (OhosNativePlayerEvent) -> Unit) {
        if (listener !== eventSink) return
        listener = null
        host.unbind(receive)
    }

    override fun setRenderMode(mode: PlayerRenderMode) {
        this.mode = mode
        host.setRenderMode(mode)
    }

    override fun loadUrl(handle: Long, url: String, headers: Map<String, String>, subtitleUrl: String?,
        allowInsecureTls: Boolean, multiConnectionAllowed: Boolean, audioOnly: Boolean, startPositionMs: Long,
        preferences: PlayerPlaybackPreferences,
    ) {
        if (this.handle != handle || sdk.released) return
        this.preferences = preferences
        sdk.startSession()
        live = true
        val sent = sdk.load(
            OhosPlayerSource(url, headers = headers,
                externalSubtitles = listOfNotNull(subtitleUrl?.let { OhosPlayerExternalSubtitle(it) })),
            OhosPlayerLoadOptions(startPositionMs = startPositionMs, playWhenReady = false, audioOnly = audioOnly,
                cacheSizeMb = preferences.cacheSizeMb, renderMode = mode.name,
                multiConnectionEnabled = multiConnectionAllowed, allowInsecureTls = allowInsecureTls),
        )
        if (!sent) {
            live = false
            listener?.invoke(OhosNativePlayerEvent.Failed(handle, PlayerFailure.Engine))
        }
    }

    override fun play(handle: Long, pendingSeekPositionMs: Long?) {
        if (!active(handle)) return
        playFrom = pendingSeekPositionMs
        try { sdk.play() } finally { playFrom = null }
    }
    override fun pause(handle: Long) { if (active(handle)) sdk.pause() }
    override fun seek(handle: Long, positionMs: Long) { if (active(handle)) sdk.seek(positionMs) }
    override fun setSpeed(handle: Long, speed: Float) { if (active(handle)) sdk.setPlaybackSpeed(speed.toDouble()) }
    override fun stop(handle: Long) {
        if (!active(handle)) return
        live = false
        sdk.stop()
    }
    override fun releasePlayer(handle: Long) {
        if (this.handle != handle) return
        live = false
        if (sdk.handle == 0L) host.releasePlayer(handle) else sdk.release()
        this.handle = 0L
    }
    override fun selectTrack(handle: Long, kind: TrackKind, id: String?) {
        if (active(handle)) sdk.selectTrack(kind.name.lowercase(), id)
    }
    override fun setContentMode(handle: Long, mode: PlayerContentMode) {
        // Preserve host fitting even before the first source has been loaded.
        if (active(handle)) sdk.setContentMode(mode.name) else if (handle == this.handle) host.setContentMode(handle, mode)
    }

    private fun active(handle: Long) = live && this.handle == handle

    private fun dispatch(payload: String): Boolean {
        val command = OhosPlayerProtocol.decodeCommand(payload)
        when (command.action) {
            OhosPlayerCommandAction.Load -> {
                val source = command.source ?: return false
                val options = command.options ?: return false
                host.loadUrl(handle, source.url, source.headers, source.externalSubtitles.firstOrNull()?.url,
                    options.allowInsecureTls, options.multiConnectionEnabled, options.audioOnly,
                    options.startPositionMs, preferences.copy(cacheSizeMb = options.cacheSizeMb))
            }
            OhosPlayerCommandAction.Play -> host.play(handle, playFrom)
            OhosPlayerCommandAction.Pause -> host.pause(handle)
            OhosPlayerCommandAction.Stop -> host.stop(handle)
            OhosPlayerCommandAction.Seek -> host.seek(handle, command.positionMs ?: return false)
            OhosPlayerCommandAction.SetSpeed -> host.setSpeed(handle, command.speed?.toFloat() ?: return false)
            OhosPlayerCommandAction.SelectTrack -> {
                val track = command.track ?: return false
                val kind = TrackKind.entries.firstOrNull { it.name.equals(track.kind, true) } ?: return false
                host.selectTrack(handle, kind, track.id)
            }
            OhosPlayerCommandAction.SetContentMode -> {
                val mode = PlayerContentMode.entries.firstOrNull { it.name == command.contentMode } ?: return false
                host.setContentMode(handle, mode)
            }
            OhosPlayerCommandAction.Release -> host.releasePlayer(handle)
            else -> return false
        }
        return true
    }

    private fun receive(event: OhosNativePlayerEvent) {
        if (!active(event.handle)) return
        val kind = when (event) {
            is OhosNativePlayerEvent.Ready -> OhosPlayerEventKind.Ready
            is OhosNativePlayerEvent.Playing -> OhosPlayerEventKind.Playing
            is OhosNativePlayerEvent.Paused -> OhosPlayerEventKind.Paused
            is OhosNativePlayerEvent.Buffering -> OhosPlayerEventKind.Buffering
            is OhosNativePlayerEvent.Progress -> OhosPlayerEventKind.Progress
            is OhosNativePlayerEvent.Tracks -> OhosPlayerEventKind.Tracks
            is OhosNativePlayerEvent.Ended -> OhosPlayerEventKind.Ended
            is OhosNativePlayerEvent.Failed -> OhosPlayerEventKind.Failed
            else -> null // System capabilities and detailed diagnostics stay on the native host contract.
        }
        if (kind != null) {
            val progress = event as? OhosNativePlayerEvent.Progress
            val payload = OhosPlayerProtocol.encodeEvent(OhosPlayerEvent(
                handle = sdk.handle,
                generation = sdk.generation,
                event = kind,
                firstFrame = false,
                positionMs = progress?.positionMs,
                durationMs = progress?.durationMs ?: (event as? OhosNativePlayerEvent.Ready)?.durationMs,
                bufferedPositionMs = progress?.bufferedPositionMs,
                tracks = (event as? OhosNativePlayerEvent.Tracks)?.tracks.orEmpty().map {
                    OhosPlayerTrack(it.id, it.kind.name.lowercase(), it.label, it.language, selected = it.selected)
                },
                failure = (event as? OhosNativePlayerEvent.Failed)?.let { OhosPlayerFailure("engine") },
            ))
            if (sdk.onHostEvent(payload) != OhosEventDisposition.ACCEPTED) return
        }
        listener?.invoke(event)
    }
}
