package cn.mafei.cmp.player.ui

import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState

data class NowPlayingMedia(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String? = null,
    val live: Boolean = false,
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
    }
}

data class NowPlayingPresentation(
    val isPlaying: Boolean,
    val isBusy: Boolean,
    val canSeek: Boolean,
    val showRetry: Boolean,
    val status: String,
    val elapsed: String,
    val duration: String,
    val progress: Float,
    val bufferedProgress: Float,
)

internal fun useWideNowPlayingLayout(widthDp: Float, heightDp: Float): Boolean =
    widthDp >= 760f || (widthDp >= 600f && widthDp > heightDp)

fun PlayerState.toNowPlayingPresentation(
    capabilities: PlayerCapabilities,
): NowPlayingPresentation {
    val duration = durationMs?.takeIf { it > 0L }
    val clampedPosition = duration?.let { positionMs.coerceIn(0L, it) } ?: positionMs.coerceAtLeast(0L)
    val clampedBuffered = duration?.let { bufferedPositionMs.coerceIn(0L, it) }
        ?: bufferedPositionMs.coerceAtLeast(0L)
    return NowPlayingPresentation(
        isPlaying = phase == PlayerPhase.Playing || phase == PlayerPhase.Buffering,
        isBusy = phase == PlayerPhase.Loading || phase == PlayerPhase.Buffering,
        canSeek = capabilities.seek && duration != null && phase !in setOf(
            PlayerPhase.Idle,
            PlayerPhase.Loading,
            PlayerPhase.Released,
        ),
        showRetry = phase == PlayerPhase.Error,
        status = playerPhaseStatus(phase),
        elapsed = formatPlayerDuration(clampedPosition),
        duration = duration?.let(::formatPlayerDuration) ?: "--:--",
        progress = duration?.let { (clampedPosition.toDouble() / it).toFloat() } ?: 0f,
        bufferedProgress = duration?.let { (clampedBuffered.toDouble() / it).toFloat() } ?: 0f,
    )
}

/** Shared with the 「播放信息」 card so both surfaces name a phase identically. */
internal fun playerPhaseStatus(phase: PlayerPhase): String = when (phase) {
    PlayerPhase.Idle -> "尚未播放"
    PlayerPhase.Loading -> "正在准备播放"
    PlayerPhase.Ready -> "可以开始播放"
    PlayerPhase.Playing -> "正在播放"
    PlayerPhase.Paused -> "已暂停"
    PlayerPhase.Buffering -> "正在缓冲"
    PlayerPhase.Ended -> "播放结束"
    PlayerPhase.Error -> "播放出现问题"
    PlayerPhase.Released -> "播放器已关闭"
}

internal fun formatPlayerDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
