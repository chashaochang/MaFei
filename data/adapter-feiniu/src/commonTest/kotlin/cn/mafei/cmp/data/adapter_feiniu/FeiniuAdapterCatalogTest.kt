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
             "ts":1700000000,"overview":"Overview text","official_rating_unused":"x",
             "media_stream":{"resolutions":["4k","1080p"],"audio_type":"DTS","color_range_type":"HDR","progress":50.0,"current_time":3600.0}},
            {"guid":"it-0002","title":"Fixture Show","type":"TV","poster":"/p/s.jpg","season_number":1},
            {"guid":"it-0003","title":"Hologram","type":"LiveChannel"}
        ]}""",
    )

private class Fixture(route: FeiniuRoute = FeiniuRoute.Local) {
    val transport = FakeFeiniuTransport()
    val adapter = FeiniuMediaProviderAdapter(
        facade = DefaultFeiniuFacade(
            transport = transport,
            sessionState = InMemoryFeiniuSessionState(FeiniuCredentials("fixture-token", "Trim-MC-token=fixture-token")),
        ),
        serverId = ServerId("fn-1"),
        accountId = AccountId("acc-1"),
        resolution = FeiniuHostResolution(FeiniuHostCandidate("https://feiniu.test", route)),
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
        // No evidence for the watched-tag wire literal anywhere (legacy never
        // used it): the filter stays unsupported until fixtures prove it.
        assertFalse(fixture.adapter.capabilities.supports(AdapterCapability.WatchedFilter))
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.WatchedEdit))
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.PlaybackReporting))
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
    fun homeUsesRankedCatalogAndAuthoritativePlayHistory() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope("""{"list":[{"guid":"latest","title":"Latest","type":"Movie","poster":"/latest.jpg","vote_average":"7.0"}],"total":1}"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""{"list":[{"guid":"rated","title":"Rated","type":"TV","poster":"/rated.jpg","vote_average":"9.0"}],"total":1}"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""[{"guid":"resume","title":"Resume","type":"Movie","poster":"/resume.jpg","duration":7200,"media_stream":{"progress":50.0,"current_time":3600.0}}]"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""{"guid":"rated","title":"Rated","type":"TV","backdrops":"/rated-backdrop.jpg","release_date":"2023-01-01"}"""),
        )
        fixture.transport.enqueueJson(
            200,
            envelope("""{"guid":"latest","title":"Latest","type":"Movie","backdrops":"/latest-backdrop.jpg","release_date":"2024-01-01"}"""),
        )

        val home = fixture.adapter.home(HomeFeedRequest())
        assertEquals(listOf("Rated", "Latest"), home.recommendations.map { it.title })
        assertEquals(listOf(2023, 2024), home.recommendations.map { it.year })
        assertTrue(home.recommendations[0].images.first { it.kind == ImageKind.Backdrop }.url.contains("/rated-backdrop.jpg"))
        assertEquals(listOf("Resume"), home.resume.map { it.title })
        assertEquals(3_600_000L, home.resume.single().userData.playbackPositionMs)
        assertTrue(home.nextUp.isEmpty())
        assertEquals(listOf("Latest"), home.latest.map { it.title })
        assertEquals(
            listOf(
                "/v/api/v1/item/list",
                "/v/api/v1/item/list",
                "/v/api/v1/play/list",
                "/v/api/v1/item/rated",
                "/v/api/v1/item/latest",
            ),
            fixture.transport.requests.map { it.url.encodedPath },
        )
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
        assertEquals(3_600_000L, movie.userData.playbackPositionMs) // current_time 3600s
        assertEquals(50.0, movie.userData.playedPercentage)
        assertEquals(1_700_000_000_000L, movie.dateAddedEpochMs) // ts seconds -> ms
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
    fun favoritesOnlyRejectsCombinedConstraints() = runTest {
        val fixture = Fixture()
        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(
                null,
                PageRequest(filter = MediaFilter(favoritesOnly = true, mediaTypes = setOf(MediaType.Movie))),
            )
        }
        assertEquals(0, fixture.transport.requests.size)
    }

    @Test
    fun watchedAndGenreAndYearFiltersAreRejectedWithoutEvidence() = runTest {
        val fixture = Fixture()
        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(null, PageRequest(filter = MediaFilter(playedOnly = true)))
        }
        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(null, PageRequest(filter = MediaFilter(unplayedOnly = true)))
        }
        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(null, PageRequest(filter = MediaFilter(genres = setOf("Drama"))))
        }
        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(null, PageRequest(filter = MediaFilter(years = setOf(2024))))
        }
        assertEquals(0, fixture.transport.requests.size)
    }

    @Test
    fun singleKnownMediaTypeMapsToCategoryAndCombinationsAreRejected() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.adapter.items(null, PageRequest(filter = MediaFilter(mediaTypes = setOf(MediaType.Series))))
        assertTrue(bodyOf(fixture.transport.requests.single()).contains("\"type\":[\"TV\"]"))

        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(null, PageRequest(filter = MediaFilter(mediaTypes = setOf(MediaType.Movie, MediaType.Series))))
        }
        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(null, PageRequest(filter = MediaFilter(mediaTypes = setOf(MediaType.Unknown))))
        }
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
    fun dateAddedSortMapsToCreateTimeAndOtherFieldsAreRejected() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, envelope("""{"list":[],"total":0}"""))
        fixture.adapter.items(null, PageRequest(sort = MediaSort(MediaSortField.DateAdded, SortDirection.Ascending)))
        val body = bodyOf(fixture.transport.requests.single())
        assertTrue(body.contains("\"sort_column\":\"create_time\""), body)
        assertTrue(body.contains("\"sort_type\":\"ASC\""), body)

        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(null, PageRequest(sort = MediaSort(MediaSortField.Title)))
        }
    }

    // --------------------------------------------------------------- search

    @Test
    fun searchHitsTheKeywordEndpointAndServesASingleChunk() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            envelope("""[
                {"guid":"it-0001","title":"One","type":"Movie"},
                {"guid":"it-0002","title":"Two","type":"Movie"},
                {"guid":"it-0003","title":"Three","type":"Movie"}
            ]"""),
        )
        val page = fixture.adapter.search("fixture", PageRequest(pageSize = 2))
        val request = fixture.transport.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/v/api/v1/search/list"), request.url.encodedPath)
        assertTrue(request.url.toString().contains("q=fixture"), request.url.toString())
        // Unpaged endpoint, stateless adapter: the whole result is one chunk.
        assertEquals(listOf("One", "Two", "Three"), page.items.map { it.title })
        assertEquals(3, page.totalEstimate)
        assertNull(page.nextCursor)
        // A cursor cannot be honored (it would re-query, not page).
        assertFailsWith<ClientConfigError> {
            fixture.adapter.search("fixture", PageRequest(cursor = OffsetPageCursorCodec.encode(2)))
        }
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

    // ------------------------------------------------------------ hierarchy

    @Test
    fun seasonsAreASingleChunkWithHierarchy() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, FeiniuFixtures.SEASON_LIST)
        val page = fixture.adapter.seasons(MediaId("it-0002"))
        assertEquals(listOf("第一季"), page.items.map { it.title })
        assertEquals(MediaType.Series, page.items[0].mediaType) // "TV" wire type
        assertEquals(1, page.items[0].seasonNumber)
        assertEquals(MediaId("it-0002"), page.items[0].parentId)
        assertNull(page.nextCursor)
        val request = fixture.transport.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/v/api/v1/season/list/it-0002"), request.url.encodedPath)
    }

    @Test
    fun episodesRequireTheSeasonGuidAndStayASingleChunk() = runTest {
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

        // The endpoint takes a season guid only; whole-series walks are refused.
        assertFailsWith<ClientConfigError> { fixture.adapter.episodes(MediaId("it-0002"), null, PageRequest()) }
        assertFailsWith<ClientConfigError> {
            fixture.adapter.episodes(MediaId("it-0002"), MediaId("se-0001"), PageRequest(filter = MediaFilter(playedOnly = true)))
        }
        assertFailsWith<ClientConfigError> {
            fixture.adapter.episodes(MediaId("it-0002"), MediaId("se-0001"), PageRequest(sort = MediaSort(MediaSortField.Title)))
        }
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
