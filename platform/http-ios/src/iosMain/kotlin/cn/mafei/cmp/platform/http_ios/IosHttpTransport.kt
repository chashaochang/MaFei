package cn.mafei.cmp.platform.http_ios

import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.platform.http_ktor.createKtorHttpTransport
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.setHTTPShouldHandleCookies

/**
 * Thin Darwin factory. Shared Ktor transport owns deadlines, cancellation,
 * redirect policy and bounded reads. CPF Darwin's KtorNSURLSessionDelegate
 * explicitly refuses native redirects; no custom session/delegate replaces it.
 * System TLS verification is unchanged; credentials only come from the request.
 */
@OptIn(ExperimentalForeignApi::class)
fun createIosHttpTransport(defaultTimeoutMillis: Long = 30_000): HttpTransport =
    DarwinCookieTransport(createKtorHttpTransport(Darwin, defaultTimeoutMillis) {
        configureSession {
            setHTTPCookieStorage(null)
            setHTTPShouldSetCookies(false)
            setURLCredentialStorage(null)
            setURLCache(null)
            setRequestCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
        }
        configureRequest {
            setHTTPShouldHandleCookies(false)
        }
    })

/** NSURLSession folds repeated Set-Cookie lines into one value. Restore the boundaries,
 * retaining raw attributes (including Expires commas) for the shared credential parser.
 */
private class DarwinCookieTransport(private val delegate: HttpTransport) : HttpTransport by delegate {
    override suspend fun execute(request: HttpRequest): HttpResponse {
        val response = delegate.execute(request)
        val headers = HttpHeaders.builder()
        response.headers.forEach { (name, value) ->
            if (name.equals("Set-Cookie", true)) {
                splitDarwinCookies(value).forEach { headers.add(name, it) }
            } else headers.add(name, value)
        }
        return HttpResponse(response.statusCode, headers.build(), response.bodyBytes,
            response.requestUrl, response.effectiveUrl)
    }
}

internal fun splitDarwinCookies(value: String): List<String> {
    val values = mutableListOf<String>()
    var start = 0
    var quoted = false
    var escaped = false
    value.forEachIndexed { index, char ->
        if (escaped) { escaped = false; return@forEachIndexed }
        if (quoted && char == '\\') { escaped = true; return@forEachIndexed }
        if (char == '"') quoted = !quoted
        if (char == ',' && !quoted && cookieBoundary.containsMatchIn(value.substring(index + 1))) {
            values += value.substring(start, index).trim()
            start = index + 1
        }
    }
    values += value.substring(start).trim()
    return values
}

private val cookieBoundary = Regex("^\\s*[!#$%&'*+.^_`|~0-9A-Za-z-]+=")
