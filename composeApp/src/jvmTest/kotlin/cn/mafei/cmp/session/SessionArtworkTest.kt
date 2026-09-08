package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.core.secure_credential_api.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlin.test.*

class SessionArtworkTest {
    private class Transport : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        var contentType = "image/jpeg"
        var status = 200
        var onExecute: () -> Unit = {}
        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request; onExecute()
            return HttpResponse(status, HttpHeaders.of("Content-Type" to contentType), byteArrayOf(1, 2, 3), request.url, request.url)
        }
    }
    private fun connection(provider: ProviderKind = ProviderKind.Jellyfin): SavedConnection {
        val server = Server(ServerId("a"), provider, "Test", "https://media.test/base")
        return SavedConnection(server, Account(AccountId("a"), server.id, provider, "Test"), "user", "a".repeat(32),
            if (provider == ProviderKind.Feiniu) listOf(SavedFeiniuRoute(server.baseUrl, "Relay")) else emptyList())
    }
    private fun bind(c: SavedConnection, t: Transport) = DefaultProviderSessionFactory(t).restore(c, buildMap {
        put(c.tokenAlias, SecureSecret.AuthToken(if (c.server.provider == ProviderKind.Jellyfin) "MediaBrowser" else "Bearer", "synthetic-token"))
        if (c.server.provider == ProviderKind.Feiniu) put(c.cookieAlias, SecureSecret.CookieText("session=fixture"))
    })

    @Test fun sameOriginArtworkUsesHeadersBoundedBodyAndSameOriginRedirects() = runBlocking {
        val t = Transport(); val c = connection(); val b = bind(c, t)
        assertNotNull(SessionArtwork(t).load(c, b, MediaImage(url = "https://media.test/base/Items/a/Images/Primary")))
        val r = t.requests.single()
        assertEquals("synthetic-token", r.headers.first("X-Emby-Token"))
        assertFalse(r.url.toEncodedString().contains("synthetic-token"))
        assertEquals("480", r.url.queryParameters.first("maxWidth"))
        assertEquals(RedirectPolicy.FollowSameOrigin, r.redirectPolicy)
        assertEquals(BodyReadPolicy.BufferedAtMost(4L * 1024 * 1024), r.bodyReadPolicy)
    }
    @Test fun foreignOriginAndDowngradeNeverReceiveCredentials() = runBlocking {
        val t = Transport(); val c = connection(); val b = bind(c, t)
        for (url in listOf("https://other.test/a", "http://media.test/a", "file:///etc/passwd", "https://media.test:444/a")) {
            assertNull(SessionArtwork(t).load(c, b, MediaImage(url = url)))
        }
        assertTrue(t.requests.isEmpty())
    }
    @Test fun staleSessionDoesNotDispatchOrAcceptLateArtwork() = runBlocking {
        val t = Transport(); val c = connection(); val b = bind(c, t)
        t.onExecute = b::clear
        assertFailsWith<CancellationException> { SessionArtwork(t).load(c, b, MediaImage(url = "https://media.test/a")) }
        assertNull(SessionArtwork(t).load(c, b, MediaImage(url = "https://media.test/a")))
        assertEquals(1, t.requests.size)
    }
    @Test fun htmlAndNonSuccessArePlaceholdersNotImages() = runBlocking {
        val t = Transport(); val c = connection(); val b = bind(c, t)
        t.contentType = "text/html"
        assertNull(SessionArtwork(t).load(c, b, MediaImage(url = "https://media.test/a")))
        t.contentType = "image/jpeg"; t.status = 404
        assertNull(SessionArtwork(t).load(c, b, MediaImage(url = "https://media.test/a")))
    }
    @Test fun feiniuUsesAuthoritativeTokenCookieAndRelayContext() = runBlocking {
        val t = Transport(); val c = connection(ProviderKind.Feiniu); val b = bind(c, t)
        assertNotNull(SessionArtwork(t).load(c, b, MediaImage(url = "https://media.test/base/v/api/v1/sys/img/a")))
        val r = t.requests.single()
        assertEquals("synthetic-token", r.headers.first("Authorization"))
        assertTrue(r.headers.first("Cookie")!!.contains("mode=relay"))
        assertEquals(1, r.headers.values("Cookie").size)
    }
}
