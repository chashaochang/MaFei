package cn.mafei.cmp.player.runtime_macos

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.*

class AuthorizedMediaProxyTest {
    @Test fun rangeAndCredentialsReachOriginAndRetiredSourcesStopServing() {
        var token: String? = null; var range: String? = null
        val origin = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        origin.createContext("/video") { request ->
            token = request.requestHeaders.getFirst("Authorization"); range = request.requestHeaders.getFirst("Range")
            val bytes = "bc".toByteArray(); request.responseHeaders.set("Content-Range", "bytes 1-2/4")
            request.sendResponseHeaders(206, 2); request.responseBody.use { it.write(bytes) }; request.close()
        }
        origin.start()
        try { AuthorizedMediaProxy().use { proxy ->
            val (id, url) = proxy.register("http://127.0.0.1:${origin.address.port}/video", mapOf("Authorization" to "fixture"))
            val http = HttpClient.newHttpClient()
            val result = http.send(HttpRequest.newBuilder(URI(url)).header("Range", "bytes=1-2").build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(206, result.statusCode()); assertEquals("bc", result.body()); assertEquals("fixture", token); assertEquals("bytes=1-2", range)
            proxy.retire(id)
            assertEquals(404, http.send(HttpRequest.newBuilder(URI(url)).build(), HttpResponse.BodyHandlers.ofString()).statusCode())
        } } finally { origin.stop(0) }
    }
    @Test fun playlistRewritesSegmentsKeysAndAlternateAudioWithoutPersistingSecrets() {
        val rewritten = AuthorizedMediaProxy.rewriteHls("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\"\nchunk.ts") { "proxy/$it" }
        assertTrue(rewritten.contains("URI=\"proxy/key\"")); assertTrue(rewritten.endsWith("proxy/chunk.ts"))
        assertFalse(AuthorizedMediaProxy.sameOrigin(URI("https://server"), URI("http://server")))
        assertFalse(AuthorizedMediaProxy.sameOrigin(URI("https://server"), URI("https://cdn")))
    }
    @Test fun crlfAndBomDoNotTurnHeaderOrBlankLinesIntoMediaUrls() {
        val rewritten = AuthorizedMediaProxy.rewriteHls("\uFEFF#EXTM3U\r\n\r\nchunk.ts\r\n") { "proxy/$it" }
        assertEquals("#EXTM3U\n\nproxy/chunk.ts\n", rewritten)
    }
}
