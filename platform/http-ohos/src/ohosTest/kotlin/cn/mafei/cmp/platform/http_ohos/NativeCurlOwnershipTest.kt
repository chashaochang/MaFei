@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp.platform.http_ohos

import cn.mafei.cmp.platform.http_ohos.curl.internal.*
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import libcurl.CURLE_COULDNT_CONNECT
import libcurl.CURLE_COULDNT_RESOLVE_HOST
import libcurl.CURLE_COULDNT_RESOLVE_PROXY
import libcurl.CURLE_SEND_ERROR
import libcurl.CURLE_SSL_CONNECT_ERROR
import libcurl.curl_slist_append
import libcurl.curl_slist_free_all
import kotlin.test.*

/** Uses real libcurl handles, but does not perform any network request. Requires OHOS to run. */
class NativeCurlOwnershipTest {
    @Test fun repeatedCancelReleasesOnlyOnceAndDisposesListener() {
        val api = CurlMultiApiHandler()
        try {
            val response = CompletableDeferred<CurlSuccess>()
            val request = request()
            val handle = api.scheduleRequest(request, response)
            var disposed = false
            api.keepCancellationUntilBodyCompletes(handle, DisposableHandle { disposed = true })
            api.cancelRequest(handle, CancellationException("synthetic cancellation"), request)
            api.cancelRequest(handle, CancellationException("late cancellation"), request)
            assertFalse(api.hasHandlers())
            assertTrue(response.isCancelled)
            assertTrue(disposed)
        } finally { api.close(); api.close() }
    }

    @Test fun delayedCancelCannotTargetDifferentRequestAtSameAddress() {
        val api = CurlMultiApiHandler()
        val obsolete = request()
        try {
            val response = CompletableDeferred<CurlSuccess>()
            val current = request()
            val handle = api.scheduleRequest(current, response)
            api.cancelRequest(handle, CancellationException("old identity"), obsolete)
            assertTrue(api.hasHandlers())
            assertFalse(response.isCompleted)
            api.cancelRequest(handle, CancellationException("current identity"), current)
            assertFalse(api.hasHandlers())
        } finally {
            curl_slist_free_all(obsolete.headers)
            obsolete.executionContext.cancel()
            api.close()
        }
    }

    @Test fun closeCancelsEveryPendingResponse() {
        val api = CurlMultiApiHandler()
        val pending = List(3) { CompletableDeferred<CurlSuccess>() }
        try {
            pending.forEach { api.scheduleRequest(request(), it) }
            api.close()
            assertFalse(api.hasHandlers())
            assertTrue(pending.all { it.isCancelled })
        } finally { api.close() }
    }

    @Test fun dnsFallbackKeepsPostBodyAndCancellationOwnershipUntilCancelled() = runBlocking<Unit> {
        val api = CurlMultiApiHandler()
        val payload = "synthetic-login-body".encodeToByteArray()
        val request = request(method = "POST", body = payload)
        val response = CompletableDeferred<CurlSuccess>()
        try {
            val handle = api.scheduleRequest(request, response)
            var disposalCount = 0
            api.keepCancellationUntilBodyCompletes(handle, DisposableHandle { disposalCount++ })

            assertTrue(api.retryIpv6AfterDnsFailure(handle, CURLE_COULDNT_RESOLVE_HOST, 0))
            assertFalse(api.retryIpv6AfterDnsFailure(handle, CURLE_COULDNT_RESOLVE_HOST, 0))
            assertTrue(api.hasHandlers())
            assertFalse(response.isCompleted)
            assertEquals(0, disposalCount)
            assertContentEquals(payload, request.content.readRemaining().readByteArray())

            api.cancelRequest(handle, CancellationException("cancelled during IPv6 fallback"), request)
            api.cancelRequest(handle, CancellationException("late cancellation"), request)
            assertFalse(api.hasHandlers())
            assertTrue(response.isCancelled)
            assertEquals(1, disposalCount)
        } finally { api.close() }
    }

    @Test fun onlyHostDnsFailureBeforeResponseMayFallback() {
        val api = CurlMultiApiHandler()
        try {
            val response = CompletableDeferred<CurlSuccess>()
            val handle = api.scheduleRequest(request(method = "POST"), response)
            for (failure in listOf(CURLE_COULDNT_RESOLVE_PROXY, CURLE_COULDNT_CONNECT,
                CURLE_SSL_CONNECT_ERROR, CURLE_SEND_ERROR)) {
                assertFalse(api.retryIpv6AfterDnsFailure(handle, failure, 0))
            }
            assertFalse(api.retryIpv6AfterDnsFailure(handle, CURLE_COULDNT_RESOLVE_HOST, 401))
            assertTrue(api.retryIpv6AfterDnsFailure(handle, CURLE_COULDNT_RESOLVE_HOST, 0))
            api.close()
            assertTrue(response.isCancelled)
        } finally { api.close() }
    }

    @Test fun cancelledRequestDoesNotStartDnsFallback() {
        val api = CurlMultiApiHandler()
        val request = request()
        try {
            val response = CompletableDeferred<CurlSuccess>()
            val handle = api.scheduleRequest(request, response)
            request.executionContext.cancel()
            assertFalse(api.retryIpv6AfterDnsFailure(handle, CURLE_COULDNT_RESOLVE_HOST, 0))
            api.close()
            assertTrue(response.isCancelled)
        } finally { api.close() }
    }

    private fun request(method: String = "GET", body: ByteArray = ByteArray(0)) = CurlRequestData(
        protocol = "http", url = "http://127.0.0.1:1/", method = method,
        headers = checkNotNull(curl_slist_append(null, "X-Mafei-Test: synthetic")),
        proxy = null, content = ByteReadChannel(body), contentLength = body.size.toLong(),
        connectTimeout = null, executionContext = Job(), forceProxyTunneling = false,
        sslVerify = true, caInfo = null, caPath = null,
    )
}
