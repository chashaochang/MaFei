package cn.mafei.cmp.providers.jellyfin.transport

import cn.mafei.cmp.core.http_contract.HttpBody
import cn.mafei.cmp.core.http_contract.HttpMethod
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.core.JellyfinRequestMode
import cn.mafei.cmp.providers.jellyfin.testkit.JellyfinFixtures
import cn.mafei.cmp.providers.jellyfin.wire.DeviceProfileDto
import cn.mafei.cmp.providers.jellyfin.wire.DirectPlayProfileDto
import cn.mafei.cmp.providers.jellyfin.wire.DlnaProfileType
import cn.mafei.cmp.providers.jellyfin.wire.ImageType
import cn.mafei.cmp.providers.jellyfin.wire.ItemField
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackInfoRequestDto
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackInfoResponseDto
import cn.mafei.cmp.providers.jellyfin.wire.SubtitleDeliveryMethod
import cn.mafei.cmp.providers.jellyfin.wire.SubtitleProfileDto
import cn.mafei.cmp.providers.jellyfin.wire.TranscodingProfileDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JellyfinRequestsTest {

    private val base: HttpUrl = HttpUrl.parse("https://jellyfin.test")
    private val basePath: HttpUrl = HttpUrl.parse("https://jellyfin.test/stable")

    private fun urlOf(request: cn.mafei.cmp.core.http_contract.HttpRequest): HttpUrl = request.url

    // ------------------------------------------------- official endpoint shapes

    @Test
    fun publicSystemInfoBuildsAnonymousGet() {
        val request = JellyfinRequests.publicSystemInfo(base)
        assertEquals("https://jellyfin.test/System/Info/Public", urlOf(request).toString())
        assertEquals(HttpMethod.Get, request.method)
        assertEquals(JellyfinRequestMode.ANONYMOUS, request.attributes[JellyfinRequestMode.ATTRIBUTE])
    }

    @Test
    fun viewsUseTheOfficialUserViewsEndpointWithLowerCamelQuery() {
        val request = JellyfinRequests.views(base, "user-1")
        assertEquals("https://jellyfin.test/UserViews?userId=user-1", urlOf(request).toString())
        assertEquals(JellyfinRequestMode.AUTHENTICATED, request.attributes[JellyfinRequestMode.ATTRIBUTE])
    }

    @Test
    fun itemByIdUsesOfficialItemsIdOperationWithoutFields() {
        val request = JellyfinRequests.itemById(base, "user/1", "item?2")
        assertEquals("https://jellyfin.test/Items/item%3F2?userId=user%2F1", urlOf(request).toString())
        assertNull(urlOf(request).queryParameters.first("fields"))
    }

    @Test
    fun userImageUsesTheOfficialUserImageEndpoint() {
        assertEquals(
            "https://jellyfin.test/UserImage?userId=u1&imageType=Primary&tag=t1",
            JellyfinRequests.userImageUrl(base, "u1", tag = "t1").toString(),
        )
        assertEquals(
            "https://jellyfin.test/UserImage?userId=u1&imageType=Banner&api_key=tok",
            JellyfinRequests.userImageUrl(base, "u1", imageType = ImageType.Banner, token = "tok").toString(),
        )
    }

    @Test
    fun itemsQueryUsesOfficialLowerCamelParameterNames() {
        val request = JellyfinRequests.items(
            base,
            JellyfinItemQuery(
                userId = "user-1",
                parentId = "parent-2",
                startIndex = 100,
                limit = 25,
                includeItemTypes = listOf("Movie", "Series"),
                fields = listOf(ItemField.People, ItemField.MediaSources),
                sortBy = listOf(JellyfinSortBy.DateCreated, JellyfinSortBy.SortName),
                sortOrder = JellyfinSortOrder.Descending,
                recursive = true,
                searchTerm = "bat man & robin 中文",
                genres = listOf("Sci-Fi", "Drama"),
                years = listOf(2023, 2024),
                filters = listOf(JellyfinItemFilter.IsUnplayed),
            ),
        )
        val query = urlOf(request).queryParameters
        assertEquals("user-1", query.first("userId"))
        assertEquals("parent-2", query.first("parentId"))
        assertEquals("100", query.first("startIndex"))
        assertEquals("25", query.first("limit"))
        assertEquals("Movie,Series", query.first("includeItemTypes"))
        assertEquals("People,MediaSources", query.first("fields"))
        assertEquals("DateCreated,SortName", query.first("sortBy"))
        assertEquals("Descending", query.first("sortOrder"))
        assertEquals("true", query.first("recursive"))
        assertEquals("bat man & robin 中文", query.first("searchTerm"))
        assertEquals("Sci-Fi|Drama", query.first("genres"))
        assertEquals("2023,2024", query.first("years"))
        assertEquals("IsUnplayed", query.first("filters"))
        val wire = urlOf(request).toEncodedString()
        assertTrue(wire.contains("searchTerm=bat%20man%20%26%20robin%20%E4%B8%AD%E6%96%87"), wire)
    }

    @Test
    fun seasonsAndEpisodesBindOfficialShowEndpoints() {
        assertEquals(
            "https://jellyfin.test/Shows/s1/Seasons?userId=u1",
            urlOf(JellyfinRequests.seasons(base, "u1", "s1")).toString(),
        )
        assertEquals(
            "https://jellyfin.test/Shows/s1/Episodes?userId=u1&seasonId=season1&startIndex=5&limit=10&fields=People",
            urlOf(
                JellyfinRequests.episodes(base, "u1", "s1", seasonId = "season1", startIndex = 5, limit = 10, fields = listOf(ItemField.People)),
            ).toString(),
        )
    }

    @Test
    fun authenticateByNameEncodesOfficialBody() {
        val request = JellyfinRequests.authenticateByName(base, "ada@example.org", "p@ss\"word")
        val body = (request.body as HttpBody.Text).text
        assertEquals("""{"Username":"ada@example.org","Pw":"p@ss\"word"}""", body)
        assertEquals("application/json", request.headers.first("content-type"))
        assertEquals(JellyfinRequestMode.ANONYMOUS, request.attributes[JellyfinRequestMode.ATTRIBUTE])
    }

    @Test
    fun playbackInfoSendsPascalCaseJsonBodyAndLowerCamelQuery() {
        val request = JellyfinRequests.playbackInfo(
            base,
            userId = "u1",
            itemId = "m1",
            body = PlaybackInfoRequestDto(userId = "u1", playSessionId = "ps1", autoOpenLiveStream = false),
        )
        val url = urlOf(request)
        assertEquals("https://jellyfin.test/Items/m1/PlaybackInfo", url.toEncodedString().substringBefore('?'))
        assertEquals("u1", url.queryParameters.first("userId"))
        assertEquals("false", url.queryParameters.first("autoOpenLiveStream"))
        assertEquals("""{"UserId":"u1","PlaySessionId":"ps1","AutoOpenLiveStream":false}""", (request.body as HttpBody.Text).text)
    }

    @Test
    fun deviceProfileIsEncodedExactly() {
        val request = JellyfinRequests.playbackInfo(
            base,
            userId = "u1",
            itemId = "m1",
            body = PlaybackInfoRequestDto(
                userId = "u1",
                playSessionId = "ps1",
                deviceProfile = DeviceProfileDto(
                    name = "MaFei Test",
                    maxStreamingBitrate = 120_000_000,
                    directPlayProfiles = listOf(
                        DirectPlayProfileDto(container = "mkv", type = DlnaProfileType.Video),
                    ),
                    transcodingProfiles = listOf(
                        TranscodingProfileDto(container = "ts", type = DlnaProfileType.Video, videoCodec = "h264", audioCodec = "aac", protocol = "hls"),
                    ),
                    subtitleProfiles = listOf(
                        SubtitleProfileDto(format = "srt", method = SubtitleDeliveryMethod.External),
                    ),
                ),
            ),
        )
        val body = (request.body as HttpBody.Text).text
        assertTrue(
            body.contains(
                """"DeviceProfile":{"Name":"MaFei Test","MaxStreamingBitrate":120000000,""" +
                    """"DirectPlayProfiles":[{"Container":"mkv","Type":"Video"}],""" +
                    """"TranscodingProfiles":[{"Container":"ts","Type":"Video","VideoCodec":"h264","AudioCodec":"aac","Protocol":"hls"}],""" +
                    """"SubtitleProfiles":[{"Format":"srt","Method":"External"}]}""",
            ),
            body,
        )
    }

    // ------------------------------------------------------------- resource URLs

    @Test
    fun directStreamUrlCarriesStaticSourceIdAndTokenWithLowerCamelParams() {
        val url = JellyfinRequests.directStreamUrl(base, itemId = "m1", mediaSourceId = "ms1", container = "mkv", playSessionId = "ps1", token = "tok")
        assertEquals("https://jellyfin.test/Videos/m1/stream.mkv?static=true&mediaSourceId=ms1&playSessionId=ps1&api_key=tok", url.toString())

        val audio = JellyfinRequests.directStreamUrl(base, itemId = "a1", token = null, audio = true)
        assertEquals("https://jellyfin.test/Audio/a1/stream?static=true", audio.toString())
        assertNull(audio.queryParameters.first("api_key"))
    }

    @Test
    fun transcodingUrlIsAbsolutizedAndGetsApiKeyOnce() {
        val url = JellyfinRequests.absolutizeTranscodingUrl(base, "/videos/m1/master.m3u8?VideoCodec=h264&PlaySessionId=ps1", token = "tok")
        assertEquals("https://jellyfin.test/videos/m1/master.m3u8?VideoCodec=h264&PlaySessionId=ps1&api_key=tok", url.toString())

        val alreadyHasKey = JellyfinRequests.absolutizeTranscodingUrl(base, "/videos/m1/master.m3u8?api_key=server", token = "tok")
        assertEquals("server", alreadyHasKey.queryParameters.first("api_key"))
        assertEquals(1, alreadyHasKey.queryParameters.values("api_key").size)
    }

    @Test
    fun transcodingUrlRejectsCrossOriginTargets() {
        val error = assertFailsWith<IllegalArgumentException> {
            JellyfinRequests.absolutizeTranscodingUrl(base, "https://evil.test/videos/m1/master.m3u8", token = "tok")
        }
        assertTrue(error.message!!.contains("cross-origin"))

        val protocolRelative = assertFailsWith<IllegalArgumentException> {
            JellyfinRequests.absolutizeTranscodingUrl(base, "//evil.test/videos/m1/master.m3u8", token = "tok")
        }
        assertTrue(protocolRelative.message!!.contains("cross-origin"))
    }

    @Test
    fun transcodingUrlKeepsTheServerBasePath() {
        // root-relative target joins the base path
        assertEquals(
            "https://jellyfin.test/stable/videos/m1/master.m3u8?api_key=tok",
            JellyfinRequests.absolutizeTranscodingUrl(basePath, "/videos/m1/master.m3u8", token = "tok").toString(),
        )
        // server already prefixed the base path -> no duplication
        assertEquals(
            "https://jellyfin.test/stable/videos/m1/master.m3u8",
            JellyfinRequests.absolutizeTranscodingUrl(basePath, "/stable/videos/m1/master.m3u8").toString(),
        )
        // plain requests and stream URLs keep the base path too
        assertEquals("https://jellyfin.test/stable/System/Info/Public", urlOf(JellyfinRequests.publicSystemInfo(basePath)).toString())
        assertEquals(
            "https://jellyfin.test/stable/Videos/m1/stream?static=true",
            JellyfinRequests.directStreamUrl(basePath, itemId = "m1").toString(),
        )
        assertEquals(
            "https://jellyfin.test/stable/UserViews?userId=u1",
            urlOf(JellyfinRequests.views(basePath, "u1")).toString(),
        )
    }

    @Test
    fun deliveryUrlsFollowTheSameRules() {
        val subtitleUrl = JellyfinRequests.absolutizeDeliveryUrl(
            base,
            "/Videos/m0102030405060708/Subtitles/3/0/Stream.srt",
            token = "tok",
        )
        assertEquals("https://jellyfin.test/Videos/m0102030405060708/Subtitles/3/0/Stream.srt?api_key=tok", subtitleUrl.toString())

        assertFailsWith<IllegalArgumentException> {
            JellyfinRequests.absolutizeDeliveryUrl(base, "https://evil.test/Subtitles/3/0/Stream.srt", token = "tok")
        }
    }

    @Test
    fun imageUrlsCarrySizeTagAndOptionalToken() {
        assertEquals(
            "https://jellyfin.test/Items/m1/Images/Primary?maxWidth=300&tag=abc",
            JellyfinRequests.imageUrl(base, "m1", maxWidth = 300, tag = "abc").toString(),
        )
        assertEquals(
            "https://jellyfin.test/Items/m1/Images/Backdrop?api_key=tok",
            JellyfinRequests.imageUrl(base, "m1", imageType = ImageType.Backdrop, token = "tok").toString(),
        )
    }

    @Test
    fun wireDecodingCoversPlaybackInfoFixtureRoundTrip() {
        val json = cn.mafei.cmp.core.serialization.MafeiJson.Default
        val decoded = json.decodeFromString(PlaybackInfoResponseDto.serializer(), cn.mafei.cmp.providers.jellyfin.testkit.JellyfinFixtures.PLAYBACK_INFO)
        assertEquals(2, decoded.mediaSources.size)
        val subtitle = decoded.mediaSources[0].mediaStreams.last()
        assertEquals(SubtitleDeliveryMethod.External, subtitle.deliveryMethod)
        assertEquals("/Videos/m0102030405060708/Subtitles/3/0/Stream.srt", subtitle.deliveryUrl)
        assertEquals("live-0001", decoded.mediaSources[0].liveStreamId)
    }
}
