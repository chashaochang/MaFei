package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.key
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.feature.music.MusicRepeatMode
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.api.PlayerRenderMode
import cn.mafei.cmp.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
internal fun BindBrowsePlayerLifecycle(
    player: BrowsePlayerState,
    playerShellState: PlayerShellState,
    playerRenderMode: PlayerRenderMode,
    detailId: MediaId?,
    onPlayerScreenChanged: (Boolean) -> Unit,
    onPlayerFullscreenChanged: (Boolean) -> Unit,
) {
    val playback = player.playback
    val hostWindowFeatures = LocalMaFeiWindowFeatures.current
    LaunchedEffect(playback, playerRenderMode) {
        playback?.controller?.setRenderMode(playerRenderMode)
    }

    // HarmonyOS PC/2-in-1 owns the native maximize/fullscreen state. When the
    // user presses that system button while the player is open, reflect the
    // host state in the player so its fullscreen button becomes "exit" rather
    // than issuing another enter request. Do not key this effect by the local
    // player state: an internal request is asynchronous and must not be reset
    // before the window manager reports the resulting status.
    LaunchedEffect(
        player.playerExpanded,
        hostWindowFeatures.windowedHost,
        hostWindowFeatures.windowFullscreen,
    ) {
        if (player.playerExpanded && hostWindowFeatures.windowedHost &&
            player.playerFullscreen != hostWindowFeatures.windowFullscreen
        ) {
            player.playerFullscreen = hostWindowFeatures.windowFullscreen
        }
    }
    val videoPlayerPresented = player.playerExpanded && player.playingMedia?.mediaType != MediaType.Audio
    val currentPlayerScreenChanged by rememberUpdatedState(onPlayerScreenChanged)
    DisposableEffect(videoPlayerPresented) {
        currentPlayerScreenChanged(videoPlayerPresented)
        onDispose {
            if (videoPlayerPresented) currentPlayerScreenChanged(false)
        }
    }
    val currentFullscreenChanged by rememberUpdatedState(onPlayerFullscreenChanged)
    DisposableEffect(player.playerExpanded) {
        if (!player.playerExpanded) {
            player.playerFullscreen = false
            // 清晰度是**本次播放会话**的选择，不是全局偏好：看完退出就回到自动。
            player.requestedBitrate = null
        }
        onDispose {
            // Window hosts restore only transitions owned by the player via
            // onPlayerScreenChanged(false), not an app-wide native fullscreen.
            if (player.playerFullscreen && !hostWindowFeatures.windowedHost) currentFullscreenChanged(false)
        }
    }

    LaunchedEffect(detailId) { player.playbackError = null }
    LaunchedEffect(playerShellState.phase) {
        if (playerShellState.phase == PlayerPhase.Error) player.playbackError = "播放失败，请重试"
        if (!player.playbackBusy &&
            (playerShellState.phase == PlayerPhase.Idle || playerShellState.phase == PlayerPhase.Released)
        ) {
            player.playerExpanded = false
        }
    }
    LaunchedEffect(player.pendingSeekPosition, playback) {
        val target = player.pendingSeekPosition ?: return@LaunchedEffect
        delay(90)
        try {
            playback?.controller?.seek(target)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            player.playbackError = "跳转播放位置失败，请重试"
        } finally {
            if (player.pendingSeekPosition == target) player.pendingSeekPosition = null
        }
    }

    // 播完自动衔接下一集（旧版队列的连续播放）。phase 由「播放中」变为
    // 「已结束」时 effect 重启一次，因此只会自动前进一集。
    LaunchedEffect(playerShellState.phase) {
        if (playerShellState.phase == PlayerPhase.Ended) {
            val queue = player.playQueue
            if (player.playingMedia?.mediaType == MediaType.Audio && queue != null) {
                val next = queue.next() ?: when (player.musicRepeatMode) {
                    MusicRepeatMode.Off -> null
                    MusicRepeatMode.All -> queue.copy(index = 0)
                    MusicRepeatMode.One -> queue
                }
                if (next != null) player.startPlaybackItem(next.current, next, resumePositionMs = 0L)
            } else player.playNextEpisode()
        }
    }

}

@Composable
internal fun rememberPlayerShellState(playback: SessionPlaybackRuntime?): PlayerShellState {
    return if (playback == null) PlayerShellState() else {
        val shellState = remember(playback) { playback.controller.state.shellStates() }
        shellState.collectAsState(initial = playback.controller.state.value.toPlayerShellState()).value
    }
}


internal fun kotlinx.coroutines.flow.Flow<PlayerState>.shellStates(): kotlinx.coroutines.flow.Flow<PlayerShellState> =
    map { it.toPlayerShellState() }.distinctUntilChanged()

internal fun PlayerState.toPlayerShellState() = PlayerShellState(
    phase = phase,
    resourceId = resourceId,
)
