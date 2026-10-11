package cn.mafei.cmp.feature.music

import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType

/** Converts the provider-neutral audio item into the legacy music-track shape. */
fun MediaItem.toMusicTrackOrNull(): MusicTrack? {
    if (mediaType != MediaType.Audio) return null
    return MusicTrack(
        id = id.value,
        title = title,
        artist = artistNames.joinToString(" / ").ifBlank { albumArtist.orEmpty() },
        artistId = artistIds.firstOrNull()?.value.orEmpty(),
        album = albumTitle.orEmpty(),
        albumId = albumId?.value.orEmpty(),
        discNumber = parentIndexNumber ?: 0,
        trackNumber = indexNumber ?: 0,
        durationMs = runtimeMs ?: 0L,
        imageUrl = primaryImage?.url.orEmpty(),
        favorite = userData.isFavorite,
        playbackPositionMs = userData.playbackPositionMs,
    )
}

fun Iterable<MediaItem>.toMusicTracks(): List<MusicTrack> = mapNotNull { it.toMusicTrackOrNull() }
