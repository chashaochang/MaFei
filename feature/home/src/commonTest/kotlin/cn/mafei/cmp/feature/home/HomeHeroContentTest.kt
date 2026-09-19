package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeHeroContentTest {

    @Test
    fun jellyfinRecommendationUsesMovieAndYearWithoutRating() {
        assertEquals(
            "电影 · 2024",
            homeHeroSubtitle(item(ProviderKind.Jellyfin, MediaType.Movie, year = 2024, rating = 8.14)),
        )
        assertEquals(
            "电影",
            homeHeroSubtitle(item(ProviderKind.Jellyfin, MediaType.Movie, rating = 8.0)),
        )
        assertEquals("电影", homeHeroSubtitle(item(ProviderKind.Jellyfin, MediaType.Movie)))
    }

    @Test
    fun jellyfinNonMovieDoesNotInventHeroMetadata() {
        assertNull(homeHeroSubtitle(item(ProviderKind.Jellyfin, MediaType.Series, year = 2024, rating = 9.0)))
    }

    @Test
    fun feiniuRecommendationUsesOnlyYear() {
        assertEquals(
            "2024",
            homeHeroSubtitle(item(ProviderKind.Feiniu, MediaType.Series, year = 2024, rating = 9.8)),
        )
        assertNull(homeHeroSubtitle(item(ProviderKind.Feiniu, MediaType.Movie, rating = 9.8)))
    }

    private fun item(
        provider: ProviderKind,
        mediaType: MediaType,
        year: Int? = null,
        rating: Double? = null,
    ) = MediaItem(
        id = MediaId("item-1"),
        serverId = ServerId("server-1"),
        provider = provider,
        title = "Title",
        mediaType = mediaType,
        year = year,
        communityRating = rating,
        runtimeMs = 7_200_000L,
    )
}
