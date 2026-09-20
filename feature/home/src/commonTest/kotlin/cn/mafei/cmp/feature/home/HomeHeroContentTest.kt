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

    @Test
    fun heroLayerStaysAtOriginBeforeTheFirstLayout() {
        assertEquals(
            0f,
            homeHeroTranslationY(heroOffsetY = null, firstVisibleItemIndex = 0, clearHeightPx = 640f),
        )
    }

    @Test
    fun heroLayerFollowsItsVisibleLazyItem() {
        assertEquals(
            -240f,
            homeHeroTranslationY(heroOffsetY = -240, firstVisibleItemIndex = 0, clearHeightPx = 640f),
        )
    }

    @Test
    fun heroLayerRemainsOffscreenAfterLazyGridDisposesItemZero() {
        assertEquals(
            -640f,
            homeHeroTranslationY(heroOffsetY = null, firstVisibleItemIndex = 4, clearHeightPx = 640f),
        )
    }

    @Test
    fun heroForegroundIsHiddenWhenOnlyTheClippedTailRemains() {
        assertEquals(
            0f,
            homeHeroForegroundAlpha(
                heroOffsetY = -591,
                firstVisibleItemIndex = 0,
                clearHeightPx = 640f,
                hiddenTailPx = 72f,
                fadeDistancePx = 144f,
            ),
        )
    }

    @Test
    fun heroForegroundFadesBeforeItsTailReachesTheViewportEdge() {
        assertEquals(
            0.5f,
            homeHeroForegroundAlpha(
                heroOffsetY = -496,
                firstVisibleItemIndex = 0,
                clearHeightPx = 640f,
                hiddenTailPx = 72f,
                fadeDistancePx = 144f,
            ),
        )
    }

    @Test
    fun disposedHeroForegroundIsFullyTransparent() {
        assertEquals(
            0f,
            homeHeroForegroundAlpha(
                heroOffsetY = null,
                firstVisibleItemIndex = 1,
                clearHeightPx = 640f,
                hiddenTailPx = 72f,
                fadeDistancePx = 144f,
            ),
        )
    }

    @Test
    fun heroOnlyKeepsAdjacentFullScreenPagesWhilePagerMoves() {
        assertEquals(10..10, homeHeroPageRange(currentPage = 10, itemCount = 6, inMotion = false))
        assertEquals(9..11, homeHeroPageRange(currentPage = 10, itemCount = 6, inMotion = true))
        assertEquals(10..10, homeHeroPageRange(currentPage = 10, itemCount = 1, inMotion = true))
    }

    @Test
    fun heroAutoplayStopsBeforeOnlyTheClippedTailRemains() {
        assertEquals(
            true,
            homeHeroAutoplayVisible(
                heroOffsetY = -420,
                heroHeightPx = 640,
                firstVisibleItemIndex = 0,
                minimumVisiblePx = 160,
            ),
        )
        assertEquals(
            false,
            homeHeroAutoplayVisible(
                heroOffsetY = -500,
                heroHeightPx = 640,
                firstVisibleItemIndex = 0,
                minimumVisiblePx = 160,
            ),
        )
        assertEquals(
            false,
            homeHeroAutoplayVisible(
                heroOffsetY = null,
                heroHeightPx = null,
                firstVisibleItemIndex = 2,
                minimumVisiblePx = 160,
            ),
        )
    }

    @Test
    fun heroPrefetchKeepsOnlyCurrentAndAdjacentItemsWithoutDuplicates() {
        assertEquals(listOf(2, 3, 1), homeHeroPrefetchOrder(itemCount = 6, currentIndex = 2))
        assertEquals(listOf(0, 1), homeHeroPrefetchOrder(itemCount = 2, currentIndex = 0))
        assertEquals(emptyList(), homeHeroPrefetchOrder(itemCount = 0, currentIndex = 0))
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
