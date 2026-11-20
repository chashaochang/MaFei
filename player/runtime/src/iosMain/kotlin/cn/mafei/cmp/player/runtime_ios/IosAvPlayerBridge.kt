package cn.mafei.cmp.player.runtime_ios

import cn.mafei.cmp.player.api.PlayerSurfaceHandle
import kotlin.concurrent.AtomicReference

/**
 * iOS engine bridge over AVPlayer. The direct Kotlin/Native AVFoundation
 * binding is NOT compilable with the current toolchain: the bundled
 * `platform.AVFoundation` klib of Kotlin `2.2.21-1.1.0` exports the AVPlayer
 * type shell but none of its members (play/pause/currentItem/timeControlStatus/
 * rate/seekToTime/replaceCurrentItemWithPlayerItem) and neither AVMediaPlayback
 * nor AVPlayerItems resolve — verified by probe compilations (GLM-05 report).
 *
 * The engine therefore arrives as an injectable bridge implemented on the
 * Apple platform side (ObjC++ adapter or a future fixed Kotlin distribution).
 * Every value is a live engine query; implementations must never fabricate
 * readiness or progress.
 */
interface IosAvPlayerBridge {
    fun create(): Long

    /**
     * Loads [url] with [headers]. Feiniu playback REQUIRES token/cookie
     * headers, so implementations unable to apply them MUST throw
     * (the runtime turns that into Failed(UnsupportedMedia)) — silently
     * dropping headers would ship a doomed 401 playback.
     */
    fun loadUrl(handle: Long, url: String, headers: Map<String, String>)
    fun play(handle: Long)
    fun pause(handle: Long)
    fun stop(handle: Long)
    fun seek(handle: Long, positionMs: Long)
    fun setSpeed(handle: Long, speed: Float)
    fun attachLayer(handle: Long, layer: Any?)
    fun detachLayer(handle: Long)
    fun positionMs(handle: Long): Long
    fun durationMs(handle: Long): Long
    fun bufferedMs(handle: Long): Long

    /** 0 paused, 1 waiting/buffering, 2 playing (AVPlayerTimeControlStatus). */
    fun timeControl(handle: Long): Int

    /** 0 unknown, 1 readyToPlay, 2 failed (AVPlayerItemStatus). */
    fun itemStatus(handle: Long): Int

    /** 0 none, 1 network, 2 decode. */
    fun errorKind(handle: Long): Int
    fun releasePlayer(handle: Long)
    /** True only when the platform audio session really grants background music playback. */
    fun configureAudioOnly(handle: Long, audioOnly: Boolean): Boolean = false
    /** Returns a binding token so a retired scene cannot detach a newer scene's controls. */
    fun bindSystemControls(title: String, canNext: Boolean, canPrevious: Boolean,
        handler: (String, Double) -> Unit): Long = 0
    fun unbindSystemControls(binding: Long) {}
    fun createVideoView(): Any = throw UnsupportedOperationException("Video view unavailable")
    fun tracks(handle: Long): List<cn.mafei.cmp.player.api.PlayerTrack> = emptyList()
    fun selectTrack(handle: Long, kind: cn.mafei.cmp.player.api.TrackKind, id: String?) {
        throw UnsupportedOperationException("Track selection unavailable")
    }
    fun contentMode(handle: Long, mode: cn.mafei.cmp.player.api.PlayerContentMode) {
        throw UnsupportedOperationException("Content mode unavailable")
    }
}

/**
 * iOS video surface registry. `PlayerSurfaceHandle` is opaque (frozen API);
 * the mapping to a `CALayer` owned by the UI layer stays here so platform
 * view types never cross into commonMain. Copy-on-write over an atomic
 * reference: lock-free on the Kotlin/Native runtime, safe for concurrent
 * registration and polling reads.
 */
class IosSurfaceRegistry {
    private val layers = AtomicReference(emptyMap<PlayerSurfaceHandle, Any>())

    /** Registers a `CALayer` that the engine will composite video into. */
    fun register(layer: Any): PlayerSurfaceHandle {
        val handle = PlayerSurfaceHandle()
        while (true) {
            val current = layers.value
            if (layers.compareAndSet(current, current + (handle to layer))) return handle
        }
    }

    fun layerOf(handle: PlayerSurfaceHandle): Any? = layers.value[handle]

    fun unregister(handle: PlayerSurfaceHandle): Boolean {
        while (true) {
            val current = layers.value
            if (!current.containsKey(handle)) return false
            if (layers.compareAndSet(current, current - handle)) return true
        }
    }
}
