package cn.mafei.cmp.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 展示字段的格式验收：断言内容与 `MediaPresentation.kt` 里每个字段 KDoc 写明的
 * **期望格式**逐条对应。改格式必须先改这里的断言（和 KDoc），否则不允许合入。
 */
class MediaPresentationTest {

    // --- heroSubtitle -------------------------------------------------------

    @Test
    fun typeThenYearHeroSubtitleKeepsTypeWordThenYear() {
        assertEquals("电影 · 2024", jellyfinMovie(year = 2024).heroSubtitle)
        assertEquals("电影", jellyfinMovie(year = null).heroSubtitle)
        assertEquals("电影", jellyfinMovie(year = 0).heroSubtitle)
    }

    @Test
    fun typeThenYearHeroSubtitleRefusesNonMovies() {
        assertNull(jellyfin(MediaType.Series, year = 2024).heroSubtitle)
        assertNull(jellyfin(MediaType.Episode, year = 2024).heroSubtitle)
    }

    @Test
    fun yearOnlyHeroSubtitleIgnoresMediaType() {
        assertEquals("2024", feiniu(MediaType.Series, year = 2024).heroSubtitle)
        assertEquals("2024", feiniu(MediaType.Movie, year = 2024).heroSubtitle)
        assertNull(feiniu(MediaType.Movie).heroSubtitle)
    }

    // --- metaLine / typeLabel / durationLabel -------------------------------

    @Test
    fun metaLineJoinsYearTypeAndDuration() {
        val item = jellyfin(MediaType.Movie, year = 2024, runtimeMs = 7_200_000L)
        assertEquals("电影", item.typeLabel)
        assertEquals("120 分钟", item.durationLabel)
        assertEquals("2024 · 电影 · 120 分钟", item.metaLine)
    }

    @Test
    fun metaLineDropsMissingPartsAndFallsBackToMediaWord() {
        assertEquals("2024", jellyfin(MediaType.Unknown, year = 2024).metaLine)
        assertEquals("电影", jellyfin(MediaType.Movie).metaLine)
        assertEquals("媒体", jellyfin(MediaType.Unknown).metaLine)
    }

    @Test
    fun durationLabelUsesWholeMinutesAndRejectsNonPositiveValues() {
        assertEquals("1 分钟", jellyfin(MediaType.Movie, runtimeMs = 90_001L).durationLabel)
        assertNull(jellyfin(MediaType.Movie, runtimeMs = 0L).durationLabel)
        assertNull(jellyfin(MediaType.Movie, runtimeMs = null).durationLabel)
    }

    // --- episodeSubtitle ----------------------------------------------------

    @Test
    fun episodeSubtitleUsesSeriesThenSeasonThenEpisode() {
        val episode = jellyfin(
            MediaType.Episode,
            seriesTitle = "漫长的季节",
            seasonTitle = "第一季",
            seasonNumber = 1,
            episodeNumber = 2,
        )
        assertEquals("漫长的季节 · 第一季 · 第 2 集", episode.episodeSubtitle)
    }

    @Test
    fun episodeSubtitleFallsBackToSeasonNumberAndEpisodeOne() {
        val episode = jellyfin(
            MediaType.Episode,
            seriesTitle = "漫长的季节",
            seasonNumber = 3,
            episodeNumber = null,
        )
        assertEquals("漫长的季节 · 第 3 季 · 第 1 集", episode.episodeSubtitle)
    }

    @Test
    fun episodeSubtitleIsNullForNonEpisodes() {
        assertNull(jellyfin(MediaType.Movie, seriesTitle = "x").episodeSubtitle)
    }

    // --- episodeCardLine / cardTitle ----------------------------------------

    @Test
    fun seriesCodeCardLinePadsMissingNumbersWithZero() {
        val episode = jellyfin(MediaType.Episode, title = "Pilot", seasonNumber = 1, episodeNumber = 2)
        assertEquals("S1:E2 - Pilot", episode.episodeCardLine)
        assertEquals("S0:E0 - Pilot", jellyfin(MediaType.Episode, title = "Pilot").episodeCardLine)
        assertEquals("S1:E2", jellyfin(MediaType.Episode, title = " ", seasonNumber = 1, episodeNumber = 2).episodeCardLine)
        assertNull(jellyfin(MediaType.Movie, title = "Pilot").episodeCardLine)
    }

    @Test
    fun feiniuStyleHasNoEpisodeCodeLine() {
        val episode = feiniu(MediaType.Episode, title = "Pilot", seasonNumber = 1, episodeNumber = 2)
        assertNull(episode.episodeCardLine)
    }

    @Test
    fun cardTitlePrefersSeriesTitleForEpisodes() {
        assertEquals("漫长的季节", jellyfin(MediaType.Episode, title = "第 2 集", seriesTitle = "漫长的季节").cardTitle)
        assertEquals("第 2 集", jellyfin(MediaType.Episode, title = "第 2 集").cardTitle)
        assertEquals("电影名", jellyfin(MediaType.Movie, title = "电影名", seriesTitle = "忽略").cardTitle)
    }

    // --- episodeLabel -------------------------------------------------------

    @Test
    fun longFormEpisodeLabelJoinsWithTwoSpaces() {
        val episode = jellyfin(MediaType.Episode, seasonNumber = 1, episodeNumber = 2)
        assertEquals("第 1 季  第 2 集", episode.episodeLabel)
        assertEquals("第 2 集", jellyfin(MediaType.Episode, episodeNumber = 2).episodeLabel)
        assertEquals("第 1 季", jellyfin(MediaType.Episode, seasonNumber = 1).episodeLabel)
        assertNull(jellyfin(MediaType.Episode).episodeLabel)
    }

    @Test
    fun compactEpisodeLabelUsesCompactWords() {
        val episode = feiniu(MediaType.Episode, seasonNumber = 1, episodeNumber = 2)
        assertEquals("季 1 集 2", episode.episodeLabel)
        assertEquals("集 2", feiniu(MediaType.Episode, episodeNumber = 2).episodeLabel)
        assertEquals("季 1", feiniu(MediaType.Episode, seasonNumber = 1).episodeLabel)
        assertNull(feiniu(MediaType.Episode).episodeLabel)
    }

    // --- playback history --------------------------------------------------

    @Test
    fun playbackHistoryTitleAndUpdateBadgeFollowLegacySeasonRules() {
        val episode = PlaybackHistoryItem(
            media = jellyfin(
                MediaType.Episode,
                title = "单集名",
                seriesTitle = "漫长的季节",
                seasonNumber = 1,
                episodeNumber = 3,
            ),
            latestSeasonNumber = 2,
            latestEpisodeNumber = 8,
            seriesHasMultipleSeasons = true,
        )
        assertEquals("漫长的季节 · 第1季第3集", episode.titleText)
        assertEquals("更新至2季8集", episode.updateBadgeText)

        val singleSeason = episode.copy(
            latestSeasonNumber = 1,
            latestEpisodeNumber = 9,
            seriesHasMultipleSeasons = false,
        )
        assertEquals("漫长的季节 · 第3集", singleSeason.titleText)
        assertEquals("更新至9集", singleSeason.updateBadgeText)
    }

    @Test
    fun playbackHistoryRatingRoundsAndRejectsOutOfRangeValues() {
        assertEquals(
            "8.7",
            PlaybackHistoryItem(jellyfinMovie(year = 2024).copy(communityRating = 8.74)).movieRatingText,
        )
        assertEquals(
            "8.8",
            PlaybackHistoryItem(jellyfinMovie(year = 2024).copy(communityRating = 8.75)).movieRatingText,
        )
        assertNull(PlaybackHistoryItem(jellyfinMovie(year = 2024).copy(communityRating = 10.1)).movieRatingText)
        assertNull(PlaybackHistoryItem(jellyfin(MediaType.Episode).copy(communityRating = 9.0)).movieRatingText)
    }

    @Test
    fun playbackHistoryProgressRoundsClampsAndHidesCompletedRows() {
        val movie = jellyfinMovie(year = 2024)
        assertEquals(
            36,
            PlaybackHistoryItem(movie.copy(userData = MediaUserData(playedPercentage = 35.6))).progressPercent,
        )
        assertEquals(
            100,
            PlaybackHistoryItem(movie.copy(userData = MediaUserData(playedPercentage = 135.0))).progressPercent,
        )
        assertEquals(
            25,
            PlaybackHistoryItem(
                movie.copy(
                    runtimeMs = 400_000L,
                    userData = MediaUserData(playbackPositionMs = 100_000L),
                ),
            ).progressPercent,
        )
        assertNull(
            PlaybackHistoryItem(movie.copy(userData = MediaUserData(isPlayed = true, playedPercentage = 75.0)))
                .progressPercent,
        )
    }

    // --- 风格表与覆盖 --------------------------------------------------------

    @Test
    fun providerStyleTableIsTheSingleSourceOfTruth() {
        val legacySortFields = listOf(
            MediaSortField.Default,
            MediaSortField.Title,
            MediaSortField.DateAdded,
            MediaSortField.PremiereDate,
            MediaSortField.DatePlayed,
            MediaSortField.CommunityRating,
            MediaSortField.CriticRating,
            MediaSortField.ProductionYear,
            MediaSortField.PlayCount,
            MediaSortField.Runtime,
            MediaSortField.OfficialRating,
            MediaSortField.Random,
            MediaSortField.Album,
            MediaSortField.Artist,
            MediaSortField.AlbumArtist,
            MediaSortField.AiredEpisodeOrder,
            MediaSortField.SeriesSortName,
            MediaSortField.Studio,
            MediaSortField.IsFavoriteOrLiked,
            MediaSortField.DateLastContentAdded,
            MediaSortField.IsUnplayed,
            MediaSortField.IsPlayed,
        )
        assertEquals(legacySortFields, MediaDisplayStyle.forProvider(ProviderKind.Jellyfin).sortableFields)
        assertEquals(legacySortFields, MediaDisplayStyle.forProvider(ProviderKind.Feiniu).sortableFields)
    }

    @Test
    fun explicitOverrideWinsOverTheProviderTable() {
        val overridden = feiniu(
            MediaType.Episode,
            seasonNumber = 1,
            episodeNumber = 2,
            displayStyle = MediaDisplayStyle(episodeLabelStyle = EpisodeLabelStyle.LongForm),
        )
        assertEquals("第 1 季  第 2 集", overridden.episodeLabel)
    }

    // --- builders -----------------------------------------------------------

    private fun jellyfinMovie(year: Int?) = jellyfin(MediaType.Movie, year = year)

    private fun jellyfin(
        mediaType: MediaType,
        title: String = "标题",
        seriesTitle: String? = null,
        seasonTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        year: Int? = null,
        runtimeMs: Long? = null,
        displayStyle: MediaDisplayStyle? = null,
    ) = item(
        ProviderKind.Jellyfin,
        mediaType, title, seriesTitle, seasonTitle, seasonNumber, episodeNumber, year, runtimeMs, displayStyle,
    )

    private fun feiniu(
        mediaType: MediaType,
        title: String = "标题",
        seriesTitle: String? = null,
        seasonTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        year: Int? = null,
        runtimeMs: Long? = null,
        displayStyle: MediaDisplayStyle? = null,
    ) = item(
        ProviderKind.Feiniu,
        mediaType, title, seriesTitle, seasonTitle, seasonNumber, episodeNumber, year, runtimeMs, displayStyle,
    )

    private fun item(
        provider: ProviderKind,
        mediaType: MediaType,
        title: String,
        seriesTitle: String?,
        seasonTitle: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        year: Int?,
        runtimeMs: Long?,
        displayStyle: MediaDisplayStyle?,
    ) = MediaItem(
        id = MediaId("item-1"),
        serverId = ServerId("server-1"),
        provider = provider,
        title = title,
        mediaType = mediaType,
        seriesTitle = seriesTitle,
        seasonTitle = seasonTitle,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        year = year,
        runtimeMs = runtimeMs,
        displayStyleOverride = displayStyle,
    )
}
