package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.providers.jellyfin.wire.BaseItemDto
import cn.mafei.cmp.providers.jellyfin.wire.ImageType
import cn.mafei.cmp.providers.jellyfin.wire.ItemKind
import cn.mafei.cmp.providers.jellyfin.wire.NameLongIdPairDto
import kotlin.test.Test
import kotlin.test.assertEquals

class JellyfinMusicMappingTest {
    @Test fun albumFallbacksMatchLegacyMapper() {
        val item = BaseItemDto(id = "album", type = ItemKind.MusicAlbum,
            albumArtists = listOf(NameLongIdPairDto(name = "Album Artist", id = "album-artist")),
            artists = listOf("Track Artist"),
            artistItems = listOf(NameLongIdPairDto(name = "Track Artist", id = "track-artist")),
            childCount = 0, recursiveItemCount = 12, runTimeTicks = 10000,
            cumulativeRunTimeTicks = 36000000000)
        val mapped = item.toMediaItem(ServerId("server"), { id, _, _ -> id })!!
        assertEquals("Album Artist", mapped.albumArtist)
        assertEquals("album-artist", mapped.artistIds.first().value)
        assertEquals(12, mapped.childCount)
        assertEquals(3600000L, mapped.runtimeMs)
    }

    @Test fun trackImageAndArtistFallBackToPrimaryTagAndAlbumArtist() {
        val item = BaseItemDto(id = "track", type = ItemKind.Audio, albumId = "album",
            imageTags = mapOf("Primary" to "fallback-tag"),
            albumArtists = listOf(NameLongIdPairDto(name = "Artist", id = "artist")))
        val mapped = item.toMediaItem(ServerId("server"), { id, _, tag -> "$id/$tag" })!!
        assertEquals("album/fallback-tag", mapped.primaryImage?.url)
        assertEquals("artist", mapped.artistIds.first().value)
    }
    @Test fun mapsJellyfinMusicMetadataIntoDomain() {
        val item = BaseItemDto(
            id = "track",
            name = "Track",
            type = ItemKind.Audio,
            album = "Album",
            albumId = "album",
            albumArtist = "Album Artist",
            artists = listOf("Artist One", "Artist Two"),
            artistItems = listOf(NameLongIdPairDto(name = "Artist One", id = "artist")),
            albumPrimaryImageTag = "tag",
            indexNumber = 4,
            parentIndexNumber = 2,
        )
        val mapped = item.toMediaItem(ServerId("server"), { id, type, tag -> "$id/$type/$tag" })!!
        assertEquals(MediaType.Audio, mapped.mediaType)
        assertEquals("album", mapped.albumId?.value)
        assertEquals("Album", mapped.albumTitle)
        assertEquals(listOf("Artist One", "Artist Two"), mapped.artistNames)
        assertEquals(listOf("artist"), mapped.artistIds.map { it.value })
        assertEquals("album/${ImageType.Primary}/tag", mapped.images.firstOrNull()?.url)
    }
}
