package cn.mafei.cmp

import cn.mafei.cmp.core.domain.MediaDownload
import cn.mafei.cmp.core.http_contract.HttpUrl
import kotlinx.coroutines.ensureActive
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Streams a provider-issued original to a platform destination without buffering the whole photo. */
internal suspend fun writePhoto(download: MediaDownload, output: OutputStream) {
    val original = HttpUrl.parse(download.url)
    var target = original
    repeat(5) { attempt ->
        coroutineContext.ensureActive()
        val connection = URL(target.toString()).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        download.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        try {
            val status = connection.responseCode
            if (status in listOf(301, 302, 303, 307, 308)) {
                check(attempt < 4) { "Photo redirect limit" }
                val next = HttpUrl.parse(URL(URL(target.toString()), connection.getHeaderField("Location") ?: error("Missing location")).toString())
                check(next.scheme == original.scheme && next.host == original.host && next.port == original.port) {
                    "Photo redirected away from its provider"
                }
                target = next
                return@repeat
            }
            check(status in 200..299) { "Photo download failed" }
            var bytes = 0L
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    bytes += count
                    check(bytes <= 256L * 1024 * 1024) { "Photo exceeds download limit" }
                    output.write(buffer, 0, count)
                }
            }
            check(bytes > 0) { "Empty photo" }
            return
        } finally { connection.disconnect() }
    }
}
