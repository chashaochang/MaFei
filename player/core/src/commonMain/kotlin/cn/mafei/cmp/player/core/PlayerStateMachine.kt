package cn.mafei.cmp.player.core

import cn.mafei.cmp.player.api.*

sealed interface PlayerEffect {
    data class Engine(val command: RuntimeCommand) : PlayerEffect
    data class Report(val value: PlayerReport) : PlayerEffect
}

/** Pure deterministic reducer. Not thread-safe: controller serializes every call. */
class PlayerStateMachine(private val capabilities: PlayerCapabilities, private val reportIntervalMs: Long = 10_000) {
    init { require(reportIntervalMs > 0) }
    var state = PlayerState()
        private set
    private var queue = emptyList<PlayableResource>()
    private var wantsPlay = false
    private var resumeOnForeground = false
    private var backgrounded = false
    private var prepared = false
    private var reportedStart = false
    private var nativePaused = false
    private var lastReportedPaused = false
    private var lastReportAt: Long? = null
    private var resumePosition = 0L
    private var surface: PlayerSurfaceHandle? = null

    fun load(resources: List<PlayableResource>, index: Int, resumeMs: Long, autoplay: Boolean): List<PlayerEffect> {
        active(); require(resources.isNotEmpty()); require(index in resources.indices); require(resumeMs >= 0)
        if (resumeMs > 0 && !capabilities.seek) throw UnsupportedPlayerCapability("seek")
        val effects = terminalReport()
        queue = resources.toList()
        wantsPlay = autoplay
        resumeOnForeground = autoplay && backgrounded && !capabilities.backgroundPlayback
        resumePosition = resumeMs
        prepared = false; reportedStart = false; lastReportAt = null
        nativePaused = false; lastReportedPaused = false
        val generation = PlaybackGeneration()
        state = PlayerState(
            phase = PlayerPhase.Loading,
            generation = generation,
            resourceId = queue[index].id,
            queueIndex = index,
            queueSize = queue.size,
            // Picture fitting is a property of the viewer, not of the media: a
            // queued next episode or a re-negotiated stream must keep the
            // ratio the user picked.
            contentMode = state.contentMode,
        )
        return effects + engine(RuntimeCommand.Load(generation, queue[index], resumePosition))
    }

    fun accept(event: RuntimeEvent, nowMs: Long): List<PlayerEffect> {
        if (event.generation !== state.generation || state.phase in terminalPhases) return emptyList()
        val generation = event.generation
        when (event) {
            is RuntimeEvent.Ready -> {
                if (prepared) return emptyList()
                if (event.durationMs?.let { it < 0 } == true) return fail(PlayerFailure.Engine)
                prepared = true
                val position = event.durationMs?.let { resumePosition.coerceAtMost(it) } ?: resumePosition
                state = state.copy(phase = PlayerPhase.Ready, durationMs = event.durationMs, positionMs = position)
                return buildList {
                    if (position > 0) add(engine(RuntimeCommand.Seek(generation, position)))
                    if (wantsPlay && (!backgrounded || capabilities.backgroundPlayback)) add(engine(RuntimeCommand.Play(generation)))
                }
            }
            is RuntimeEvent.Playing -> {
                if (!prepared) return emptyList()
                if (!wantsPlay || (backgrounded && !capabilities.backgroundPlayback)) return listOf(engine(RuntimeCommand.Pause(generation)))
                nativePaused = false
                state = state.copy(phase = PlayerPhase.Playing)
                if (!reportedStart) { reportedStart = true; lastReportAt = nowMs; return listOf(report(PlayerReportKind.Start)) }
                if (lastReportedPaused) { lastReportAt = nowMs; return listOf(report(PlayerReportKind.Progress)) }
            }
            is RuntimeEvent.Paused -> {
                if (!prepared) return emptyList()
                nativePaused = true
                state = state.copy(phase = PlayerPhase.Paused)
                if (reportedStart && !lastReportedPaused) { lastReportAt = nowMs; return listOf(report(PlayerReportKind.Progress)) }
            }
            is RuntimeEvent.Buffering -> {
                if (prepared && wantsPlay && (!backgrounded || capabilities.backgroundPlayback)) state = state.copy(phase = PlayerPhase.Buffering)
            }
            is RuntimeEvent.Progress -> {
                if (!prepared || event.positionMs < 0 || event.bufferedPositionMs < 0) return emptyList()
                if (event.durationMs?.let { it < 0L } == true) return emptyList()
                val duration = event.durationMs?.takeIf { it > 0L } ?: state.durationMs
                state = state.copy(
                    durationMs = duration,
                    positionMs = clamp(event.positionMs, duration),
                    bufferedPositionMs = clamp(event.bufferedPositionMs, duration),
                )
                val previous = lastReportAt
                if (reportedStart && (previous == null || nowMs < previous || nowMs - previous >= reportIntervalMs)) {
                    lastReportAt = nowMs; return listOf(report(PlayerReportKind.Progress))
                }
            }
            is RuntimeEvent.Tracks -> state = state.copy(tracks = event.tracks.toList())
            is RuntimeEvent.Failed -> return fail(event.failure)
            is RuntimeEvent.Ended -> {
                if (!prepared) return emptyList()
                state = state.copy(positionMs = state.durationMs ?: state.positionMs)
                val reports = terminalReport()
                if (state.queueIndex + 1 < queue.size) return reports + load(queue, state.queueIndex + 1, 0, wantsPlay)
                state = state.copy(phase = PlayerPhase.Ended); wantsPlay = false
                return reports
            }
        }
        return emptyList()
    }

    fun play(): List<PlayerEffect> {
        usable(); wantsPlay = true
        resumeOnForeground = backgrounded && !capabilities.backgroundPlayback
        return if (prepared && (!backgrounded || capabilities.backgroundPlayback)) listOf(engine(RuntimeCommand.Play(generation()))) else emptyList()
    }
    fun pause(): List<PlayerEffect> { usable(); wantsPlay = false; resumeOnForeground = false; return if (prepared) listOf(engine(RuntimeCommand.Pause(generation()))) else emptyList() }
    fun seek(positionMs: Long): List<PlayerEffect> {
        usable(); require(positionMs >= 0)
        if (!capabilities.seek) throw UnsupportedPlayerCapability("seek")
        if (!prepared) { resumePosition = positionMs; return emptyList() }
        return listOf(engine(RuntimeCommand.Seek(generation(), clamp(positionMs))))
    }
    fun speed(value: Float): List<PlayerEffect> {
        usable(); require(value.isFinite() && value in 0.25f..4f)
        if (!capabilities.speed) throw UnsupportedPlayerCapability("speed")
        check(prepared) { "Player not ready" }
        state = state.copy(speed = value)
        return listOf(engine(RuntimeCommand.Speed(generation(), value)))
    }
    fun selectTrack(kind: TrackKind, id: String?): List<PlayerEffect> {
        usable()
        if (!capabilities.trackSelection) throw UnsupportedPlayerCapability("trackSelection")
        check(prepared) { "Player not ready" }
        require(id == null && kind == TrackKind.Subtitle || state.tracks.any { it.kind == kind && it.id == id }) { "Unknown track" }
        return listOf(engine(RuntimeCommand.SelectTrack(generation(), kind, id)))
    }

    /**
     * Picture fitting. Deliberately NOT gated on [prepared]: the legacy page
     * keeps the aspect menu live while loading, paused or after the queue
     * ended, because the surface is already on screen in all three.
     */
    fun contentMode(mode: PlayerContentMode): List<PlayerEffect> {
        active()
        if (!capabilities.contentMode) throw UnsupportedPlayerCapability("contentMode")
        if (state.contentMode == mode) return emptyList()
        state = state.copy(contentMode = mode)
        return listOf(engine(RuntimeCommand.ContentMode(mode)))
    }
    fun background(): List<PlayerEffect> {
        active(); backgrounded = true
        if (capabilities.backgroundPlayback || state.generation == null || state.phase in terminalPhases) return emptyList()
        resumeOnForeground = wantsPlay
        return if (prepared && wantsPlay) listOf(engine(RuntimeCommand.Pause(generation()))) else emptyList()
    }
    fun foreground(): List<PlayerEffect> {
        active(); backgrounded = false
        val resume = resumeOnForeground; resumeOnForeground = false
        return if (resume && wantsPlay && prepared && state.phase !in terminalPhases) listOf(engine(RuntimeCommand.Play(generation()))) else emptyList()
    }
    fun attachSurface(handle: PlayerSurfaceHandle): List<PlayerEffect> {
        active(); if (surface === handle) return emptyList()
        val old = surface; surface = handle
        return buildList { if (old != null) add(engine(RuntimeCommand.DetachSurface(old))); add(engine(RuntimeCommand.AttachSurface(handle))) }
    }
    fun detachSurface(handle: PlayerSurfaceHandle): List<PlayerEffect> {
        if (surface !== handle || state.phase == PlayerPhase.Released) return emptyList()
        surface = null; return listOf(engine(RuntimeCommand.DetachSurface(handle)))
    }
    fun cancelLoad(generation: PlaybackGeneration): List<PlayerEffect> =
        if (generation === state.generation && state.phase == PlayerPhase.Loading) stop() else emptyList()

    fun stop(): List<PlayerEffect> {
        if (state.phase == PlayerPhase.Released || state.generation == null) return emptyList()
        val effects = terminalReport() + engine(RuntimeCommand.Stop(generation()))
        queue = emptyList(); state = PlayerState(); wantsPlay = false; prepared = false; resumeOnForeground = false
        return effects
    }
    fun release(): List<PlayerEffect> {
        if (state.phase == PlayerPhase.Released) return emptyList()
        val effects = stop() + engine(RuntimeCommand.Release)
        surface = null; state = PlayerState(phase = PlayerPhase.Released)
        return effects
    }
    private fun fail(failure: PlayerFailure): List<PlayerEffect> {
        val effects = terminalReport()
        state = state.copy(phase = PlayerPhase.Error, failure = failure); wantsPlay = false; resumeOnForeground = false
        return effects + engine(RuntimeCommand.Stop(generation()))
    }
    private fun terminalReport(): List<PlayerEffect> {
        if (!reportedStart) return emptyList()
        reportedStart = false; return listOf(report(PlayerReportKind.Stop))
    }
    private fun report(kind: PlayerReportKind): PlayerEffect.Report {
        lastReportedPaused = nativePaused
        return PlayerEffect.Report(PlayerReport(generation(), state.resourceId!!, kind, state.positionMs, state.durationMs, nativePaused))
    }
    private fun engine(command: RuntimeCommand) = PlayerEffect.Engine(command)
    private fun generation() = checkNotNull(state.generation)
    private fun active() = check(state.phase != PlayerPhase.Released) { "Player released" }
    private fun usable() { active(); check(state.generation != null && state.phase !in terminalPhases) { "No active resource" } }
    private fun clamp(position: Long, durationMs: Long? = state.durationMs) =
        durationMs?.let { position.coerceAtMost(it) } ?: position
    private val terminalPhases = setOf(PlayerPhase.Idle, PlayerPhase.Ended, PlayerPhase.Error, PlayerPhase.Released)
}
