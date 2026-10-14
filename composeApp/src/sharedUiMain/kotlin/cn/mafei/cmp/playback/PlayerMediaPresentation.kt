package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.feature.music.toMusicTrackOrNull
import cn.mafei.cmp.session.*

internal fun MediaItem.miniPlayerSubtitle(): String = if (mediaType == MediaType.Audio) {
    toMusicTrackOrNull()?.let { listOf(it.artist, it.album).filter(String::isNotBlank).joinToString(" · ") }.orEmpty()
} else listOfNotNull(
    year?.toString(),
    when (mediaType) {
        cn.mafei.cmp.core.domain.MediaType.Movie -> "电影"
        cn.mafei.cmp.core.domain.MediaType.Series -> "剧集"
        cn.mafei.cmp.core.domain.MediaType.Episode -> "单集"
        cn.mafei.cmp.core.domain.MediaType.Audio -> "音乐"
        else -> null
    },
).joinToString(" · ")
