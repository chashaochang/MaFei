package cn.mafei.cmp.feature.music

import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaUserData
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MusicItemMappingTest {
    @Test fun mapsProviderNeutralAudioFieldsWithoutInventingMetadata() {
        val item = MediaItem(
            id = MediaId("track"),
            serverId = ServerId("server"),
            provider = ProviderKind.Jellyfin,
            title = "Track",
            mediaType = MediaType.Audio,
            albumId = MediaId("album"),
            albumTitle = "Album",
            albumArtist = "Album Artist",
            artistNames = listOf("Artist One", "Artist Two"),
            artistIds = listOf(MediaId("artist")),
            indexNumber = 4,
            parentIndexNumber = 2,
            runtimeMs = 12_345,
            images = listOf(MediaImage(ImageKind.Primary, "https://example.test/track.jpg")),
            userData = MediaUserData(isFavorite = true, playbackPositionMs = 500),
        )
        val track = item.toMusicTrackOrNull()!!
        assertEquals("Artist One / Artist Two", track.artist)
        assertEquals("Album", track.album)
        assertEquals(4, track.trackNumber)
        assertEquals(2, track.discNumber)
        assertEquals(500, track.playbackPositionMs)
        assertEquals("https://example.test/track.jpg", track.imageUrl)
    }

    @Test fun nonAudioItemsAreNotPresentedAsMusic() {
        val item = MediaItem(MediaId("movie"), ServerId("server"), ProviderKind.Jellyfin, "Movie")
        assertNull(item.toMusicTrackOrNull())
    }
}
