package cn.mafei.cmp.player.ui

import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState

data class MiniPlayerMedia(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
    }
}

data class MiniPlayerPresentation(
    val isVisible: Boolean,
    val isPlaying: Boolean,
    val isBusy: Boolean,
    val status: String,
    val progress: Float,
)

fun PlayerState.toMiniPlayerPresentation(): MiniPlayerPresentation {
    val visible = phase !in setOf(PlayerPhase.Idle, PlayerPhase.Released)
    val duration = durationMs?.takeIf { it > 0L }
    return MiniPlayerPresentation(
        isVisible = visible,
        isPlaying = phase == PlayerPhase.Playing || phase == PlayerPhase.Buffering,
        isBusy = phase == PlayerPhase.Loading || phase == PlayerPhase.Buffering,
        status = when (phase) {
            PlayerPhase.Loading -> "正在准备"
            PlayerPhase.Ready -> "准备播放"
            PlayerPhase.Playing -> formatPlaybackTime(positionMs, duration)
            PlayerPhase.Paused -> "已暂停 · ${formatPlaybackTime(positionMs, duration)}"
            PlayerPhase.Buffering -> "正在缓冲"
            PlayerPhase.Ended -> "播放结束"
            PlayerPhase.Error -> "播放失败"
            PlayerPhase.Idle, PlayerPhase.Released -> ""
        },
        progress = if (duration == null) 0f else (positionMs.toDouble() / duration).toFloat().coerceIn(0f, 1f),
    )
}

private fun formatPlaybackTime(positionMs: Long, durationMs: Long?): String {
    val position = formatDuration(positionMs)
    return durationMs?.let { "$position / ${formatDuration(it)}" } ?: position
}

private fun formatDuration(milliseconds: Long): String {
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
