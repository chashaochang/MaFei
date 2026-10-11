package cn.mafei.cmp.feature.music

import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals

class MusicLibraryMappingTest {
    @Test fun audioProjectionDropsNonAudioItemsAndKeepsOrder() {
        val items = listOf(
            MediaItem(MediaId("movie"), ServerId("server"), ProviderKind.Jellyfin, "Movie"),
            MediaItem(MediaId("track-1"), ServerId("server"), ProviderKind.Jellyfin, "Track 1", MediaType.Audio),
            MediaItem(MediaId("track-2"), ServerId("server"), ProviderKind.Jellyfin, "Track 2", MediaType.Audio),
        )
        assertEquals(listOf("track-1", "track-2"), items.toMusicTracks().map { it.id })
    }
}
