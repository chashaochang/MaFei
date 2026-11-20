package cn.mafei.cmp.player.runtime_macos

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Loopback-only, streaming adapter for AVPlayer's lack of a public HTTP-header option. */
internal class AuthorizedMediaProxy : AutoCloseable {
    private data class Source(val url: URI, val headers: Map<String, String>)
    private val sources = ConcurrentHashMap<String, Source>()
    private val workers = Executors.newCachedThreadPool { Thread(it, "mafei-media-proxy").apply { isDaemon = true } }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = workers; createContext("/", ::serve); start()
    }
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(15)).build()

    fun register(url: String, headers: Map<String, String>): Pair<String, String> {
        val source = URI(url)
        require(source.scheme in setOf("http", "https") && source.host != null)
        val id = UUID.randomUUID().toString()
        sources[id] = Source(source, headers.toMap())
        return id to localUrl(id, source)
    }
    fun retire(id: String) { sources.remove(id) }
    private fun localUrl(id: String, url: URI) = "http://127.0.0.1:${server.address.port}/$id${url.rawPath.ifEmpty { "/" }}" +
        (url.rawQuery?.let { "?$it" } ?: "")

    private fun serve(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.rawPath
            val id = path.drop(1).substringBefore('/')
            val source = sources[id]
            if (source == null || exchange.requestMethod !in setOf("GET", "HEAD")) {
                exchange.sendResponseHeaders(404, -1); return
            }
            val origin = "${source.url.scheme}://${source.url.rawAuthority}"
            var target = URI(origin + path.removePrefix("/$id") + (exchange.requestURI.rawQuery?.let { "?$it" } ?: ""))
            var response: HttpResponse<java.io.InputStream>? = null
            for (hop in 0..4) {
                val request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(45))
                    .method(exchange.requestMethod, HttpRequest.BodyPublishers.noBody())
                source.headers.filterKeys { it.lowercase() !in setOf("host", "connection", "content-length") }
                    .forEach { (key, value) -> request.header(key, value) }
                for (header in listOf("Range", "If-Range", "If-None-Match", "If-Modified-Since")) {
                    exchange.requestHeaders.getFirst(header)?.let { request.header(header, it) }
                }
                val received = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream())
                if (received.statusCode() in setOf(301, 302, 303, 307, 308)) {
                    val location = received.headers().firstValue("Location").orElse(null)
                    received.body().close()
                    require(location != null && hop < 4)
                    val next = target.resolve(location)
                    require(sameOrigin(source.url, next)) { "Authenticated redirect rejected" }
                    target = next
                } else { response = received; break }
            }
            val received = requireNotNull(response)
            received.body().use { input ->
                if (sources[id] !== source) { exchange.sendResponseHeaders(410, -1); return }
                val contentType = received.headers().firstValue("Content-Type").orElse("")
                for (header in listOf("Content-Type", "Content-Range", "Accept-Ranges", "ETag", "Last-Modified")) {
                    received.headers().firstValue(header).ifPresent { exchange.responseHeaders.set(header, it) }
                }
                if (exchange.requestMethod == "HEAD") {
                    received.headers().firstValue("Content-Length").ifPresent { exchange.responseHeaders.set("Content-Length", it) }
                    exchange.sendResponseHeaders(received.statusCode(), -1); return
                }
                val playlist = contentType.lowercase().contains("mpegurl") || target.path.endsWith(".m3u8")
                if (playlist && received.statusCode() in 200..299) {
                    val bytes = input.readNBytes(512 * 1024 + 1)
                    require(bytes.size <= 512 * 1024) { "Playlist too large" }
                    val text = rewriteHls(bytes.toString(Charsets.UTF_8)) { relative ->
                        val resolved = target.resolve(relative)
                        require(sameOrigin(source.url, resolved)) { "Authenticated HLS origin rejected" }
                        localUrl(id, resolved)
                    }.toByteArray()
                    exchange.sendResponseHeaders(received.statusCode(), text.size.toLong())
                    exchange.responseBody.use { it.write(text) }
                } else {
                    val length = received.headers().firstValueAsLong("Content-Length").orElse(0)
                    exchange.sendResponseHeaders(received.statusCode(), if (length > 0) length else 0)
                    exchange.responseBody.use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (sources[id] === source) {
                            val count = input.read(buffer); if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            runCatching { exchange.sendResponseHeaders(502, -1) }
        } finally { exchange.close() }
    }

    override fun close() { sources.clear(); server.stop(0); workers.shutdownNow() }
    internal companion object {
        fun sameOrigin(a: URI, b: URI) = a.scheme == b.scheme && a.host.equals(b.host, true) &&
            (if (a.port >= 0) a.port else if (a.scheme == "https") 443 else 80) ==
            (if (b.port >= 0) b.port else if (b.scheme == "https") 443 else 80)
        fun rewriteHls(text: String, resolve: (String) -> String): String = text.removePrefix("\uFEFF").lineSequence().joinToString("\n") { line ->
            when {
                line.startsWith('#') -> Regex("URI=\"([^\"]+)\"").replace(line) { "URI=\"${resolve(it.groupValues[1])}\"" }
                line.isBlank() -> line
                else -> resolve(line.trim())
            }
        }
    }
}
