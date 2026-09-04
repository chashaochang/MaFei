package cn.mafei.cmp.core.http_contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpUrlTest {

    @Test
    fun parsesFullUrl() {
        val url = HttpUrl.parse("https://media.example.org:8443/jellyfin/Users/42/Items?Limit=50&SortBy=Name#top")

        assertEquals("https", url.scheme)
        assertEquals("media.example.org", url.host)
        assertEquals(8443, url.port)
        assertEquals("/jellyfin/Users/42/Items", url.encodedPath)
        assertEquals("50", url.queryParameters.first("Limit"))
        assertEquals("Name", url.queryParameters.first("SortBy"))
        assertEquals("top", url.fragment)
        assertTrue(url.isHttps)
    }

    @Test
    fun appliesDefaultPorts() {
        assertEquals(443, HttpUrl.parse("https://example.org").port)
        assertEquals(80, HttpUrl.parse("http://example.org").port)
        assertFalse(HttpUrl.parse("http://example.org").isHttps)
    }

    @Test
    fun defaultsEmptyPathToRoot() {
        assertEquals("/", HttpUrl.parse("http://example.org").encodedPath)
        assertEquals("http://example.org/", HttpUrl.parse("http://example.org").toString())
    }

    @Test
    fun canonicalToStringOmitsDefaultPortAndKeepsExplicitPort() {
        assertEquals("https://example.org/a", HttpUrl.parse("https://example.org:443/a").toString())
        assertEquals("http://example.org:8080/a", HttpUrl.parse("http://example.org:8080/a").toString())
    }

    @Test
    fun normalizesSchemeAndHostCase() {
        val url = HttpUrl.parse("HTTP://Media.Example.ORG/Path")
        assertEquals("http", url.scheme)
        assertEquals("media.example.org", url.host)
    }

    @Test
    fun parsesIpv6Host() {
        val url = HttpUrl.parse("http://[::1]:8096/emby")
        assertEquals("::1", url.host)
        assertEquals(8096, url.port)
        assertEquals("http://[::1]:8096/emby", url.toString())
    }

    @Test
    fun parsesIpv6HostWithoutPort() {
        val url = HttpUrl.parse("http://[::1]/emby")
        assertEquals("::1", url.host)
        assertEquals(80, url.port)
        assertEquals("http://[::1]/emby", url.toString())
    }

    @Test
    fun rejectsBareIpv6AndMalformedAuthorities() {
        // bare IPv6 without brackets is ambiguous, never a valid authority
        assertNull(HttpUrl.parseOrNull("http://::1"))
        assertNull(HttpUrl.parseOrNull("http://[::1"))
        assertNull(HttpUrl.parseOrNull("http://[::1]junk"))
        assertNull(HttpUrl.parseOrNull("http://[::1]:"))
        assertNull(HttpUrl.parseOrNull("http://:8080"))
        assertNull(HttpUrl.parseOrNull("http://host:"))
        assertNull(HttpUrl.parseOrNull("http://host:notaport"))
        assertNull(HttpUrl.parseOrNull("http://host:99999"))
        assertNull(HttpUrl.parseOrNull("http://user@example.org/"))
        assertNull(HttpUrl.parseOrNull("http://h/a%zz"))
        assertNull(HttpUrl.parseOrNull("http://h/a%2"))
        // valid escapes pass:
        assertEquals("/%E4%B8%AD", HttpUrl.parseOrNull("http://h/%E4%B8%AD")!!.encodedPath)
    }

    @Test
    fun trailingSlashIsPreserved() {
        assertEquals("/a/", HttpUrl.parse("http://h/a/").encodedPath)
        assertEquals("http://h/a/", HttpUrl.parse("http://h/a/").toString())
        assertEquals("/a//b/", HttpUrl.parse("http://h/a//b/").encodedPath)
    }

    @Test
    fun rejectsHostsWithReservedWhitespaceOrControlCharacters() {
        assertNull(HttpUrl.parseOrNull("http://ho st.org"))
        assertNull(HttpUrl.parseOrNull("http://host\texample.org"))
        assertNull(HttpUrl.parseOrNull("http://host\u0007"))
        // path and query characters must not appear in the host via the builder
        val builder = HttpUrl.Builder(scheme = "http", host = "ok")
        for (bad in listOf("a/b", "a?b", "a#b", "a b", "a\tb")) {
            assertFailsWith<IllegalArgumentException>("host '$bad' must be rejected") { builder.host(bad) }
        }
    }

    @Test
    fun queryParametersPlusRejectsEmptyNames() {
        val query = QueryParameters.of("ok" to "1")
        assertFailsWith<IllegalArgumentException> { query + ("" to "x") }
        // existing collection untouched
        assertEquals(1, query.size)
    }

    @Test
    fun builderRejectsEmptyHostAndQueryNames() {
        assertFailsWith<IllegalArgumentException> { HttpUrl.Builder(scheme = "http", host = "") }
        val builder = HttpUrl.Builder(scheme = "http", host = "ok")
        assertFailsWith<IllegalArgumentException> { builder.host("") }
        assertFailsWith<IllegalArgumentException> { builder.addQueryParameter("", "x") }
        assertFailsWith<IllegalArgumentException> { builder.setQueryParameter("", "x") }
        assertEquals("http://ok/", builder.build().toString())
    }

    @Test
    fun decodesMixedEncodedAndRawUtf8QueryValues() {
        val url = HttpUrl.parse("http://h/search?q=%E4%B8%AD%E6%96%87%20mix")
        assertEquals("中文 mix", url.queryParameters.first("q"))

        val rendered = HttpUrl.Builder(scheme = "http", host = "h")
            .encodedPath("/search")
            .addQueryParameter("q", "中文 mix")
            .build()
        assertEquals("http://h/search?q=%E4%B8%AD%E6%96%87%20mix", rendered.toString())
    }

    @Test
    fun queryParametersKeepMultipleValuesInOrder() {
        val url = HttpUrl.parse("http://h/p?tag=a&tag=b&other=1&tag=c")
        assertEquals(listOf("a", "b", "c"), url.queryParameters.values("tag"))
        assertEquals("a", url.queryParameters.first("tag"))
        assertEquals(setOf("tag", "other"), url.queryParameters.names)
        assertEquals(4, url.queryParameters.size)
    }

    @Test
    fun queryValuesAreDecodedOnAccess() {
        val url = HttpUrl.parse("http://h/search?q=a%20b%2Bc&plus=x+y")
        assertEquals("a b+c", url.queryParameters.first("q"))
        assertEquals("x y", url.queryParameters.first("plus"))
    }

    @Test
    fun renderedUrlEncodesQueryValues() {
        val url = HttpUrl.Builder(scheme = "http", host = "h")
            .encodedPath("/search")
            .addQueryParameter("q", "bat man&more")
            .addQueryParameter("tag", "ä")
            .build()

        assertEquals("http://h/search?q=bat%20man%26more&tag=%C3%A4", url.toString())
    }

    @Test
    fun querySetReplacesAllValues() {
        val query = QueryParameters.of("tag" to "a", "tag" to "b", "keep" to "1")
        val updated = query.plus("tag" to "z") // plus appends
        assertEquals(listOf("a", "b", "z"), updated.values("tag"))

        val replaced = QueryParameters.builder()
            .add("tag", "a")
            .add("tag", "b")
            .set("tag", "only")
            .build()
        assertEquals(listOf("only"), replaced.values("tag"))
        assertTrue(replaced.names.contains("tag"))
    }

    @Test
    fun builderEncodesPathSegments() {
        val url = HttpUrl.Builder(scheme = "https", host = "example.org")
            .addPathSegment("Users")
            .addPathSegment("a b/c")
            .build()
        assertEquals("/Users/a%20b%2Fc", url.encodedPath)
        assertEquals(listOf("Users", "a b/c"), url.pathSegments)
    }

    @Test
    fun builderAddPathSegmentsSplitsOnSlash() {
        val url = HttpUrl.Builder(scheme = "https", host = "example.org")
            .addPathSegments("Users/abc/Items")
            .build()
        assertEquals("/Users/abc/Items", url.encodedPath)
    }

    @Test
    fun rejectsMalformedUrls() {
        assertFailsWith<IllegalArgumentException> { HttpUrl.parse("not a url") }
        assertFailsWith<IllegalArgumentException> { HttpUrl.parse("ftp://example.org") }
        assertFailsWith<IllegalArgumentException> { HttpUrl.parse("http://") }
        assertFailsWith<IllegalArgumentException> { HttpUrl.parse("://example.org") }
        assertFailsWith<IllegalArgumentException> { HttpUrl.parse("http://user:pass@example.org/") }
    }

    @Test
    fun parseOrNullReturnsNullInsteadOfThrowing() {
        assertNull(HttpUrl.parseOrNull("::::"))
        assertEquals("/", HttpUrl.parseOrNull("https://ok.org")?.encodedPath)
    }

    @Test
    fun urlsAreStructurallyEqual() {
        assertEquals(
            HttpUrl.parse("http://example.org/a?b=1"),
            HttpUrl.parse("http://example.org/a?b=1"),
        )
        assertFalse(
            HttpUrl.parse("http://example.org/a?b=1") == HttpUrl.parse("http://example.org/a?b=2"),
        )
    }

    @Test
    fun newBuilderProducesEquivalentUrl() {
        val original = HttpUrl.parse("https://example.org:8443/x?y=1#f")
        val rebuilt = original.newBuilder()
            .addQueryParameter("z", "2")
            .build()
        assertEquals("https://example.org:8443/x?y=1&z=2#f", rebuilt.toString())
    }
}
