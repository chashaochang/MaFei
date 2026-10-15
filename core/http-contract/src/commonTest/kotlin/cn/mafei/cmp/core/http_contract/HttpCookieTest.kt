package cn.mafei.cmp.core.http_contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpCookieTest {

    @Test
    fun parsesNameValueOnly() {
        val cookie = Cookie.parseSetCookie("session=abc123", nowEpochMs = 1_000L)!!
        assertEquals("session", cookie.name)
        assertEquals("abc123", cookie.value)
        assertNull(cookie.path)
        assertNull(cookie.domain)
        assertFalse(cookie.secure)
        assertFalse(cookie.httpOnly)
        assertNull(cookie.sameSite)
        assertFalse(cookie.isExpired(nowEpochMs = 2_000L))
    }

    @Test
    fun parsesAllAttributes() {
        val cookie = Cookie.parseSetCookie(
            "SID=31d4d96e407aad42; Path=/; Domain=.example.org; Secure; HttpOnly; SameSite=Lax",
            nowEpochMs = 1_000L,
        )!!

        assertEquals("SID", cookie.name)
        assertEquals("31d4d96e407aad42", cookie.value)
        assertEquals("/", cookie.path)
        assertEquals("example.org", cookie.domain)
        assertTrue(cookie.secure)
        assertTrue(cookie.httpOnly)
        assertEquals(CookieSameSite.Lax, cookie.sameSite)
    }

    @Test
    fun maxAgeResolvesRelativeToNow() {
        val cookie = Cookie.parseSetCookie("t=1; Max-Age=60", nowEpochMs = 100_000L)!!
        assertEquals(60L, cookie.maxAgeSeconds)
        assertEquals(160_000L, cookie.effectiveExpiresEpochMs)
        assertFalse(cookie.isExpired(159_999L))
        assertTrue(cookie.isExpired(160_000L))
    }

    @Test
    fun maxAgeWinsOverExpires() {
        // Expires far in the future, Max-Age expires immediately.
        val cookie = Cookie.parseSetCookie(
            "t=1; Expires=Wed, 21 Oct 2199 07:28:00 GMT; Max-Age=0",
            nowEpochMs = 1_000_000L,
        )!!
        assertTrue(cookie.isExpired(1_000_000L))
    }

    @Test
    fun parsesHttpDateExpires() {
        val cookie = Cookie.parseSetCookie(
            "t=1; Expires=Wed, 21 Oct 2199 07:28:00 GMT",
            nowEpochMs = 1_000L,
        )!!
        // 2199-10-21T07:28:00Z
        assertEquals(7_251_924_480_000L, cookie.expiresEpochMs)
        assertFalse(cookie.isExpired(1_000L))
    }

    @Test
    fun unparsableExpiresIsIgnored() {
        val cookie = Cookie.parseSetCookie("t=1; Expires=yesterday", nowEpochMs = 1_000L)!!
        assertNull(cookie.expiresEpochMs)
        assertFalse(cookie.isExpired(9_999_999_999_999L))
    }

    @Test
    fun quotedValuesAreUnquoted() {
        val cookie = Cookie.parseSetCookie("token=\"abc def\"", nowEpochMs = 0L)!!
        assertEquals("abc def", cookie.value)
    }

    @Test
    fun malformedSetCookieReturnsNull() {
        assertNull(Cookie.parseSetCookie("nonsense", nowEpochMs = 0L))
        assertNull(Cookie.parseSetCookie("", nowEpochMs = 0L))
        assertNull(Cookie.parseSetCookie("; Path=/", nowEpochMs = 0L))
        assertNull(Cookie.parseSetCookie("=value", nowEpochMs = 0L))
    }

    @Test
    fun whitespaceIsTolerated() {
        val cookie = Cookie.parseSetCookie("  name = value ;  Path = /x ;  HttpOnly", nowEpochMs = 0L)!!
        assertEquals("name", cookie.name)
        assertEquals("value", cookie.value)
        assertEquals("/x", cookie.path)
        assertTrue(cookie.httpOnly)
    }

    @Test
    fun headerValuesProduceMultipleCookies() {
        val headers = HttpHeaders.Builder()
            .add("Set-Cookie", "a=1; Path=/")
            .add("Set-Cookie", "b=2; Path=/x; Secure")
            .build()

        val cookies = headers.values("set-cookie").mapNotNull { Cookie.parseSetCookie(it, nowEpochMs = 0L) }
        assertEquals(2, cookies.size)
        assertEquals(listOf("a", "b"), cookies.map { it.name })
        assertTrue(cookies[1].secure)
    }

    @Test
    fun acceptsNegativeMaxAgeAsExpired() {
        val cookie = Cookie.parseSetCookie("gone=1; Max-Age=-1", nowEpochMs = 10_000L)!!
        assertEquals(-1L, cookie.maxAgeSeconds)
        assertTrue(cookie.isExpired(10_000L))
    }
}
