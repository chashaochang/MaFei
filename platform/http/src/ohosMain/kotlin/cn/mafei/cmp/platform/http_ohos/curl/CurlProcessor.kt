/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// Modified for MaFei (2026-09-08): relocated, HTTP-only; see third-party/NOTICE.md.
package cn.mafei.cmp.platform.http_ohos.curl

import cn.mafei.cmp.platform.http_ohos.curl.internal.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import libcurl.curl_slist_free_all
import kotlin.coroutines.CoroutineContext

internal class RequestContainer(
    val requestData: CurlRequestData,
    val completionHandler: CompletableDeferred<CurlSuccess>
)

internal class CurlProcessor(coroutineContext: CoroutineContext) {
    @OptIn(InternalAPI::class)
    private val curlDispatcher: CloseableCoroutineDispatcher =
        Dispatchers.createFixedThreadDispatcher("curl-dispatcher", 1)

    private val apiState = MutableStateFlow<CurlMultiApiHandler?>(null)
    private val curlApi: CurlMultiApiHandler get() = checkNotNull(apiState.value)
    private val closed = MutableStateFlow(false)

    private val curlScope = CoroutineScope(coroutineContext + curlDispatcher)
    @OptIn(ExperimentalForeignApi::class)
    private val requestQueue: Channel<RequestContainer> = Channel(Channel.UNLIMITED, onUndeliveredElement = {
        curl_slist_free_all(it.requestData.headers)
        it.requestData.content.cancel(null)
        it.completionHandler.cancel()
    })

    init {
        runBlocking {
            withContext(curlDispatcher) { apiState.value = CurlMultiApiHandler() }
        }

        runEventLoop()
    }

    @OptIn(ExperimentalForeignApi::class)
    suspend fun executeRequest(request: CurlRequestData): CurlSuccess {
        val result = CompletableDeferred<CurlSuccess>()
        val container = RequestContainer(request, result)
        if (requestQueue.trySend(container).isFailure) {
            val cause = CancellationException("HTTP engine closed")
            disposeQueuedRequest(container, cause)
            throw cause
        }
        curlApi.wakeup()
        return result.await()
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalForeignApi::class)
    private fun runEventLoop() {
        curlScope.launch {
            val api = curlApi
            try {
                memScoped {
                    val transfersRunning = alloc<IntVar>()
                    while (!closed.value) {
                        drainRequestQueue(api)
                        if (closed.value) break
                        api.perform(transfersRunning)
                        // Native polling does not suspend; allow queued cancellation jobs to run.
                        yield()
                    }
                }
            } finally {
                closed.value = true
                requestQueue.close()
                val cause = CancellationException("HTTP engine closed")
                while (true) {
                    val queued = requestQueue.tryReceive().getOrNull() ?: break
                    disposeQueuedRequest(queued, cause)
                }
                api.close()
            }
        }.invokeOnCompletion { curlDispatcher.close() }
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun drainRequestQueue(api: CurlMultiApiHandler) {
        while (true) {
            if (closed.value) break
            val container = if (api.hasHandlers()) {
                requestQueue.tryReceive()
            } else {
                requestQueue.receiveCatching()
            }.getOrNull() ?: break

            if (!container.requestData.executionContext.isActive || closed.value) {
                disposeQueuedRequest(container, CancellationException("HTTP request cancelled"))
                continue
            }

            val requestHandler = try {
                api.scheduleRequest(container.requestData, container.completionHandler)
            } catch (failure: Throwable) {
                // scheduleRequest owns and releases its input even on native setup failure.
                container.completionHandler.completeExceptionally(failure)
                continue
            }

            val requestCleaner = container.requestData.executionContext.invokeOnCompletion { cause ->
                if (cause == null) return@invokeOnCompletion
                cancelRequest(requestHandler, cause, container.requestData)
            }

            api.keepCancellationUntilBodyCompletes(requestHandler, requestCleaner)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun close() {
        if (!closed.compareAndSet(false, true)) return

        requestQueue.close()
        curlApi.wakeup()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun cancelRequest(easyHandle: EasyHandle, cause: Throwable, request: CurlRequestData) {
        curlScope.launch {
            curlApi.cancelRequest(easyHandle, cause, request)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun disposeQueuedRequest(container: RequestContainer, cause: Throwable) {
        curl_slist_free_all(container.requestData.headers)
        container.requestData.content.cancel(cause)
        container.completionHandler.completeExceptionally(cause)
    }
}
