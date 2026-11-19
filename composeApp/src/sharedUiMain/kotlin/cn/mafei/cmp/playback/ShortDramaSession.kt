package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.feature.detail.legacyPlaybackResumePositionMs
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.session.SessionPlaybackRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ShortDramaPlatform(
    val createRuntime: () -> PlayerRuntime,
    val surface: @Composable (PlayerRuntime, Modifier) -> Unit,
    val setSelected: (PlayerRuntime, Boolean) -> Unit = { _, _ -> },
)

internal val LocalShortDramaPlatform = staticCompositionLocalOf<ShortDramaPlatform?> { null }
internal val LocalShortDramaForeground = staticCompositionLocalOf { true }

internal class ShortDramaRequest(val item: MediaItem, val options: PlaybackOptions)

internal fun shortDramaWindow(index: Int, episodeCount: Int): IntRange =
    if (index in 0 until episodeCount) maxOf(0, index - 1)..minOf(episodeCount - 1, index + 1) else IntRange.EMPTY

/** A retained page owns its native runtime and provider reporting generation. */
internal class ShortDramaSession(
    private val request: ShortDramaRequest,
    private val scope: CoroutineScope,
    private val platform: ShortDramaPlatform,
    private val createPlayback: (PlayerRuntime) -> SessionPlaybackRuntime,
) {
    class Slot(val runtime: PlayerRuntime, val playback: SessionPlaybackRuntime) {
        var preparation: Job? = null
        val preparationFailed = mutableStateOf(false)
    }

    private val slots = mutableStateMapOf<MediaId, Slot>()
    private val commands = Mutex()
    private var active: MediaItem? = null
    private var closed = false
    private var foreground = true
    private var covered = false
    private var resumeAfterDetails = false
    var speed by mutableStateOf(1f)
        private set

    fun slot(episode: MediaItem): Slot? = slots[episode.id]

    suspend fun select(episodes: List<MediaItem>, index: Int) = commands.withLock {
        val episode = episodes.getOrNull(index) ?: return@withLock
        if (closed) return@withLock
        val changed = active?.id != episode.id
        if (changed) {
            val previousSlot = active?.let(::slot)
            previousSlot?.let { platform.setSelected(it.runtime, false) }
            val previous = previousSlot?.playback?.controller
            if (previous != null && previous.state.value.phase.acceptsPlaybackIntent()) previous.pause()
        }
        if (closed) return@withLock
        val retained = shortDramaWindow(index, episodes.size).map { episodes[it] }
        val retainedIds = retained.mapTo(mutableSetOf()) { it.id }
        (slots.keys - retainedIds).forEach { id -> slots.remove(id)?.let(::retire) }
        active = episode
        val current = slot(episode)
        if (current == null) {
            create(episode)
        } else if (changed && current.preparationFailed.value.not()) {
            platform.setSelected(current.runtime, true)
            when (current.playback.controller.state.value.phase) {
                PlayerPhase.Ended -> prepare(current, episode, positionMs = 0)
                PlayerPhase.Loading, PlayerPhase.Ready, PlayerPhase.Playing, PlayerPhase.Paused, PlayerPhase.Buffering ->
                    if (!covered) current.playback.controller.play() else Unit
                else -> Unit
            }
        } else if (changed) {
            platform.setSelected(current.runtime, true)
        }
        retained.filter { !closed && slot(it) == null }.forEach { create(it) }
    }

    suspend fun setForeground(value: Boolean) = commands.withLock {
        if (closed) return@withLock
        foreground = value
        for (slot in slots.values.toList()) {
            if (closed) break
            if (value) slot.playback.controller.foreground() else slot.playback.controller.background()
        }
    }

    suspend fun toggle() = commands.withLock {
        if (covered || closed) return@withLock
        val episode = active ?: return@withLock
        val slot = slot(episode) ?: return@withLock
        val controller = slot.playback.controller
        val state = controller.state.value
        when {
            slot.preparationFailed.value || state.phase == PlayerPhase.Error ->
                prepare(slot, episode, positionMs = state.positionMs)
            state.phase == PlayerPhase.Ended -> prepare(slot, episode, positionMs = 0)
            state.phase == PlayerPhase.Playing || state.phase == PlayerPhase.Buffering -> controller.pause()
            state.phase == PlayerPhase.Ready || state.phase == PlayerPhase.Paused -> controller.play()
            else -> Unit
        }
    }

    suspend fun seek(id: MediaId, positionMs: Long) = commands.withLock {
        if (closed || covered || active?.id != id) return@withLock
        slots[id]?.playback?.controller?.seek(positionMs)
    }

    suspend fun setSpeed(value: Float) = commands.withLock {
        if (closed || !value.isFinite() || value !in 0.25f..4f || speed == value) return@withLock
        speed = value
        slots.values.forEach { it.applySpeed() }
    }

    suspend fun openDetails() = commands.withLock {
        if (closed || covered) return@withLock
        covered = true
        val controller = active?.let(::slot)?.playback?.controller
        if (controller == null) {
            resumeAfterDetails = true
            return@withLock
        }
        val phase = controller.state.value.phase
        resumeAfterDetails = phase == PlayerPhase.Playing || phase == PlayerPhase.Buffering ||
            phase == PlayerPhase.Loading || phase == PlayerPhase.Idle || phase == PlayerPhase.Ready
        if (phase.acceptsPlaybackIntent()) controller.pause()
    }

    suspend fun closeDetails(play: Boolean) = commands.withLock {
        if (closed || !covered) return@withLock
        covered = false
        val episode = active ?: return@withLock
        val slot = slot(episode) ?: return@withLock
        val controller = slot.playback.controller
        if (play || resumeAfterDetails) {
            if (controller.state.value.phase == PlayerPhase.Ended) prepare(slot, episode, positionMs = 0)
            else if (controller.state.value.phase.acceptsPlaybackIntent()) controller.play()
        }
        resumeAfterDetails = false
    }

    fun close() {
        if (closed) return
        closed = true
        slots.values.forEach(::retire)
        slots.clear()
        active = null
    }

    private suspend fun create(episode: MediaItem) {
        val runtime = platform.createRuntime()
        platform.setSelected(runtime, active?.id == episode.id)
        val slot = Slot(runtime, createPlayback(runtime))
        slots[episode.id] = slot
        if (!foreground) slot.playback.controller.background()
        prepare(slot, episode, legacyPlaybackResumePositionMs(episode))
    }

    private fun prepare(slot: Slot, episode: MediaItem, positionMs: Long) {
        slot.preparation?.cancel()
        slot.preparationFailed.value = false
        slot.preparation = scope.launch {
            try {
                // Selection can change during negotiation. Prepare paused,
                // then honor current intent, not intent captured at request time.
                val generation = slot.playback.play(
                    itemId = episode.id,
                    resumePositionMs = positionMs,
                    autoplay = false,
                    options = request.options.takeIf { episode.id == request.item.id } ?: PlaybackOptions(),
                )
                commands.withLock {
                    if (closed || slot !== this@ShortDramaSession.slot(episode)) return@withLock
                    slot.preparationFailed.value = generation == null
                    val controller = slot.playback.controller
                    if (generation != null && !covered && active?.id == episode.id && controller.state.value.phase.acceptsPlaybackIntent()) {
                        controller.play()
                    }
                }
                if (generation != null && slot.runtime.capabilities.speed) {
                    val state = slot.playback.controller.state.first {
                        it.generation !== generation || it.phase.canSetShortDramaSpeed() ||
                            it.phase in setOf(PlayerPhase.Error, PlayerPhase.Ended, PlayerPhase.Released)
                    }
                    commands.withLock {
                        if (!closed && state.generation === generation && slot === this@ShortDramaSession.slot(episode)) {
                            slot.applySpeed()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                slot.preparationFailed.value = true
            }
        }
    }

    private suspend fun Slot.applySpeed() {
        val controller = playback.controller
        val state = controller.state.value
        if (runtime.capabilities.speed && state.phase.canSetShortDramaSpeed() && state.speed != speed) {
            controller.speed(speed)
        }
    }

    private fun retire(slot: Slot) {
        platform.setSelected(slot.runtime, false)
        slot.preparation?.cancel()
        scope.launch(NonCancellable) { slot.playback.release() }
    }
}

private fun PlayerPhase.canSetShortDramaSpeed(): Boolean = when (this) {
    PlayerPhase.Ready, PlayerPhase.Playing, PlayerPhase.Paused, PlayerPhase.Buffering -> true
    else -> false
}

private fun PlayerPhase.acceptsPlaybackIntent(): Boolean = when (this) {
    PlayerPhase.Loading, PlayerPhase.Ready, PlayerPhase.Playing, PlayerPhase.Paused, PlayerPhase.Buffering -> true
    else -> false
}
