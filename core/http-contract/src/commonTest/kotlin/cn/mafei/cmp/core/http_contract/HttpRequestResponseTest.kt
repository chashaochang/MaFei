package cn.mafei.cmp.core.http_contract

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpRequestResponseTest {

    private val url = HttpUrl.parse("https://example.org/api")

    @Test
    fun requestDefaults() {
        val request = HttpRequest.builder(HttpMethod.Get, url).build()

        assertEquals(HttpMethod.Get, request.method)
        assertEquals(url, request.url)
        assertEquals(0, request.headers.size)
        assertIs<HttpBody.Empty>(request.body)
        assertNull(request.timeoutMillis)
        assertEquals(RedirectPolicy.Follow, request.redirectPolicy)
        assertEquals(BodyReadPolicy.BufferedAtMost.DEFAULT_MAX_BYTES, (request.bodyReadPolicy as BodyReadPolicy.BufferedAtMost).maxBytes)
    }

    @Test
    fun bodyContentTypeIsMirroredIntoHeaders() {
        val request = HttpRequest.builder(HttpMethod.Post, url)
            .body(HttpBody.Text("hello", contentType = "text/plain; charset=utf-8"))
            .build()

        assertEquals("text/plain; charset=utf-8", request.headers.first("content-type"))
    }

    @Test
    fun explicitContentTypeHeaderWins() {
        val request = HttpRequest.builder(HttpMethod.Post, url)
            .header("Content-Type", "application/json")
            .body(HttpBody.Text("{}", contentType = "text/plain"))
            .build()

        assertEquals("application/json", request.headers.first("content-type"))
    }

    @Test
    fun newBuilderPreservesEverything() {
        val original = HttpRequest.builder(HttpMethod.Put, url)
            .header("Accept", "application/json")
            .body(HttpBody.Bytes(byteArrayOf(1, 2, 3)))
            .timeoutMillis(5_000)
            .redirectPolicy(RedirectPolicy.DoNotFollow)
            .readPolicy(BodyReadPolicy.Discarded)
            .attribute("log-redact", "true")
            .build()

        val copy = original.newBuilder()
            .setHeader("Accept", "text/html")
            .build()

        assertEquals(HttpMethod.Put, copy.method)
        assertEquals(url, copy.url)
        assertEquals("text/html", copy.headers.first("accept"))
        assertEquals(5_000L, copy.timeoutMillis)
        assertEquals(RedirectPolicy.DoNotFollow, copy.redirectPolicy)
        assertEquals(BodyReadPolicy.Discarded, copy.bodyReadPolicy)
        assertEquals("true", copy.attributes["log-redact"])
    }

    @Test
    fun responseHelpers() {
        val response = HttpResponse(
            statusCode = 401,
            headers = HttpHeaders.of("Content-Type" to "application/json; charset=utf-8"),
            bodyBytes = "{\"error\":\"unauthorized\"}".encodeToByteArray(),
            requestUrl = url,
            effectiveUrl = url,
        )

        assertFalse(response.isSuccessful)
        assertTrue(response.isClientError)
        assertEquals("application/json; charset=utf-8", response.contentType())
        assertEquals("utf-8", response.charset())
        assertEquals("{\"error\":\"unauthorized\"}", response.bodyText())
    }

    @Test
    fun ensureSuccessThrowsWithResponseAttached() {
        val response = HttpResponse(
            statusCode = 503,
            headers = HttpHeaders.EMPTY,
            bodyBytes = ByteArray(0),
            requestUrl = url,
            effectiveUrl = url,
        )

        val failure = assertFailsWith<HttpStatusCodeException> { response.ensureSuccess() }
        assertEquals(503, failure.response.statusCode)
        assertIs<HttpException>(failure)
    }

    @Test
    fun ensureSuccessPassesThroughOnSuccess() {
        val response = HttpResponse(200, HttpHeaders.EMPTY, ByteArray(0), url, url)
        assertTrue(response.ensureSuccess().isSuccessful)
    }

    @Test
    fun bytesBodyIsCopiedOnConstruction() {
        val source = byteArrayOf(1, 2, 3)
        val body = HttpBody.Bytes(source)
        source[0] = 99
        assertContentEquals(byteArrayOf(1, 2, 3), body.data)

        body.data[1] = 42 // consumers must not mutate; copy protects equality semantics
        assertEquals(HttpBody.Bytes(byteArrayOf(1, 42, 3)), body)
    }

    @Test
    fun errorHierarchyLayers() {
        // Transport-layer failures never carry a response.
        val transport = HttpTransportException(
            url = url,
            kind = TransportFailureKind.Connect,
            message = "connection refused",
        )
        assertTrue(transport is HttpException)

        // Timeout is its own branch (request may or may not have been sent).
        val timeout = HttpTimeoutException(url = url, timeoutMillis = 1_000)
        assertTrue(timeout is HttpException)

        // Decode failures carry content type info.
        val decode = HttpContentDecodeException(url = url, contentType = "text/html", message = "expected JSON")
        assertTrue(decode is HttpException)

        // Body over the read limit.
        val tooBig = HttpBodyLimitExceededException(url = url, maxBytes = 10)
        assertTrue(tooBig is HttpException)
    }
}
