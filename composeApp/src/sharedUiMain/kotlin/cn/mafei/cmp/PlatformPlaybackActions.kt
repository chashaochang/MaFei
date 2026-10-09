package cn.mafei.cmp

import androidx.compose.runtime.staticCompositionLocalOf
import cn.mafei.cmp.player.api.PlaybackGeneration

/** System controls reuse the visible player's actions and queue, never a platform-owned queue. */
internal data class PlatformPlaybackActions(
    val generation: PlaybackGeneration?,
    val mediaId: String,
    val title: String,
    val onPlay: () -> Unit,
    val onPause: () -> Unit,
    val onStop: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onSpeed: (Float) -> Unit,
    val onNext: (() -> Unit)?,
    val onPrevious: (() -> Unit)?,
)

internal val LocalPlatformPlaybackActions = staticCompositionLocalOf<PlatformPlaybackActions?> { null }
