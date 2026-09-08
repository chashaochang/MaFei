package cn.mafei.cmp.platform.http_ktor

import cn.mafei.cmp.core.http_contract.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod as KtorMethod
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.*

/** Buffered API transport shared by Android and Mac factories. Owns its client and engine.
 * No authentication, Cookie, retry, content decoding, or logging plugins are installed.
 * Uses CIO's default engine configuration; no custom trust or insecure fallback exists.
 * Actual OS trust-store/proxy behavior requires platform verification, not a compilation claim.
 * Native compilation does not certify CIO networking/TLS on iOS or OHOS.
 */
class CioHttpTransport(private val defaultTimeoutMillis: Long = 30_000) : HttpTransport {
    init { require(defaultTimeoutMillis > 0) { "Default timeout must be positive" } }

    private val lifetime = SupervisorJob()
    private val client = HttpClient(CIO) {
        expectSuccess = false
        followRedirects = false
        // The outer timeout is the only deadline. The plugin disables endpoint connection and
        // socket clocks per request without overflowing CIO's endpoint idle-time calculation.
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }
        engine {
            requestTimeout = 0
            endpoint { connectAttempts = 1 }
        }
    }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        if (!lifetime.isActive) throw HttpTransportException(request.url, TransportFailureKind.Unknown,
            "HTTP transport is closed")
        val policy = request.bodyReadPolicy
        require(policy !is BodyReadPolicy.BufferedAtMost || policy.maxBytes >= 0) {
            "Buffered body limit must be non-negative"
        }
        val timeout = request.timeoutMillis ?: defaultTimeoutMillis
        return coroutineScope {
            // Caller owns this child; lifetime only provides an additional close/cancel signal.
            // Completion-handler registration is safe even if close wins the registration race.
            val operation = async {
                try {
                    withTimeoutOrNull(timeout) { executeChain(request) }
                        ?: throw HttpTimeoutException(request.url, timeout)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: HttpException) {
                    throw e
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    // Ktor exception chains may contain full URLs or header values.
                    // Preserve the typed failure kind, never export raw engine text
                    // into a public stack trace that bypasses the HTTP log redactor.
                    throw HttpTransportException(request.url, classifyEngineFailure(e),
                        "HTTP engine failed")
                }
            }
            val registration = lifetime.invokeOnCompletion {
                operation.cancel(CancellationException("HTTP transport closed"))
            }
            try { operation.await() } finally { registration.dispose() }
        }
    }

    private suspend fun executeChain(original: HttpRequest): HttpResponse {
        var current = original
        var hops = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val hop = current
            val result = client.prepareRequest(hop.url.toEncodedString()) {
                method = KtorMethod(hop.method.name.uppercase())
                hop.headers.forEach { (name, value) ->
                    // CIO's mergeHeaders chooses the Cookie separator by canonical spelling;
                    // normalize this case-insensitive name or lowercase cookies become comma-joined.
                    headers.append(if (name.equals(HttpHeaders.COOKIE, true)) HttpHeaders.COOKIE else name, value)
                }
                when (val body = hop.body) {
                    HttpBody.Empty -> Unit
                    is HttpBody.Bytes -> setBody(body.data.copyOf())
                    is HttpBody.Text -> setBody(body.bytes)
                }
            }.execute { response ->
                val headers = HttpHeaders.builder()
                response.headers.entries().forEach { (name, values) -> values.forEach { headers.add(name, it) } }
                val head = HttpResponse(response.status.value, headers.build(), ByteArray(0), original.url, hop.url)
                if (head.statusCode == 101) {
                    // CIO exposes an upgraded session object instead of a ByteReadChannel. This
                    // interface is buffered-only: return the status and close the unsupported stream.
                    response.coroutineContext.cancel()
                    return@execute HopResult(head, null)
                }
                val channel = response.bodyAsChannel()
                try {
                    val next = HttpRedirects.nextRequest(hop, head, hops)
                    if (next != null) {
                        HopResult(head, next)
                    } else {
                        val hasPayload = hop.method != HttpMethod.Head && head.statusCode !in 100..199 &&
                            head.statusCode != 204 && head.statusCode != 304
                        val expectedLength = if (hasPayload && !head.headers.contains("Transfer-Encoding"))
                            head.headers.first(HttpHeaders.CONTENT_LENGTH)?.toLongOrNull() else null
                        val bytes = readBody(channel, hop, expectedLength)
                        HopResult(HttpResponse(head.statusCode, head.headers, bytes, original.url, hop.url), null)
                    }
                } finally {
                    // Merely finalizing an unconsumed CIO statement can wait for a stalled body.
                    // Explicit cancellation also applies to redirects and the Discarded policy.
                    channel.cancel(null)
                    // Cancelling the channel alone can leave CIO's parser awaiting socket input;
                    // cancelling its call context closes the socket and joins that parser reliably.
                    response.coroutineContext.cancel()
                }
            }
            current = result.next ?: return result.response
            hops++
        }
    }

    private suspend fun readBody(channel: ByteReadChannel, request: HttpRequest, expectedLength: Long?): ByteArray {
        val policy = request.bodyReadPolicy
        if (policy == BodyReadPolicy.Discarded) return ByteArray(0)
        val limit = (policy as? BodyReadPolicy.BufferedAtMost)?.maxBytes ?: Long.MAX_VALUE
        val chunks = mutableListOf<ByteArray>()
        var size = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            // Read at most the first excess byte, rather than retaining an entire oversized chunk.
            val requested = if (limit - size < 8192) (limit - size + 1).toInt() else 8192
            val buffer = ByteArray(requested)
            val count = channel.readAvailable(buffer, 0, buffer.size)
            if (count < 0) break
            if (count == 0) { yield(); continue }
            size += count
            if (size > limit) throw HttpBodyLimitExceededException(request.url, limit, size)
            // HttpResponse is a single ByteArray on every target. A larger array is unrepresentable,
            // even for the explicitly unbounded policy; fail before integer overflow/allocation.
            if (size > Int.MAX_VALUE) throw HttpBodyLimitExceededException(request.url, Int.MAX_VALUE.toLong(), size)
            chunks += if (count == buffer.size) buffer else buffer.copyOf(count)
        }
        // CPF Ktor readAvailable returns -1 for a failed closed channel too. Never convert a
        // socket reset/truncated HTTP body into a successful partial JSON response.
        channel.closedCause?.let { throw it }
        if (expectedLength != null && expectedLength != size) {
            throw HttpTransportException(request.url, TransportFailureKind.Unknown, "Incomplete HTTP response body")
        }
        val output = ByteArray(size.toInt())
        var offset = 0
        for (chunk in chunks) { chunk.copyInto(output, offset); offset += chunk.size }
        return output
    }

    override fun close() {
        lifetime.cancel(CancellationException("HTTP transport closed"))
        client.close() // Ktor close is itself idempotent and owns the factory-created CIO engine.
    }

    private data class HopResult(val response: HttpResponse, val next: HttpRequest?)
}

/** Traverse wrapped engine causes without guessing that every IOException is a write failure.
 * These runtime exception names are the Java/Ktor platform names, not message substrings (which
 * may contain credentials or localized text). Unknown native errors deliberately remain Unknown.
 */
internal fun classifyEngineFailure(error: Throwable): TransportFailureKind {
    var cause: Throwable? = error
    val visited = mutableListOf<Throwable>()
    var writeFailure = false
    while (cause != null && visited.size < 16 && visited.none { it === cause }) {
        visited += cause
        when (cause::class.simpleName) {
            "UnknownHostException", "UnresolvedAddressException" -> return TransportFailureKind.Dns
            "ConnectException", "FailToConnectException", "NoRouteToHostException" -> return TransportFailureKind.Connect
            "ClosedWriteChannelException" -> writeFailure = true
            "TLSException", "SSLException", "SSLHandshakeException", "SSLPeerUnverifiedException",
            "SSLProtocolException", "CertificateException", "CertificateExpiredException",
            "CertificateNotYetValidException", "CertificateParsingException" -> return TransportFailureKind.Tls
        }
        cause = cause.cause
    }
    return if (writeFailure) TransportFailureKind.SocketWrite else TransportFailureKind.Unknown
}
