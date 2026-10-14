package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.session.*
import kotlinx.coroutines.flow.map

@Composable
internal fun rememberPlayerQualityOptions(
    playingMedia: MediaItem?,
    preparedStream: cn.mafei.cmp.data.repositories.playback.PreparedStreamFacts?,
): List<cn.mafei.cmp.player.ui.PlayerQualityOption> {
    return remember(playingMedia, preparedStream) {
        val facts = preparedStream ?: return@remember emptyList()
        val applied = facts.appliedBitrate
        val providerLadder = facts.qualities
        when {
            // provider 自己给了档位表（fnOS 的上游直链）：原样呈现，不造表。
            !providerLadder.isNullOrEmpty() ->
                cn.mafei.cmp.player.ui.PlayerQualityOptions.fromProvider(
                    renditions = providerLadder.map { quality -> quality.label to quality.bitrate },
                    appliedBitrate = applied,
                )
            // 空表 = 这条流没有别的档位（飞牛 range 原画直连）：**不给菜单**，
            // 否则就是旧版那个 bug —— 点了档位却还是同一个文件。
            providerLadder != null -> emptyList()
            // null = provider 按需重编，可以自己造档位表（Jellyfin）。
            else -> {
                val videoStream = playingMedia?.mediaSources?.firstOrNull { source -> source.videoStream != null }?.videoStream
                cn.mafei.cmp.player.ui.PlayerQualityOptions.applicable(
                    videoWidthPx = videoStream?.widthPx,
                    videoHeightPx = videoStream?.heightPx,
                    appliedBitrate = applied,
                )
            }
        }
    }
}
