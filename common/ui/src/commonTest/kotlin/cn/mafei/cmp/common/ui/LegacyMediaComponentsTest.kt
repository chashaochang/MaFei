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
    fun listSubtitleKeepsTheLegacyYearFirstMapping() {
        assertEquals("2024", legacyListSubtitle(item(MediaType.Movie)))
        assertEquals("2024", legacyListSubtitle(item(MediaType.Episode, season = 1, episode = 2)))
        assertEquals(
            "S1:E2 - Pilot",
            legacyListSubtitle(item(MediaType.Episode, season = 1, episode = 2, year = null)),
        )
        assertEquals(
            "第 2 集 - Pilot",
            legacyListSubtitle(item(MediaType.Episode, season = null, episode = 2, year = null)),
        )
        assertEquals(
            "Pilot",
            legacyListSubtitle(item(MediaType.Episode, season = null, episode = null, year = null)),
        )
    }

    @Test
    fun actorWorkSubtitleUsesBaseItemCardGrammarInstead() {
        assertEquals("2024", legacyBaseItemSubtitle(item(MediaType.Movie)))
        assertEquals(
            "S1:E2 - Pilot",
            legacyBaseItemSubtitle(item(MediaType.Episode, season = 1, episode = 2)),
        )
        assertEquals(
            "S0:E0 - Pilot",
            legacyBaseItemSubtitle(item(MediaType.Episode, season = null, episode = null, year = null)),
        )
    }

    @Test
    fun ratingUsesTheLegacyOneDecimalRoundingInsteadOfTruncation() {
        assertEquals("9.0", formatLegacyRating(8.96))
        assertEquals("8.9", formatLegacyRating(8.94))
    }

    @Test
    fun mediaGridAddsColumnsAsTabletContentWidthGrows() {
        assertEquals(4, legacyMediaColumns(719f))
        assertEquals(5, legacyMediaColumns(720f))
        assertEquals(6, legacyMediaColumns(840f))
        assertEquals(6, legacyMediaColumns(1199f))
        assertEquals(8, legacyMediaColumns(1200f))
    }
}
