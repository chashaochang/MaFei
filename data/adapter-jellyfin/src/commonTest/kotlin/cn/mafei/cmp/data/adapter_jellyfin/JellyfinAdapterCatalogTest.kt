package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.AuthenticationError
import cn.mafei.cmp.core.domain.AuthFailureKind
import cn.mafei.cmp.core.domain.AuthorizationError
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
import cn.mafei.cmp.core.domain.PersonKind
import cn.mafei.cmp.core.domain.ProtocolDecodingError
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.RateLimitedError
import cn.mafei.cmp.core.domain.ResourceNotFoundError
import cn.mafei.cmp.core.domain.ServerError
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.domain.SortDirection
import cn.mafei.cmp.core.domain.TimeoutError
import cn.mafei.cmp.core.http_contract.HttpTimeoutException
import cn.mafei.cmp.core.http_contract.HttpTransportException
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.core.http_contract.TransportFailureKind
import cn.mafei.cmp.data.adapter_contract.AdapterCapability
import cn.mafei.cmp.data.adapter_contract.HomeFeedRequest
import cn.mafei.cmp.data.adapter_contract.OffsetPageCursorCodec
import cn.mafei.cmp.providers.jellyfin.core.InMemoryJellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerConfig
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSession
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import cn.mafei.cmp.providers.jellyfin.testkit.JellyfinFixtures
import cn.mafei.cmp.providers.jellyfin.wire.MediaStreamDto
import cn.mafei.cmp.providers.jellyfin.wire.MediaStreamType
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Local PascalCase item fixtures beyond the shared testkit set. */
private const val EPISODE_JSON = """{"Name":"Pilot","Id":"e0102030405060708","Type":"Episode","SeriesId":"s0102030405060708","SeriesName":"Fixture Series","SeriesPrimaryImageTag":"series-primary-tag","SeasonId":"se01020304050607","SeasonName":"Season 1","ParentThumbItemId":"thumb-parent-id","ParentThumbImageTag":"parent-thumb-tag","ParentBackdropItemId":"backdrop-parent-id","ParentBackdropImageTags":["parent-backdrop-tag"],"IndexNumber":2,"ParentIndexNumber":1,"RunTimeTicks":15000000000,"PremiereDate":"2023-04-01T20:00:00Z","UserData":{"IsFavorite":true,"Played":true,"PlayCount":2,"PlaybackPositionTicks":123450000,"PlayedPercentage":41.2,"LastPlayedDate":"2023-05-01T00:00:00Z"}}"""

private const val MOVIE_JSON =
    """{"Name":"Fixture Movie","OriginalTitle":"Original Movie","Id":"m0102030405060708","Type":"Movie","Overview":"Overview text","ProductionYear":2024,"CommunityRating":8.1,"OfficialRating":"PG-13","Genres":["Drama"],"PremiereDate":"2024-01-01T00:00:00Z","DateCreated":"2024-02-01T00:00:00Z","RunTimeTicks":72000000000,"People":[{"Id":"person-director","Name":"Ada Director","Role":"Director","Type":"Director","PrimaryImageTag":"director-tag"},{"Id":"person-actor","Name":"Bob Actor","Role":"Lead","Type":"Actor","PrimaryImageTag":"actor-tag"}],"MediaStreams":[{"Type":"Video","Width":3840,"Height":2160},{"Type":"Video","Width":1920,"Height":1080},{"Type":"Audio","Codec":"aac"}],"ImageTags":{"Primary":"1111111122222222","Thumb":"3333333344444444","ScreenGrab":"ignored"},"BackdropImageTags":["bd00000000000001"],"UserData":{"IsFavorite":false}}"""

private const val PERSON_JSON =
    """{"Name":"Bob Actor","SortName":"Actor, Bob","Id":"person-actor","Type":"Person","Overview":"Person biography","MovieCount":3,"SeriesCount":2,"EpisodeCount":4,"ImageTags":{"Primary":"actor-tag"},"UserData":{"IsFavorite":true}}"""

private const val SEASON_JSON = """{"Name":"Season 1","Id":"se01020304050607","Type":"Season","IndexNumber":2,"SeriesId":"s0102030405060708"}"""

private const val UNKNOWN_TYPE_JSON = """{"Name":"Hologram Show","Id":"h0102030405060708","Type":"Hologram"}"""

private const val NO_ID_JSON = """{"Name":"No identity","Type":"Movie"}"""

private const val VIEWS_JSON = """{"Items":[{"Name":"Movies","Id":"v0102030405060708","Type":"CollectionFolder","CollectionType":"movies","ChildCount":42,"ImageTags":{"Primary":"1111111122222222"}},{"Name":"TV","Id":"v0908070605040302","Type":"CollectionFolder","CollectionType":"tvshows"},{"Name":"Mixed","Id":"v11011121314151617","Type":"UserView","CollectionType":"HolographicLibrary"}],"TotalRecordCount":3,"StartIndex":0}"""

private fun queryResult(itemsJson: List<String>, total: Int, startIndex: Int = 0): String =
    """{"Items": [${itemsJson.joinToString(",")}], "TotalRecordCount": $total, "StartIndex": $startIndex}"""

class JellyfinAdapterCatalogTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private class Fixture(session: JellyfinSession? = JellyfinSession("u1", "fixture-token")) {
        val transport = FakeJellyfinTransport()
        val adapter = JellyfinMediaProviderAdapter(
            facade = cn.mafei.cmp.providers.jellyfin.facade.DefaultJellyfinFacade(
                config = JellyfinServerConfig(
                    baseUrl = HttpUrl.parse(JellyfinFixtures.BASE_URL),
                    clientName = "MaFei",
                    deviceName = "Test",
                    deviceId = "device-1",
                    appVersion = "2.0.0",
                ),
                transport = transport,
                sessionState = InMemoryJellyfinSessionState(session),
            ),
            serverId = ServerId("js-1"),
            accountId = AccountId("acc-1"),
            jellyfinUserId = "u1",
        )
    }

    @Test
    fun mapsFullLegacyStreamMetadataWithoutCollapsingTitles() {
        val stream = MediaStreamDto(
            index = 3,
            type = MediaStreamType.Video,
            codec = "hevc",
            profile = "Main 10",
            level = 153.0,
            title = "Raw title",
            displayTitle = "4K HEVC HDR",
            bitDepth = 10,
            refFrames = 4,
            width = 3840,
            height = 2160,
            averageFrameRate = 23.976,
            isInterlaced = false,
            videoRangeType = "HDR10",
            dolbyVisionProfile = 8,
            colorPrimaries = "bt2020",
            colorSpace = "bt2020nc",
            colorTransfer = "smpte2084",
            pixelFormat = "yuv420p10le",
        ).toMediaStreamInfo()

        assertEquals("Raw title", stream.title)
        assertEquals("4K HEVC HDR", stream.displayTitle)
        assertEquals("Main 10", stream.profile)
        assertEquals(153.0, stream.level)
        assertEquals(10, stream.bitDepth)
        assertEquals(4, stream.referenceFrames)
        assertEquals(23.976, stream.frameRate)
        assertEquals("HDR10", stream.videoRangeType)
        assertEquals(8, stream.dolbyVisionProfile)
        assertEquals("bt2020", stream.colorPrimaries)
        assertEquals("bt2020nc", stream.colorSpace)
        assertEquals("smpte2084", stream.colorTransfer)
        assertEquals("yuv420p10le", stream.pixelFormat)
    }

    // ------------------------------------------------------------ identity

    @Test
    fun exposesIdentityAndFrozenCapabilities() {
        val fixture = Fixture()
        assertEquals(ProviderKind.Jellyfin, fixture.adapter.provider)
        assertEquals(ServerId("js-1"), fixture.adapter.serverId)
        assertEquals(AccountId("acc-1"), fixture.adapter.accountId)
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.Search))
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.FavoritesRead))
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.FavoritesFilter))
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.FavoritesEdit))
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.WatchedFilter))
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.WatchedEdit))
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.PlaybackReporting))
        assertTrue(fixture.adapter.capabilities.supports(AdapterCapability.MediaDelete))
    }

    @Test
    fun userDataWritesHitOfficialEndpoints() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, "{}")
        fixture.transport.enqueueJson(200, "{}")
        fixture.transport.enqueueJson(200, "{}")
        fixture.transport.enqueueJson(200, "{}")
        fixture.adapter.setFavorite(MediaId("m1"), favorite = true)
        fixture.adapter.setFavorite(MediaId("m1"), favorite = false)
        fixture.adapter.setWatched(MediaId("m1"), watched = true)
        fixture.adapter.setWatched(MediaId("m1"), watched = false)
        val paths = fixture.transport.requests.map { it.url.encodedPath }
        assertEquals(
            listOf(
                "/Users/u1/FavoriteItems/m1",
                "/Users/u1/FavoriteItems/m1",
                "/Users/u1/PlayedItems/m1",
                "/Users/u1/PlayedItems/m1",
            ),
            paths,
        )
        val methods = fixture.transport.requests.map { it.method.name }
        assertEquals(listOf("Post", "Delete", "Post", "Delete"), methods)
    }

    // ---------------------------------------------------------- collections

    @Test
    fun viewsMapToCollectionsWithCollectionTypesAndImages() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, VIEWS_JSON)
        val page = fixture.adapter.collections(PageRequest())
        assertEquals(listOf("Movies", "TV", "Mixed"), page.items.map { it.name })
        assertEquals(ProviderKind.Jellyfin, page.items.first().provider)
        assertEquals(setOf(MediaType.Movie), page.items[0].mediaTypes)
        assertEquals(setOf(MediaType.Series), page.items[1].mediaTypes)
        assertEquals(42, page.items[0].itemCount)
        // Unknown future CollectionType is tolerated as an empty set.
        assertEquals(emptySet(), page.items[2].mediaTypes)
        assertTrue(page.items[0].primaryImage!!.url.contains("/Items/v0102030405060708/Images/Primary"), page.items[0].primaryImage!!.url)
        assertTrue(page.items[0].primaryImage!!.url.contains("tag=1111111122222222"))
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
    fun homeUsesDedicatedJellyfinSections() = runTest {
        val fixture = Fixture()
        val series = MOVIE_JSON
            .replace("Fixture Movie", "Fixture Series")
            .replace("m0102030405060708", "s0102030405060708")
            .replace("\"Type\":\"Movie\"", "\"Type\":\"Series\"")
        val secondMovie = MOVIE_JSON
            .replace("Fixture Movie", "Second Movie")
            .replace("m0102030405060708", "second-movie")
        val tvEpisode = EPISODE_JSON.replace("e0102030405060708", "tv-episode")
        fixture.transport.handler = { request ->
            val body = when (request.url.encodedPath.substringAfter("/jellyfin")) {
                "/Movies/Recommendations" ->
                    """[{"Items":[$series,$MOVIE_JSON],"RecommendationType":"SimilarToRecentlyPlayed"}]"""
                "/UserItems/Resume" -> queryResult(listOf(EPISODE_JSON), total = 1)
                "/Shows/NextUp" ->
                    queryResult(listOf(EPISODE_JSON.replace("e0102030405060708", "next-episode")), total = 1)
                "/UserViews" -> VIEWS_JSON
                "/Items/Latest" -> when (request.url.queryParameters.first("parentId")) {
                    "v0102030405060708" -> """[$MOVIE_JSON,$secondMovie]"""
                    "v0908070605040302" -> """[$tvEpisode]"""
                    else -> "[]"
                }
                else -> error("Unexpected home request ${request.url}")
            }
            FakeJellyfinTransport.jsonResponse(200, body, request.url)
        }

        val home = fixture.adapter.home(HomeFeedRequest())
        assertEquals(listOf("Fixture Movie"), home.recommendations.map { it.title })
        assertEquals(MediaType.Movie, home.recommendations.single().mediaType)
        assertEquals(listOf("Pilot"), home.resume.map { it.title })
        assertEquals(listOf(MediaId("next-episode")), home.nextUp.map { it.id })
        assertEquals(
            listOf(MediaId("m0102030405060708"), MediaId("tv-episode"), MediaId("second-movie")),
            home.latest.map { it.id },
        )
        assertEquals(
            listOf(CollectionId("v0102030405060708"), CollectionId("v0908070605040302"), CollectionId("v0102030405060708")),
            home.latest.map { it.collectionId },
        )
        assertEquals(
            listOf(
                "/Movies/Recommendations",
                "/UserItems/Resume",
                "/Shows/NextUp",
                "/UserViews",
                "/Items/Latest",
                "/Items/Latest",
                "/Items/Latest",
            ),
            fixture.transport.requests.map { request -> request.url.encodedPath.substringAfter("/jellyfin") },
        )
    }

    @Test
    fun identifiedItemKeepsCanonicalPrimaryArtworkWithoutImageTag() = runTest {
        val fixture = Fixture()
        val noTagMovie = MOVIE_JSON.replace(
            "\"ImageTags\":{\"Primary\":\"1111111122222222\",\"Thumb\":\"3333333344444444\",\"ScreenGrab\":\"ignored\"},",
            "\"ImageTags\":{},",
        ).replace("\"BackdropImageTags\":[\"bd00000000000001\"],", "\"BackdropImageTags\":[],")
        fixture.transport.enqueueJson(200, queryResult(listOf(noTagMovie), total = 1))

        val movie = fixture.adapter.items(null, PageRequest()).items.single()

        assertEquals(listOf("4K", "1080"), movie.tags)

        assertTrue(movie.primaryImage!!.url.contains("/Items/m0102030405060708/Images/Primary"))
        assertFalse(movie.primaryImage!!.url.contains("tag="), movie.primaryImage!!.url)
    }

    @Test
    fun canonicalPrimaryKeepsTaggedThumbAsRuntimeFallback() = runTest {
        val fixture = Fixture()
        val thumbOnlyMovie = MOVIE_JSON.replace(
            "\"ImageTags\":{\"Primary\":\"1111111122222222\",\"Thumb\":\"3333333344444444\",\"ScreenGrab\":\"ignored\"},",
            "\"ImageTags\":{\"Thumb\":\"3333333344444444\"},",
        )
        fixture.transport.enqueueJson(200, queryResult(listOf(thumbOnlyMovie), total = 1))

        val movie = fixture.adapter.items(null, PageRequest()).items.single()

        assertEquals(
            listOf(ImageKind.Primary, ImageKind.Thumb, ImageKind.Backdrop),
            movie.images.map { it.kind },
        )
        assertTrue(movie.images[0].url.contains("/Items/m0102030405060708/Images/Primary"))
        assertTrue(movie.images[1].url.contains("/Items/m0102030405060708/Images/Thumb?tag=3333333344444444"))
    }

    @Test
    fun inheritedPrimaryImageFollowsCanonicalPrimaryAsFallback() = runTest {
        val fixture = Fixture()
        val inheritedMovie = MOVIE_JSON.replace(
            "\"ImageTags\":{\"Primary\":\"1111111122222222\",\"Thumb\":\"3333333344444444\",\"ScreenGrab\":\"ignored\"},",
            "\"ParentPrimaryImageItemId\":\"parent-primary-id\",\"ParentPrimaryImageTag\":\"parent-primary-tag\",\"ImageTags\":{},",
        )
        fixture.transport.enqueueJson(200, queryResult(listOf(inheritedMovie), total = 1))

        val movie = fixture.adapter.items(null, PageRequest()).items.single()

        assertEquals(listOf(ImageKind.Primary, ImageKind.Primary, ImageKind.Backdrop), movie.images.map { it.kind })
        assertTrue(movie.images[0].url.contains("/Items/m0102030405060708/Images/Primary"))
        assertTrue(movie.images[1].url.contains("/Items/parent-primary-id/Images/Primary?tag=parent-primary-tag"))
    }

    @Test
    fun inheritedThumbIsKeptAsPosterFallbackForGroupedLatestItem() = runTest {
        val fixture = Fixture()
        val inheritedThumbMovie = MOVIE_JSON.replace(
            "\"ImageTags\":{\"Primary\":\"1111111122222222\",\"Thumb\":\"3333333344444444\",\"ScreenGrab\":\"ignored\"},",
            "\"ParentThumbItemId\":\"parent-thumb-id\",\"ParentThumbImageTag\":\"parent-thumb-tag\",\"ImageTags\":{},",
        ).replace("\"BackdropImageTags\":[\"bd00000000000001\"],", "\"BackdropImageTags\":[],")
        fixture.transport.enqueueJson(200, queryResult(listOf(inheritedThumbMovie), total = 1))

        val movie = fixture.adapter.items(null, PageRequest()).items.single()

        assertEquals(listOf(ImageKind.Primary, ImageKind.Thumb), movie.images.map { it.kind })
        assertTrue(movie.images[0].url.contains("/Items/m0102030405060708/Images/Primary"))
        assertTrue(movie.images[1].url.contains("/Items/parent-thumb-id/Images/Thumb?tag=parent-thumb-tag"))
    }

    // ---------------------------------------------------------- item mapping

    @Test
    fun itemsMapEpisodeHierarchyAndTicks() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, queryResult(listOf(EPISODE_JSON), total = 7))
        val episode = fixture.adapter.items(null, PageRequest()).items.single()
        assertEquals(MediaId("e0102030405060708"), episode.id)
        assertEquals(MediaType.Episode, episode.mediaType)
        assertEquals(MediaId("s0102030405060708"), episode.seriesId)
        assertEquals("Fixture Series", episode.seriesTitle)
        assertEquals(MediaId("se01020304050607"), episode.seasonId)
        assertEquals("Season 1", episode.seasonTitle)
        // Jellyfin expresses the season of an episode via ParentIndexNumber.
        assertEquals(1, episode.seasonNumber)
        assertEquals(2, episode.episodeNumber)
        assertEquals(1_500_000L, episode.runtimeMs) // 15_000_000_000 ticks / 10_000
        assertTrue(episode.premiereEpochMs != null)
        assertTrue(episode.canPlay)
    }

    @Test
    fun episodeArtworkUsesLegacySeriesPosterAndParentLandscapeFallback() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, queryResult(listOf(EPISODE_JSON), total = 1))
        val images = fixture.adapter.items(null, PageRequest()).items.single().images
        assertEquals(listOf(ImageKind.Primary, ImageKind.Backdrop), images.map { it.kind })
        assertTrue(
            images[0].url.contains("/Items/s0102030405060708/Images/Primary?tag=series-primary-tag"),
            images[0].url,
        )
        assertTrue(
            images[1].url.contains("/Items/thumb-parent-id/Images/Thumb?tag=parent-thumb-tag"),
            images[1].url,
        )
    }

    @Test
    fun itemsMapUserStateFields() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, queryResult(listOf(EPISODE_JSON), total = 7))
        val userData = fixture.adapter.items(null, PageRequest()).items.single().userData
        assertEquals(true, userData.isFavorite)
        assertEquals(true, userData.isPlayed)
        assertEquals(2, userData.playCount)
        assertEquals(12_345L, userData.playbackPositionMs) // 123_450_000 ticks / 10_000
        assertEquals(41.2, userData.playedPercentage)
        assertTrue(userData.hasProgress)
        assertTrue(userData.lastPlayedEpochMs != null)
    }

    @Test
    fun itemsMapMovieFieldsImagesAndPeople() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, queryResult(listOf(MOVIE_JSON), total = 1))
        val movie = fixture.adapter.items(null, PageRequest()).items.single()
        assertEquals("Fixture Movie", movie.title)
        assertEquals("Original Movie", movie.originalTitle)
        assertEquals(MediaType.Movie, movie.mediaType)
        assertEquals(2024, movie.year)
        assertEquals(8.1, movie.communityRating)
        assertEquals("PG-13", movie.officialRating)
        assertEquals(listOf("Drama"), movie.genres)
        assertEquals(7_200_000L, movie.runtimeMs) // 72_000_000_000 ticks / 10_000 = 2h
        assertTrue(movie.canPlay)
        assertEquals(2024, movie.premiereEpochMs?.let(::yearOfEpochMs))
        assertEquals(2024, movie.dateAddedEpochMs?.let(::yearOfEpochMs))
        assertEquals(listOf("Ada Director", "Bob Actor"), movie.people.map { it.name })
        assertEquals(PersonKind.Director, movie.people[0].personType)
        assertEquals(MediaId("person-actor"), movie.people[1].id)
        assertTrue(movie.people[1].image!!.url.contains("/Items/person-actor/Images/Primary?tag=actor-tag"))
        // Primary first, thumb kept, non-contract image kinds dropped, backdrops appended.
        assertEquals(
            listOf(ImageKind.Primary, ImageKind.Thumb, ImageKind.Backdrop),
            movie.images.map { it.kind },
        )
        assertTrue(movie.images[0].url.contains("/Items/m0102030405060708/Images/Primary?tag=1111111122222222"), movie.images[0].url)
    }

    @Test
    fun itemsMapSeasonNumberUnknownTypesAndDropNullIds() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(
            200,
            queryResult(listOf(SEASON_JSON, UNKNOWN_TYPE_JSON, NO_ID_JSON), total = 2),
        )
        val page = fixture.adapter.items(null, PageRequest())
        // The id-less item is dropped instead of failing the page.
        assertEquals(2, page.items.size)
        val season = page.items[0]
        assertEquals(MediaType.Season, season.mediaType)
        assertEquals(2, season.seasonNumber)
        assertEquals(MediaId("s0102030405060708"), season.seriesId)
        assertFalse(season.canPlay)
        val unknown = page.items[1]
        assertEquals(MediaType.Unknown, unknown.mediaType)
        assertFalse(unknown.canPlay)
    }

    @Test
    fun itemDetailMapsSeriesWithPeople() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.SERIES_DETAIL)
        val series = fixture.adapter.item(MediaId("s0102030405060708"))
        assertEquals("Fixture Series", series.title)
        assertEquals(MediaType.Series, series.mediaType)
        assertEquals(2023, series.year)
        assertEquals("A fixture series used in protocol tests.", series.overview)
        assertEquals(listOf("Drama"), series.genres)
        assertEquals(2, series.people.size)
        assertEquals(PersonKind.Director, series.people[0].personType)
        assertEquals(PersonKind.Actor, series.people[1].personType)
        assertTrue(series.premiereEpochMs != null)
        assertTrue(series.primaryImage != null)
    }

    @Test
    fun personDetailMapsLegacyPersonFieldsAndAvatar() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, PERSON_JSON)

        val person = fixture.adapter.person(MediaId("person-actor"))

        assertEquals("Bob Actor", person.name)
        assertEquals("Actor, Bob", person.sortName)
        assertEquals("Person biography", person.overview)
        assertEquals(3, person.movieCount)
        assertEquals(2, person.seriesCount)
        assertEquals(4, person.episodeCount)
        assertTrue(person.isFavorite)
        assertTrue(person.image!!.url.contains("/Items/person-actor/Images/Primary?tag=actor-tag"))
    }

    @Test
    fun itemsByPersonCarriesPersonIdTypeAndLegacyTitleSort() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, queryResult(listOf(MOVIE_JSON), total = 1))

        val page = fixture.adapter.itemsByPerson(
            MediaId("person-actor"),
            PageRequest(
                pageSize = 10,
                sort = MediaSort(MediaSortField.Title, SortDirection.Ascending),
                filter = MediaFilter(mediaTypes = setOf(MediaType.Movie)),
            ),
        )

        assertEquals(listOf("Fixture Movie"), page.items.map { it.title })
        val url = fixture.transport.requests.single().url.toString()
        assertTrue(url.contains("personIds=person-actor"), url)
        assertTrue(url.contains("includeItemTypes=Movie"), url)
        assertTrue(url.contains("sortBy=Name"), url)
        assertTrue(url.contains("sortOrder=Ascending"), url)
        assertTrue(url.contains("limit=10"), url)
    }

    @Test
    fun searchWithoutExplicitSortKeepsLegacyServerOrdering() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, queryResult(listOf(MOVIE_JSON), total = 1))

        val page = fixture.adapter.search(
            "fixture",
            PageRequest(
                pageSize = 500,
                filter = MediaFilter(
                    mediaTypes = setOf(MediaType.Episode, MediaType.Movie, MediaType.Video, MediaType.Series),
                ),
            ),
        )

        assertEquals(listOf("Fixture Movie"), page.items.map { it.title })
        val url = fixture.transport.requests.single().url.toString()
        assertTrue(url.contains("searchTerm=fixture"), url)
        assertTrue(url.contains("recursive=true"), url)
        assertTrue(url.contains("limit=500"), url)
        assertFalse(url.contains("sortBy="), url)
    }

    @Test
    fun legacySortFieldsMapToTheOriginalJellyfinWireNames() {
        val expected = linkedMapOf(
            MediaSortField.Default to "Default",
            MediaSortField.Title to "Name",
            MediaSortField.DateAdded to "DateCreated",
            MediaSortField.PremiereDate to "PremiereDate",
            MediaSortField.DatePlayed to "DatePlayed",
            MediaSortField.CommunityRating to "CommunityRating",
            MediaSortField.CriticRating to "CriticRating",
            MediaSortField.ProductionYear to "ProductionYear",
            MediaSortField.PlayCount to "PlayCount",
            MediaSortField.Runtime to "Runtime",
            MediaSortField.OfficialRating to "OfficialRating",
            MediaSortField.Random to "Random",
            MediaSortField.Album to "Album",
            MediaSortField.Artist to "Artist",
            MediaSortField.AlbumArtist to "AlbumArtist",
            MediaSortField.AiredEpisodeOrder to "AiredEpisodeOrder",
            MediaSortField.SeriesSortName to "SeriesSortName",
            MediaSortField.Studio to "Studio",
            MediaSortField.IsFavoriteOrLiked to "IsFavoriteOrLiked",
            MediaSortField.DateLastContentAdded to "DateLastContentAdded",
            MediaSortField.IsUnplayed to "IsUnplayed",
            MediaSortField.IsPlayed to "IsPlayed",
        )
        assertEquals(expected.keys, MediaSortField.entries.toSet() - setOf(
            MediaSortField.SeasonNumber,
            MediaSortField.EpisodeNumber,
        ))
        expected.forEach { (field, wireName) ->
            assertEquals(wireName, MediaSort(field).toQuerySort().first.single().wireName, field.name)
        }
    }

    @Test
    fun missingDetailBecomesResourceNotFound() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(404, """{"title":"Not Found"}""")
        assertFailsWith<ResourceNotFoundError> {
            fixture.adapter.item(MediaId("gone"))
        }
    }

    // -------------------------------------------------------------- seasons

    @Test
    fun seasonsAreASingleChunkWithSeasonNumbers() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.SEASONS)
        val page = fixture.adapter.seasons(MediaId("s0102030405060708"))
        assertEquals(listOf("Season 1", "Season 2"), page.items.map { it.title })
        assertEquals(listOf(10, 8), page.items.map { it.childCount })
        assertEquals(listOf(1, 2), page.items.map { it.seasonNumber })
        assertNull(page.nextCursor)
        assertEquals(2, page.totalEstimate)
        val request = fixture.transport.requests.single()
        assertTrue(request.url.toString().contains("/Shows/s0102030405060708/Seasons"), request.url.toString())
    }

    @Test
    fun nextUpForSeriesUsesTheServerTargetInsteadOfInventingAnEpisode() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, queryResult(listOf(EPISODE_JSON), total = 1))

        val target = fixture.adapter.nextUp(MediaId("s0102030405060708"))

        assertEquals(MediaId("e0102030405060708"), target?.id)
        assertEquals(1, target?.seasonNumber)
        assertEquals(2, target?.episodeNumber)
        val query = fixture.transport.requests.single().url.queryParameters
        assertEquals("s0102030405060708", query.first("seriesId"))
        assertEquals("true", query.first("enableResumable"))
        assertEquals("true", query.first("enableRewatching"))
    }

    @Test
    fun chasingNextUpUsesTheLegacyCutoffWithoutResumeOrRewatchCandidates() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, queryResult(listOf(EPISODE_JSON), total = 1))

        val target = fixture.adapter.chasingNextUp(
            seriesId = MediaId("s0102030405060708"),
            nextUpDateCutoffEpochMs = 1_000L,
        )

        assertEquals(MediaId("e0102030405060708"), target?.id)
        val query = fixture.transport.requests.single().url.queryParameters
        assertEquals("s0102030405060708", query.first("seriesId"))
        assertEquals("false", query.first("enableResumable"))
        assertEquals("false", query.first("enableRewatching"))
        assertEquals("1970-01-01T00:00:01Z", query.first("nextUpDateCutoff"))
    }

    // --------------------------------------------------------------- paging

    @Test
    fun firstPageUsesNullCursorAndWalkContinuesWhileTotalAllows() = runTest {
        val fixture = Fixture()
        fixture.transport.handler = { request ->
            val url = request.url.toString()
            when {
                url.contains("startIndex=0") -> FakeJellyfinTransport.jsonResponse(
                    200,
                    queryResult(listOf(MOVIE_JSON, MOVIE_JSON.replace("m0102030405060708", "m0102030405060709")), total = 5),
                    request.url,
                )
                url.contains("startIndex=2") -> FakeJellyfinTransport.jsonResponse(
                    200,
                    queryResult(listOf(MOVIE_JSON.replace("m0102030405060708", "m2")), total = 5, startIndex = 2),
                    request.url,
                )
                else -> FakeJellyfinTransport.jsonResponse(
                    200,
                    queryResult(emptyList(), total = 5, startIndex = 3),
                    request.url,
                )
            }
        }
        val first = fixture.adapter.items(null, PageRequest(pageSize = 2))
        assertEquals(2, first.items.size)
        assertEquals(OffsetPageCursorCodec.encode(2), first.nextCursor)
        assertEquals(5, first.totalEstimate)
        assertTrue(fixture.transport.requests.first().url.toString().contains("startIndex=0"))
        // Clamped second page: 1 item, but the total says 5 — keep walking.
        val second = fixture.adapter.items(null, PageRequest(cursor = first.nextCursor, pageSize = 2))
        assertEquals(1, second.items.size)
        assertEquals(OffsetPageCursorCodec.encode(3), second.nextCursor)
        // Terminal empty page ends the walk.
        val tail = fixture.adapter.items(null, PageRequest(cursor = second.nextCursor, pageSize = 2))
        assertTrue(tail.isEmpty)
        assertNull(tail.nextCursor)
    }

    @Test
    fun alignedPageWithKnownTotalTerminatesWithoutAnExtraProbe() = runTest {
        val fixture = Fixture()
        // total=2, full page of 2: the server already declared the end.
        fixture.transport.enqueueJson(
            200,
            queryResult(listOf(MOVIE_JSON, MOVIE_JSON.replace("m0102030405060708", "m0102030405060709")), total = 2),
        )
        val page = fixture.adapter.items(null, PageRequest(pageSize = 2))
        assertEquals(2, page.items.size)
        assertNull(page.nextCursor)
        assertEquals(1, fixture.transport.requests.size)
    }

    @Test
    fun totalRecordCountZeroIsNotNoMedia() = runTest {
        val fixture = Fixture()
        // Server answered 0 for the unknown total yet returned items.
        fixture.transport.enqueueJson(200, queryResult(listOf(MOVIE_JSON, MOVIE_JSON), total = 0))
        val page = fixture.adapter.items(null, PageRequest(pageSize = 2))
        assertEquals(2, page.items.size)
        assertNull(page.totalEstimate) // 0 is "unknown", never reported as an estimate
        assertEquals(OffsetPageCursorCodec.encode(2), page.nextCursor) // paging continues
        assertTrue(page.hasMore)
    }

    @Test
    fun alignedPageWithUnknownTotalStillProbesThenStopsOnEmptyPage() = runTest {
        val fixture = Fixture()
        fixture.transport.handler = { request ->
            val url = request.url.toString()
            when {
                url.contains("startIndex=0") -> FakeJellyfinTransport.jsonResponse(
                    200,
                    queryResult(listOf(MOVIE_JSON, MOVIE_JSON.replace("m0102030405060708", "m0102030405060709")), total = 0),
                    request.url,
                )
                url.contains("startIndex=2") -> FakeJellyfinTransport.jsonResponse(
                    200,
                    queryResult(emptyList(), total = 0, startIndex = 2),
                    request.url,
                )
                else -> throw IllegalStateException("unexpected paging request $url")
            }
        }
        // Total unknown (0): the aligned full page still earns a probe.
        val first = fixture.adapter.items(null, PageRequest(pageSize = 2))
        assertEquals(2, first.items.size)
        assertEquals(OffsetPageCursorCodec.encode(2), first.nextCursor)
        assertNull(first.totalEstimate)
        val tail = fixture.adapter.items(null, PageRequest(cursor = first.nextCursor, pageSize = 2))
        assertTrue(tail.isEmpty)
        assertNull(tail.nextCursor)
        assertNull(tail.totalEstimate)
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
    fun episodesOffsetPageThroughTheShowsEndpoint() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.EPISODES)
        val page = fixture.adapter.episodes(
            seriesId = MediaId("s0102030405060708"),
            seasonId = MediaId("se01020304050607"),
            request = PageRequest(pageSize = 200),
        )
        assertEquals(2, page.items.size)
        // Fixture "Pilot" is episode 1 of season 1.
        assertEquals(1, page.items[0].episodeNumber)
        assertEquals(1, page.items[0].seasonNumber)
        val url = fixture.transport.requests.single().url.toString()
        assertTrue(url.contains("/Shows/s0102030405060708/Episodes"), url)
        assertTrue(url.contains("seasonId=se01020304050607"), url)
        assertTrue(url.contains("startIndex=0"), url)
        assertTrue(url.contains("limit=200"), url)
    }

    // -------------------------------------------------------- query mapping

    @Test
    fun filtersMapOntoJellyfinQueryParameters() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, queryResult(emptyList(), total = 0))
        fixture.adapter.items(
            CollectionId("v0102030405060708"),
            PageRequest(
                filter = MediaFilter(
                    mediaTypes = setOf(MediaType.Movie, MediaType.Series),
                    genres = setOf("Drama"),
                    years = setOf(2024),
                    favoritesOnly = true,
                    playedOnly = true,
                ),
                sort = MediaSort(MediaSortField.SeasonNumber, SortDirection.Descending),
            ),
        )
        val url = fixture.transport.requests.single().url.toString()
        assertTrue(url.contains("parentId=v0102030405060708"), url)
        assertTrue(url.contains("recursive=true"), url)
        assertTrue(url.contains("includeItemTypes=Movie%2CSeries") || url.contains("includeItemTypes=Movie,Series"), url)
        assertTrue(url.contains("filters=IsFavorite%2CIsPlayed") || url.contains("filters=IsFavorite,IsPlayed"), url)
        assertTrue(url.contains("genres=Drama"), url)
        assertTrue(url.contains("years=2024"), url)
        assertTrue(url.contains("sortBy=ParentIndexNumber"), url)
        assertTrue(url.contains("sortOrder=Descending"), url)
        assertFalse(url.contains("searchTerm"), url)
    }

    @Test
    fun unplayedOnlyMapsToIsUnplayed() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, queryResult(emptyList(), total = 0))
        fixture.adapter.items(null, PageRequest(filter = MediaFilter(unplayedOnly = true)))
        assertTrue(fixture.transport.requests.single().url.toString().contains("filters=IsUnplayed"))
    }

    @Test
    fun unknownMediaTypeFilterIsRejected() = runTest {
        val fixture = Fixture()
        assertFailsWith<ClientConfigError> {
            fixture.adapter.items(null, PageRequest(filter = MediaFilter(mediaTypes = setOf(MediaType.Unknown))))
        }
        assertEquals(0, fixture.transport.requests.size)
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
    fun searchRoutesSearchTermThroughItems() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, queryResult(listOf(MOVIE_JSON), total = 1))
        val page = fixture.adapter.search("fixture", PageRequest())
        assertEquals(1, page.items.size)
        val url = fixture.transport.requests.single().url.toString()
        assertTrue(url.contains("searchTerm=fixture"), url)
        assertTrue(url.contains("/Items"), url)
        assertTrue(url.contains("recursive=true"), url)
        assertFalse(url.contains("parentId="), url)
    }

    @Test
    fun searchCarriesExpressibleConstraintsAndStampsNoCollection() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, queryResult(listOf(MOVIE_JSON), total = 1))
        val page = fixture.adapter.search(
            "fixture",
            PageRequest(
                filter = MediaFilter(
                    mediaTypes = setOf(MediaType.Movie),
                    years = setOf(2024),
                    unplayedOnly = true,
                ),
            ),
        )
        val url = fixture.transport.requests.single().url.toString()
        assertTrue(url.contains("searchTerm=fixture"), url)
        assertTrue(url.contains("includeItemTypes=Movie"), url)
        assertTrue(url.contains("years=2024"), url)
        assertTrue(url.contains("filters=IsUnplayed"), url)
        // Search results have no collection context to stamp.
        assertNull(page.items.single().collectionId)
    }

    @Test
    fun itemsStampTheQueriedCollectionOntoEntries() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, queryResult(listOf(MOVIE_JSON), total = 1))
        val page = fixture.adapter.items(CollectionId("library-1"), PageRequest())
        assertEquals(CollectionId("library-1"), page.items.single().collectionId)
        assertTrue(fixture.transport.requests.single().url.toString().contains("parentId=library-1"))
    }

    @Test
    fun episodesRejectFiltersAndSortTheShowsEndpointCannotExpress() = runTest {
        val fixture = Fixture()
        assertFailsWith<ClientConfigError> {
            fixture.adapter.episodes(
                MediaId("s1"),
                seasonId = null,
                request = PageRequest(filter = MediaFilter(unplayedOnly = true)),
            )
        }
        assertFailsWith<ClientConfigError> {
            fixture.adapter.episodes(
                MediaId("s1"),
                seasonId = null,
                request = PageRequest(sort = MediaSort(MediaSortField.Title)),
            )
        }
        assertEquals(0, fixture.transport.requests.size)
    }

    @Test
    fun clampedShortPageWithKnownTotalKeepsWalkingUntilExhausted() = runTest {
        val fixture = Fixture()
        fixture.transport.handler = { request ->
            val url = request.url.toString()
            when {
                // Server clamps the requested page of 50 down to one item,
                // but the total says three exist.
                url.contains("startIndex=0") -> FakeJellyfinTransport.jsonResponse(
                    200,
                    queryResult(listOf(MOVIE_JSON), total = 3),
                    request.url,
                )
                url.contains("startIndex=1") -> FakeJellyfinTransport.jsonResponse(
                    200,
                    queryResult(listOf(MOVIE_JSON.replace("m0102030405060708", "m2")), total = 3, startIndex = 1),
                    request.url,
                )
                url.contains("startIndex=2") -> FakeJellyfinTransport.jsonResponse(
                    200,
                    queryResult(listOf(MOVIE_JSON.replace("m0102030405060708", "m3")), total = 3, startIndex = 2),
                    request.url,
                )
                else -> throw IllegalStateException("unexpected paging request $url")
            }
        }
        var cursor: PageCursor? = null
        val seen = mutableListOf<String>()
        do {
            val page = fixture.adapter.items(null, PageRequest(cursor = cursor, pageSize = 50))
            seen += page.items.map { it.id.value }
            cursor = page.nextCursor
        } while (cursor != null)
        assertEquals(listOf("m0102030405060708", "m2", "m3"), seen)
    }

    // ------------------------------------------------------------- failures

    @Test
    fun providerErrorsTranslateToDomainErrors() = runTest {
        // 401 with a live session -> expired token.
        run {
            val fixture = Fixture()
            fixture.transport.enqueueJson(401, JellyfinFixtures.PROBLEM_DETAILS_401)
            val error = assertFailsWith<AuthenticationError> { fixture.adapter.collections(PageRequest()) }
            assertEquals(AuthFailureKind.TokenExpired, error.kind)
        }
        // 401 without a session -> bad credentials.
        run {
            val fixture = Fixture(session = null)
            fixture.transport.enqueueJson(401, JellyfinFixtures.PROBLEM_DETAILS_401)
            val error = assertFailsWith<AuthenticationError> { fixture.adapter.collections(PageRequest()) }
            assertEquals(AuthFailureKind.InvalidCredentials, error.kind)
        }
        // 403 -> authorization.
        run {
            val fixture = Fixture()
            fixture.transport.enqueueJson(403, """{"title":"Forbidden"}""")
            assertFailsWith<AuthorizationError> { fixture.adapter.collections(PageRequest()) }
        }
        // 429 -> rate limited.
        run {
            val fixture = Fixture()
            fixture.transport.enqueueJson(429, """{"title":"Slow down"}""")
            assertFailsWith<RateLimitedError> { fixture.adapter.collections(PageRequest()) }
        }
        // 5xx and other 4xx surface as server errors with status preserved.
        run {
            val fixture = Fixture()
            fixture.transport.enqueueJson(500, """{"title":"Boom"}""")
            assertEquals(500, assertFailsWith<ServerError> { fixture.adapter.collections(PageRequest()) }.statusCode)
        }
        run {
            val fixture = Fixture()
            fixture.transport.enqueueJson(400, """{"title":"Bad request"}""")
            assertEquals(400, assertFailsWith<ServerError> { fixture.adapter.collections(PageRequest()) }.statusCode)
        }
    }

    @Test
    fun garbagePayloadTranslatesToProtocolDecodingError() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, """{"Items": "not-a-list"}""")
        assertFailsWith<ProtocolDecodingError> { fixture.adapter.collections(PageRequest()) }
    }

    @Test
    fun connectionFailuresTranslateToNetworkErrors() = runTest {
        val fixture = Fixture()
        fixture.transport.handler = { throw HttpTimeoutException(it.url, timeoutMillis = 5_000L) }
        assertFailsWith<TimeoutError> { fixture.adapter.collections(PageRequest()) }
    }

    @Test
    fun transportOutageTranslatesToNetworkUnreachable() = runTest {
        val fixture = Fixture()
        fixture.transport.handler = {
            throw HttpTransportException(it.url, TransportFailureKind.Connect, "connection refused")
        }
        assertFailsWith<NetworkUnreachableError> { fixture.adapter.collections(PageRequest()) }
    }

    @Test
    fun cancellationPropagatesUntouched() = runTest {
        val fixture = Fixture()
        fixture.transport.handler = { throw CancellationException("scope cancelled") }
        val thrown = assertFailsWith<CancellationException> { fixture.adapter.collections(PageRequest()) }
        assertEquals("scope cancelled", thrown.message)
    }

    // -------------------------------------------------------------- helpers

    /** 2024-01-01T00:00:00Z / 2024-02-01T00:00:00Z both belong to year 2024. */
    private fun yearOfEpochMs(epochMs: Long): Int {
        // Pure Kotlin epoch math to avoid platform date APIs in common tests.
        val days = epochMs / 86_400_000L
        var year = 1970L
        var remaining = days
        while (remaining >= if (isLeap(year)) 366 else 365) {
            remaining -= if (isLeap(year)) 366 else 365
            year += 1
        }
        return year.toInt()
    }

    private fun isLeap(year: Long): Boolean =
        (year % 4 == 0L && year % 100 != 0L) || year % 400 == 0L
}
