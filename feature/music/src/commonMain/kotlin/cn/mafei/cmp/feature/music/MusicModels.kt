package cn.mafei.cmp.feature.music

/** Provider-neutral projections of the legacy music library content. */
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String = "",
    val artistId: String = "",
    val album: String = "",
    val albumId: String = "",
    val discNumber: Int = 0,
    val trackNumber: Int = 0,
    val durationMs: Long = 0L,
    val imageUrl: String = "",
    val favorite: Boolean = false,
    val playbackPositionMs: Long = 0L,
)

data class MusicAlbum(
    val id: String,
    val title: String,
    val artist: String = "",
    val artistId: String = "",
    val year: Int = 0,
    val imageUrl: String = "",
    val trackCount: Int = 0,
    val durationMs: Long = 0L,
    val favorite: Boolean = false,
    val overview: String = "",
)

data class MusicArtist(
    val id: String,
    val name: String,
    val imageUrl: String = "",
    val albumCount: Int = 0,
    val trackCount: Int = 0,
    val overview: String = "",
)

data class MusicQueueItem(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val albumId: String = "",
    val imageUrl: String = "",
    val durationMs: Long = 0L,
    val favorite: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val scope: String,
)

enum class MusicRepeatMode { Off, All, One }
