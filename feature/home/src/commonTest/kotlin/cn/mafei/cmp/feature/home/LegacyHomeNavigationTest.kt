package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyHomeNavigationTest {
    @Test
    fun recommendedEpisodeOpensOwningSeriesWithLegacyMediaName() {
        val episode = item(
            id = "episode",
            type = MediaType.Episode,
            seriesId = MediaId("series"),
        )

        assertEquals(
            LegacyHomeDetailTarget(MediaId("series"), "电视节目"),
            episode.legacyHomeHeroDetailTarget(),
        )
    }

    @Test
    fun recommendedMovieOpensItselfWithMovieLabel() {
        val movie = item(id = "movie", type = MediaType.Movie)

        assertEquals(
            LegacyHomeDetailTarget(MediaId("movie"), "电影"),
            movie.legacyHomeHeroDetailTarget(),
        )
    }

    @Test
    fun latestItemKeepsItsLibraryContext() {
        val collection = MediaCollection(
            id = CollectionId("shows"),
            serverId = ServerId("server"),
            provider = ProviderKind.Jellyfin,
            name = "电视剧",
        )
        val series = item(
            id = "series",
            type = MediaType.Series,
            collectionId = collection.id,
        )

        assertEquals("电视剧", series.legacyHomeLatestMediaName(listOf(collection)))
        assertEquals("最近添加", series.copy(collectionId = null).legacyHomeLatestMediaName(listOf(collection)))
    }

    private fun item(
        id: String,
        type: MediaType,
        seriesId: MediaId? = null,
        collectionId: CollectionId? = null,
    ) = MediaItem(
        id = MediaId(id),
        serverId = ServerId("server"),
        provider = ProviderKind.Jellyfin,
        title = id,
        mediaType = type,
        seriesId = seriesId,
        collectionId = collectionId,
    )
}
