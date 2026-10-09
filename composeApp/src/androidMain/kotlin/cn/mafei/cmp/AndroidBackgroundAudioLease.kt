package cn.mafei.cmp

import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.RuntimeEvent

/** Main-thread lease. A delayed service/callback cannot grant a retired resource background audio. */
internal class AndroidBackgroundAudioLease {
    private var sequence = 0L
    private var owner: Any? = null
    private var sink: ((RuntimeEvent) -> Unit)? = null
    private var pendingPlay: (() -> Unit)? = null
    var token = 0L; private set
    var generation: PlaybackGeneration? = null; private set
    var granted = false; private set

    fun install(owner: Any, generation: PlaybackGeneration, audioOnly: Boolean, sink: (RuntimeEvent) -> Unit): Long {
        retire()
        if (!audioOnly) return 0
        this.owner = owner; this.generation = generation; this.sink = sink
        token = ++sequence
        return token
    }
    fun grant(token: Long, active: Boolean): Boolean {
        if (token == 0L || token != this.token) return false
        if (granted != active) {
            granted = active
            sink?.invoke(RuntimeEvent.BackgroundAudio(checkNotNull(generation), active))
        }
        if (active) { val play = pendingPlay; pendingPlay = null; play?.invoke() }
        else pendingPlay = null
        return true
    }
    fun deferPlay(token: Long, play: () -> Unit): Boolean {
        if (token == 0L || token != this.token || granted) return false
        pendingPlay = play; return true
    }
    fun cancelPending(owner: Any) { if (this.owner === owner) pendingPlay = null }
    fun close(owner: Any): Boolean {
        if (this.owner !== owner) return false
        retire(); return true
    }
    private fun retire() {
        if (granted) sink?.invoke(RuntimeEvent.BackgroundAudio(checkNotNull(generation), false))
        owner = null; generation = null; sink = null; token = 0; granted = false
        pendingPlay = null
    }
}
