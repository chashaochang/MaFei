package cn.mafei.cmp.core.http_contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.fail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * Reference contract every real transport must satisfy; exercised against an
 * in-memory transport that mirrors the documented semantics.
 */
class TransportContractTest {

    @Test
    fun cancellationPropagatesAsCancellationException() = runBlocking {
        val neverEnding = SlowTransport(delayMs = 60_000)
        val outcome = CompletableDeferred<Throwable>()
        val supervisor = Job()

        val worker = launch(supervisor) {
            try {
                neverEnding.execute(sampleRequest())
                outcome.complete(IllegalStateException("expected cancellation"))
            } catch (t: Throwable) {
                outcome.complete(t)
            }
        }

        yield() // let the worker reach its suspension point
        worker.cancel()

        val thrown = withTimeout(10_000) { outcome.await() }
        assertIs<CancellationException>(thrown)

        supervisor.cancel()
    }

    @Test
    fun timeoutSurfacesAsHttpTimeoutException() = runBlocking {
        val transport = SlowTransport(delayMs = 60_000)
        val request = HttpRequest.builder(HttpMethod.Get, sampleUrl())
            .timeoutMillis(50)
            .build()

        val thrown = try {
            transport.execute(request)
            fail("expected timeout")
        } catch (t: Throwable) {
            t
        }

        assertIs<HttpTimeoutException>(thrown)
        assertEquals(50L, thrown.timeoutMillis)
        assertEquals(sampleUrl(), thrown.url)
        assertTrueCauseIsTimeoutCancellation(thrown)
    }

    private fun assertTrueCauseIsTimeoutCancellation(thrown: Throwable) {
        assertEquals(true, thrown.cause is TimeoutCancellationException)
    }

    @Test
    fun slowTransportCompletesWithinTimeout() = runBlocking {
        val transport = SlowTransport(delayMs = 20)
        val request = HttpRequest.builder(HttpMethod.Get, sampleUrl())
            .timeoutMillis(5_000)
            .build()

        val response = transport.execute(request)
        assertEquals(200, response.statusCode)
        assertEquals("ok", response.bodyText())
    }

    @Test
    fun bodyReadLimitIsEnforcedByTransport() = runBlocking {
        val payload = "0123456789abcdef"
        val transport = PayloadTransport(payload.encodeToByteArray())
        val limited = HttpRequest.builder(HttpMethod.Get, sampleUrl())
            .readPolicy(BodyReadPolicy.BufferedAtMost(maxBytes = 8))
            .build()

        val thrown = try {
            transport.execute(limited)
            fail("expected body limit exception")
        } catch (t: Throwable) {
            t
        }
        assertIs<HttpBodyLimitExceededException>(thrown)
        assertEquals(8L, thrown.maxBytes)

        val unlimited = HttpRequest.builder(HttpMethod.Get, sampleUrl()).build()
        assertEquals(payload, transport.execute(unlimited).bodyText())
    }

    @Test
    fun discardedBodiesYieldEmptyBytes() = runBlocking {
        val transport = PayloadTransport("payload".encodeToByteArray())
        val request = HttpRequest.builder(HttpMethod.Get, sampleUrl())
            .readPolicy(BodyReadPolicy.Discarded)
            .build()

        val response = transport.execute(request)
        assertEquals(0, response.bodyBytes.size)
        assertNull(response.headers.first("irrelevant"))
    }

    private fun sampleUrl(): HttpUrl = HttpUrl.parse("https://example.org/x")!!

    private fun sampleRequest(): HttpRequest =
        HttpRequest.builder(HttpMethod.Get, sampleUrl()).build()

    /** Transport that delays before answering: models remote latency. */
    private class SlowTransport(private val delayMs: Long) : HttpTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse {
            delayWithinBudget(request)
            return HttpResponse(
                statusCode = 200,
                headers = HttpHeaders.of("Content-Type" to "text/plain"),
                bodyBytes = "ok".encodeToByteArray(),
                requestUrl = request.url,
                effectiveUrl = request.url,
            )
        }

        private suspend fun delayWithinBudget(request: HttpRequest) {
            val budget = request.timeoutMillis
            try {
                if (budget == null) {
                    delay(delayMs)
                } else {
                    withTimeout(budget) { delay(delayMs) }
                }
            } catch (e: TimeoutCancellationException) {
                throw HttpTimeoutException(url = request.url, timeoutMillis = budget ?: 0, cause = e)
            }
        }
    }

    /** Transport that returns a fixed body, enforcing the read policy like real engines must. */
    private class PayloadTransport(private val payload: ByteArray) : HttpTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse {
            val body = when (val policy = request.bodyReadPolicy) {
                is BodyReadPolicy.BufferedAtMost -> {
                    if (payload.size.toLong() > policy.maxBytes) {
                        throw HttpBodyLimitExceededException(
                            url = request.url,
                            maxBytes = policy.maxBytes,
                            actualBytes = payload.size.toLong(),
                        )
                    }
                    payload
                }
                BodyReadPolicy.Buffered -> payload
                BodyReadPolicy.Discarded -> ByteArray(0)
            }
            return HttpResponse(
                statusCode = 200,
                headers = HttpHeaders.EMPTY,
                bodyBytes = body,
                requestUrl = request.url,
                effectiveUrl = request.url,
            )
        }
    }
}
