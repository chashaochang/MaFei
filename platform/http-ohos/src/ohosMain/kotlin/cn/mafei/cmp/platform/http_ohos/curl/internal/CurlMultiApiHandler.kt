/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// Modified for MaFei (2026-09-08): relocated, HTTP-only; see third-party/NOTICE.md.
package cn.mafei.cmp.platform.http_ohos.curl.internal

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.locks.*
import kotlinx.cinterop.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import libcurl.*
import platform.posix.getenv
import cn.mafei.cmp.core.http_contract.HttpTransportException
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.core.http_contract.TransportFailureKind

@OptIn(ExperimentalForeignApi::class)
private class RequestHolder(
    val request: CurlRequestData,
    val responseCompletable: CompletableDeferred<CurlSuccess>,
    val requestWrapper: StableRef<CurlRequestBodyData>,
    val responseWrapper: StableRef<CurlResponseBodyData>,
) {
    var cancellation: DisposableHandle? = null
    fun dispose() {
        cancellation?.dispose()
        requestWrapper.dispose()
        responseWrapper.dispose()
    }
}

@OptIn(InternalAPI::class, ExperimentalForeignApi::class)
internal class CurlMultiApiHandler : Closeable {
    private val activeHandles = mutableMapOf<EasyHandle, RequestHolder>()

    private val multiHandle: MultiHandle = curl_multi_init()
        ?: throw RuntimeException("Could not initialize curl multi handle")

    private val easyHandlesToUnpauseLock = SynchronizedObject()
    private val easyHandlesToUnpause = mutableListOf<Pair<EasyHandle, CurlRequestData>>()
    private var closed = false // guarded by easyHandlesToUnpauseLock

    override fun close() {
        for (handle in activeHandles.keys.toList()) {
            cancelRequest(handle, CancellationException("HTTP engine closed"))
        }
        synchronized(easyHandlesToUnpauseLock) {
            if (closed) return
            closed = true
            easyHandlesToUnpause.clear()
            curl_multi_cleanup(multiHandle).verify()
        }
    }

    fun scheduleRequest(request: CurlRequestData, deferred: CompletableDeferred<CurlSuccess>): EasyHandle {
        val easyHandle = curl_easy_init() ?: run {
            curl_slist_free_all(request.headers)
            request.content.cancel(null)
            error("Could not initialize an easy handle")
        }

        val bodyStartedReceiving = CompletableDeferred<Unit>()
        val responseBody = CurlHttpResponseBody(request.executionContext) {
            unpauseEasyHandle(easyHandle, request)
        }
        val responseData = CurlResponseBuilder(request, bodyStartedReceiving, responseBody)
        val responseDataRef = responseData.asStablePointer()
        val responseWrapper = responseBody.asStablePointer()

        bodyStartedReceiving.invokeOnCompletion {
            val result = collectSuccessResponse(easyHandle) ?: return@invokeOnCompletion
            activeHandles[easyHandle]?.responseCompletable?.complete(result)
        }

        val requestWrapper = CurlRequestBodyData(request.content, request.executionContext) {
            unpauseEasyHandle(easyHandle, request)
        }.asStablePointer()
        val requestHolder = RequestHolder(
            request,
            deferred,
            requestWrapper.asStableRef(),
            responseWrapper.asStableRef()
        )

        activeHandles[easyHandle] = requestHolder

        try {
          setupMethod(easyHandle, request.method, request.contentLength)
          easyHandle.apply {
            option(CURLOPT_READDATA, requestWrapper)
            option(CURLOPT_READFUNCTION, staticCFunction(::onBodyChunkRequested))
            option(CURLOPT_URL, request.url)
            option(CURLOPT_HTTPHEADER, request.headers)
            option(CURLOPT_HEADERFUNCTION, staticCFunction(::onHeadersReceived))
            option(CURLOPT_HEADERDATA, responseDataRef)
            option(CURLOPT_WRITEFUNCTION, staticCFunction(::onBodyChunkReceived))
            option(CURLOPT_WRITEDATA, responseWrapper)
            option(CURLOPT_PRIVATE, responseDataRef)
            option(CURLOPT_ACCEPT_ENCODING, "")
            request.connectTimeout?.let {
                if (it != HttpTimeoutConfig.INFINITE_TIMEOUT_MS) {
                    option(CURLOPT_CONNECTTIMEOUT_MS, request.connectTimeout)
                } else {
                    option(CURLOPT_CONNECTTIMEOUT_MS, Int.MAX_VALUE.toLong())
                }
            }

            request.proxy?.let { proxy ->
                option(CURLOPT_PROXY, fixProxyUrl(proxy.toString(), proxy.type))
                option(CURLOPT_SUPPRESS_CONNECT_HEADERS, 1L)
                if (request.forceProxyTunneling) {
                    option(CURLOPT_HTTPPROXYTUNNEL, 1L)
                }
            }

            option(CURLOPT_SSL_VERIFYPEER, 1L)
            option(CURLOPT_SSL_VERIFYHOST, 2L)
            request.caPath?.let { option(CURLOPT_CAPATH, it) }
            request.caInfo?.let { option(CURLOPT_CAINFO, it) }
        }

          curl_multi_add_handle(multiHandle, easyHandle).verify()
          return easyHandle
        } catch (failure: Throwable) {
            // No perform/callback can occur until scheduling returns to the event loop.
            activeHandles.remove(easyHandle)
            responseBody.close(failure)
            responseData.headersBytes.close()
            responseDataRef.asStableRef<CurlResponseBuilder>().dispose()
            request.content.cancel(failure)
            curl_slist_free_all(request.headers)
            requestHolder.dispose()
            curl_easy_cleanup(easyHandle)
            throw failure
        }
    }

    private fun fixProxyUrl(url: String, proxyType: ProxyType): String {
        return if (proxyType == ProxyType.SOCKS) url.replaceFirst("socks://", "socks5://") else url
    }

    internal fun cancelRequest(easyHandle: EasyHandle, cause: Throwable, expectedRequest: CurlRequestData? = null) {
        // libcurl may reuse an address before a queued cancellation runs.
        if (expectedRequest != null && activeHandles[easyHandle]?.request !== expectedRequest) return
        // One owner removes the handle before freeing it. A late cancel after completion is a no-op.
        val holder = activeHandles.remove(easyHandle) ?: return
        try {
            processCancelledEasyHandle(easyHandle, cause)
            holder.responseCompletable.completeExceptionally(cause)
        } finally { holder.dispose() }
    }

    internal fun keepCancellationUntilBodyCompletes(easyHandle: EasyHandle, hook: DisposableHandle) {
        val holder = activeHandles[easyHandle]
        if (holder == null) hook.dispose() else holder.cancellation = hook
    }

    internal fun perform(transfersRunning: IntVarOf<Int>) {
        if (activeHandles.isEmpty()) return

        synchronized(easyHandlesToUnpauseLock) {
            var entry = easyHandlesToUnpause.removeFirstOrNull()
            while (entry != null) {
                if (activeHandles[entry.first]?.request === entry.second) curl_easy_pause(entry.first, CURLPAUSE_CONT)
                entry = easyHandlesToUnpause.removeFirstOrNull()
            }
        }
        curl_multi_perform(multiHandle, transfersRunning.ptr).verify()
        if (transfersRunning.value != 0) {
            curl_multi_poll(multiHandle, null, 0.toUInt(), pollTimeout, null).verify()
        }
        if (transfersRunning.value < activeHandles.size) {
            handleCompleted()
        }
    }

    internal fun hasHandlers(): Boolean = activeHandles.isNotEmpty()

    private fun setupMethod(
        easyHandle: EasyHandle,
        method: String,
        size: Long
    ) {
        easyHandle.apply {
            when (method) {
                "GET" -> option(CURLOPT_HTTPGET, 1L)

                "PUT" -> {
                    option(CURLOPT_PUT, 1L)
                    option(CURLOPT_INFILESIZE_LARGE, size)
                }

                "POST" -> {
                    option(CURLOPT_POST, 1L)
                    option(CURLOPT_POSTFIELDSIZE_LARGE, size)
                }

                "HEAD" -> option(CURLOPT_NOBODY, 1L)

                else -> {
                    if (size > 0) {
                        option(CURLOPT_POST, 1L)
                        option(CURLOPT_POSTFIELDSIZE_LARGE, size)
                    }
                    option(CURLOPT_CUSTOMREQUEST, method)
                }
            }
        }
    }

    private fun handleCompleted() {
        memScoped {
            do {
                val messagesLeft = alloc<IntVar>()
                val messagePtr = curl_multi_info_read(multiHandle, messagesLeft.ptr)
                val message = messagePtr?.pointed ?: continue

                val easyHandle = message.easy_handle
                    ?: error("Got a null easy handle from the message")
                if (easyHandle !in activeHandles) continue

                try {
                    val result = processCompletedEasyHandle(message.msg, easyHandle, message.data.result)
                    val deferred = activeHandles[easyHandle]!!.responseCompletable
                    if (deferred.isCompleted) {
                        // already completed with partial response
                        continue
                    }
                    when (result) {
                        is CurlSuccess -> deferred.complete(result)
                        is CurlFail -> deferred.completeExceptionally(result.cause)
                    }
                } finally {
                    activeHandles.remove(easyHandle)!!.dispose()
                }
            } while (messagesLeft.value != 0)
        }
    }

    private fun processCancelledEasyHandle(easyHandle: EasyHandle, cause: Throwable): CurlFail = memScoped {
        try {
            val responseDataRef = alloc<COpaquePointerVar>()
            easyHandle.apply { getInfo(CURLINFO_PRIVATE, responseDataRef.ptr) }
            val responseBuilder = responseDataRef.value!!.fromCPointer<CurlResponseBuilder>()
            try {
                return CurlFail(cause)
            } finally {
                responseBuilder.responseBody.close(cause)
                responseBuilder.headersBytes.close()
            }
        } finally {
            cleanupEasyHandle(easyHandle)
        }
    }

    private fun processCompletedEasyHandle(
        message: CURLMSG?,
        easyHandle: EasyHandle,
        result: CURLcode
    ): CurlResponseData = memScoped {
        try {
            val responseDataRef = alloc<COpaquePointerVar>()
            val httpStatusCode = alloc<LongVar>()

            easyHandle.apply {
                getInfo(CURLINFO_RESPONSE_CODE, httpStatusCode.ptr)
                getInfo(CURLINFO_PRIVATE, responseDataRef.ptr)
            }

            val responseBuilder = responseDataRef.value!!.fromCPointer<CurlResponseBuilder>()
            try {
                val failure = collectFailedResponse(message, responseBuilder.request, result, httpStatusCode.value)
                // A response head may already have been returned. Read failures must still reach its channel.
                responseBuilder.responseBody.close(failure?.cause)
                failure ?: collectSuccessResponse(easyHandle)!!
            } finally {
                responseBuilder.responseBody.close()
                responseBuilder.headersBytes.close()
            }
        } finally {
            cleanupEasyHandle(easyHandle)
        }
    }

    private fun collectFailedResponse(
        message: CURLMSG?,
        request: CurlRequestData,
        result: CURLcode,
        httpStatusCode: Long
    ): CurlFail? {
        if (message == CURLMSG.CURLMSG_DONE && result == CURLE_OK && httpStatusCode != 0L) {
            return null
        }
        val kind = when (result) {
            CURLE_COULDNT_RESOLVE_HOST, CURLE_COULDNT_RESOLVE_PROXY -> TransportFailureKind.Dns
            CURLE_COULDNT_CONNECT -> TransportFailureKind.Connect
            CURLE_SSL_CONNECT_ERROR, CURLE_PEER_FAILED_VERIFICATION, CURLE_SSL_CERTPROBLEM,
            CURLE_SSL_CACERT_BADFILE, CURLE_SSL_CIPHER -> TransportFailureKind.Tls
            CURLE_SEND_ERROR -> TransportFailureKind.SocketWrite
            else -> TransportFailureKind.Unknown
        }
        return CurlFail(HttpTransportException(HttpUrl.parse(request.url), kind, "Native HTTP engine failed"))
    }

    private fun collectSuccessResponse(easyHandle: EasyHandle): CurlSuccess? = memScoped {
        val responseDataRef = alloc<COpaquePointerVar>()
        val httpProtocolVersion = alloc<LongVar>()
        val httpStatusCode = alloc<LongVar>()

        easyHandle.apply {
            getInfo(CURLINFO_RESPONSE_CODE, httpStatusCode.ptr)
            getInfo(CURLINFO_PRIVATE, responseDataRef.ptr)
            getInfo(CURLINFO_HTTP_VERSION, httpProtocolVersion.ptr)
        }

        if (httpStatusCode.value == 0L) {
            // if error happened, it will be handled in collectCompleted
            return@memScoped null
        }

        val responseBuilder = responseDataRef.value!!.fromCPointer<CurlResponseBuilder>()
        with(responseBuilder) {
            val headers = headersBytes.build().readByteArray()

            CurlSuccess(
                httpStatusCode.value.toInt(),
                httpProtocolVersion.value.toUInt(),
                headers,
                responseBody
            )
        }
    }

    fun wakeup() {
        synchronized(easyHandlesToUnpauseLock) {
            if (!closed) curl_multi_wakeup(multiHandle)
        }
    }

    private fun unpauseEasyHandle(easyHandle: EasyHandle, request: CurlRequestData) {
        synchronized(easyHandlesToUnpauseLock) {
            if (closed) return
            easyHandlesToUnpause.add(easyHandle to request)
            curl_multi_wakeup(multiHandle)
        }
    }

    private fun cleanupEasyHandle(easyHandle: EasyHandle) {
        synchronized(easyHandlesToUnpauseLock) {
            easyHandlesToUnpause.removeAll { it.first == easyHandle }
        }
        memScoped {
            val privateData = alloc<COpaquePointerVar>()
            easyHandle.getInfo(CURLINFO_PRIVATE, privateData.ptr)
            privateData.value?.asStableRef<CurlResponseBuilder>()?.let { ref ->
                ref.get().request.content.cancel()
                curl_slist_free_all(ref.get().request.headers)
                ref.dispose()
            }
        }
        curl_multi_remove_handle(multiHandle, easyHandle).verify()
        curl_easy_cleanup(easyHandle)
    }

    private companion object {
        private const val DEFAULT_POLL_TIMEOUT_MS = 100
        val pollTimeout by lazy { getenv("KTOR_CURL_POLL_TIMEOUT")?.toKString()?.toInt() ?: DEFAULT_POLL_TIMEOUT_MS }
    }
}
