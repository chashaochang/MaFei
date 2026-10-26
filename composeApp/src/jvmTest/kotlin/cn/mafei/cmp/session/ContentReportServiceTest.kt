package cn.mafei.cmp.session

import cn.mafei.cmp.core.http_contract.HttpBody
import cn.mafei.cmp.core.http_contract.HttpHeaders
import cn.mafei.cmp.core.http_contract.HttpMethod
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.http_contract.RedirectPolicy
import cn.mafei.cmp.core.http_contract.BodyReadPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContentReportServiceTest {
    private class RecordingTransport(
        private val status: Int = 200,
        private val responseBody: String = """{"ok":true,"message":"举报已提交"}""",
        private val failure: Exception? = null,
    ) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            failure?.let { throw it }
            return HttpResponse(
                statusCode = status,
                headers = HttpHeaders.EMPTY,
                bodyBytes = responseBody.encodeToByteArray(),
                requestUrl = request.url,
                effectiveUrl = request.url,
            )
        }
    }

    private val request = ContentReportRequest(
        title = "A film",
        itemId = "item-42",
        serverUrl = "https://media.example",
        description = "Incorrect episode",
        playSessionId = "play-7",
        reporterId = "user-5",
        reporterName = "Viewer",
    )

    @Test
    fun sendsTheHarmonyReportProtocolWithoutFollowingRedirects() = runBlocking {
        val transport = RecordingTransport()
        val result = ContentReportService(transport, "test-report-token").submit(request)

        assertTrue(result.submitted)
        val sent = transport.requests.single()
        assertEquals(HttpMethod.Post, sent.method)
        assertEquals("https://mafei.hmos.site/api/report", sent.url.toEncodedString())
        assertEquals("test-report-token", sent.headers.first("x-report-token"))
        assertEquals(RedirectPolicy.DoNotFollow, sent.redirectPolicy)
        assertEquals(BodyReadPolicy.BufferedAtMost(64 * 1024L), sent.bodyReadPolicy)
        val body = assertIs<HttpBody.Text>(sent.body).text
        val fields = Json.parseToJsonElement(body).jsonObject
        assertEquals("A film", fields["title"]?.jsonPrimitive?.content)
        assertEquals("item-42", fields["itemId"]?.jsonPrimitive?.content)
        assertEquals("https://media.example", fields["serverUrl"]?.jsonPrimitive?.content)
        assertEquals("player", fields["source"]?.jsonPrimitive?.content)
        assertEquals("Incorrect episode", fields["description"]?.jsonPrimitive?.content)
        assertEquals("mafei-ohos", fields["client"]?.jsonPrimitive?.content)
        assertEquals("play-7", fields["playSessionId"]?.jsonPrimitive?.content)
        assertEquals("user-5", fields["reporterId"]?.jsonPrimitive?.content)
        assertEquals("Viewer", fields["reporterName"]?.jsonPrimitive?.content)
    }

    @Test
    fun duplicateAndRateLimitedResponsesKeepTheirDistinctResults() = runBlocking {
        val duplicate = ContentReportService(
            RecordingTransport(status = 409, responseBody = """{"code":"DUPLICATE_REPORT"}"""),
            "test-report-token",
        ).submit(request)
        val limited = ContentReportService(
            RecordingTransport(status = 429, responseBody = """{"code":"RATE_LIMITED"}"""),
            "test-report-token",
        ).submit(request)

        assertFalse(duplicate.submitted)
        assertTrue(duplicate.alreadyReported)
        assertFalse(limited.submitted)
        assertFalse(limited.alreadyReported)
        assertTrue(limited.message.contains("频繁"))
    }

    @Test
    fun applicationRejectionAndTransportFailureAreReportedAsFailures() = runBlocking {
        val rejected = ContentReportService(
            RecordingTransport(responseBody = """{"ok":false,"message":"拒绝提交"}"""),
            "test-report-token",
        ).submit(request)
        val failed = ContentReportService(
            RecordingTransport(failure = IllegalStateException("offline")),
            "test-report-token",
        ).submit(request)

        assertFalse(rejected.submitted)
        assertEquals("拒绝提交", rejected.message)
        assertFalse(failed.submitted)
        assertTrue(failed.message.contains("网络"))
    }

    @Test
    fun cancellationIsNotConvertedIntoAReportFailure() {
        val service = ContentReportService(
            RecordingTransport(failure = CancellationException("cancelled")),
            "test-report-token",
        )

        assertFailsWith<CancellationException> {
            runBlocking { service.submit(request) }
        }
    }
}
