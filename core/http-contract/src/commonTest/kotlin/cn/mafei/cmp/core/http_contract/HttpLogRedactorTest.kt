package cn.mafei.cmp.core.http_contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpLogRedactorTest {

    private val redactor = DefaultHttpLogRedactor

    @Test
    fun masksSensitiveHeaderValues() {
        for (name in listOf("Authorization", "authorization", "Cookie", "SET-COOKIE", "X-Emby-Token", "Proxy-Authorization", "X-Emby-Authorization", "Trim-MC-token")) {
            val redacted = redactor.redactHeader(name, "super-secret")
            assertFalse(redacted.contains("super-secret"), "header $name leaked")
            assertTrue(redacted.isNotEmpty())
        }
    }

    @Test
    fun keepsInnocuousHeadersIntact() {
        assertEquals("application/json", redactor.redactHeader("Content-Type", "application/json"))
        assertEquals("50", redactor.redactHeader("Content-Length", "50"))
    }

    @Test
    fun redactsTokenQueryParametersInUrls() {
        val url = HttpUrl.parse("https://example.org/Images/42?api_key=abcdef0123456789&width=300")!!
        val rendered = redactor.redactUrl(url)

        assertFalse(rendered.contains("abcdef0123456789"))
        assertTrue(rendered.contains("width=300"))
        assertTrue(rendered.contains("api_key="))
    }

    @Test
    fun keepsOrdinaryQueryParameters() {
        val url = HttpUrl.parse("https://example.org/Users/42/Items?Limit=50&SortBy=Name")!!
        assertEquals(url.toString(), redactor.redactUrl(url))
    }

    @Test
    fun sensitiveHeaderSetIsCaseInsensitive() {
        assertTrue(redactor.isSensitiveHeader("authorization"))
        assertTrue(redactor.isSensitiveHeader("SET-COOKIE"))
        assertFalse(redactor.isSensitiveHeader("accept-language"))
    }

    @Test
    fun jellyfinAuthorizationHeaderIsMasked() {
        val redacted = redactor.redactHeader("X-Emby-Authorization", "MediaBrowser Token=\"secret\"")
        assertFalse(redacted.contains("secret"))
    }
}
