@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp.platform.http_ios

import cn.mafei.cmp.core.http_contract.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.NSOperationQueue
import platform.posix.*
import platform.posix.close as closeSocket
import kotlin.test.*

/** Real Darwin NSURLSession -> loopback sockets on iOS Simulator, not JVM mocks. */
class IosHttpTransportLoopbackTest {
    @Test fun returnsSuccessAndHttpErrorWithoutThrowOnStatus() = runBlocking<Unit> {
        withLoopback({ request -> Reply(if (request.path == "/denied") 401 else 200, "response") }) { server, transport ->
            assertEquals("response", transport.execute(server.request()).bodyText())
            assertEquals(401, transport.execute(server.request("/denied")).statusCode)
        }
    }

    @Test fun redirect302PreservesPostAndBodyThroughSharedResolver() = runBlocking<Unit> {
        withLoopback({ request ->
            if (request.path == "/start") Reply(302, "", listOf("Location" to "/finish"))
            else Reply(200, "${request.method}:${request.body}")
        }) { server, transport ->
            val request = HttpRequest.builder(HttpMethod.Post, server.url("/start"))
                .body(HttpBody.Text("payload", "text/plain")).build()
            val response = transport.execute(request)
            assertEquals("POST:payload", response.bodyText())
            assertEquals(server.url("/finish"), response.effectiveUrl)
            assertEquals(2, server.requests.value.size)
        }
    }

    @Test fun doNotFollowExposesOriginalRedirect() = runBlocking<Unit> {
        withLoopback({ Reply(302, "moved", listOf("Location" to "/finish")) }) { server, transport ->
            val response = transport.execute(server.request().newBuilder().redirectPolicy(RedirectPolicy.DoNotFollow).build())
            assertEquals(302, response.statusCode); assertEquals("moved", response.bodyText())
            assertEquals(1, server.requests.value.size)
        }
    }

    @Test fun rejectsBodyBeyondFrozenReadLimit() = runBlocking<Unit> {
        withLoopback({ Reply(200, "0123456789") }) { server, transport ->
            assertFailsWith<HttpBodyLimitExceededException> {
                transport.execute(server.request().newBuilder().readPolicy(BodyReadPolicy.BufferedAtMost(5)).build())
            }
        }
    }

    @Test fun totalDeadlineAndCallerCancellationRemainDistinct() = runBlocking<Unit> {
        withLoopback({ Reply(200, "late", delayMs = 350) }) { server, transport ->
            assertFailsWith<HttpTimeoutException> {
                transport.execute(server.request().newBuilder().timeoutMillis(100).build())
            }
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(100) { transport.execute(server.request().newBuilder().timeoutMillis(3000).build()) }
            }
        }
    }

    @Test fun responseCookiesAreNotImplicitlySentOnNextRequest() = runBlocking<Unit> {
        withLoopback({ Reply(200, "ok", listOf("Set-Cookie" to "mafei_ios_probe=synthetic; Path=/")) }) { server, transport ->
            transport.execute(server.request()); transport.execute(server.request())
            assertEquals(2, server.requests.value.size)
            assertTrue(server.requests.value.last().headers.none { it.first.equals("Cookie", ignoreCase = true) })
        }
    }

    @Test fun repeatedSetCookieHeadersKeepBothCredentialValues() = runBlocking<Unit> {
        val cookies = listOf("first=one; Expires=Wed, 09 Jun 2027 10:18:14 GMT; Path=/", "second=two; Path=/")
        withLoopback({ Reply(200, "ok", cookies.map { "Set-Cookie" to it }) }) { server, transport ->
            val values = transport.execute(server.request()).headers.values("Set-Cookie")
            assertEquals(cookies, values)
        }
    }
}

private data class Reply(val status: Int, val body: String, val headers: List<Pair<String, String>> = emptyList(), val delayMs: Int = 0)
private data class Captured(val method: String, val path: String, val headers: List<Pair<String, String>>, val body: String)

private suspend fun withLoopback(
    respond: (Captured) -> Reply,
    block: suspend (Loopback, HttpTransport) -> Unit,
) {
    val server = Loopback(respond)
    val transport = createIosHttpTransport()
    try { withTimeout(5000) { block(server, transport) } }
    finally { transport.close(); server.close() }
}

/** Test-local fixture only: finite socket waits and a single independent NSOperationQueue. */
private class Loopback(private val respond: (Captured) -> Reply) {
    private val listener = socket(AF_INET, SOCK_STREAM, 0)
    private val stopped = MutableStateFlow(false)
    private val queue = NSOperationQueue().apply { maxConcurrentOperationCount = 1 }
    val requests = MutableStateFlow<List<Captured>>(emptyList())
    private val port: Int

    init {
        check(listener >= 0) { "Cannot open test loopback socket" }
        port = memScoped {
            val address = alloc<sockaddr_in>()
            memset(address.ptr, 0, sizeOf<sockaddr_in>().convert())
            address.sin_len = sizeOf<sockaddr_in>().convert()
            address.sin_family = AF_INET.convert()
            // This module targets only little-endian Apple ARM64; libc byte-order macros
            // are not exposed by the CPF platform.posix bindings.
            address.sin_addr.s_addr = 0x0100007fu
            address.sin_port = 0u
            check(bind(listener, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0)
            check(listen(listener, 8) == 0)
            val length = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_in>().convert() }
            check(getsockname(listener, address.ptr.reinterpret(), length.ptr) == 0)
            val networkPort = address.sin_port.toInt()
            ((networkPort and 0xff) shl 8) or (networkPort ushr 8)
        }
        queue.addOperationWithBlock { acceptLoop() }
    }

    fun url(path: String = "/") = HttpUrl.parse("http://127.0.0.1:$port$path")
    fun request(path: String = "/") = HttpRequest.builder(HttpMethod.Get, url(path)).build()

    fun close() {
        stopped.value = true
        // The accept loop polls rather than blocking forever in accept().
        queue.waitUntilAllOperationsAreFinished()
        closeSocket(listener)
    }

    private fun acceptLoop() = memScoped {
        val descriptor = alloc<pollfd>().apply { fd = listener; events = POLLIN.convert(); revents = 0 }
        while (!stopped.value) {
            if (poll(descriptor.ptr, 1u, 50) <= 0) continue
            val client = accept(listener, null, null)
            if (client < 0) continue
            try {
                val yes = alloc<IntVar>().apply { value = 1 }
                setsockopt(client, SOL_SOCKET, SO_NOSIGPIPE, yes.ptr, sizeOf<IntVar>().convert())
                val timeout = alloc<timeval>().apply { tv_sec = 2; tv_usec = 0 }
                setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert())
                val request = readRequest(client) ?: continue
                requests.value = requests.value + request
                val reply = respond(request)
                if (reply.delayMs > 0) usleep((reply.delayMs * 1000).convert())
                val body = reply.body.encodeToByteArray()
                val head = buildString {
                    append("HTTP/1.1 ${reply.status} Test\r\nContent-Length: ${body.size}\r\nConnection: close\r\n")
                    reply.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                    append("\r\n")
                }.encodeToByteArray()
                sendBytes(client, head + body)
            } finally { closeSocket(client) }
        }
    }

    private fun readRequest(client: Int): Captured? {
        val text = StringBuilder()
        val buffer = ByteArray(1024)
        while (text.length < 16 * 1024) {
            val count = buffer.usePinned { recv(client, it.addressOf(0), buffer.size.convert(), 0).toInt() }
            if (count <= 0) return null
            text.append(buffer.decodeToString(0, count))
            val end = text.indexOf("\r\n\r\n")
            if (end < 0) continue
            val lines = text.substring(0, end).split("\r\n")
            val parts = lines.first().split(' ')
            val headers = lines.drop(1).map { line -> line.substringBefore(':') to line.substringAfter(':').trim() }
            val bodySize = headers.firstOrNull { it.first.equals("Content-Length", true) }?.second?.toIntOrNull() ?: 0
            if (text.length - end - 4 < bodySize) continue
            return Captured(parts[0], parts[1], headers, text.substring(end + 4, end + 4 + bodySize))
        }
        return null
    }

    private fun sendBytes(client: Int, bytes: ByteArray) {
        bytes.usePinned { pinned ->
            var offset = 0
            while (offset < bytes.size) {
                val count = send(client, pinned.addressOf(offset), (bytes.size - offset).convert(), 0).toInt()
                if (count <= 0) return
                offset += count
            }
        }
    }
}
