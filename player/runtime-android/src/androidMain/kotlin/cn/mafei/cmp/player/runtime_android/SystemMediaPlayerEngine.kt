package cn.mafei.cmp.player.runtime_android

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Production [AndroidPlayerEngine] over `android.media.MediaPlayer`.
 *
 * MediaPlayer requires all calls on the thread that owns its event looper, so
 * every call is posted to the main looper via [Handler]; callbacks also arrive
 * there. Session discipline: each load runs on a fresh MediaPlayer instance —
 * [reset] (and [release]) RELEASE the previous native player instead of merely
 * resetting it, and every listener closure captures the session id from its
 * creation, so a late callback from a retired player can never be mistaken
 * for the new session.
 *
 * The context is required because HTTP-header streams go through
 * `setDataSource(context, uri, headers)`.
 *
 * Device verification (audio/video decode, surface rendering, buffering
 * transitions) is a Pending real-device item; JVM unit tests cover the
 * runtime translation layer with a fake engine.
 */
class SystemMediaPlayerEngine(private val context: Context) : AndroidPlayerEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessions = AtomicInteger(0)

    @Volatile private var sessionValue = 0
    @Volatile private var listener: AndroidPlayerEngineListener? = null
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var speedValue = 1.0f
    @Volatile private var bufferedPercent = 0
    @Volatile private var fillScaling = false

    override val session: Int get() = sessionValue

    override val isPlaying: Boolean
        get() = try { player?.isPlaying == true } catch (_: IllegalStateException) { false }

    override val currentPositionMs: Long
        get() = try { player?.currentPosition?.toLong() ?: 0L } catch (_: IllegalStateException) { 0L }

    override val durationMs: Long
        get() = try { player?.duration?.takeIf { it > 0 }?.toLong() ?: 0L } catch (_: IllegalStateException) { 0L }

    // MediaPlayer exposes buffering only through OnBufferingUpdateListener
    // (no getBufferedPercentage API); the runtime caches the last percentage.
    override val bufferedPositionMs: Long
        get() = try {
            player?.let { it.duration.toLong() * bufferedPercent / 100L } ?: 0L
        } catch (_: IllegalStateException) { 0L }

    override fun setListener(listener: AndroidPlayerEngineListener?) {
        this.listener = listener
    }

    override fun setSurface(surface: Any?) {
        onLooper {
            val current = player ?: return@onLooper
            when (surface) {
                null -> current.setSurface(null)
                is android.view.Surface -> current.setSurface(surface)
                is android.view.SurfaceHolder -> current.setDisplay(surface)
            }
        }
    }

    override fun setVideoScalingMode(fill: Boolean) {
        fillScaling = fill
        onLooper { applyScaling(player) }
    }

    /**
     * Sticky: a fresh MediaPlayer per load would otherwise silently fall back
     * to the SDK default and undo the viewer's 「填满」 choice. IllegalState
     * before preparation is expected — [applyScaling] runs again from the
     * prepared listener.
     */
    private fun applyScaling(target: MediaPlayer?) {
        val current = target ?: return
        try {
            current.setVideoScalingMode(
                if (fillScaling) {
                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                } else {
                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                },
            )
        } catch (_: IllegalStateException) {
            // Not prepared yet; the prepared listener re-applies.
        }
    }

    override fun setDataSource(url: String, headers: Map<String, String>) {
        onLooper {
            // Every load runs on a FRESH player: the retired one was already
            // release()d by reset(), so there is never a reused instance.
            check(player == null) { "setDataSource requires a reset engine" }
            val created = MediaPlayer()
            val sessionAtCreation = sessionValue
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            created.setOnPreparedListener { mp ->
                // Scaling is only settable once prepared; the request made
                // before this load would otherwise be lost with the old player.
                applyScaling(mp)
                // The CAPTURED session id, never the current field: a late
                // prepared from a retired player keeps its old identity and
                // the runtime drops it.
                listener?.onPrepared(sessionAtCreation, mp.duration.toLong())
            }
            created.setOnCompletionListener { listener?.onCompletion(sessionAtCreation) }
            created.setOnErrorListener { _, what, extra ->
                listener?.onError(sessionAtCreation, what, extra)
                true
            }
            created.setOnInfoListener { _, what, _ ->
                when (what) {
                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> listener?.onBuffering(sessionAtCreation, true)
                    MediaPlayer.MEDIA_INFO_BUFFERING_END -> listener?.onBuffering(sessionAtCreation, false)
                }
                true
            }
            created.setOnBufferingUpdateListener { _, percent ->
                bufferedPercent = percent
            }
            player = created
            created.setDataSource(context, Uri.parse(url), headers.takeIf { it.isNotEmpty() })
        }
    }

    override fun prepareAsync() = onLooper { player?.prepareAsync() }
    override fun start() = onLooper {
        player?.let { current ->
            // Apply the requested speed on start; PlaybackParams is the only
            // public speed API on MediaPlayer (setPlaybackSpeed is absent
            // from the public SDK jar).
            val params = current.playbackParams
            if (params.speed != speedValue) {
                current.playbackParams = params.setSpeed(speedValue)
            }
            current.start()
        }
    }
    override fun pause() = onLooper { player?.pause() }

    override fun seekTo(positionMs: Long) = onLooper {
        // Plain ms seek: SEEK_CLOSEST requires API 26, minSdk is 24.
        player?.seekTo(positionMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
    }

    override fun setPlaybackSpeed(speed: Float) {
        speedValue = speed
        onLooper {
            player?.let { current ->
                val params = current.playbackParams
                if (params.speed != speedValue) {
                    current.playbackParams = params.setSpeed(speedValue)
                }
            }
        }
    }

    override fun reset() {
        sessionValue = sessions.incrementAndGet()
        onLooper {
            // RELEASE (not reset): native decoders and surfaces must be
            // relinquished; a fresh instance is created for the next load.
            player?.release()
            player = null
        }
    }

    override fun release() {
        sessionValue = sessions.incrementAndGet()
        listener = null
        onLooper {
            player?.release()
            player = null
        }
    }

    private fun onLooper(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}

/** Main-looper polling scheduler; [AutoCloseable] cancels the pending posts. */
class MainThreadPollScheduler : AndroidPollScheduler {
    override fun schedule(periodMs: Long, tick: () -> Unit): AutoCloseable {
        val handler = Handler(Looper.getMainLooper())
        val active = AtomicBoolean(true)
        val runnable = object : Runnable {
            override fun run() {
                if (!active.get()) return
                try { tick() } finally {
                    if (active.get()) handler.postDelayed(this, periodMs)
                }
            }
        }
        handler.postDelayed(runnable, periodMs)
        return AutoCloseable { active.set(false); handler.removeCallbacks(runnable) }
    }
}
