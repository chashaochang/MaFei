package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.data.adapter_contract.*
import cn.mafei.cmp.providers.jellyfin.core.*
import cn.mafei.cmp.providers.jellyfin.facade.DefaultJellyfinFacade
import cn.mafei.cmp.providers.jellyfin.photos.photoItemQuery
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinRequests
import kotlin.test.*

class JellyfinPhotoLiveTest {
    @Test fun liveStreamStopAndEncodingCleanupUseLegacyBindingsExactlyOnce() = runSuspendTest {
        val (http, adapter) = fixture()
        http.enqueueJson(200, """{"MediaSources":[{"Id":"source","LiveStreamId":"live-id","SupportsDirectPlay":true,"Container":"ts"}],"PlaySessionId":"session"}""")
        val ready = assertIs<PlaybackPreparation.Ready>(adapter.preparePlayback(MediaId("channel"), PlaybackOptions(live = true)))
        http.enqueueJson(204, "")
        adapter.reportPlayback(ready, PlaybackEvent(PlaybackEventKind.Stop, 1200))
        assertEquals("live-id", cn.mafei.cmp.core.serialization.MafeiJson.Default
            .parseToJsonElement(http.requests.last().body!!.let { (it as HttpBody.Text).text })
            .let { (it as kotlinx.serialization.json.JsonObject)["LiveStreamId"] }
            .let { (it as kotlinx.serialization.json.JsonPrimitive).content })
        http.enqueueJson(204, "")
        adapter.releasePlayback(ready)
        val close = http.requests.last()
        assertEquals(HttpMethod.Delete, close.method)
        assertEquals("/jellyfin/Videos/ActiveEncodings", close.url.encodedPath)
        assertEquals("device", close.url.queryParameters.first("deviceId"))
        assertEquals("session", close.url.queryParameters.first("playSessionId"))
        val count = http.requests.size; adapter.releasePlayback(ready); assertEquals(count, http.requests.size)
    }
    private val base = HttpUrl.parse("https://test/jellyfin")
    private fun fixture(): Pair<FakeJellyfinTransport, JellyfinMediaProviderAdapter> {
        val transport = FakeJellyfinTransport()
        val facade = DefaultJellyfinFacade(JellyfinServerConfig(base, "MaFei", "Test", "device", "2.0.0"),
            transport, InMemoryJellyfinSessionState(JellyfinSession("user", "token")))
        return transport to JellyfinMediaProviderAdapter(facade, ServerId("s"), AccountId("a"), "user")
    }

    @Test fun photosUseExactOldFlagsAndMultikeyOrder() {
        val query = JellyfinRequests.items(base, photoItemQuery("user", "photos", false, 60, 60)).url.queryParameters
        assertEquals("Photo", query.first("includeItemTypes"))
        assertEquals("PremiereDate,DateCreated,SortName", query.first("sortBy"))
        assertEquals("Descending,Descending,Ascending", query.first("sortOrder"))
        assertEquals("true", query.first("recursive")); assertEquals("Primary", query.first("enableImageTypes"))
        assertEquals("true", query.first("enableUserData")); assertEquals("true", query.first("enableTotalRecordCount"))
        val albums = JellyfinRequests.items(base, photoItemQuery("user", "photos", true, 0, 60)).url.queryParameters
        assertEquals("PhotoAlbum,Folder", albums.first("includeItemTypes"))
        assertEquals("false", albums.first("recursive")); assertEquals("SortName", albums.first("sortBy"))
    }

    @Test fun thumbnailFullImageExifAndAlbumsKeepOldData() = runSuspendTest {
        val (http, adapter) = fixture()
        http.enqueueJson(200, """{"Items":[{"Id":"p","Name":"Photo","Type":"Photo","ParentId":"album","Path":"/photos/original.JPG","PremiereDate":"0001-01-01T00:00:00Z","DateCreated":"2026-10-08T12:00:00Z","Width":4000,"Height":3000,"CanDownload":true,"CameraMake":"Maker","ExposureTime":0.005,"UserData":{"IsFavorite":true}}],"TotalRecordCount":1}""")
        val item = adapter.photos(PhotoCatalogQuery(MediaId("library")), PageRequest()).items.single()
        assertEquals("original.JPG", item.photo!!.fileName); assertEquals("jpg", item.photo!!.format)
        assertEquals("2026-10-08", item.photo!!.dateKey); assertEquals(4000, item.photo!!.width)
        assertEquals("2026-10-08T12:00:00Z", item.photo!!.dateTaken)
        assertEquals(cn.mafei.cmp.core.serialization.parseIsoInstantToEpochMs("2026-10-08T12:00:00Z"), item.photo!!.dateTakenEpochMs)
        assertTrue(item.userData.isFavorite)
        assertEquals("720", HttpUrl.parse(item.primaryImage!!.url).queryParameters.first("maxWidth"))
        assertEquals("4096", HttpUrl.parse(item.photo!!.fullImage.url).queryParameters.first("maxWidth"))
        http.enqueueJson(200, """{"Items":[{"Id":"a","Name":"Album","Type":"PhotoAlbum","ChildCount":2,"RecursiveItemCount":9}],"TotalRecordCount":1}""")
        assertEquals(9, adapter.photos(PhotoCatalogQuery(MediaId("library"), albums = true), PageRequest()).items.single().childCount)
    }

    @Test fun originalIsAuthorizedByFreshProviderDataAndNeverLogged() = runSuspendTest {
        val (http, adapter) = fixture()
        http.enqueueJson(200, """{"Id":"p","Type":"Photo","Path":"/photos/test.png","CanDownload":false}""")
        assertFailsWith<ClientConfigError> { adapter.photoOriginal(MediaId("p")) }
        http.enqueueJson(200, """{"Id":"p","Type":"Photo","Path":"/photos/test.png","CanDownload":true}""")
        val download = adapter.photoOriginal(MediaId("p"))
        assertEquals("/jellyfin/Items/p/Download", HttpUrl.parse(download.url).encodedPath)
        assertEquals("token", HttpUrl.parse(download.url).queryParameters.first("api_key"))
        assertEquals("test.png", download.fileName); assertFalse(download.toString().contains("token"))
    }

    @Test fun photoGroupingKeepsOriginalOffsetDayRatherThanUtcDay() = runSuspendTest {
        val (http, adapter) = fixture()
        http.enqueueJson(200, """{"Items":[{"Id":"p","Type":"Photo","PremiereDate":"2026-10-09T00:30:00+08:00"}],"TotalRecordCount":1}""")
        assertEquals("2026-10-09", adapter.photos(PhotoCatalogQuery(MediaId("library")), PageRequest()).items.single().photo!!.dateKey)
    }

    @Test fun liveChannelRequestAndProgramMappingDoNotInventDuration() = runSuspendTest {
        val (http, adapter) = fixture()
        http.enqueueJson(200, """{"Items":[{"Id":"c","Name":"Channel","Type":"TvChannel","ChannelNumber":"12","CurrentProgram":{"Name":"Program","Overview":"Summary","StartDate":"2026-10-09T00:00:00Z","EndDate":"2026-10-09T01:00:00Z"}}],"TotalRecordCount":1}""")
        val channel = adapter.liveChannels(PageRequest(pageSize = 100)).items.single()
        assertEquals(MediaType.LiveChannel, channel.mediaType); assertEquals("12", channel.channelNumber)
        assertEquals("Program", channel.currentProgram!!.title); assertNull(channel.runtimeMs)
        assertEquals("/jellyfin/LiveTv/Channels", http.requests.single().url.encodedPath)
        assertEquals("100", http.requests.single().url.queryParameters.first("limit"))
        assertFailsWith<ClientConfigError> { adapter.liveChannels(PageRequest(filter = MediaFilter(favoritesOnly = true))) }
    }
}
