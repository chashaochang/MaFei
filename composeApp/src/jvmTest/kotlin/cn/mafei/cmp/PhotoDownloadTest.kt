package cn.mafei.cmp

import cn.mafei.cmp.core.domain.MediaDownload
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PhotoDownloadTest {
    @Test fun originalStreamsWithProviderHeadersThroughSameOriginRedirect() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val bytes = ByteArray(150_000) { (it % 255).toByte() }
        server.createContext("/start") { exchange ->
            exchange.responseHeaders.add("Location", "/original"); exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/original") { exchange ->
            val authorized = exchange.requestHeaders.getFirst("X-Emby-Token") == "fixture-token"
            exchange.sendResponseHeaders(if (authorized) 200 else 401, if (authorized) bytes.size.toLong() else -1)
            if (authorized) exchange.responseBody.use { it.write(bytes) }; exchange.close()
        }
        server.start()
        try {
            val output = ByteArrayOutputStream()
            writePhoto(MediaDownload("http://127.0.0.1:${server.address.port}/start", "original.jpg",
                mapOf("X-Emby-Token" to "fixture-token")), output)
            assertContentEquals(bytes, output.toByteArray())
        } finally { server.stop(0) }
    }

    @Test fun errorAndCrossOriginRedirectNeverCommitContentOrLeakCredentials() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var reached = false
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "http://localhost:${server.address.port}/foreign")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/foreign") { reached = true; it.sendResponseHeaders(200, -1); it.close() }
        server.createContext("/failure") { it.sendResponseHeaders(503, -1); it.close() }
        server.start()
        try {
            for (path in listOf("redirect", "failure")) {
                val output = ByteArrayOutputStream()
                assertFailsWith<IllegalStateException> {
                    writePhoto(MediaDownload("http://127.0.0.1:${server.address.port}/$path", "photo.jpg",
                        mapOf("Authorization" to "fixture-token")), output)
                }
                assertEquals(0, output.size())
            }
            assertFalse(reached)
        } finally { server.stop(0) }
    }
}
