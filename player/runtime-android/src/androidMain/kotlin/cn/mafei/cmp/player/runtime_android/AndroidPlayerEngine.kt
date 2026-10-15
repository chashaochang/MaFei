package cn.mafei.cmp.player.runtime_android

/**
 * Engine abstraction over `android.media.MediaPlayer` so the runtime command
 * translation is unit-testable on the JVM with a fake engine. Implementations
 * MUST guarantee that after [reset]/[release] no listener callback for the
 * retired session is delivered anymore; every callback therefore carries the
 * session it belongs to.
 */
interface AndroidPlayerEngine {

    /** Monotonic session id; increments on every [reset] (including via load). */
    val session: Int

    /** Live engine query results; never fabricated by the runtime. */
    val isPlaying: Boolean
    val currentPositionMs: Long
    val durationMs: Long
    val bufferedPositionMs: Long

    fun setListener(listener: AndroidPlayerEngineListener?)
    fun setSurface(surface: Any?)

    /**
     * `android.media.MediaPlayer` exposes exactly two scalings — letterbox or
     * crop-to-fill — and only once a player is prepared, so implementations
     * must remember the request and re-apply it to every new player instance.
     */
    fun setVideoScalingMode(fill: Boolean)

    fun setDataSource(url: String, headers: Map<String, String>)
    fun prepareAsync()
    fun start()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun reset()
    fun release()
}

/** Engine callbacks. [session] lets the runtime drop stale-player events. */
interface AndroidPlayerEngineListener {
    fun onPrepared(session: Int, durationMs: Long)
    fun onCompletion(session: Int)
    fun onError(session: Int, what: Int, extra: Int)
    fun onBuffering(session: Int, buffering: Boolean)
}

/** Periodic engine polling scheduler; real wiring posts to the main looper. */
interface AndroidPollScheduler {
    fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable
}
