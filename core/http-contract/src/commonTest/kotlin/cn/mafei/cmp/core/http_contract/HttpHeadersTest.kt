package cn.mafei.cmp.core.http_contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpHeadersTest {

    @Test
    fun lookupsAreCaseInsensitive() {
        val headers = HttpHeaders {
            set("Content-Type", "application/json")
            set("X-EMBY-TOKEN", "abc")
        }

        assertEquals("application/json", headers.first("content-type"))
        assertEquals("application/json", headers.first("CONTENT-TYPE"))
        assertEquals("abc", headers.first("x-emby-token"))
        assertTrue(headers.contains("Content-Type"))
    }

    @Test
    fun setReplacesAllPreviousValues() {
        val rebuilt = HttpHeaders.Builder()
            .add("Authorization", "old")
            .add("authorization", "stale")
            .set("AUTHORIZATION", "new")
            .build()

        assertEquals(listOf("new"), rebuilt.values("authorization"))
        assertEquals(1, rebuilt.entries.size)
    }

    @Test
    fun addKeepsMultipleValuesInOrder() {
        val headers = HttpHeaders.Builder()
            .add("set-cookie", "a=1")
            .add("SET-COOKIE", "b=2")
            .add("Accept", "text/html")
            .build()

        assertEquals(listOf("a=1", "b=2"), headers.values("Set-Cookie"))
        assertEquals(3, headers.entries.size)
        assertEquals(listOf("a=1", "b=2", "text/html"), headers.entries.map { it.second })
    }

    @Test
    fun removeDeletesAllValuesForName() {
        val headers = HttpHeaders.Builder()
            .add("Cookie", "a=1")
            .add("cookie", "b=2")
            .remove("COOKIE")
            .build()

        assertNull(headers.first("cookie"))
        assertFalse(headers.contains("cookie"))
        assertEquals(0, headers.size)
    }

    @Test
    fun namesAreReportedLowercased() {
        val headers = HttpHeaders.Builder()
            .add("Content-Type", "application/json")
            .add("X-Trace", "1")
            .build()

        assertEquals(setOf("content-type", "x-trace"), headers.names)
    }

    @Test
    fun rejectsBlankHeaderNames() {
        assertFailsWith<IllegalArgumentException> {
            HttpHeaders.Builder().add("  ", "v").build()
        }
        assertFailsWith<IllegalArgumentException> {
            HttpHeaders.Builder().add("Bad\nHeader", "v").build()
        }
    }

    @Test
    fun plusReturnsNewInstance() {
        val base = HttpHeaders.of("a" to "1")
        val extended = base + ("b" to "2")
        assertEquals(1, base.size)
        assertEquals(2, extended.size)
        assertEquals("2", extended.first("b"))
    }

    @Test
    fun emptyHeaders() {
        assertEquals(0, HttpHeaders.EMPTY.size)
        assertNull(HttpHeaders.EMPTY.first("anything"))
    }
}
