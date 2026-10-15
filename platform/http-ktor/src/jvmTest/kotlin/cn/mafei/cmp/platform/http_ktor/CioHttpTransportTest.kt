package cn.mafei.cmp.platform.http_ktor

import cn.mafei.cmp.core.http_contract.*
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class CioHttpTransportTest {
    @Test fun returnsAllHttpStatusesAndRepeatedResponseHeaders() = runBlocking<Unit> {
        server { _, socket -> socket.respond(503, "failure", listOf("Set-Cookie" to "a=1", "Set-Cookie" to "b=2")) }.use { s ->
            CioHttpTransport().useTransport { t ->
                val response = t.execute(request(s))
                assertEquals(503, response.statusCode); assertEquals("failure", response.bodyText())
                assertEquals(listOf("a=1", "b=2"), response.headers.values("set-cookie"))
                assertEquals(request(s).url, response.requestUrl); assertEquals(response.requestUrl, response.effectiveUrl)
            }
        }
    }
    @Test fun followsOnlySharedRedirectsAndPreservesPostOn302() = runBlocking<Unit> {
        server { req, socket ->
            if (req.target == "/start") socket.respond(302, "", listOf("Location" to "/finish"))
            else socket.respond(200, "${req.method}:${req.body}")
        }.use { s -> CioHttpTransport().useTransport { t ->
            val req = HttpRequest.builder(HttpMethod.Post, s.url("/start")).body(HttpBody.Text("payload", "text/plain")).build()
            val response = t.execute(req)
            assertEquals("POST:payload", response.bodyText()); assertEquals(s.url("/finish"), response.effectiveUrl)
            assertEquals(s.url("/start"), response.requestUrl); assertEquals(2, s.requests.size)
        } }
    }
    @Test fun redirect303DropsBodyAndContentHeaders() = runBlocking<Unit> {
        server { req, socket ->
            if (req.target == "/start") socket.respond(303, "", listOf("Location" to "/finish"))
            else socket.respond(200, "done")
        }.use { s -> CioHttpTransport().useTransport { t ->
            t.execute(HttpRequest.builder(HttpMethod.Post, s.url("/start")).body(HttpBody.Text("payload", "text/plain")).build())
            val last = s.requests.last(); assertEquals("GET", last.method); assertEquals("", last.body)
            assertTrue(last.values("Content-Type").isEmpty()); assertTrue(last.values("Content-Length").isEmpty())
        } }
    }
    @Test fun doNotFollowPreservesRedirectBody() = runBlocking<Unit> {
        server { _, socket -> socket.respond(302, "moved", listOf("Location" to "/next")) }.use { s ->
            CioHttpTransport().useTransport { t ->
                val response = t.execute(request(s).newBuilder().redirectPolicy(RedirectPolicy.DoNotFollow).build())
                assertEquals(302, response.statusCode); assertEquals("moved", response.bodyText()); assertEquals(1, s.requests.size)
            }
        }
    }
    @Test fun totalTimeoutIncludesAllRedirectHops() = runBlocking<Unit> {
        server { req, socket ->
            Thread.sleep(220)
            socket.respond(if (req.target == "/end") 200 else 302, "", listOf("Location" to "/end"))
        }.use { s -> CioHttpTransport().useTransport { t ->
            val error = assertFailsWith<HttpTimeoutException> { t.execute(request(s).newBuilder().timeoutMillis(330).build()) }
            assertEquals(330, error.timeoutMillis); assertEquals(2, s.requests.size)
        } }
    }
    @Test fun timeoutIncludesBodyRead() = runBlocking<Unit> {
        server { _, socket -> socket.headers(200, 100); Thread.sleep(600) }.use { s ->
            CioHttpTransport().useTransport { t -> assertFailsWith<HttpTimeoutException> {
                t.execute(request(s).newBuilder().timeoutMillis(200).build())
            } }
        }
    }
    @Test fun callerTimeoutIsCancellationNotTransportDeadline() = runBlocking<Unit> {
        server { _, socket -> socket.headers(200, 100); Thread.sleep(600) }.use { s ->
            CioHttpTransport().useTransport { t -> assertFailsWith<TimeoutCancellationException> {
                withTimeout(150) { t.execute(request(s).newBuilder().timeoutMillis(5_000).build()) }
            } }
        }
    }
    @Test fun crossOriginStripsAllCredentialHeaders() = runBlocking<Unit> {
        server { _, socket -> socket.respond(200, "target") }.use { target ->
            server { _, socket -> socket.respond(302, "", listOf("Location" to target.url().toString())) }.use { source ->
                CioHttpTransport().useTransport { t ->
                    val builder = request(source).newBuilder().header("X-Keep", "public")
                    HttpCredentialHeaders.ALL.forEach { builder.header(it, "synthetic-secret") }
                    assertEquals("target", t.execute(builder.build()).bodyText())
                    HttpCredentialHeaders.ALL.forEach { assertTrue(target.requests.single().values(it).isEmpty(), it) }
                    assertEquals(listOf("public"), target.requests.single().values("X-Keep"))
                }
            }
        }
    }
    @Test fun sameOriginPolicyDoesNotVisitOtherPort() = runBlocking<Unit> {
        server { _, socket -> socket.respond(200, "must-not-hit") }.use { target ->
            server { _, socket -> socket.respond(302, "stay", listOf("Location" to target.url().toString())) }.use { source ->
                CioHttpTransport().useTransport { t ->
                    val response = t.execute(request(source).newBuilder().redirectPolicy(RedirectPolicy.FollowSameOrigin).build())
                    assertEquals(302, response.statusCode); assertTrue(target.requests.isEmpty())
                }
            }
        }
    }
    @Test fun redirectLoopStopsAtExactlyFrozenBudget() = runBlocking<Unit> {
        server { _, socket -> socket.respond(302, "", listOf("Location" to "/")) }.use { s ->
            CioHttpTransport().useTransport { t ->
                assertFailsWith<HttpRedirectLimitExceededException> { t.execute(request(s)) }
                assertEquals(HttpRedirects.DEFAULT_MAX_REDIRECTS + 1, s.requests.size)
            }
        }
    }
    @Test fun cookieJarIsNotImplicitlyInstalled() = runBlocking<Unit> {
        server { _, socket -> socket.respond(200, "ok", listOf("Set-Cookie" to "hidden=secret")) }.use { s ->
            CioHttpTransport().useTransport { t -> t.execute(request(s)); t.execute(request(s)) }
            assertTrue(s.requests.last().values("Cookie").isEmpty())
        }
    }
    @Test fun repeatedRequestHeadersRemainPresent() = runBlocking<Unit> {
        server { _, socket -> socket.respond(200, "ok") }.use { s -> CioHttpTransport().useTransport { t ->
            t.execute(request(s).newBuilder().header("X-Test", "one").header("X-Test", "two")
                .header("cookie", "a=1").header("COOKIE", "b=2").build())
            // CIO combines ordinary duplicate headers with comma and Cookie with semicolon;
            // protocol values must survive, not necessarily the original physical line count.
            assertEquals(listOf("one", "two"), s.requests.single().values("X-Test").flatMap { it.split(',') })
            assertEquals(listOf("a=1; b=2"), s.requests.single().values("Cookie"))
        } }
    }
    @Test fun boundedReadAcceptsExactLimitAndRejectsOneExtra() = runBlocking<Unit> {
        server { _, socket -> socket.respond(200, "12345") }.use { s -> CioHttpTransport().useTransport { t ->
            assertEquals("12345", t.execute(request(s).newBuilder().readPolicy(BodyReadPolicy.BufferedAtMost(5)).build()).bodyText())
            val error = assertFailsWith<HttpBodyLimitExceededException> {
                t.execute(request(s).newBuilder().readPolicy(BodyReadPolicy.BufferedAtMost(4)).build())
            }
            assertEquals(4, error.maxBytes)
        } }
    }
    @Test fun chunkedBodyCannotBypassByteLimit() = runBlocking<Unit> {
        server { _, socket ->
            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n3\r\nabc\r\n3\r\ndef\r\n0\r\n\r\n".toByteArray())
            socket.getOutputStream().flush()
        }.use { s -> CioHttpTransport().useTransport { t -> assertFailsWith<HttpBodyLimitExceededException> {
            t.execute(request(s).newBuilder().readPolicy(BodyReadPolicy.BufferedAtMost(5)).build())
        } } }
    }
    @Test fun bufferedReadsWholePayload() = runBlocking<Unit> {
        val content = "x".repeat(20_000)
        server { _, socket -> socket.respond(200, content) }.use { s -> CioHttpTransport().useTransport { t ->
            assertEquals(content, t.execute(request(s).newBuilder().readPolicy(BodyReadPolicy.Buffered).build()).bodyText())
        } }
    }
    @Test fun discardedDoesNotWaitForBodyAndClosesSocket() = runBlocking<Unit> {
        val disconnected = CountDownLatch(1)
        server { _, socket -> socket.headers(200, 999_999); if (socket.getInputStream().read() == -1) disconnected.countDown() }.use { s ->
            CioHttpTransport().useTransport { t ->
                val response = withTimeout(1_500) { t.execute(request(s).newBuilder().readPolicy(BodyReadPolicy.Discarded).build()) }
                assertTrue(response.bodyBytes.isEmpty()); assertTrue(disconnected.await(2, TimeUnit.SECONDS))
            }
        }
    }
    @Test fun cancellationClosesInFlightConnection() = runBlocking<Unit> {
        val connected = CountDownLatch(1); val disconnected = CountDownLatch(1)
        server { _, socket -> socket.headers(200, 999); connected.countDown()
            if (socket.getInputStream().read() == -1) disconnected.countDown()
        }.use { s -> CioHttpTransport().useTransport { t ->
            val call = async(Dispatchers.Default) { t.execute(request(s)) }
            assertTrue(connected.await(2, TimeUnit.SECONDS)); call.cancel()
            assertFailsWith<CancellationException> { call.await() }
            assertTrue(disconnected.await(2, TimeUnit.SECONDS))
        } }
    }
    @Test fun closeIsIdempotentAndCancelsActiveThenRejectsNewCalls() = runBlocking<Unit> {
        val connected = CountDownLatch(1)
        server { _, socket -> socket.headers(200, 999); connected.countDown(); socket.getInputStream().read() }.use { s ->
            val t = CioHttpTransport()
            val call = async(Dispatchers.Default) { t.execute(request(s)) }
            assertTrue(connected.await(2, TimeUnit.SECONDS)); t.close(); t.close()
            assertFailsWith<CancellationException> { call.await() }
            assertFailsWith<HttpTransportException> { t.execute(request(s)) }
        }
    }
    @Test fun connectionRefusedMapsToConnectAndIsNotRetried() = runBlocking<Unit> {
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        CioHttpTransport().useTransport { t ->
            val error = assertFailsWith<HttpTransportException> {
                t.execute(HttpRequest.builder(HttpMethod.Get, HttpUrl.parse("http://127.0.0.1:$port/")).build())
            }
            assertEquals(TransportFailureKind.Connect, error.kind)
            assertNull(error.cause, "Raw engine causes may embed unredacted request metadata")
        }
    }
    @Test fun encodedPathAndDuplicateQueryAreNotRewritten() = runBlocking<Unit> {
        server { req, socket -> socket.respond(200, req.target) }.use { s -> CioHttpTransport().useTransport { t ->
            val path = "/a//b%2Fc?x=one%20two&x=three"
            assertEquals(path, t.execute(request(s, path)).bodyText())
        } }
    }
    @Test fun explicitContentLengthMatchesBufferedBody() = runBlocking<Unit> {
        server { req, socket -> socket.respond(200, req.body) }.use { s -> CioHttpTransport().useTransport { t ->
            val req = HttpRequest.builder(HttpMethod.Post, s.url()).body(HttpBody.Text("hello"))
                .header("Content-Length", "5").build()
            assertEquals("hello", t.execute(req).bodyText())
            assertEquals(listOf("5"), s.requests.single().values("Content-Length"))
        } }
    }
    @Test fun intermediateRedirectBodyIsDiscardedBeforeFollowing() = runBlocking<Unit> {
        server { req, socket -> if (req.target == "/") socket.respond(302, "x".repeat(100), listOf("Location" to "/final"))
            else socket.respond(200, "ok")
        }.use { s -> CioHttpTransport().useTransport { t ->
            assertEquals("ok", t.execute(request(s).newBuilder().readPolicy(BodyReadPolicy.BufferedAtMost(2)).build()).bodyText())
        } }
    }
    @Test fun stalledRedirectBodyDoesNotDelayNextHop() = runBlocking<Unit> {
        server { req, socket -> if (req.target == "/") {
            socket.headers(302, 50_000, listOf("Location" to "/final")); socket.getInputStream().read()
        } else socket.respond(200, "ok")
        }.use { s -> CioHttpTransport().useTransport { t ->
            assertEquals("ok", withTimeout(1_500) { t.execute(request(s)) }.bodyText())
        } }
    }
    @Test fun emptyBodyFitsZeroLimitAndNegativeLimitIsRejected() = runBlocking<Unit> {
        server { _, socket -> socket.respond(204, "") }.use { s -> CioHttpTransport().useTransport { t ->
            assertTrue(t.execute(request(s).newBuilder().readPolicy(BodyReadPolicy.BufferedAtMost(0)).build()).bodyBytes.isEmpty())
            assertFailsWith<IllegalArgumentException> { t.execute(request(s).newBuilder().readPolicy(BodyReadPolicy.BufferedAtMost(-1)).build()) }
        } }
    }
    @Test fun closeNeverCancelsUnrelatedCallerScope() = runBlocking<Unit> {
        val transport = CioHttpTransport(); transport.close()
        assertTrue(currentCoroutineContext().isActive)
    }
    @Test fun preciseKnownFailureKindsDoNotClassifyAllIoAsWrite() {
        assertEquals(TransportFailureKind.Dns, classifyEngineFailure(java.net.UnknownHostException()))
        assertEquals(TransportFailureKind.Tls, classifyEngineFailure(javax.net.ssl.SSLHandshakeException("synthetic")))
        assertEquals(TransportFailureKind.SocketWrite, classifyEngineFailure(io.ktor.utils.io.ClosedWriteChannelException()))
        assertEquals(TransportFailureKind.Unknown, classifyEngineFailure(java.io.EOFException()))
    }
    @Test fun tlsProtocolFailureIsNotAcceptedAsPlainHttp() = runBlocking<Unit> {
        val socketServer = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val fixture = Thread {
            try { socketServer.accept().use {
                it.soTimeout = 2_000
                // Consume ClientHello before replying, so closing does not reset unread TCP data.
                val input = java.io.DataInputStream(it.getInputStream())
                val recordHeader = ByteArray(5); input.readFully(recordHeader)
                val length = ((recordHeader[3].toInt() and 255) shl 8) or (recordHeader[4].toInt() and 255)
                input.readFully(ByteArray(length))
                // Well-formed fatal TLS handshake_failure, no certificate/trust customization.
                it.getOutputStream().write(byteArrayOf(21, 3, 3, 0, 2, 2, 40)); it.getOutputStream().flush()
                it.shutdownOutput()
            } }
            catch (_: Exception) { }
        }.apply { isDaemon = true; start() }
        try { CioHttpTransport().useTransport { t ->
            val error = assertFailsWith<HttpTransportException> {
                t.execute(HttpRequest.builder(HttpMethod.Get, HttpUrl.parse("https://127.0.0.1:${socketServer.localPort}/")).build())
            }
            assertEquals(TransportFailureKind.Tls, error.kind)
        } } finally { socketServer.close(); fixture.join(1_000) }
    }
    @Test fun headDoesNotReadAdvertisedBodyLength() = runBlocking<Unit> {
        server { _, socket -> socket.headers(200, 50_000) }.use { s -> CioHttpTransport().useTransport { t ->
            val response = t.execute(HttpRequest.builder(HttpMethod.Head, s.url()).readPolicy(BodyReadPolicy.BufferedAtMost(0)).build())
            assertEquals(200, response.statusCode); assertTrue(response.bodyBytes.isEmpty())
        } }
    }
    @Test fun missingRedirectLocationReturnsOriginalResponse() = runBlocking<Unit> {
        server { _, socket -> socket.respond(302, "no location") }.use { s -> CioHttpTransport().useTransport { t ->
            assertEquals("no location", t.execute(request(s)).bodyText()); assertEquals(1, s.requests.size)
        } }
    }
    @Test fun configuredDefaultTimeoutIsUsedForNullRequestTimeout() = runBlocking<Unit> {
        server { _, socket -> socket.headers(200, 100); Thread.sleep(400) }.use { s ->
            CioHttpTransport(defaultTimeoutMillis = 100).useTransport { t ->
                assertEquals(100, assertFailsWith<HttpTimeoutException> { t.execute(request(s)) }.timeoutMillis)
            }
        }
    }
    @Test fun readFailureDoesNotReplayNonIdempotentRequest() = runBlocking<Unit> {
        server { _, socket -> socket.headers(200, 100); socket.getOutputStream().write("short".toByteArray()) }.use { s ->
            CioHttpTransport().useTransport { t ->
                assertFailsWith<HttpTransportException> {
                    t.execute(HttpRequest.builder(HttpMethod.Post, s.url()).body(HttpBody.Text("write-once")).build())
                }
                assertEquals(1, s.requests.size)
            }
        }
    }
    @Test fun switchingProtocolsReturnsStatusButDoesNotExposeStream() = runBlocking<Unit> {
        server { _, socket -> socket.headers(101, 0, listOf("Upgrade" to "websocket")) }.use { s ->
            CioHttpTransport().useTransport { t ->
                val response = t.execute(request(s))
                assertEquals(101, response.statusCode); assertTrue(response.bodyBytes.isEmpty())
            }
        }
    }
}

private suspend fun <T> CioHttpTransport.useTransport(block: suspend (CioHttpTransport) -> T): T =
    try { withTimeout(5.seconds) { block(this@useTransport) } } finally { close() }
private fun request(server: LoopbackServer, path: String = "/") = HttpRequest.builder(HttpMethod.Get, server.url(path)).build()
private fun server(handler: (WireRequest, Socket) -> Unit) = LoopbackServer(handler)
private data class WireRequest(val method: String, val target: String, val headers: List<Pair<String, String>>, val body: String) {
    fun values(name: String) = headers.filter { it.first.equals(name, true) }.map { it.second }
}
private class LoopbackServer(private val handler: (WireRequest, Socket) -> Unit) : AutoCloseable {
    private val server = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newCachedThreadPool { runnable -> Thread(runnable, "mafei-http-fixture").apply { isDaemon = true } }
    private val sockets = CopyOnWriteArrayList<Socket>()
    val requests = CopyOnWriteArrayList<WireRequest>()
    init {
        executor.execute {
            try { while (!server.isClosed) {
                val socket = server.accept(); sockets += socket
                executor.execute {
                    try { socket.use {
                        socket.soTimeout = 4_000
                        val input = BufferedInputStream(socket.getInputStream())
                        val line = input.line().split(' ')
                        val headers = mutableListOf<Pair<String, String>>()
                        while (true) { val header = input.line(); if (header.isEmpty()) break
                            headers += header.substringBefore(':') to header.substringAfter(':').trim()
                        }
                        val size = headers.firstOrNull { it.first.equals("Content-Length", true) }?.second?.toInt() ?: 0
                        val bytes = ByteArray(size); var offset = 0
                        while (offset < size) { val read = input.read(bytes, offset, size - offset); check(read > 0); offset += read }
                        val request = WireRequest(line[0], line[1], headers, bytes.toString(Charsets.UTF_8))
                        requests += request; handler(request, socket)
                    } } catch (_: Exception) { /* Caller cancellation/fixture shutdown closes sockets. */ }
                }
            } } catch (_: Exception) { /* closed accept */ }
        }
    }
    fun url(path: String = "/") = HttpUrl.parse("http://127.0.0.1:${server.localPort}$path")
    override fun close() { server.close(); sockets.forEach { it.close() }; executor.shutdownNow() }
}
private fun BufferedInputStream.line(): String {
    val out = StringBuilder()
    while (true) { val value = read(); check(value >= 0); if (value == 10) return out.toString().removeSuffix("\r")
        out.append(value.toChar()); check(out.length < 32_768)
    }
}
private fun Socket.headers(status: Int, length: Int, headers: List<Pair<String, String>> = emptyList()) {
    val text = buildString { append("HTTP/1.1 $status Test\r\nContent-Length: $length\r\nConnection: close\r\n")
        headers.forEach { (name, value) -> append("$name: $value\r\n") }; append("\r\n") }
    getOutputStream().write(text.toByteArray()); getOutputStream().flush()
}
private fun Socket.respond(status: Int, body: String, headers: List<Pair<String, String>> = emptyList()) {
    val bytes = body.toByteArray(); headers(status, bytes.size, headers)
    getOutputStream().write(bytes); getOutputStream().flush()
}
