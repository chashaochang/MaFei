@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp.platform.http_ohos

import cn.mafei.cmp.platform.http_ohos.curl.internal.*
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
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

    private fun request() = CurlRequestData(
        protocol = "http", url = "http://127.0.0.1:1/", method = "GET",
        headers = checkNotNull(curl_slist_append(null, "X-Mafei-Test: synthetic")),
        proxy = null, content = ByteReadChannel.Empty, contentLength = 0,
        connectTimeout = null, executionContext = Job(), forceProxyTunneling = false,
        sslVerify = true, caInfo = null, caPath = null,
    )
}
