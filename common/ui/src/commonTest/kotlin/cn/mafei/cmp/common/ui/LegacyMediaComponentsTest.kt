package cn.mafei.cmp.common.ui

import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyMediaComponentsTest {
    private fun item(
        type: MediaType,
        title: String = "Pilot",
        season: Int? = null,
        episode: Int? = null,
        year: Int? = 2024,
    ) = MediaItem(
        id = MediaId("id"),
        serverId = ServerId("server"),
        provider = ProviderKind.Jellyfin,
        title = title,
        mediaType = type,
        seasonNumber = season,
        episodeNumber = episode,
        year = year,
    )

    @Test
    fun listSubtitleMatchesLegacyEpisodeGrammar() {
        assertEquals("2024", legacyListSubtitle(item(MediaType.Movie)))
        assertEquals("S1:E2 - Pilot", legacyListSubtitle(item(MediaType.Episode, season = 1, episode = 2)))
        assertEquals("第 2 集 - Pilot", legacyListSubtitle(item(MediaType.Episode, season = null, episode = 2)))
        assertEquals("Pilot", legacyListSubtitle(item(MediaType.Episode, season = null, episode = null)))
    }
}
