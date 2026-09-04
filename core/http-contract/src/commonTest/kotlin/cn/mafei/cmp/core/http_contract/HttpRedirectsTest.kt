package cn.mafei.cmp.core.http_contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpRedirectsTest {

    private val startUrl: HttpUrl = HttpUrl.parse("https://media.example.org/jellyfin/Items?Limit=10")

    private fun request(
        url: HttpUrl = startUrl,
        method: HttpMethod = HttpMethod.Get,
        policy: RedirectPolicy = RedirectPolicy.Follow,
        headers: List<Pair<String, String>> = emptyList(),
    ): HttpRequest {
        val builder = HttpRequest.builder(method, url).redirectPolicy(policy)
        headers.forEach { (name, value) -> builder.setHeader(name, value) }
        builder.body(if (method == HttpMethod.Post) HttpBody.Text("payload") else HttpBody.Empty)
        return builder.build()
    }

    private fun redirect(
        statusCode: Int,
        location: String?,
        from: HttpUrl = startUrl,
    ): HttpResponse = HttpResponse(
        statusCode = statusCode,
        headers = HttpHeaders.Builder().apply { location?.let { add("Location", it) } }.build(),
        bodyBytes = ByteArray(0),
        requestUrl = from,
        effectiveUrl = from,
    )

    @Test
    fun status303TurnsRequestIntoGetAndClearsBody() {
        val post = request(method = HttpMethod.Post, headers = listOf("Content-Type" to "application/json"))
        val next = HttpRedirects.nextRequest(post, redirect(303, "/session"), hopsCompleted = 0)!!

        assertEquals(HttpMethod.Get, next.method)
        assertEquals(HttpUrl.parse("https://media.example.org/session"), next.url)
        assertEquals(HttpBody.Empty, next.body)
        assertNull(next.headers.first("content-type"))
        assertNull(next.headers.first("content-length"))
    }

    @Test
    fun status301_302_307_308PreserveMethodAndBody() {
        for (status in listOf(301, 302, 307, 308)) {
            val post = request(method = HttpMethod.Post, headers = listOf("Content-Type" to "application/json"))
            val next = HttpRedirects.nextRequest(post, redirect(status, "/session"), hopsCompleted = 0)!!

            assertEquals(HttpMethod.Post, next.method, "status $status must preserve the method")
            assertEquals("payload", (next.body as HttpBody.Text).text, "status $status must preserve the body")
            assertEquals("application/json", next.headers.first("content-type"))
        }
    }

    @Test
    fun rootRelativeAndAbsoluteLocationsResolve() {
        val previous = request()

        val rootRelative = HttpRedirects.nextRequest(previous, redirect(302, "/next/path"), 0)!!
        assertEquals("https://media.example.org/next/path", rootRelative.url.toString())

        val absolute = HttpRedirects.nextRequest(previous, redirect(302, "https://other.example.org/x"), 0)!!
        assertEquals("https://other.example.org/x", absolute.url.toString())

        val protocolRelative = HttpRedirects.nextRequest(previous, redirect(302, "//mirror.example.org/y"), 0)!!
        assertEquals("https://mirror.example.org/y", protocolRelative.url.toString())

        val relativePath = HttpRedirects.nextRequest(previous, redirect(302, "Items/42"), 0)!!
        assertEquals("https://media.example.org/jellyfin/Items/42", relativePath.url.toString())

        val parentPath = HttpRedirects.nextRequest(previous, redirect(302, "up/here"), 0)!!
        assertEquals("https://media.example.org/jellyfin/up/here", parentPath.url.toString())

        val withQuery = HttpRedirects.nextRequest(previous, redirect(302, "/search?q=term%20x"), 0)!!
        assertEquals("term x", withQuery.url.queryParameters.first("q"))
    }

    @Test
    fun redirectLocationsCarryingUserInfoAreRejected() {
        val previous = request()
        assertNull(HttpRedirects.nextRequest(previous, redirect(302, "https://user:pass@media.example.org/"), 0))
    }

    @Test
    fun missingOrGarbageLocationEndsTheChain() {
        val previous = request()
        assertNull(HttpRedirects.nextRequest(previous, redirect(302, null), 0))
        assertNull(HttpRedirects.nextRequest(previous, redirect(302, "  "), 0))
        // note: "::::" would be a *valid* relative path reference and is followed
        assertNull(HttpRedirects.nextRequest(previous, redirect(302, "https://[broken"), 0))
    }

    @Test
    fun unrecognized3xxStatusesAreNotFollowed() {
        val previous = request()
        assertNull(HttpRedirects.nextRequest(previous, redirect(304, "/x"), 0))
        assertNull(HttpRedirects.nextRequest(previous, redirect(200, "/x"), 0))
    }

    @Test
    fun httpsToHttpDowngradeIsRejected() {
        val previous = request()
        val failure = assertFailsWith<HttpInsecureRedirectException> {
            HttpRedirects.nextRequest(previous, redirect(302, "http://media.example.org/plain"), 0)
        }
        assertEquals("https", failure.from.scheme)
        assertEquals("http", failure.to.scheme)
    }

    @Test
    fun crossOriginRedirectStripsCredentials() {
        val previous = request(headers = listOf("Authorization" to "MediaBrowser token", "Cookie" to "mc=1", "Accept" to "application/json"))
        val next = HttpRedirects.nextRequest(previous, redirect(302, "https://mirror.example.org/assets"), 0)!!

        assertNull(next.headers.first("authorization"))
        assertNull(next.headers.first("cookie"))
        assertEquals("application/json", next.headers.first("accept"))
    }

    @Test
    fun crossOriginRedirectStripsEveryCredentialHeader() {
        val credentials = HttpCredentialHeaders.ALL.map { it to "leak-me" }
        val previous = request(headers = credentials)
        val next = HttpRedirects.nextRequest(previous, redirect(302, "https://mirror.example.org/assets"), 0)!!

        for (name in HttpCredentialHeaders.ALL) {
            assertNull(next.headers.first(name), "credential header '$name' survived a cross-origin redirect")
        }
    }

    @Test
    fun credentialHeaderSetCoversBothProtocols() {
        for (expected in listOf("authorization", "proxy-authorization", "cookie", "x-emby-authorization", "x-emby-token", "trim-mc-token")) {
            assertTrue(
                HttpCredentialHeaders.ALL.contains(expected),
                "credential header set must contain '$expected'",
            )
        }
        assertTrue(HttpCredentialHeaders.isCredentialHeader("X-Emby-Authorization"))
        assertFalse(HttpCredentialHeaders.isCredentialHeader("Accept"))
    }

    @Test
    fun dotSegmentAndQueryOnlyLocationsResolve() {
        val previous = request()

        // "./sibling" resolves inside the current directory
        assertEquals(
            "https://media.example.org/jellyfin/sibling",
            HttpRedirects.nextRequest(previous, redirect(302, "./sibling"), 0)!!.url.toString(),
        )
        // "../up" pops one directory
        assertEquals(
            "https://media.example.org/up",
            HttpRedirects.nextRequest(previous, redirect(302, "../up"), 0)!!.url.toString(),
        )
        // popping beyond the root clamps at the root
        assertEquals(
            "https://media.example.org/beyond",
            HttpRedirects.nextRequest(previous, redirect(302, "../../beyond"), 0)!!.url.toString(),
        )
        // "/a/." and "/a/.." end as directories
        assertEquals(
            "https://media.example.org/jellyfin/",
            HttpRedirects.nextRequest(previous, redirect(302, "/jellyfin/."), 0)!!.url.toString(),
        )
        assertEquals(
            "https://media.example.org/",
            HttpRedirects.nextRequest(previous, redirect(302, "/jellyfin/.."), 0)!!.url.toString(),
        )
    }

    @Test
    fun queryOnlyAndFragmentOnlyLocationsKeepThePath() {
        // query-only reference replaces the query, keeps the path
        val queryOnly = HttpRedirects.nextRequest(previous = request(), response = redirect(302, "?page=2"), hopsCompleted = 0)!!
        assertEquals("https://media.example.org/jellyfin/Items?page=2", queryOnly.url.toString())
        assertNull(queryOnly.url.queryParameters.first("Limit"))

        // fragment-only reference keeps path and query, replaces the fragment
        val fragmentOnly = HttpRedirects.nextRequest(request(), redirect(302, "#section"), 0)!!
        assertEquals("https://media.example.org/jellyfin/Items?Limit=10#section", fragmentOnly.url.toString())
    }

    @Test
    fun sameOriginRedirectKeepsCredentials() {
        val previous = request(headers = listOf("Authorization" to "MediaBrowser token", "Cookie" to "mc=1"))
        val next = HttpRedirects.nextRequest(previous, redirect(302, "/same/origin"), 0)!!

        assertEquals("MediaBrowser token", next.headers.first("authorization"))
        assertEquals("mc=1", next.headers.first("cookie"))
    }

    @Test
    fun policiesLimitFollowing() {
        val previousDoNotFollow = request(policy = RedirectPolicy.DoNotFollow)
        assertNull(HttpRedirects.nextRequest(previousDoNotFollow, redirect(302, "/x"), 0))

        val previousSameHost = request(policy = RedirectPolicy.FollowSameOrigin)
        assertNull(HttpRedirects.nextRequest(previousSameHost, redirect(302, "https://other.example.org/x"), 0))
        assertEquals(
            "https://media.example.org/stay",
            HttpRedirects.nextRequest(previousSameHost, redirect(302, "/stay"), 0)!!.url.toString(),
        )
    }

    @Test
    fun hopBudgetIsEnforcedForTheWholeChain() {
        val previous = request()
        assertFailsWith<HttpRedirectLimitExceededException> {
            HttpRedirects.nextRequest(previous, redirect(302, "/hop5"), hopsCompleted = 4)
        }.let { failure ->
            assertEquals(4, failure.maxRedirects)
            assertEquals(302, failure.lastStatusCode)
        }
        // one hop before the limit is fine
        assertEquals("/hop4", HttpRedirects.nextRequest(previous, redirect(302, "/hop4"), hopsCompleted = 3)!!.url.encodedPath)
    }

    @Test
    fun customBudgetCanBeTightened() {
        val previous = request()
        assertFailsWith<HttpRedirectLimitExceededException> {
            HttpRedirects.nextRequest(previous, redirect(302, "/hop3"), hopsCompleted = 2, maxRedirects = 2)
        }
    }

    @Test
    fun previousPoliciesAndAttributesSurviveTheHop() {
        val previous = HttpRequest.builder(HttpMethod.Get, startUrl)
            .timeoutMillis(9_000)
            .readPolicy(BodyReadPolicy.Discarded)
            .attribute("trace", "abc")
            .build()
        val next = HttpRedirects.nextRequest(previous, redirect(302, "/x"), 0)!!

        assertEquals(9_000L, next.timeoutMillis)
        assertEquals(BodyReadPolicy.Discarded, next.bodyReadPolicy)
        assertEquals("abc", next.attributes["trace"])
        assertEquals(RedirectPolicy.Follow, next.redirectPolicy)
    }
}
