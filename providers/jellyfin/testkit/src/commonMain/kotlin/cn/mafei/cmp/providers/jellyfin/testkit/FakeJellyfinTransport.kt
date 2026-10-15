package cn.mafei.cmp.providers.jellyfin.testkit

import cn.mafei.cmp.core.http_contract.HttpHeaders
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.http_contract.HttpUrl

/**
 * Scripted in-memory transport for protocol tests: enqueue responses in
 * order or install a handler, and inspect every recorded request. Canned
 * responses are sanitized fixture material only.
 */
class FakeJellyfinTransport : HttpTransport {

    class RecordedRequest(val request: HttpRequest, val index: Int)

    private val queue = ArrayDeque<HttpResponse>()
    private val recorded = mutableListOf<HttpRequest>()

    /** Requests in execution order. */
    val requests: List<HttpRequest> get() = recorded

    fun enqueue(response: HttpResponse) {
        queue.addLast(response)
    }

    fun enqueueJson(status: Int, body: String, url: HttpUrl = HttpUrl.parse("https://jellyfin.test/fallback")) {
        enqueue(jsonResponse(status, body, url))
    }

    /** Installs a dynamic responder; takes precedence over the queue. */
    var handler: ((HttpRequest) -> HttpResponse)? = null

    override suspend fun execute(request: HttpRequest): HttpResponse {
        recorded += request
        handler?.let { return it(request) }
        return queue.removeFirstOrNull()
            ?: throw IllegalStateException("FakeJellyfinTransport has no scripted response for ${request.url}")
    }

    companion object {
        fun jsonResponse(
            status: Int,
            body: String,
            url: HttpUrl,
            headers: HttpHeaders = HttpHeaders.of(HttpHeaders.CONTENT_TYPE to HttpHeaders.CONTENT_TYPE_JSON),
        ): HttpResponse = HttpResponse(
            statusCode = status,
            headers = headers,
            bodyBytes = body.encodeToByteArray(),
            requestUrl = url,
            effectiveUrl = url,
        )

        fun noContent(url: HttpUrl): HttpResponse = HttpResponse(
            statusCode = 204,
            headers = HttpHeaders.EMPTY,
            bodyBytes = ByteArray(0),
            requestUrl = url,
            effectiveUrl = url,
        )
    }
}
