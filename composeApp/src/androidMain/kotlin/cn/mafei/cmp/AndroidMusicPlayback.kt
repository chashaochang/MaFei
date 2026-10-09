package cn.mafei.cmp

import android.content.Context
import android.content.Intent
import cn.mafei.cmp.player.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** The service keeps the existing process player alive; it never creates an engine or a queue. */
internal object AndroidMusicPlayback {
    private val lease = AndroidBackgroundAudioLease()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var controller: PlayerController? = null
    private var actions: (() -> PlatformPlaybackActions?)? = null
    private var serviceUpdate: (() -> Unit)? = null
    private var requestFocus: (() -> Unit)? = null
    private var pauseForFocus = false
    private var resumeAfterFocus = false
    var state = PlayerState(); private set
    val token get() = lease.token
    val title get() = currentActions()?.title.orEmpty().ifBlank { "马飞" }
    val canNext get() = currentActions()?.onNext != null
    val canPrevious get() = currentActions()?.onPrevious != null

    fun wrap(context: Context, runtime: PlayerRuntime): PlayerRuntime = object : PlayerRuntime {
        override val capabilities get() = runtime.capabilities
        private val owner = Any()
        private var sink: ((RuntimeEvent) -> Unit)? = null
        private var generation: PlaybackGeneration? = null
        override fun bind(eventSink: (RuntimeEvent) -> Unit) { sink = eventSink; runtime.bind(eventSink) }
        override fun execute(command: RuntimeCommand) {
            when (command) {
                is RuntimeCommand.Load -> {
                    observer?.cancel(); observer = null; controller = null; actions = null
                    pauseForFocus = false; resumeAfterFocus = false
                    state = PlayerState(generation = command.generation, phase = PlayerPhase.Loading)
                    val token = lease.install(owner, command.generation, command.resource.audioOnly, checkNotNull(sink))
                    generation = command.generation
                    if (token == 0L) stopService(context)
                    if (token != 0L) {
                        try {
                            val intent = Intent(context, AndroidMusicService::class.java).putExtra(AndroidMusicService.TokenExtra, token)
                            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
                        } catch (_: IllegalStateException) { lease.grant(token, false) }
                        catch (_: SecurityException) { lease.grant(token, false) }
                    }
                }
                is RuntimeCommand.Stop -> if (command.generation === generation && lease.close(owner)) {
                    stopService(context); observer?.cancel(); observer = null; controller = null; actions = null
                }
                RuntimeCommand.Release -> if (lease.close(owner)) {
                    stopService(context); observer?.cancel(); observer = null; controller = null; actions = null
                }
                is RuntimeCommand.Play -> if (command.generation === lease.generation && !lease.granted) {
                    lease.deferPlay(lease.token) {
                        try { runtime.execute(command) }
                        catch (_: Exception) { sink?.invoke(RuntimeEvent.Failed(command.generation, PlayerFailure.Engine)) }
                    }
                    requestFocus?.invoke()
                    return
                }
                is RuntimeCommand.Pause -> lease.cancelPending(owner)
                else -> Unit
            }
            try { runtime.execute(command) }
            catch (failure: Exception) {
                if (lease.close(owner)) stopService(context)
                throw failure
            }
            if (command == RuntimeCommand.Release) sink = null
        }
        override fun setRenderMode(mode: PlayerRenderMode) = runtime.setRenderMode(mode)
    }

    fun bind(controller: PlayerController, generation: PlaybackGeneration?, actions: () -> PlatformPlaybackActions?) {
        if (generation == null || generation !== lease.generation) return
        this.controller = controller; this.actions = actions
        observer?.cancel()
        observer = scope.launch {
            controller.state.collect { next ->
                if (next.generation !== lease.generation) return@collect
                state = next; serviceUpdate?.invoke()
            }
        }
        if (pauseForFocus) command("pause")
        serviceUpdate?.invoke()
    }

    fun serviceAttached(token: Long, update: () -> Unit, requestFocus: () -> Unit): Boolean {
        if (token == 0L || token != lease.token) return false
        serviceUpdate = update; this.requestFocus = requestFocus; return true
    }
    fun serviceDetached(token: Long) {
        if (token != lease.token) return
        serviceUpdate = null; requestFocus = null; lease.grant(token, false)
    }
    fun focus(token: Long, granted: Boolean, transientLoss: Boolean = false) {
        if (token != lease.token) return
        lease.grant(token, granted)
        pauseForFocus = !granted
        if (!granted) {
            val resume = transientLoss && (resumeAfterFocus || state.phase in setOf(PlayerPhase.Playing, PlayerPhase.Buffering))
            command("pause")
            resumeAfterFocus = resume
        } else if (resumeAfterFocus) { resumeAfterFocus = false; command("play") }
    }
    fun command(action: String, value: Long = 0, token: Long = lease.token) {
        if (token == 0L || token != lease.token) return
        val target = controller ?: return
        val generation = lease.generation ?: return
        if (target.state.value.generation !== generation) return
        if (action == "pause" || action == "stop") resumeAfterFocus = false
        when (action) {
            "next" -> currentActions()?.onNext?.invoke()
            "previous" -> currentActions()?.onPrevious?.invoke()
            else -> scope.launch {
                if (token != lease.token || target.state.value.generation !== generation) return@launch
                when (action) {
                    "play" -> target.play()
                    "pause" -> target.pause()
                    "toggle" -> if (target.state.value.phase == PlayerPhase.Playing) target.pause() else target.play()
                    "stop" -> target.stop()
                    "seek" -> target.seek(value.coerceAtLeast(0))
                }
            }
        }
    }
    private fun currentActions() = actions?.invoke()?.takeIf { it.generation === lease.generation }
    private fun stopService(context: Context) {
        serviceUpdate = null; requestFocus = null
        context.stopService(Intent(context, AndroidMusicService::class.java))
    }
}
