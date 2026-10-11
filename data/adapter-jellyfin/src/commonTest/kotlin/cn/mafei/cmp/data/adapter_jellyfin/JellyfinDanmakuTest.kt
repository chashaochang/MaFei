package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.providers.jellyfin.core.*
import cn.mafei.cmp.providers.jellyfin.facade.DefaultJellyfinFacade
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import kotlin.test.*

class JellyfinDanmakuTest {
    @Test fun missingPluginFallsBackUsingLegacySeriesAndEpisodeFields() = runSuspendTest {
        val http = FakeJellyfinTransport()
        val facade = DefaultJellyfinFacade(JellyfinServerConfig(HttpUrl.parse("https://server/jellyfin"), "MaFei", "test", "id", "2"),
            http, InMemoryJellyfinSessionState(JellyfinSession("user", "fixture-token")))
        val fallback = cn.mafei.cmp.providers.jellyfin.playback.DandanPlayFallback(http,
            cn.mafei.cmp.providers.jellyfin.playback.DandanPlayConfiguration(HttpUrl.parse("https://comments.test/api/v2"), "fixture-app", "fixture-secret"))
        http.enqueueJson(404, "{}")
        http.enqueueJson(200, """{"Id":"episode","SeriesName":"间谍过家家","IndexNumber":9}""")
        http.enqueueJson(200, """{"success":true,"animes":[{"episodes":[{"episodeId":42}]}]}""")
        http.enqueueJson(200, """{"comments":[{"p":"1.5,1,16777215,user","m":"A & <B>"}]}""")
        val xml = JellyfinMediaProviderAdapter(facade, ServerId("s"), AccountId("a"), "user", fallback).danmakuXml(MediaId("episode"))
        assertEquals("<i><d p=\"1.5,1,25,16777215\">A &amp; &lt;B&gt;</d></i>", xml)
        assertEquals("/api/v2/search/episodes", http.requests[2].url.encodedPath)
        assertTrue(http.requests[2].url.toString().contains("episode=9"))
        assertEquals("/api/v2/comment/42", http.requests[3].url.encodedPath)
        assertFalse(http.requests[2].headers.toString().contains("fixture-token"))
        assertEquals("fixture-secret", http.requests[2].headers.first("X-AppSecret"))
        assertEquals(RedirectPolicy.FollowSameOrigin, http.requests[2].redirectPolicy)
    }
    @Test fun pluginIsAccountAuthenticatedAndExternalDocumentIsAnonymous() = runSuspendTest {
        val http = FakeJellyfinTransport()
        val facade = DefaultJellyfinFacade(JellyfinServerConfig(HttpUrl.parse("https://server/jellyfin"), "MaFei", "test", "id", "2"),
            http, InMemoryJellyfinSessionState(JellyfinSession("user", "fixture-token")))
        http.enqueueJson(200, """{"url":"https://comments.test/media.xml"}""")
        http.enqueueJson(200, "<i><d p=\"1,1,25,16777215,0,0,user,1\">test</d></i>")
        val xml = JellyfinMediaProviderAdapter(facade, ServerId("s"), AccountId("a"), "user").danmakuXml(MediaId("episode"))
        assertTrue(xml!!.startsWith("<i>"))
        assertEquals("/jellyfin/api/danmu/episode", http.requests[0].url.encodedPath)
        assertTrue(http.requests[0].headers.toString().contains("fixture-token"))
        assertFalse(http.requests[1].headers.toString().contains("fixture-token"))
        assertEquals(2 * 1024 * 1024L, (http.requests[1].bodyReadPolicy as BodyReadPolicy.BufferedAtMost).maxBytes)
    }
    @Test fun missingPluginUrlDoesNotRequestAnInventedEndpoint() = runSuspendTest {
        val http = FakeJellyfinTransport()
        val facade = DefaultJellyfinFacade(JellyfinServerConfig(HttpUrl.parse("https://server"), "MaFei", "test", "id", "2"), http)
        http.enqueueJson(200, "{}")
        assertNull(facade.playback.danmakuXml("episode")); assertEquals(1, http.requests.size)
    }
}
