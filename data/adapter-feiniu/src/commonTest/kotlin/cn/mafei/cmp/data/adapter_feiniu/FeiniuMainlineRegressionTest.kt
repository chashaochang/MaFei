package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.data.adapter_contract.*
import cn.mafei.cmp.providers.feiniu.core.*
import cn.mafei.cmp.providers.feiniu.facade.DefaultFeiniuFacade
import cn.mafei.cmp.providers.feiniu.testkit.FakeFeiniuTransport
import cn.mafei.cmp.providers.feiniu.testkit.FeiniuFixtures
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

class FeiniuMainlineRegressionTest {
    @Test fun suspendHarnessRunsBodiesThatActuallySuspend() = runSuspendTest {
        delay(1)
        assertTrue(true)
    }

    private fun recommendationCandidate(
        id: String,
        type: String,
        rating: String,
        year: Int,
        genres: List<Int>,
        watched: Int = 0,
    ) = FeiniuMediaItem(
        guid = id,
        title = id,
        type = type,
        poster = "/$id.jpg",
        voteAverage = rating,
        releaseDate = "$year-01-01",
        genres = genres,
        watched = watched,
    )

    @Test fun recommendationPlannerMatchesLegacySignalsAndExclusions() {
        val favorite = recommendationCandidate("favorite", "Movie", "8.0", 2024, listOf(10))
        val unfinished = recommendationCandidate("unfinished", "Movie", "9.8", 2026, listOf(10))
        val completed = recommendationCandidate("completed", "Movie", "9.7", 2026, listOf(20), watched = 1)
        val latest = listOf(
            unfinished,
            completed,
            recommendationCandidate("genre-match", "Movie", "8.2", 2025, listOf(10)),
            recommendationCandidate("series", "TV", "8.5", 2025, listOf(30)),
        )
        val highestRated = listOf(recommendationCandidate("high-score", "Movie", "9.2", 2025, listOf(20)))

        val result = rankFeiniuRecommendations(
            latest = latest,
            highestRated = highestRated,
            unfinished = listOf(unfinished),
            completed = listOf(completed),
            favorites = listOf(favorite),
            resultLimit = 3,
            seed = "account|2026-08-21",
            currentYear = 2026,
        )

        assertEquals(3, result.size)
        assertEquals("genre-match", result.first().guid)
        assertFalse(result.any { it.guid == "unfinished" })
        assertFalse(result.any { it.guid == "completed" })
    }

    @Test fun recommendationPlannerKeepsColdStartPriorityDiversityAndStableOrder() {
        val latest = listOf(
            recommendationCandidate("latest-first", "Movie", "7.0", 2024, listOf(10)),
            recommendationCandidate("latest-second", "TV", "7.5", 2024, listOf(20)),
        )
        val coldStart = rankFeiniuRecommendations(
            latest = latest,
            highestRated = listOf(recommendationCandidate("older-high-score", "Movie", "9.9", 2010, listOf(30))),
            unfinished = emptyList(),
            completed = emptyList(),
            favorites = emptyList(),
            resultLimit = 3,
            seed = "cold-start",
            currentYear = 2026,
        )
        assertEquals(listOf("latest-first", "latest-second"), coldStart.take(2).map { it.guid })

        val source = listOf(
            recommendationCandidate("movie-1", "Movie", "9.5", 2026, listOf(10)),
            recommendationCandidate("movie-1", "Movie", "9.5", 2026, listOf(10)),
            recommendationCandidate("movie-2", "Movie", "9.4", 2026, listOf(10)),
            recommendationCandidate("movie-3", "Movie", "9.3", 2026, listOf(10)),
            recommendationCandidate("series-1", "TV", "8.8", 2026, listOf(20)),
            recommendationCandidate("series-2", "TV", "8.7", 2026, listOf(30)),
        )
        val first = rankFeiniuRecommendations(
            source, emptyList(), emptyList(), emptyList(), emptyList(), 4, "stable-seed", 2026,
        )
        val second = rankFeiniuRecommendations(
            source, emptyList(), emptyList(), emptyList(), emptyList(), 4, "stable-seed", 2026,
        )
        assertEquals(first.map { it.guid }, second.map { it.guid })
        assertEquals(4, first.map { it.guid }.toSet().size)
        assertTrue(first.any { it.type == "TV" })

        val noPoster = recommendationCandidate("no-poster", "Movie", "9.0", 2026, listOf(10)).copy(poster = "")
        val visible = rankFeiniuRecommendations(
            latest = listOf(
                recommendationCandidate("episode", "Episode", "9.5", 2026, listOf(10)),
                noPoster,
                recommendationCandidate("movie", "Movie", "8.0", 2025, listOf(20)),
            ),
            highestRated = emptyList(),
            unfinished = emptyList(),
            completed = emptyList(),
            favorites = emptyList(),
            resultLimit = 6,
            seed = "seed",
            currentYear = 2026,
        )
        assertEquals(listOf("movie"), visible.map { it.guid })
    }

    @Test fun recommendationPlannerTreatsNonFiniteRatingsAsMissing() {
        val invalid = listOf(
            recommendationCandidate("nan", "Movie", "NaN", 2025, listOf(10)),
            recommendationCandidate("finite", "TV", "8.5", 2025, listOf(20)),
        )
        val missing = invalid.map { item ->
            if (item.guid == "nan") item.copy(voteAverage = "") else item
        }

        val invalidOrder = rankFeiniuRecommendations(
            invalid, emptyList(), emptyList(), emptyList(), emptyList(), 2, "seed", 2026,
        ).map { it.guid }
        val missingOrder = rankFeiniuRecommendations(
            missing, emptyList(), emptyList(), emptyList(), emptyList(), 2, "seed", 2026,
        ).map { it.guid }

        assertEquals(missingOrder, invalidOrder)
    }

    @Test fun mapperDropsNonFiniteRatingProgressAndPosition() {
        val mapped = recommendationCandidate("nan", "Movie", "NaN", 2025, emptyList())
            .copy(mediaStream = FeiniuItemStream(progress = Double.NaN, currentTimeSeconds = Double.POSITIVE_INFINITY))
            .toMediaItem(ServerId("server"), { path -> "https://feiniu.test$path" })
        assertNotNull(mapped)
        assertNull(mapped.communityRating)
        assertNull(mapped.userData.playedPercentage)
        assertEquals(0L, mapped.userData.playbackPositionMs)

        val play = FeiniuPlayItem(
            guid = "play",
            title = "Play",
            type = "Movie",
            mediaStream = FeiniuItemStream(progress = Double.NaN, currentTimeSeconds = Double.NEGATIVE_INFINITY),
        ).toMediaItem(ServerId("server"), { path -> "https://feiniu.test$path" })
        assertNotNull(play)
        assertNull(play.userData.playedPercentage)
        assertEquals(0L, play.userData.playbackPositionMs)
    }

    @Test fun playHistoryEpisodeNumberOverridesUnreliableType() {
        val play = FeiniuPlayItem(
            guid = "episode",
            title = "Episode",
            type = "TV",
            episodeNumber = 3,
            poster = "/episode.jpg",
        ).toMediaItem(ServerId("server"), { path -> "https://feiniu.test$path" })

        assertNotNull(play)
        assertEquals(MediaType.Episode, play.mediaType)
    }

    @Test fun listMapperUsesStillPathAndTreatsTsAsPlaybackPosition() {
        val item = FeiniuMediaItem(
            guid = "episode",
            title = "Episode",
            type = "Episode",
            stillPath = "/still.jpg",
            epochSeconds = 30,
            durationSeconds = 120,
        ).toMediaItem(ServerId("server"), { path -> "https://feiniu.test$path" })

        assertNotNull(item)
        assertTrue(item.primaryImage?.url?.endsWith("/still.jpg") == true)
        assertEquals(30_000L, item.userData.playbackPositionMs)
        assertEquals(25.0, item.userData.playedPercentage)
        assertNull(item.dateAddedEpochMs)

        val watched = FeiniuMediaItem(
            guid = "watched",
            title = "Watched",
            type = "Movie",
            watched = 1,
        ).toMediaItem(ServerId("server"), { path -> "https://feiniu.test$path" })
        assertEquals(100.0, watched?.userData?.playedPercentage)
    }

    @Test fun detailMapperKeepsLegacyTitleAndImageFallbackOrder() {
        val detail = FeiniuItemDetail(
            guid = "episode",
            title = "",
            fileName = "Episode 01.mkv",
            type = "Episode",
            originalTitle = "",
            stillPath = "/still.jpg",
            poster = "/poster.jpg",
            posters = "/posters.jpg",
        ).toMediaItem(
            ServerId("server"),
            { path -> "https://feiniu.test$path" },
            people = listOf(
                FeiniuPersonCredit(personGuid = "person", name = "", originalName = "Actor A"),
            ),
        )

        assertNotNull(detail)
        assertEquals("Episode 01.mkv", detail.title)
        assertNull(detail.originalTitle)
        assertTrue(detail.primaryImage?.url?.endsWith("/still.jpg") == true)
        assertTrue(detail.images.first { it.kind == ImageKind.Backdrop }.url.endsWith("/still.jpg"))
        assertEquals("Actor A", detail.people.single().name)
    }

    @Test fun mapperAcceptsLegacySeriesAndFolderAliases() {
        assertEquals(MediaType.Series, "series".toFeiniuMediaType())
        assertEquals(MediaType.Folder, "folder".toFeiniuMediaType())
    }

    @Test fun streamMapperRestoresLegacyDisplayTitles() {
        val detail = FeiniuItemDetail(
            guid = "movie",
            title = "Movie",
            type = "Movie",
        ).toMediaItem(
            serverId = ServerId("server"),
            imageUrl = { path -> "https://feiniu.test$path" },
            streams = FeiniuStreamListData(
                files = listOf(FeiniuStreamFileInfo(guid = "media", fileName = "movie.mkv")),
                videoStreams = listOf(
                    FeiniuVideoStream(mediaGuid = "media", codecName = "h264", width = 1920, height = 1080),
                ),
                audioStreams = listOf(
                    FeiniuAudioStream(mediaGuid = "media", codecName = "aac", language = "chi", channels = 2),
                ),
                subtitleStreams = listOf(
                    FeiniuSubtitleStream(
                        mediaGuid = "media",
                        format = "ass",
                        language = "chi",
                        isExternal = 1,
                    ),
                ),
            ),
        )

        assertNotNull(detail)
        val source = detail.mediaSources.single()
        assertEquals("1920 × 1080", source.videoStream?.displayTitle)
        assertEquals("chi · AAC · 2声道", source.audioStreams.single().displayTitle)
        assertEquals("chi · ASS · 外挂", source.subtitleStreams.single().displayTitle)
        assertTrue(source.streams.all { it.index == 0 })
    }

    @Test fun recommendationSeedSeparatesServersAndUsesCalendarDate() {
        val epoch = feiniuDateToEpochMsAndYear("2026-08-21")!!.first
        val first = feiniuRecommendationSeed(ProviderKind.Feiniu, ServerId("server-a"), AccountId("same"), epoch)
        val second = feiniuRecommendationSeed(ProviderKind.Feiniu, ServerId("server-b"), AccountId("same"), epoch)

        assertEquals("Feiniu|server-a|same|2026-8-21", first)
        assertTrue(first != second)
    }

    @Test fun pageCursorBindsPageSizeAndRejectsOverflow() {
        val cursor = FeiniuPagePosition().next(50, 1)
        assertEquals(FeiniuPagePosition(2, 1), FeiniuPagePosition.decode(PageRequest(cursor, 50)))
        assertFailsWith<ClientConfigError> { FeiniuPagePosition.decode(PageRequest(cursor, 25)) }
        assertFailsWith<ClientConfigError> { FeiniuPagePosition.decode(PageRequest(PageCursor("v1:fnpage:2147483648:50:1"))) }
        assertFailsWith<ClientConfigError> { FeiniuPagePosition(Int.MAX_VALUE, 1).next(50, 1) }
    }

    @Test fun sdkPinnedPlaybackKeepsOriginalCredentialsAcrossReplacement() = runSuspendTest {
        val transport = FakeFeiniuTransport()
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("old-account-token"))
        val facade = DefaultFeiniuFacade(transport, state)
        val pinned = state.snapshot()
        state.install(FeiniuCredentials("new-account-token"))
        transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        facade.playback.playInfo(resolution, "it-0001", pinnedOperation = pinned)
        assertEquals("old-account-token", transport.requests.single().headers.first("Authorization"))
        assertEquals("new-account-token", state.snapshot().credentials.token)
    }

    private val resolution = FeiniuHostResolution(FeiniuHostCandidate("https://feiniu.test", FeiniuRoute.Local))
    private fun adapter(transport: HttpTransport, state: FeiniuSessionState = InMemoryFeiniuSessionState(FeiniuCredentials("fixture-token"))) =
        FeiniuMediaProviderAdapter(DefaultFeiniuFacade(transport, state), ServerId("s"), AccountId("a"), resolution)

    @Test fun cappedPagesCanConsumeTheirOwnCursor() = runSuspendTest {
        val transport = FakeFeiniuTransport()
        val adapter = adapter(transport)
        var cursor: PageCursor? = null
        for (page in 1..3) {
            transport.enqueueJson(200, """{"code":0,"data":{"list":[{"guid":"m$page","title":"Movie","type":"Movie"}],"total":3}}""")
            val result = adapter.items(null, PageRequest(pageSize = 50, cursor = cursor))
            assertTrue((transport.requests.last().body as HttpBody.Text).text.contains("\"page\":$page"))
            cursor = result.nextCursor
            if (page < 3) assertNotNull(cursor) else assertNull(cursor)
        }
    }

    @Test fun favoritesForwardSortInsteadOfSilentlyDroppingIt() = runSuspendTest {
        val transport = FakeFeiniuTransport()
        transport.enqueueJson(200, """{"code":0,"data":{"list":[]}}""")
        adapter(transport).items(
            null,
            PageRequest(
                filter = MediaFilter(favoritesOnly = true),
                sort = MediaSort(MediaSortField.Title, SortDirection.Ascending),
            ),
        )
        val body = (transport.requests.single().body as HttpBody.Text).text
        assertTrue(body.contains("\"sort_column\":\"sort_title\""), body)
        assertTrue(body.contains("\"sort_type\":\"ASC\""), body)
    }

    @Test fun invalidCalendarDaysAreNotNormalized() {
        for (date in listOf("2023-02-29", "2024-02-30", "2024-04-31")) assertNull(feiniuDateToEpochMsAndYear(date), date)
        assertNotNull(feiniuDateToEpochMsAndYear("2024-02-29"))
        assertEquals(0L, feiniuDateToEpochMsAndYear("1970-01-01")?.first)
    }

    private fun switchingTransport(state: FeiniuSessionState, switchAt: Int, onCall: () -> Unit): HttpTransport {
        var calls = 0
        return object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                calls++; onCall()
                if (calls == switchAt) state.install(FeiniuCredentials("new-account-token"))
                val body = if (calls == 1) FeiniuFixtures.PLAY_INFO else FeiniuFixtures.PLAY_PLAY
                return HttpResponse(200, HttpHeaders.EMPTY, body.encodeToByteArray(), request.url, request.url)
            }
        }
    }

    @Test fun identitySwitchAfterInfoStopsSecondNegotiation() = runSuspendTest {
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("old-account-token"))
        var calls = 0
        val adapter = adapter(switchingTransport(state, 1) { calls++ }, state)
        assertFailsWith<CancellationException> { adapter.preparePlayback(MediaId("it-0001")) }
        assertEquals(1, calls)
    }

    @Test fun identitySwitchAfterPlayCannotReturnMixedCredentials() = runSuspendTest {
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("old-account-token"))
        val adapter = adapter(switchingTransport(state, 2) {}, state)
        assertFailsWith<CancellationException> { adapter.preparePlayback(MediaId("it-0001")) }
    }

    @Test fun oldReadyCannotReportUnderNewIdentity() = runSuspendTest {
        val transport = FakeFeiniuTransport()
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("old-account-token"))
        val adapter = adapter(transport, state)
        transport.enqueueJson(200, FeiniuFixtures.PLAY_INFO)
        transport.enqueueJson(200, FeiniuFixtures.PLAY_PLAY)
        val ready = assertIs<PlaybackPreparation.Ready>(adapter.preparePlayback(MediaId("it-0001")))
        state.install(FeiniuCredentials("new-account-token"))
        transport.enqueueJson(200, """{"code":0}""")
        assertFailsWith<CancellationException> { adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Progress, 1000)) }
        assertEquals(2, transport.requests.size)
    }
}
