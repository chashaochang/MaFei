package cn.mafei.cmp.feature.detail

import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaSourceInfo
import cn.mafei.cmp.core.domain.MediaStreamInfo
import cn.mafei.cmp.core.domain.MediaStreamKind
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaUserData
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoDetailPresentationTest {
    @Test
    fun keepsAllLegacySourcesWhenProviderMarksEveryVersionUnplayable() {
        val first = MediaSourceInfo(id = "first", playable = false)
        val second = MediaSourceInfo(id = "second", playable = false)

        assertEquals(listOf(first, second), selectLegacyDetailSources(listOf(first, second)))
        assertEquals(
            listOf("unknown"),
            selectLegacyDetailSources(
                listOf(first, MediaSourceInfo(id = "unknown", playable = null), second),
            ).map { it.id },
        )
    }

    @Test
    fun episodeDetailUsesTheLegacyProviderEpisodeLabelAfterQueueResolution() {
        val episode = item(
            type = MediaType.Episode,
            seasonNumber = 2,
            episodeNumber = 3,
            userData = MediaUserData(playbackPositionMs = 30_000L),
        )

        assertEquals("第 2 季  第 3 集", legacyDetailPlayLabel(episode, episode))
        assertEquals(
            "季 2 集 3",
            legacyDetailPlayLabel(
                episode.copy(provider = ProviderKind.Feiniu),
                episode.copy(provider = ProviderKind.Feiniu),
            ),
        )
    }

    @Test
    fun detailDurationRoundsPositiveSubMinuteRuntimeUpToOneMinute() {
        assertEquals("", legacyDetailDurationLabel(0L))
        assertEquals("1分钟", legacyDetailDurationLabel(1L))
        assertEquals("1小时1分钟", legacyDetailDurationLabel(3_660_000L))
    }

    @Test
    fun movieWithPercentageOnlyProgressKeepsTheLegacyContinueLabel() {
        val movie = item(type = MediaType.Movie).copy(
            runtimeMs = 600_000L,
            userData = MediaUserData(playedPercentage = 25.0),
        )

        assertEquals("继续播放", legacyDetailPlayLabel(movie, movie))
        val completed = movie.copy(userData = MediaUserData(playedPercentage = 100.0))
        assertEquals("播放", legacyDetailPlayLabel(completed, completed))
        val atEnd = movie.copy(userData = MediaUserData(playbackPositionMs = movie.runtimeMs!!))
        assertEquals("播放", legacyDetailPlayLabel(atEnd, atEnd))
    }

    @Test
    fun playbackResumeDoesNotInventANearEndReset() {
        val movie = item(type = MediaType.Movie).copy(
            runtimeMs = 100_000L,
            userData = MediaUserData(playbackPositionMs = 99_000L),
        )

        assertEquals(99_000L, legacyPlaybackResumePositionMs(movie))
    }

    @Test
    fun episodeCardTitleMatchesTheLegacyEmptyNumberRule() {
        assertEquals(
            "3.Title",
            legacyEpisodeCardTitle(item(MediaType.Episode, episodeNumber = 3).copy(title = "Title")),
        )
        assertEquals(".Title", legacyEpisodeCardTitle(item(MediaType.Episode).copy(title = "Title")))
    }

    @Test
    fun seasonSeriesNameUsesDetailThenRouteThenSeriesFallback() {
        val series = item(type = MediaType.Series).copy(title = "Series item")
        val season = item(type = MediaType.Season).copy(seriesTitle = "Detail series")

        assertEquals("Detail series", legacySeasonSeriesName(series, season, "Library"))
        assertEquals("Library", legacySeasonSeriesName(series, season.copy(seriesTitle = ""), "Library"))
        assertEquals("Series item", legacySeasonSeriesName(series, season.copy(seriesTitle = null), ""))
    }

    @Test
    fun keepsVersionsIndependentAndPresentsOnlyTheirOwnStreams() {
        val first = MediaSourceInfo(
            id = "first",
            name = "1080p",
            path = "/media/first.mkv",
            sizeBytes = 2L * 1024 * 1024 * 1024,
            defaultAudioIndex = 2,
            defaultSubtitleIndex = -1,
            streams = listOf(
                MediaStreamInfo(
                    index = 1,
                    kind = MediaStreamKind.Video,
                    codec = "h264",
                    displayTitle = "1080p H264 SDR",
                    widthPx = 1920,
                    heightPx = 1080,
                ),
                MediaStreamInfo(index = 2, kind = MediaStreamKind.Audio, codec = "aac", language = "eng"),
            ),
        )
        val second = MediaSourceInfo(
            id = "second",
            name = "4K",
            path = "/media/second.mkv",
            streams = listOf(
                MediaStreamInfo(
                    index = 4,
                    kind = MediaStreamKind.Video,
                    codec = "hevc",
                    widthPx = 3840,
                    heightPx = 2160,
                    dolbyVisionProfile = 8,
                ),
            ),
        )

        val presented = presentDetailPlaybackSources(ProviderKind.Jellyfin, listOf(first, second))

        assertEquals(listOf("first", "second"), presented.map { it.id })
        assertEquals("1080PSDR", presented[0].compactLabel)
        assertEquals("4K杜比", presented[1].compactLabel)
        assertEquals("/media/first.mkv", presented[0].filePath)
        assertEquals(2, presented[0].defaultAudioStreamIndex)
        assertEquals(-1, presented[0].defaultSubtitleStreamIndex)
        assertTrue(presented[0].infoSections.any { it.id == "video" })
        assertFalse(presented[1].videoSummary.contains("1080", ignoreCase = true))
    }

    @Test
    fun videoPresentationUsesTheFirstStreamWhenSourceHasNoExplicitDefault() {
        val source = MediaSourceInfo(
            id = "video-order",
            streams = listOf(
                MediaStreamInfo(
                    index = 1,
                    kind = MediaStreamKind.Video,
                    widthPx = 1920,
                    heightPx = 1080,
                ),
                MediaStreamInfo(
                    index = 2,
                    kind = MediaStreamKind.Video,
                    widthPx = 3840,
                    heightPx = 2160,
                    isDefault = true,
                ),
            ),
        )

        assertEquals(
            "1080P",
            presentDetailPlaybackSources(ProviderKind.Jellyfin, listOf(source)).single().compactLabel,
        )
    }

    @Test
    fun translatesLegacyFeiniuSummaryAndNoSubtitleOption() {
        val source = MediaSourceInfo(
            id = "fn-source",
            sizeBytes = 1_610_612_736L,
            streams = listOf(
                MediaStreamInfo(
                    index = 0,
                    kind = MediaStreamKind.Video,
                    codec = "hevc",
                    widthPx = 3840,
                    heightPx = 2160,
                    bitRate = 12_000_000,
                    bitDepth = 10,
                ),
                MediaStreamInfo(
                    index = 1,
                    kind = MediaStreamKind.Audio,
                    codec = "aac",
                    language = "chi",
                    sampleRateHz = 48_000,
                ),
            ),
        )

        val presented = presentDetailPlaybackSources(ProviderKind.Feiniu, listOf(source)).single()

        assertEquals("4K", presented.compactLabel)
        assertEquals("4k HEVC 12.00 mbps · 10 bit", presented.videoSummary)
        assertEquals("中文 AAC · 48000 Hz", presented.audioSummary)
        assertEquals("无字幕", presented.subtitleOptions.single().title)
        assertEquals(-1, presented.subtitleOptions.single().streamIndex)
        assertEquals("1.50 GB", presented.fileSize)
    }

    @Test
    fun usesLegacyLanguageLabelsForCommonProviderCodes() {
        assertEquals("简体中文", legacyLanguageDisplayName("chs"))
        assertEquals("繁体中文", legacyLanguageDisplayName("cht"))
        assertEquals("英语", legacyLanguageDisplayName("eng"))
        assertEquals("荷兰语", legacyLanguageDisplayName("dut"))
        assertEquals("捷克语", legacyLanguageDisplayName("ces"))
        assertEquals("波兰语", legacyLanguageDisplayName("pol"))
        assertEquals("英语", legacyLanguageDisplayName("en-US"))
        assertEquals("未知", legacyLanguageDisplayName("und"))
    }

    @Test
    fun interlacedAloneDoesNotCreateTheLegacyVideoInformationSection() {
        val source = MediaSourceInfo(
            id = "interlaced-only",
            streams = listOf(
                MediaStreamInfo(
                    index = 0,
                    kind = MediaStreamKind.Video,
                    interlaced = true,
                ),
            ),
        )

        assertTrue(
            presentDetailPlaybackSources(ProviderKind.Jellyfin, listOf(source))
                .single().infoSections.isEmpty(),
        )
    }

    @Test
    fun keepsProviderSpecificUnknownTrackTitlesFromLegacyAdapters() {
        val source = MediaSourceInfo(
            id = "unknown-tracks",
            streams = listOf(
                MediaStreamInfo(index = 1, kind = MediaStreamKind.Audio, codec = "aac", language = "und"),
                MediaStreamInfo(index = 2, kind = MediaStreamKind.Subtitle, codec = "ass", language = "und"),
            ),
        )

        val jellyfin = presentDetailPlaybackSources(ProviderKind.Jellyfin, listOf(source)).single()
        val feiniu = presentDetailPlaybackSources(ProviderKind.Feiniu, listOf(source)).single()

        assertEquals("未知音频", jellyfin.audioOptions.single().title)
        assertEquals("未知字幕", jellyfin.subtitleOptions.last().title)
        assertEquals("音轨 1", feiniu.audioOptions.single().title)
        assertEquals("字幕 1", feiniu.subtitleOptions.last().title)
    }

    @Test
    fun unknownSubtitleInfoCardUsesLegacyUnknownTitleInsteadOfInventedIndex() {
        val source = MediaSourceInfo(
            id = "unknown-subtitle",
            streams = listOf(
                MediaStreamInfo(index = 2, kind = MediaStreamKind.Subtitle, codec = "ass", language = "und"),
            ),
        )

        val subtitleCard = presentDetailPlaybackSources(ProviderKind.Jellyfin, listOf(source))
            .single().infoSections.single().cards.single()

        assertEquals("未知", subtitleCard.title)
    }

    @Test
    fun subtitleInfoFileSizeKeepsEachLegacyProviderFormat() {
        val source = MediaSourceInfo(
            id = "subtitle-size",
            streams = listOf(
                MediaStreamInfo(
                    index = 2,
                    kind = MediaStreamKind.Subtitle,
                    codec = "ass",
                    fileSizeBytes = 1_610_612_736L,
                ),
            ),
        )

        fun subtitleFileSize(provider: ProviderKind): String =
            presentDetailPlaybackSources(provider, listOf(source)).single()
                .infoSections.single().cards.single().rows.first { it.title == "文件大小" }.value

        assertEquals("1.5 GB", subtitleFileSize(ProviderKind.Jellyfin))
        assertEquals("1.50 GB", subtitleFileSize(ProviderKind.Feiniu))
    }

    @Test
    fun emitsCanonicalHeroMediaTagsWithoutConfusingChannelsWithFormats() {
        val source = MediaSourceInfo(
            id = "tags",
            streams = listOf(
                MediaStreamInfo(
                    kind = MediaStreamKind.Video,
                    widthPx = 3840,
                    heightPx = 2160,
                    dolbyVisionProfile = 8,
                ),
                MediaStreamInfo(
                    kind = MediaStreamKind.Audio,
                    codec = "eac3",
                    channelLayout = "7.1",
                ),
                MediaStreamInfo(
                    kind = MediaStreamKind.Video,
                    widthPx = 3840,
                    heightPx = 2160,
                    dolbyVisionProfile = 8,
                ),
            ),
        )

        assertEquals(
            listOf(
                DetailVideoTag.Tag4K,
                DetailVideoTag.DolbyVision,
                DetailVideoTag.Surround71,
            ),
            presentDetailPlaybackSources(ProviderKind.Jellyfin, listOf(source)).single().tagKeys,
        )
    }

    @Test
    fun emitsSpecificHdrAndDtsHdTagsBeforeChannelTags() {
        val source = MediaSourceInfo(
            id = "tags",
            streams = listOf(
                MediaStreamInfo(
                    kind = MediaStreamKind.Video,
                    widthPx = 1920,
                    heightPx = 1080,
                    videoRangeType = "HDR10",
                ),
                MediaStreamInfo(
                    kind = MediaStreamKind.Audio,
                    codec = "dts-hd ma",
                    channelLayout = "stereo",
                ),
            ),
        )

        assertEquals(
            listOf(
                DetailVideoTag.Tag1080,
                DetailVideoTag.Hdr10,
                DetailVideoTag.DtsHdMa,
                DetailVideoTag.Stereo,
            ),
            presentDetailPlaybackSources(ProviderKind.Feiniu, listOf(source)).single().tagKeys,
        )
    }

    @Test
    fun derivesTheExtendedBadgeSetOnlyFromExplicitStreamMetadata() {
        val source = MediaSourceInfo(
            id = "rich-tags",
            streams = listOf(
                MediaStreamInfo(
                    kind = MediaStreamKind.Video,
                    widthPx = 7680,
                    heightPx = 4320,
                    codec = "hevc",
                    videoRangeType = "HDR10Plus",
                    displayTitle = "IMAX Enhanced",
                ),
                MediaStreamInfo(
                    kind = MediaStreamKind.Audio,
                    codec = "DTS:X",
                    channelLayout = "7.1",
                ),
            ),
        )

        assertEquals(
            listOf(
                DetailVideoTag.Tag8K,
                DetailVideoTag.Hdr10Plus,
                DetailVideoTag.Hevc,
                DetailVideoTag.ImaxEnhanced,
                DetailVideoTag.DtsX,
                DetailVideoTag.Surround71,
            ),
            presentDetailPlaybackSources(ProviderKind.Jellyfin, listOf(source)).single().tagKeys,
        )
    }

    @Test
    fun restoresBadgesFromExplicitProviderLabelsWhenStreamsAreNotReady() {
        assertEquals(
            listOf(
                DetailVideoTag.Tag4K,
                DetailVideoTag.DolbyVision,
                DetailVideoTag.Dts,
            ),
            detailVideoTagsFromProviderLabels(listOf("2160P", "DolbyVision", "DTS")),
        )
    }

    @Test
    fun preservesFeiniuResolutionTypeAndCodedResolutionRows() {
        val source = MediaSourceInfo(
            id = "coded",
            streams = listOf(
                MediaStreamInfo(
                    kind = MediaStreamKind.Video,
                    codec = "hevc",
                    resolutionType = "2160p",
                    codedWidthPx = 4096,
                    codedHeightPx = 2160,
                ),
            ),
        )

        val video = presentDetailPlaybackSources(ProviderKind.Feiniu, listOf(source))
            .single().infoSections.single().cards.single()

        assertEquals("4K", presentDetailPlaybackSources(ProviderKind.Feiniu, listOf(source)).single().compactLabel)
        assertEquals("2160p", video.rows.first { it.title == "分辨率" }.value)
        assertEquals("4096 * 2160", video.rows.first { it.title == "编码分辨率" }.value)
    }

    private fun item(
        type: MediaType,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        userData: MediaUserData = MediaUserData(),
    ) = MediaItem(
        id = MediaId("item"),
        serverId = ServerId("server"),
        provider = ProviderKind.Jellyfin,
        title = "Episode",
        mediaType = type,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        userData = userData,
    )
}
