package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.AuthenticationError
import cn.mafei.cmp.core.domain.AuthFailureKind
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaFilter
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaSort
import cn.mafei.cmp.core.domain.MediaSortField
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.NetworkUnreachableError
import cn.mafei.cmp.core.domain.PageCursor
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.ProtocolDecodingError
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ResourceNotFoundError
import cn.mafei.cmp.core.domain.ServerError
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.domain.SortDirection
import cn.mafei.cmp.core.domain.TimeoutError
import cn.mafei.cmp.core.http_contract.HttpBody
import cn.mafei.cmp.core.http_contract.HttpMethod
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpTimeoutException
import cn.mafei.cmp.core.http_contract.HttpTransportException
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.core.http_contract.TransportFailureKind
import cn.mafei.cmp.data.adapter_contract.AdapterCapability
import cn.mafei.cmp.data.adapter_contract.HomeFeedRequest
import cn.mafei.cmp.data.adapter_contract.OffsetPageCursorCodec
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostCandidate
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState
import cn.mafei.cmp.providers.feiniu.facade.DefaultFeiniuFacade
import cn.mafei.cmp.providers.feiniu.testkit.FakeFeiniuTransport
import cn.mafei.cmp.providers.feiniu.testkit.FeiniuFixtures
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun envelope(dataJson: String): String = """{"code":0,"msg":"","data":$dataJson}"""

private val LIST_PAGE_ONE =
    envelope(
        """{"total":3,"list":[
            {"guid":"it-0001","title":"Fixture Movie","type":"Movie","poster":"/p/m.jpg","can_play":1,
             "watched":1,"is_favorite":1,"vote_average":"8.1","duration":7200,
             "release_date":"2024-01-01","ancestor_guid":"lib-0001","epoch":"ts",
             "ts":1800,"overview":"Overview text","official_rating_unused":"x",
             "media_stream":{"resolutions":["4k","1080p"],"audio_type":"DTS","color_range_type":"HDR","progress":50.0,"current_time":3600.0}},
            {"guid":"it-0002","title":"Fixture Show","type":"TV","poster":"/p/s.jpg","season_number":1},
            {"guid":"it-0003","title":"Hologram","type":"LiveChannel"}
        ]}""",
    )

private class Fixture(
    route: FeiniuRoute = FeiniuRoute.Local,
    nowEpochMs: () -> Long = { feiniuDateToEpochMsAndYear("2026-08-21")!!.first },
) {
    val transport = FakeFeiniuTransport()
    val adapter = FeiniuMediaProviderAdapter(
        facade = DefaultFeiniuFacade(
            transport = transport,
            sessionState = InMemoryFeiniuSessionState(FeiniuCredentials("fixture-token", "Trim-MC-token=fixture-token")),
        ),
        serverId = ServerId("fn-1"),
        accountId = AccountId("acc-1"),
        resolution = FeiniuHostResolution(FeiniuHostCandidate("https://feiniu.test", route)),
        nowEpochMs = nowEpochMs,
    )
}

private fun bodyOf(request: HttpRequest): String = (request.body as? HttpBody.Text)?.text ?: ""

class FeiniuAdapterCatalogTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    // ------------------------------------------------------------ identity

    @Test
    fun exposesIdentityAndEvidenceBasedCapabilities() {
        val fixture = Fixture()
        assertEquals(ProviderKind.Feiniu, fixture.adapter.provider)
        assertEquals(ServerId("fn-1"), fixture.adapter.serverId)
        assertEquals(AccountId("acc-1"), fixture.adapter.accountId)
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.Search))
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.FavoritesRead))
        // The dedicated favorites endpoint cannot combine further filters.
        assertFalse(fixture.adapter.capabilities.supports(AdapterCapability.FavoritesFilter))
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.FavoritesEdit))
        // Legacy itemListPayload writes the proven "1" / "0" watched tags.
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.WatchedFilter))
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.WatchedEdit))
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.PlaybackReporting))
    }

    @Test
    fun administratorStatusUsesAuthoritativeUserInfo() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope("""{"username":"demo","nickname":"Demo","is_admin":1}"""),
        )

        assertTrue(fixture.adapter.isAdministrator())
        assertEquals("/v/api/v1/user/info", fixture.transport.requests.single().url.encodedPath)
    }

    // ---------------------------------------------------------- collections

    @Test
    fun mediaLibrariesMapToCollections() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.MEDIA_DB_LIST)
        val page = fixture.adapter.collections(PageRequest())
        assertEquals(listOf("电影", "电视剧", "Future"), page.items.map { it.name })
        assertEquals(CollectionId("lib-0001"), page.items[0].id)
        assertEquals(setOf(MediaType.Movie), page.items[0].mediaTypes)
        assertEquals(setOf(MediaType.Series), page.items[1].mediaTypes)
        assertEquals(emptySet(), page.items[2].mediaTypes) // unknown category tolerated
        assertEquals(ImageKind.Primary, page.items[0].primaryImage!!.kind)
        assertTrue(page.items[0].primaryImage!!.url.contains("/v/api/v1/sys/img/p/1.jpg?w=400"))
        assertNull(page.nextCursor)
        assertEquals(3, page.totalEstimate)
    }

    @Test
    fun collectionsRejectNonNullCursor() = runTest {
        val fixture = Fixture()
        assertFailsWith<ClientConfigError> {
            fixture.adapter.collections(PageRequest(cursor = OffsetPageCursorCodec.encode(0)))
        }
        assertEquals(0, fixture.transport.requests.size)
    }

    @Test
    fun collectionsHideUnsupportedLiveLibrariesLikeLegacy() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope(
                """[
                    {"guid":"movies","title":"电影","category":"movie"},
                    {"guid":"live","title":"直播","category":"iptv"}
                ]""",
            ),
        )

        assertEquals(listOf("电影"), fixture.adapter.collections(PageRequest()).items.map { it.name })
    }

    @Test
    fun homeUsesRankedCatalogAndAuthoritativePlayHistory() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope(
                """[
                    {"guid":"lib-movies","title":"Movies","category":"movie"},
                    {"guid":"lib-shows","title":"Shows","category":"tv"}
                ]""",
            ),
        )
        fixture.transport.enqueueJson(
            200,
            envelope(
                """{"list":[
                    {"guid":"movie-latest-1","title":"Movie Latest 1","type":"Movie","poster":"/movie-1.jpg"},
                    {"guid":"movie-latest-2","title":"Movie Latest 2","type":"Movie","poster":"/movie-2.jpg"}
                ],"total":2}""",
            ),
        )
        fixture.transport.enqueueJson(
            200,
            envelope(
                """{"list":[
                    {"guid":"show-latest-1","title":"Show Latest 1","type":"TV","poster":"/show-1.jpg"},
                    {"guid":"show-latest-2","title":"Show Latest 2","type":"TV","poster":"/show-2.jpg"}
                ],"total":2}""",
            ),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""[{"guid":"resume","title":"Resume","type":"Movie","duration":7200,"ts":3600}]"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""{"list":[{"guid":"latest","title":"Latest","type":"Movie","poster":"/latest.jpg","vote_average":"7.0","release_date":"2024-01-01","genres":[10]}],"total":1}"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""{"list":[{"guid":"rated","title":"Rated","type":"TV","poster":"/rated.jpg","vote_average":"9.0","release_date":"2023-01-01","genres":[20]}],"total":1}"""),
        )
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.transport.enqueueJson(200, envelope("""[{"id":10,"value":"剧情"},{"id":20,"value":"科幻"}]"""))
        fixture.transport.enqueueJson(
            200,
            envelope("""{"guid":"latest","title":"Latest","type":"Movie","backdrops":"/latest-backdrop.jpg","release_date":"2024-01-01"}"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""{"guid":"rated","title":"Rated","type":"TV","backdrops":"/rated-backdrop.jpg","release_date":"2023-01-01"}"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""{"guid":"resume","title":"Resume","type":"Movie","posters":"/resume.jpg","duration":7200}"""),
        )

        val home = fixture.adapter.home(HomeFeedRequest())
        assertEquals(listOf("Latest", "Rated"), home.recommendations.map { it.title })
        assertEquals(listOf(2024, 2023), home.recommendations.map { it.year })
        assertEquals(listOf("剧情"), home.recommendations[0].genres)
        assertEquals(listOf("科幻"), home.recommendations[1].genres)
        assertTrue(home.recommendations[0].images.first { it.kind == ImageKind.Backdrop }.url.contains("/latest-backdrop.jpg"))
        assertEquals(listOf("Resume"), home.resume.map { it.title })
        assertEquals(3_600_000L, home.resume.single().userData.playbackPositionMs)
        assertTrue(home.resume.single().primaryImage!!.url.contains("/resume.jpg"))
        assertTrue(home.nextUp.isEmpty())
        assertEquals(
            listOf("Movie Latest 1", "Show Latest 1", "Movie Latest 2", "Show Latest 2"),
            home.latest.map { it.title },
        )
        assertEquals(
            listOf(
                "/v/api/v1/mediadb/list",
                "/v/api/v1/item/list",
                "/v/api/v1/item/list",
                "/v/api/v1/play/list",
                "/v/api/v1/item/list",
                "/v/api/v1/item/list",
                "/v/api/v1/favorite/list",
                "/v/api/v1/tag/genres",
                "/v/api/v1/item/latest",
                "/v/api/v1/item/rated",
                "/v/api/v1/item/resume",
            ),
            fixture.transport.requests.map { it.url.encodedPath },
        )
        val bodies = fixture.transport.requests.map(::bodyOf)
        assertTrue(bodies[1].contains("\"ancestor_guid\":\"lib-movies\""))
        assertTrue(bodies[2].contains("\"ancestor_guid\":\"lib-shows\""))
        assertTrue(bodies[1].contains("\"type\":[\"Movie\",\"TV\",\"Episode\",\"Video\"]"))
        assertTrue(bodies[4].contains("\"type\":[\"Movie\",\"TV\"]"))
        assertTrue(bodies[5].contains("\"sort_column\":\"vote_average\""))
    }

    @Test
    fun homeUsesTopLevelPlayListTsWhenMediaStreamIsAbsent() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, envelope("""[]"""))
        fixture.transport.enqueueJson(
            200,
            envelope(
                """[{"guid":"resume-ts","title":"Resume TS","type":"Movie","poster":"/resume.jpg","duration":8048,"ts":312}]""",
            ),
        )
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))

        val home = fixture.adapter.home(HomeFeedRequest())

        assertEquals(listOf("Resume TS"), home.resume.map { it.title })
        assertEquals(312_000L, home.resume.single().userData.playbackPositionMs)
        assertEquals(312.0 * 100.0 / 8048.0, home.resume.single().userData.playedPercentage)
        assertNull(home.resume.single().dateAddedEpochMs)
        assertFalse(fixture.transport.requests.any { it.url.encodedPath == "/v/api/v1/item/resume-ts" })
    }

    @Test
    fun homeCachesLegacyRecommendationsForSixHoursWithoutCachingOtherShelves() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope("""[{"guid":"lib-movies","title":"Movies","category":"movie"}]"""),
        )
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.transport.enqueueJson(200, envelope("""[]"""))
        fixture.transport.enqueueJson(
            200,
            envelope("""{"list":[{"guid":"recommended","title":"Recommended","type":"Movie","poster":"/r.jpg","release_date":"2025-01-01"}],"total":1}"""),
        )
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.transport.enqueueJson(
            200,
            envelope("""{"guid":"recommended","title":"Recommended","type":"Movie","backdrops":"/r-backdrop.jpg","release_date":"2025-01-01"}"""),
        )

        val first = fixture.adapter.home(HomeFeedRequest())
        assertEquals(listOf("Recommended"), first.recommendations.map { it.title })

        fixture.transport.enqueueJson(
            200,
            envelope("""[{"guid":"lib-movies","title":"Movies","category":"movie"}]"""),
        )
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.transport.enqueueJson(200, envelope("""[]"""))
        val second = fixture.adapter.home(HomeFeedRequest())

        assertEquals(first.recommendations, second.recommendations)
        assertEquals(1, fixture.transport.requests.count { it.url.encodedPath == "/v/api/v1/favorite/list" })
        assertEquals(1, fixture.transport.requests.count { it.url.encodedPath == "/v/api/v1/item/recommended" })
        assertEquals(2, fixture.transport.requests.count { it.url.encodedPath == "/v/api/v1/play/list" })
        assertEquals(4, fixture.transport.requests.count { it.url.encodedPath == "/v/api/v1/item/list" })
    }

    @Test
    fun recommendationSignalsDoNotLetAnEmptyDuplicateHideLaterProgress() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, envelope("""[]"""))
        fixture.transport.enqueueJson(
            200,
            envelope(
                """[
                    {"guid":"watched-later","title":"History","type":"Movie","poster":"/history.jpg"},
                    {"guid":"watched-later","title":"History","type":"Movie","poster":"/history.jpg","media_stream":{"progress":50.0}}
                ]""",
            ),
        )
        fixture.transport.enqueueJson(
            200,
            envelope(
                """{"list":[{"guid":"watched-later","title":"Candidate","type":"Movie","poster":"/candidate.jpg"}],"total":1}""",
            ),
        )
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))

        val home = fixture.adapter.home(HomeFeedRequest())

        assertTrue(home.recommendations.isEmpty())
    }

    // ---------------------------------------------------------- item mapping

    @Test
    fun itemsMapFieldsUserStateTicksAndStreams() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, LIST_PAGE_ONE)
        val page = fixture.adapter.items(CollectionId("lib-0001"), PageRequest())
        assertEquals(3, page.items.size)
        val movie = page.items[0]
        assertEquals(MediaId("it-0001"), movie.id)
        assertEquals(MediaType.Movie, movie.mediaType)
        assertEquals(CollectionId("lib-0001"), movie.collectionId) // query context stamped
        assertTrue(movie.canPlay)
        assertTrue(movie.userData.isPlayed)
        assertTrue(movie.userData.isFavorite)
        assertEquals(7_200_000L, movie.runtimeMs) // duration seconds -> ms
        assertEquals(2024, movie.year)
        assertTrue(movie.premiereEpochMs != null)
        assertEquals(3_600_000L, movie.userData.playbackPositionMs) // max(ts 1800s, current_time 3600s)
        assertEquals(50.0, movie.userData.playedPercentage)
        assertNull(movie.dateAddedEpochMs) // list `ts` is playback position, not creation time
        assertEquals("Overview text", movie.overview)
        assertEquals(8.1, movie.communityRating)
        assertEquals(listOf("4K", "1080P", "DTS", "HDR"), movie.tags)
        // Show: season number mapped, no play flag.
        val show = page.items[1]
        assertEquals(MediaType.Series, show.mediaType)
        assertEquals(1, show.seasonNumber)
        assertFalse(show.canPlay)
        // Unknown type tolerated as Unknown, no play capability inferred.
        assertEquals(MediaType.Unknown, page.items[2].mediaType)
        assertFalse(page.items[2].canPlay)
    }

    @Test
    fun posterBuildsAbsoluteFeiniuImageUrl() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, LIST_PAGE_ONE)
        val poster = fixture.adapter.items(null, PageRequest()).items[0].primaryImage
        assertEquals(ImageKind.Primary, poster!!.kind)
        assertEquals("https://feiniu.test/v/api/v1/sys/img/p/m.jpg?w=400", poster.url)
    }

    @Test
    fun absoluteArtworkUrlsAreNotPrefixedAgain() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope(
                """{"list":[{"guid":"remote","title":"Remote","type":"Movie",
                    "poster":"https://cdn.example/poster.jpg"}],"total":1}""",
            ),
        )

        assertEquals(
            "https://cdn.example/poster.jpg",
            fixture.adapter.items(null, PageRequest()).items.single().primaryImage?.url,
        )
    }

    @Test
    fun relativeArtworkWithoutLeadingSlashIsNormalized() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope(
                """{"list":[{"guid":"relative","title":"Relative","type":"Movie",
                    "poster":"posters/relative.jpg"}],"total":1}""",
            ),
        )

        assertEquals(
            "https://feiniu.test/v/api/v1/sys/img/posters/relative.jpg?w=400",
            fixture.adapter.items(null, PageRequest()).items.single().primaryImage?.url,
        )
    }

    // --------------------------------------------------------------- paging

    @Test
    fun issuedCursorTranslatesToOneBasedPages() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, envelope("""{"list":[{"guid":"first","title":"First"}],"total":2}"""))
        val first = fixture.adapter.items(null, PageRequest(pageSize = 36))
        fixture.transport.enqueueJson(200, LIST_PAGE_ONE)
        fixture.adapter.items(null, PageRequest(cursor = first.nextCursor, pageSize = 36))
        val body = bodyOf(fixture.transport.requests.last())
        assertTrue(body.contains("\"page\":2"), body)
        assertTrue(body.contains("\"page_size\":36"), body)
    }

    @Test
    fun misalignedOffsetCursorIsRejected() = runTest {
        val fixture = Fixture()
        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(null, PageRequest(cursor = OffsetPageCursorCodec.encode(7), pageSize = 36))
        }
        assertEquals(0, fixture.transport.requests.size)
    }

    @Test
    fun invalidCursorIsRejectedWithoutIssuingARequest() = runTest {
        val fixture = Fixture()
        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(null, PageRequest(cursor = PageCursor("v2:off:0")))
        }
        assertEquals(0, fixture.transport.requests.size)
    }

    @Test
    fun zeroTotalIsUnknownAndFullPageAdvances() = runTest {
        val fixture = Fixture()
        // total missing/null in the wire payload: paging advances on a full page.
        fixture.transport.enqueueJson(200, envelope("""{"list":[
            {"guid":"it-0001","title":"A","type":"Movie"},
            {"guid":"it-0002","title":"B","type":"Movie"},
            {"guid":"it-0003","title":"C","type":"Movie"},
            {"guid":"it-0004","title":"D","type":"Movie"}
        ]}"""))
        val page = fixture.adapter.items(null, PageRequest(pageSize = 4))
        assertEquals(4, page.items.size)
        assertNull(page.totalEstimate)
        assertNotNull(page.nextCursor)
        // Terminal empty page stops the walk.
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        val tail = fixture.adapter.items(null, PageRequest(cursor = page.nextCursor, pageSize = 4))
        assertTrue(tail.isEmpty)
        assertNull(tail.nextCursor)
    }

    @Test
    fun positiveTotalStopsPagingAtTheDeclaredEnd() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, LIST_PAGE_ONE) // 3 items, total 3
        val page = fixture.adapter.items(null, PageRequest(pageSize = 36))
        assertEquals(3, page.items.size)
        assertEquals(3, page.totalEstimate)
        assertNull(page.nextCursor) // nextOffset 3 >= total 3
    }

    // ------------------------------------------------------- capability gates

    @Test
    fun favoritesOnlyRoutesToTheDedicatedFavoritesEndpoint() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.adapter.items(CollectionId("lib-0001"), PageRequest(filter = MediaFilter(favoritesOnly = true)))
        val request = fixture.transport.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/v/api/v1/favorite/list"), request.url.encodedPath)
        assertTrue(bodyOf(request).contains("\"ancestor_guid\":\"lib-0001\""))
    }

    @Test
    fun favoritesOnlyForwardsTheLegacyMediaKindsAndSort() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.adapter.items(
            null,
            PageRequest(
                filter = MediaFilter(
                    favoritesOnly = true,
                    mediaTypes = setOf(MediaType.Movie, MediaType.Series, MediaType.Episode, MediaType.Video),
                ),
                sort = MediaSort(MediaSortField.Title, SortDirection.Ascending),
            ),
        )
        val body = bodyOf(fixture.transport.requests.single())
        assertTrue(body.contains("\"type\":[\"Movie\",\"TV\",\"Episode\",\"Video\"]"), body)
        assertTrue(body.contains("\"sort_column\":\"sort_title\""), body)
        assertTrue(body.contains("\"sort_type\":\"ASC\""), body)
    }

    @Test
    fun watchedUsesLegacyTagsWhileGenreAndYearRemainRejected() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.adapter.items(null, PageRequest(filter = MediaFilter(playedOnly = true)))
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.adapter.items(null, PageRequest(filter = MediaFilter(unplayedOnly = true)))

        assertTrue(bodyOf(fixture.transport.requests[0]).contains("\"watched\":\"1\""))
        assertTrue(bodyOf(fixture.transport.requests[1]).contains("\"watched\":\"0\""))
        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(null, PageRequest(filter = MediaFilter(genres = setOf("Drama"))))
        }
        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(null, PageRequest(filter = MediaFilter(years = setOf(2024))))
        }
        assertEquals(2, fixture.transport.requests.size)
    }

    @Test
    fun knownMediaTypeListsUseTheLegacyTypeArrayAndUnknownIsRejected() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.adapter.items(null, PageRequest(filter = MediaFilter(mediaTypes = setOf(MediaType.Series))))
        assertTrue(bodyOf(fixture.transport.requests.single()).contains("\"type\":[\"TV\"]"))

        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.adapter.items(
            null,
            PageRequest(filter = MediaFilter(mediaTypes = setOf(MediaType.Movie, MediaType.Series))),
        )
        assertTrue(bodyOf(fixture.transport.requests[1]).contains("\"type\":[\"Movie\",\"TV\"]"))
        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(null, PageRequest(filter = MediaFilter(mediaTypes = setOf(MediaType.Unknown))))
        }
        assertEquals(2, fixture.transport.requests.size)
    }

    @Test
    fun itemsRejectFreeTextSearchTerm() = runTest {
        val fixture = Fixture()
        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(null, PageRequest(filter = MediaFilter(searchTerm = "kung fu")))
        }
        assertEquals(0, fixture.transport.requests.size)
    }

    @Test
    fun legacySortFieldsMapToTheOriginalFeiniuColumns() = runTest {
        val fixture = Fixture()
        val cases = listOf(
            MediaSortField.Title to "sort_title",
            MediaSortField.SeriesSortName to "sort_title",
            MediaSortField.PremiereDate to "release_date",
            MediaSortField.ProductionYear to "release_date",
            MediaSortField.CommunityRating to "vote_average",
            MediaSortField.CriticRating to "vote_average",
            MediaSortField.DatePlayed to "watched_ts",
            MediaSortField.PlayCount to "watched_ts",
            MediaSortField.Runtime to "runtime",
            MediaSortField.DateAdded to "create_time",
            MediaSortField.Random to "create_time",
        )
        cases.forEach { (field, column) ->
            fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
            fixture.adapter.items(null, PageRequest(sort = MediaSort(field, SortDirection.Ascending)))
            val body = bodyOf(fixture.transport.requests.last())
            assertTrue(body.contains("\"sort_column\":\"$column\""), "$field: $body")
            assertTrue(body.contains("\"sort_type\":\"ASC\""), "$field: $body")
        }
    }

    // --------------------------------------------------------------- search

    @Test
    fun searchFiltersThenPagesTheUnpagedLegacyEndpoint() = runTest {
        val fixture = Fixture()
        val response = envelope("""[
                {"guid":"it-0001","title":"One","type":"Movie"},
                {"guid":"it-0002","title":"Two","type":"Audio"},
                {"guid":"it-0003","title":"Three","type":"Movie"},
                {"guid":"it-0004","title":"Four","type":"TV"}
            ]""")
        fixture.transport.enqueueJson(200, response)
        val first = fixture.adapter.search(
            "fixture",
            PageRequest(
                pageSize = 2,
                filter = MediaFilter(
                    mediaTypes = setOf(MediaType.Episode, MediaType.Movie, MediaType.Video, MediaType.Series),
                ),
            ),
        )
        val request = fixture.transport.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/v/api/v1/search/list"), request.url.encodedPath)
        assertTrue(request.url.toString().contains("q=fixture"), request.url.toString())
        assertEquals(listOf("One", "Three"), first.items.map { it.title })
        assertEquals(3, first.totalEstimate)
        assertEquals(OffsetPageCursorCodec.encode(2), first.nextCursor)

        fixture.transport.enqueueJson(200, response)
        val second = fixture.adapter.search(
            "fixture",
            PageRequest(
                cursor = first.nextCursor,
                pageSize = 2,
                filter = MediaFilter(
                    mediaTypes = setOf(MediaType.Episode, MediaType.Movie, MediaType.Video, MediaType.Series),
                ),
            ),
        )
        assertEquals(listOf("Four"), second.items.map { it.title })
        assertEquals(3, second.totalEstimate)
        assertNull(second.nextCursor)

        val requestCount = fixture.transport.requests.size
        assertFailsWith<ClientConfigError> {
            fixture.adapter.search("fixture", PageRequest(cursor = PageCursor("bad-cursor")))
        }
        assertEquals(requestCount, fixture.transport.requests.size)
    }

    @Test
    fun searchRejectsFiltersAndSort() = runTest {
        val fixture = Fixture()
        assertFailsWith<ClientConfigError> {
            fixture.adapter.search("term", PageRequest(filter = MediaFilter(favoritesOnly = true)))
        }
        assertFailsWith<ClientConfigError> {
            fixture.adapter.search("term", PageRequest(sort = MediaSort(MediaSortField.Title)))
        }
        assertEquals(0, fixture.transport.requests.size)
    }

    @Test
    fun completedHistoryForwardsLegacyKindsWatchedTagAndSort() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope(
                """{"list":[{"guid":"movie-1","title":"Movie","type":"Movie","watched":1,"watched_ts":123}],"total":1}""",
            ),
        )

        val page = fixture.adapter.items(
            collectionId = null,
            request = PageRequest(
                pageSize = 10,
                sort = MediaSort(MediaSortField.DatePlayed, SortDirection.Descending),
                filter = MediaFilter(
                    mediaTypes = setOf(MediaType.Movie, MediaType.Episode),
                    playedOnly = true,
                ),
            ),
        )

        assertEquals(listOf("Movie"), page.items.map { it.title })
        assertEquals(123_000L, page.items.single().userData.lastPlayedEpochMs)
        val body = bodyOf(fixture.transport.requests.single())
        assertTrue(body.contains("\"type\":[\"Movie\",\"Episode\"]"), body)
        assertTrue(body.contains("\"watched\":\"1\""), body)
        assertTrue(body.contains("\"sort_column\":\"watched_ts\""), body)
        assertTrue(body.contains("\"sort_type\":\"DESC\""), body)
    }

    @Test
    fun resumeHistoryUsesPlayListAndRestoresEpisodeSeriesContext() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope(
                """[{"guid":"episode-3","title":"Episode 3","type":"Episode","parent_guid":"series-1",
                    "parent_title":"Series","season_number":1,"episode_number":3,"watched_ts":200,
                    "ts":120,"duration":1200,"media_stream":{"progress":10.0}}]""",
            ),
        )
        fixture.transport.enqueueJson(
            200,
            envelope(
                """{"guid":"episode-3","title":"Episode 3","type":"Episode","parent_guid":"series-1",
                    "season_number":1,"episode_number":3,"still_path":"/episode.jpg"}""",
            ),
        )
        fixture.transport.enqueueJson(
            200,
            envelope(
                """{"guid":"series-1","title":"Series","type":"TV","poster":"/series.jpg"}""",
            ),
        )

        val page = fixture.adapter.resumeItems(
            PageRequest(
                pageSize = 10,
                filter = MediaFilter(mediaTypes = setOf(MediaType.Movie, MediaType.Episode)),
            ),
        )

        val item = page.items.single()
        assertEquals(MediaType.Episode, item.mediaType)
        assertEquals(MediaId("series-1"), item.seriesId)
        assertEquals("Series", item.seriesTitle)
        assertEquals(3, item.episodeNumber)
        assertEquals(200_000L, item.userData.lastPlayedEpochMs)
        assertEquals(10.0, item.userData.playedPercentage)
        assertEquals(
            listOf(
                "/v/api/v1/play/list",
                "/v/api/v1/item/episode-3",
                "/v/api/v1/item/series-1",
            ),
            fixture.transport.requests.map { it.url.encodedPath },
        )
    }

    // ------------------------------------------------------------ hierarchy

    @Test
    fun seasonsAreASingleChunkWithHierarchy() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.SEASON_LIST)
        val page = fixture.adapter.seasons(MediaId("it-0002"))
        assertEquals(listOf("第一季"), page.items.map { it.title })
        assertEquals(MediaType.Season, page.items[0].mediaType)
        assertEquals(1, page.items[0].seasonNumber)
        assertEquals(12, page.items[0].childCount)
        assertEquals(MediaId("it-0002"), page.items[0].parentId)
        assertNull(page.nextCursor)
        val request = fixture.transport.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/v/api/v1/season/list/it-0002"), request.url.encodedPath)
    }

    @Test
    fun seasonAndEpisodeEndpointsRestoreLegacyFallbackTitles() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope("""[{"guid":"season-2","title":"","type":"Season","season_number":2}]"""),
        )
        assertEquals("第 2 季", fixture.adapter.seasons(MediaId("series-1")).items.single().title)

        fixture.transport.enqueueJson(
            200,
            envelope("""[{"guid":"episode-3","title":"","type":"Episode","episode_number":3}]"""),
        )
        assertEquals(
            "第 3 集",
            fixture.adapter.episodes(MediaId("series-1"), MediaId("season-2"), PageRequest()).items.single().title,
        )
    }

    @Test
    fun episodesSupportSeasonAndLegacyWholeSeriesPaging() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.EPISODE_LIST)
        val page = fixture.adapter.episodes(MediaId("it-0002"), MediaId("se-0001"), PageRequest())
        assertEquals(listOf("第一集"), page.items.map { it.title })
        assertEquals(MediaType.Episode, page.items[0].mediaType)
        assertEquals(1, page.items[0].episodeNumber)
        assertEquals(MediaId("se-0001"), page.items[0].parentId)
        assertTrue(page.items[0].canPlay)
        assertNull(page.nextCursor)
        assertTrue(fixture.transport.requests.single().url.encodedPath.endsWith("/v/api/v1/episode/list/se-0001"))

        fixture.transport.enqueueJson(
            200,
            envelope(
                """[
                    {"guid":"se-0001","title":"First","type":"Season","season_number":1},
                    {"guid":"se-0002","title":"Second","type":"Season","season_number":2}
                ]""",
            ),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""[{"guid":"ep-1","title":"One","type":"Episode","episode_number":1}]"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""[{"guid":"ep-2","title":"Two","type":"Episode","episode_number":1}]"""),
        )
        val first = fixture.adapter.episodes(
            MediaId("it-0002"),
            seasonId = null,
            request = PageRequest(pageSize = 1),
        )
        assertEquals(listOf("One"), first.items.map { it.title })
        assertEquals(MediaId("se-0001"), first.items.single().seasonId)
        assertEquals(OffsetPageCursorCodec.encode(1), first.nextCursor)
        // A cursor is opaque request state, not a retained wire response; the
        // adapter replays the legacy whole-series walk for the next page.
        fixture.transport.enqueueJson(
            200,
            envelope(
                """[
                    {"guid":"se-0001","title":"First","type":"Season","season_number":1},
                    {"guid":"se-0002","title":"Second","type":"Season","season_number":2}
                ]""",
            ),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""[{"guid":"ep-1","title":"One","type":"Episode","episode_number":1}]"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""[{"guid":"ep-2","title":"Two","type":"Episode","episode_number":1}]"""),
        )
        val second = fixture.adapter.episodes(
            MediaId("it-0002"),
            seasonId = null,
            request = PageRequest(cursor = first.nextCursor, pageSize = 1),
        )
        assertEquals(listOf("Two"), second.items.map { it.title })
        assertEquals(MediaId("se-0002"), second.items.single().seasonId)
        assertNull(second.nextCursor)
        assertFailsWith<ClientConfigError> {
            fixture.adapter.episodes(MediaId("it-0002"), MediaId("se-0001"), PageRequest(filter = MediaFilter(playedOnly = true)))
        }
        assertFailsWith<ClientConfigError> {
            fixture.adapter.episodes(MediaId("it-0002"), MediaId("se-0001"), PageRequest(sort = MediaSort(MediaSortField.Title)))
        }
    }

    @Test
    fun seriesNextUpUsesLegacyResumableSelectionAcrossRealEpisodes() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope("""[{"guid":"season-1","title":"第一季","type":"Season","season_number":1}]"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope(
                """[
                    {"guid":"episode-1","title":"第一集","type":"Episode","episode_number":1,"can_play":1},
                    {"guid":"episode-2","title":"第二集","type":"Episode","episode_number":2,"can_play":1,"duration":1200},
                    {"guid":"episode-3","title":"第三集","type":"Episode","episode_number":3,"can_play":1}
                ]""",
            ),
        )
        fixture.transport.enqueueJson(
            200,
            envelope(
                """[
                    {"guid":"episode-2","title":"第二集","type":"Episode","watched":0,"watched_ts":20,
                     "ts":120,"duration":1200,"media_stream":{"progress":10.0,"current_time":120.0}}
                ]""",
            ),
        )

        val target = fixture.adapter.nextUp(MediaId("series-1"))

        assertEquals(MediaId("episode-2"), target?.id)
        assertEquals(MediaId("series-1"), target?.seriesId)
        assertEquals(MediaId("season-1"), target?.seasonId)
        assertEquals(120_000L, target?.userData?.playbackPositionMs)
        assertEquals(10.0, target?.userData?.playedPercentage)
        assertEquals(
            listOf(
                "/v/api/v1/season/list/series-1",
                "/v/api/v1/episode/list/season-1",
                "/v/api/v1/play/list",
            ),
            fixture.transport.requests.map { it.url.encodedPath },
        )
    }

    @Test
    fun nextUpDoesNotResumeAnEpisodeAlreadyWatchedInCatalogState() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope("""[{"guid":"season-1","title":"第一季","type":"Season","season_number":1}]"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope(
                """[
                    {"guid":"episode-1","title":"第一集","type":"Episode","episode_number":1,"watched":1},
                    {"guid":"episode-2","title":"第二集","type":"Episode","episode_number":2,"watched":0}
                ]""",
            ),
        )
        fixture.transport.enqueueJson(
            200,
            envelope(
                """[
                    {"guid":"episode-1","title":"第一集","type":"Episode","watched":0,"watched_ts":20,
                     "ts":120,"duration":1200,"media_stream":{"progress":10.0,"current_time":120.0}}
                ]""",
            ),
        )

        assertEquals(MediaId("episode-2"), fixture.adapter.nextUp(MediaId("series-1"))?.id)
    }

    @Test
    fun nextUpKeepsCompletedRewatchProgressAtOneHundredPercent() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope("""[{"guid":"season-1","title":"第一季","type":"Season","season_number":1}]"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope(
                """[
                    {"guid":"episode-1","title":"第一集","type":"Episode","episode_number":1,
                     "watched":1,"media_stream":{"progress":0.0}}
                ]""",
            ),
        )
        fixture.transport.enqueueJson(200, envelope("[]"))

        val target = fixture.adapter.nextUp(MediaId("series-1"))

        assertTrue(target?.userData?.isPlayed == true)
        assertEquals(100.0, target?.userData?.playedPercentage)
    }

    // ----------------------------------------------------------- item detail

    @Test
    fun detailMapsRichFieldsAndKeepsUserDataEmpty() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.ITEM_DETAIL)
        val detail = fixture.adapter.item(MediaId("it-0001"))
        assertEquals("Fixture Movie", detail.title)
        assertEquals("Fixture Movie (Original)", detail.originalTitle)
        assertEquals(MediaType.Movie, detail.mediaType)
        assertEquals(8.1, detail.communityRating)
        assertEquals(2024, detail.year)
        assertTrue(detail.premiereEpochMs != null)
        assertEquals("PG-13", detail.officialRating)
        // The wire posters field is an opaque string (legacy passes it through
        // as the image path verbatim) — mapped as-is, never parsed.
        assertTrue(detail.primaryImage!!.url.contains("/v/api/v1/sys/img"), detail.primaryImage!!.url)
        // The detail endpoint carries no user state; adapters never invent one.
        assertEquals(cn.mafei.cmp.core.domain.MediaUserData(), detail.userData)
        assertTrue(detail.canPlay)
    }

    @Test
    fun detailWithoutIdentityBecomesResourceNotFound() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, envelope("""{"title":"No guid"}"""))
        assertFailsWith<ResourceNotFoundError> { fixture.adapter.item(MediaId("gone")) }
    }

    @Test
    fun detailAggregatesPeopleGenresPlaybackAndTypedStreamsLikeLegacyProvider() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope(
                """{"guid":"movie-1","title":"电影","original_title":"Movie","type":"Movie",
                    "overview":"简介","posters":"/poster.jpg","backdrops":"/backdrop.jpg","logos":"/logo.png",
                    "vote_average":"8.6","release_date":"2025-03-01","content_ratings":"PG-13",
                    "production_countries":["中国"],"genres":[7],"imdb_id":"tt1234567",
                    "duration":7200,"watched":0,"is_favorite":1,"can_play":1,"media_guid":"media-1"}""",
            ),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""{"list":[{"person_guid":"person-1","name":"演员甲","job":"Actor","role":"角色甲","order":1,"profile_path":"/person.jpg"}],"total":1}"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""{"guid":"movie-1","media_guid":"media-1","video_guid":"video-1","audio_guid":"audio-1","subtitle_guid":"sub-1","ts":120,"item":{"duration":7200}}"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope(
                """{"files":[{"guid":"media-1","path":"/video/movie.mkv","file_name":"movie.mkv","size":4096,"create_time":1700000000,"duration":7200}],
                    "video_streams":[{"media_guid":"media-1","guid":"video-1","index":0,"codec_name":"hevc","width":3840,"height":2160,"coded_width":4096,"coded_height":2160,"resolution_type":"2160p","bps":20000000,"color_range_type":"HDR","is_bluray":false}],
                    "audio_streams":[{"media_guid":"media-1","guid":"audio-1","index":1,"codec_name":"aac","language":"chi","channels":2,"is_default":1}],
                    "subtitle_streams":[{"media_guid":"media-1","guid":"sub-1","index":2,"format":"ass","language":"chi","is_external":1}]}""",
            ),
        )
        fixture.transport.enqueueJson(200, envelope("""[{"media_guid":"media-1","filename":"movie.mkv","file_path":"/video/movie.mkv","file_size":4096}]"""))
        fixture.transport.enqueueJson(200, envelope("""{"sources":[{"source_name":"fixture-source"}]}"""))
        fixture.transport.enqueueJson(
            200,
            envelope(
                """{"file_stream":{"guid":"media-1","file_name":""},
                    "video_stream":{"media_guid":"media-1","guid":"video-1","index":0,"profile":"Main 10"},
                    "audio_streams":[{"media_guid":"media-1","guid":"audio-1","index":1,"title":"国语"}],
                    "subtitle_streams":[{"media_guid":"media-1","guid":"sub-1","index":2,"forced":1}],
                    "qualities":[{"url":"https://cdn.test/movie.m3u8","resolution":"4K","bitrate":20000000,"is_m3u8":true}]}""",
            ),
        )
        fixture.transport.enqueueJson(200, envelope("""[{"id":7,"value":"剧情"}]"""))

        val detail = fixture.adapter.item(MediaId("movie-1"))

        assertEquals(listOf("剧情"), detail.genres)
        assertEquals(listOf("中国"), detail.tags)
        assertEquals("演员甲", detail.people.single().name)
        assertEquals("角色甲", detail.people.single().role)
        assertEquals(MediaId("person-1"), detail.people.single().id)
        assertEquals(120_000L, detail.userData.playbackPositionMs)
        assertEquals(MediaId("movie-1"), detail.playbackTarget?.id)
        assertEquals(120_000L, detail.playbackTarget?.playbackPositionMs)
        assertTrue(detail.userData.isFavorite)
        assertEquals("IMDb", detail.externalUrls.single().name)
        assertTrue(detail.images.any { it.kind == ImageKind.Logo })
        val source = detail.mediaSources.single()
        assertEquals("media-1", source.id)
        assertEquals("/video/movie.mkv", source.path)
        assertEquals(4_096L, source.sizeBytes)
        assertEquals(0, source.defaultVideoIndex)
        assertEquals(1, source.defaultAudioIndex)
        assertEquals(2, source.defaultSubtitleIndex)
        assertEquals("HEVC", source.videoStream?.codec?.uppercase())
        assertEquals("Main 10", source.videoStream?.profile)
        assertEquals(3840, source.videoStream?.widthPx)
        assertEquals("2160p", source.videoStream?.resolutionType)
        assertEquals(4096, source.videoStream?.codedWidthPx)
        assertEquals(2160, source.videoStream?.codedHeightPx)
        assertEquals("AAC", source.audioStreams.single().codec?.uppercase())
        assertEquals("国语", source.audioStreams.single().title)
        assertTrue(source.subtitleStreams.single().isExternal)
        assertTrue(source.subtitleStreams.single().isForced)
        assertEquals(
            listOf(
                "/v/api/v1/item/movie-1",
                "/v/api/v1/person/list/movie-1",
                "/v/api/v1/play/info",
                "/v/api/v1/stream/list/movie-1",
                "/v/api/v1/media/itemfile/movie-1",
                "/v/api/v1/user/info",
                "/v/api/v1/stream",
                "/v/api/v1/tag/genres",
            ),
            fixture.transport.requests.map { it.url.encodedPath },
        )
        val officialBody = bodyOf(fixture.transport.requests[6])
        assertTrue(
            officialBody.contains("\"ip\":\"${cn.mafei.cmp.providers.feiniu.core.FeiniuDigest.md5("fixture-source")}\""),
            officialBody,
        )
        assertTrue(officialBody.contains("\"User-Agent\":[\"1.32.2 (com.trim.app; build:1322007; Android 36) okHttp/4.9.0\"]"), officialBody)
    }

    @Test
    fun personDetailAndWorksUseLegacyThreeJobQueries() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope("""{"guid":"person-1","name":"演员甲","original_name":"Actor A","profile":"/person.jpg","biography":"人物简介","is_favorite":1}"""),
        )
        val person = fixture.adapter.person(MediaId("person-1"))
        assertEquals("Actor A", person.sortName)
        assertEquals("人物简介", person.overview)
        assertTrue(person.isFavorite)

        fixture.transport.enqueueJson(200, envelope("""{"list":[{"item_guid":"movie-1","title":"电影","type":"Movie","poster":"/movie.jpg"}],"total":1}"""))
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        val works = fixture.adapter.itemsByPerson(
            MediaId("person-1"),
            PageRequest(
                pageSize = 10,
                sort = MediaSort(MediaSortField.Title, SortDirection.Ascending),
                filter = MediaFilter(mediaTypes = setOf(MediaType.Movie)),
            ),
        )
        assertEquals(listOf("电影"), works.items.map { it.title })
        assertNull(works.nextCursor)
        val jobBodies = fixture.transport.requests.takeLast(3).map(::bodyOf)
        assertTrue(jobBodies[0].contains("\"job\":\"Actor\""), jobBodies[0])
        assertTrue(jobBodies[1].contains("\"job\":\"Director\""), jobBodies[1])
        assertTrue(jobBodies[2].contains("\"job\":\"Screenplay\""), jobBodies[2])
        assertTrue(jobBodies.all { it.contains("\"sort_column\":\"sort_title\"") })
    }

    @Test
    fun personWorksFillMisalignedOffsetsFromTheFollowingPageLikeLegacy() = runTest {
        val fixture = Fixture()
        repeat(3) {
            fixture.transport.enqueueJson(
                200,
                envelope(
                    """{"list":[
                        {"item_guid":"movie-a","title":"电影 A","type":"Movie"},
                        {"item_guid":"series-b","title":"剧集 B","type":"TV"}
                    ],"total":3}""",
                ),
            )
            fixture.transport.enqueueJson(
                200,
                envelope("""{"list":[{"item_guid":"movie-c","title":"电影 C","type":"Movie"}],"total":3}"""),
            )
        }

        val page = fixture.adapter.itemsByPerson(
            MediaId("person-1"),
            PageRequest(
                cursor = OffsetPageCursorCodec.encode(1),
                pageSize = 2,
                sort = MediaSort(MediaSortField.Title, SortDirection.Ascending),
                filter = MediaFilter(mediaTypes = setOf(MediaType.Movie)),
            ),
        )

        // Each of Actor/Director/Screenplay consumes page 1 offset 1 and then
        // page 2. Type filtering/dedup leaves one visible item, but the legacy
        // cursor advances by the source page size rather than visible count.
        assertEquals(listOf("电影 C"), page.items.map { it.title })
        assertEquals(OffsetPageCursorCodec.encode(3), page.nextCursor)
        assertEquals(9, page.totalEstimate)
        val bodies = fixture.transport.requests.map(::bodyOf)
        assertEquals(6, bodies.size)
        assertEquals(listOf("Actor", "Actor", "Director", "Director", "Screenplay", "Screenplay"), bodies.map { body ->
            Regex("\"job\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        })
        assertEquals(listOf(1, 2, 1, 2, 1, 2), bodies.map { body ->
            Regex("\"page\":([0-9]+)").find(body)!!.groupValues[1].toInt()
        })
    }

    // ---------------------------------------------------------------- writes

    @Test
    fun favoriteAndWatchedWritesHitTheRightMethodsWithTheGuid() = runTest {
        val fixture = Fixture()
        repeat(4) { fixture.transport.enqueueJson(200, """{"code":0,"msg":""}""") }
        fixture.adapter.setFavorite(MediaId("it-0001"), favorite = true)
        fixture.adapter.setFavorite(MediaId("it-0001"), favorite = false)
        fixture.adapter.setWatched(MediaId("it-0001"), watched = true)
        fixture.adapter.setWatched(MediaId("it-0001"), watched = false)

        val (favOn, favOff, watchedOn, watchedOff) = fixture.transport.requests
        assertEquals(HttpMethod.Put, favOn.method)
        assertEquals(HttpMethod.Delete, favOff.method)
        assertEquals(HttpMethod.Post, watchedOn.method)
        assertEquals(HttpMethod.Delete, watchedOff.method)
        assertTrue(favOn.url.encodedPath.endsWith("/v/api/v1/item/favorite"))
        assertTrue(watchedOn.url.encodedPath.endsWith("/v/api/v1/item/watched"))
        assertTrue(bodyOf(favOn).contains("\"item_guid\":\"it-0001\""))
        assertTrue(bodyOf(watchedOff).contains("\"item_guid\":\"it-0001\""))
    }

    // -------------------------------------------------------------- failures

    @Test
    fun providerErrorsTranslateToDomainErrors() = runTest {
        // Envelope 100401 -> token expired.
        run {
            val fixture = Fixture()
            fixture.transport.enqueueJson(200, FeiniuFixtures.AUTH_EXPIRED)
            val error = assertFailsWith<AuthenticationError> { fixture.adapter.collections(PageRequest()) }
            assertEquals(AuthFailureKind.TokenExpired, error.kind)
        }
        // HTTP 401 on token-authenticated calls -> expired.
        run {
            val fixture = Fixture()
            fixture.transport.enqueueJson(401, """{"code":401,"msg":""}""")
            assertFailsWith<AuthenticationError> { fixture.adapter.collections(PageRequest()) }
        }
        // Business envelope failures surface as server errors.
        run {
            val fixture = Fixture()
            fixture.transport.enqueueJson(200, """{"code":500,"msg":"boom"}""")
            assertFailsWith<ServerError> { fixture.adapter.collections(PageRequest()) }
        }
        // Garbage envelope -> protocol error.
        run {
            val fixture = Fixture()
            fixture.transport.enqueueJson(200, "not-json")
            assertFailsWith<ProtocolDecodingError> { fixture.adapter.collections(PageRequest()) }
        }
    }

    @Test
    fun connectionFailuresTranslateToNetworkErrors() = runTest {
        val timeoutFacade = DefaultFeiniuFacade(
            transport = object : cn.mafei.cmp.core.http_contract.HttpTransport {
                override suspend fun execute(request: HttpRequest): cn.mafei.cmp.core.http_contract.HttpResponse =
                    throw HttpTimeoutException(request.url, timeoutMillis = 5_000L)
            },
            sessionState = InMemoryFeiniuSessionState(FeiniuCredentials("fixture-token", "")),
        )
        val adapter = FeiniuMediaProviderAdapter(
            timeoutFacade,
            ServerId("fn-1"),
            AccountId("acc-1"),
            FeiniuHostResolution(FeiniuHostCandidate("https://feiniu.test", FeiniuRoute.Local)),
        )
        assertFailsWith<TimeoutError> { adapter.collections(PageRequest()) }

        val outageFacade = DefaultFeiniuFacade(
            transport = object : cn.mafei.cmp.core.http_contract.HttpTransport {
                override suspend fun execute(request: HttpRequest): cn.mafei.cmp.core.http_contract.HttpResponse =
                    throw HttpTransportException(request.url, TransportFailureKind.Connect, "connection refused")
            },
            sessionState = InMemoryFeiniuSessionState(FeiniuCredentials("fixture-token", "")),
        )
        val outageAdapter = FeiniuMediaProviderAdapter(
            outageFacade,
            ServerId("fn-1"),
            AccountId("acc-1"),
            FeiniuHostResolution(FeiniuHostCandidate("https://feiniu.test", FeiniuRoute.Local)),
        )
        assertFailsWith<NetworkUnreachableError> { outageAdapter.collections(PageRequest()) }
    }

    @Test
    fun cancellationPropagatesUntouched() = runTest {
        val facade = DefaultFeiniuFacade(
            transport = object : cn.mafei.cmp.core.http_contract.HttpTransport {
                override suspend fun execute(request: HttpRequest): cn.mafei.cmp.core.http_contract.HttpResponse =
                    throw CancellationException("scope cancelled")
            },
            sessionState = InMemoryFeiniuSessionState(FeiniuCredentials("fixture-token", "")),
        )
        val adapter = FeiniuMediaProviderAdapter(
            facade,
            ServerId("fn-1"),
            AccountId("acc-1"),
            FeiniuHostResolution(FeiniuHostCandidate("https://feiniu.test", FeiniuRoute.Local)),
        )
        val thrown = assertFailsWith<CancellationException> { adapter.collections(PageRequest()) }
        assertEquals("scope cancelled", thrown.message)
    }
}
