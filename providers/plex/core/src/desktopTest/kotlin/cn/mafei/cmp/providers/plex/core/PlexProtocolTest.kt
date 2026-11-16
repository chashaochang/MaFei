package cn.mafei.cmp.providers.plex.core

import cn.mafei.cmp.core.http_contract.*
import kotlinx.coroutines.*
import kotlin.test.*

class PlexProtocolTest {
    private class Transport(val respond: suspend (HttpRequest) -> Pair<Int, String>) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            val (status, text) = respond(request)
            return HttpResponse(status, HttpHeaders.EMPTY, text.encodeToByteArray(), request.url, request.url)
        }
        override fun close() = Unit
    }

    @Test fun pinDiscoverySeparatesCloudCredentialsFromSelectedServerAndChecksIdentityFirst() = runBlocking {
        val transport = Transport { request ->
            200 to when (request.url.encodedPath) {
                "/api/v2/pins" -> """{"id":17,"code":"fixture-pin","expiresIn":60}"""
                "/api/v2/pins/17" -> """{"authToken":"cloud-fixture"}"""
                "/api/v2/user" -> """{"id":23,"username":"demo"}"""
                "/api/v2/resources" -> resources
                "/plex/identity", "/plex" -> """{"MediaContainer":{"machineIdentifier":"server-1"}}"""
                else -> error("Unexpected path ${request.url.encodedPath}")
            }
        }
        val api = PlexAuthorizationApi(transport)
        val pin = api.begin("client-fixture")
        assertTrue(pin.browserUrl.startsWith("https://app.plex.tv/auth#?"))
        assertTrue(pin.browserUrl.contains("code=fixture-pin"))
        assertFalse(pin.toString().contains("fixture-pin"))
        assertEquals(HttpMethod.Post, transport.requests.single().method)
        assertEquals("true", transport.requests.single().url.queryParameters.first("strong"))
        val account = api.awaitAccount(pin)
        assertEquals("23", account.userId)
        assertEquals(listOf("server-1"), account.servers.map { it.id })
        assertFalse(account.toString().contains("cloud-fixture"))
        val access = api.connect(account, "server-1")
        assertEquals("server-fixture", access.serverToken)
        assertEquals("https://media.test/plex", access.baseUrl.toEncodedString())
        assertFalse(access.toString().contains("server-fixture"))
        val cloud = transport.requests.filter { it.url.host.endsWith("plex.tv") }
        assertTrue(cloud.take(2).all { it.headers.first("X-Plex-Token") == null })
        assertTrue(cloud.drop(2).all { it.headers.first("X-Plex-Token") == "cloud-fixture" })
        val local = transport.requests.filter { it.url.host == "media.test" }
        assertEquals(listOf(null, "server-fixture"), local.map { it.headers.first("X-Plex-Token") })
        assertTrue(transport.requests.all { it.redirectPolicy == RedirectPolicy.FollowSameOrigin })
        assertTrue(transport.requests.all { it.url.queryParameters.first("X-Plex-Token") == null })
    }

    @Test fun mismatchNeverReceivesTokenAndNextEndpointCanStillConnect() = runBlocking {
        val transport = Transport { request ->
            200 to """{"MediaContainer":{"machineIdentifier":"${if (request.url.host == "wrong.test") "wrong" else "server-1"}"}}"""
        }
        val account = PlexAuthorizedAccount("u", "demo", listOf(
            PlexServerOption("server-1", "Fixture", "server-fixture", listOf(
                HttpUrl.parse("https://wrong.test"), HttpUrl.parse("https://media.test"),
            )),
        ), "client-fixture")
        val access = PlexAuthorizationApi(transport).connect(account, "server-1")
        assertEquals("media.test", access.baseUrl.host)
        assertEquals(listOf(null), transport.requests.filter { it.url.host == "wrong.test" }
            .map { it.headers.first("X-Plex-Token") })
        assertEquals(3, transport.requests.size)
    }

    @Test fun resourcesFallbackUsesCloudTokenAndFiltersUnsafeOrNonServerEntries() = runBlocking {
        val transport = Transport { request ->
            when {
                request.url.host == "clients.plex.tv" -> 503 to "private-server-message"
                request.url.encodedPath.endsWith("/pins/17") -> 200 to """{"authToken":"cloud-fixture"}"""
                request.url.encodedPath.endsWith("/user") -> 200 to """{"id":"u","title":"Demo"}"""
                else -> 200 to resources
            }
        }
        val account = PlexAuthorizationApi(transport).awaitAccount(challenge())
        assertEquals("Demo", account.username)
        assertEquals(listOf("server-1"), account.servers.map { it.id })
        assertEquals(listOf("clients.plex.tv", "plex.tv"),
            transport.requests.filter { it.url.encodedPath.endsWith("/resources") }.map { it.url.host })
        assertTrue(transport.requests.filter { it.url.encodedPath.endsWith("/resources") }
            .all { it.headers.first("X-Plex-Token") == "cloud-fixture" })
    }

    @Test fun pinPollingRetriesTransientFailureButExpiryIsTerminal() = runBlocking {
        var attempts = 0
        val transport = Transport { request ->
            when {
                request.url.encodedPath.endsWith("/pins/17") -> if (++attempts == 1) 429 to "{}"
                    else 200 to """{"authToken":"cloud-fixture"}"""
                request.url.encodedPath.endsWith("/user") -> 200 to """{"id":"u"}"""
                else -> 200 to "[]"
            }
        }
        assertTrue(PlexAuthorizationApi(transport).awaitAccount(challenge()).servers.isEmpty())
        assertEquals(2, attempts)
        val expired = Transport { 404 to "private-pin-message" }
        val failure = assertFailsWith<PlexFailure> { PlexAuthorizationApi(expired).awaitAccount(challenge()) }
        assertEquals(PlexFailureKind.Expired, failure.kind)
        assertNull(failure.cause)
        assertFalse(failure.toString().contains("private-pin-message"))
        assertEquals(1, expired.requests.size)
        assertEquals(PlexFailureKind.Expired, assertFailsWith<PlexFailure> {
            PlexAuthorizationApi(expired).awaitAccount(challenge(0))
        }.kind)
        assertEquals(1, expired.requests.size)
    }

    @Test fun cancellingPollingDoesNotDiscoverOrConnectServers() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val transport = Transport {
            entered.complete(Unit)
            awaitCancellation()
        }
        val job = launch { PlexAuthorizationApi(transport).awaitAccount(challenge()) }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(1, transport.requests.size)
    }

    @Test fun resourcesStayInsideOriginAndReverseProxyBaseAndNeverCarryTokens() {
        val client = PlexClient(HttpUrl.parse("https://media.test/plex"), "fixture", "server-fixture", Transport { 200 to "{}" })
        assertEquals("https://media.test/plex/library/parts/7/a%20b.mkv?download=1",
            client.resourceUrl("/library/parts/7/a%20b.mkv?download=1")?.toEncodedString())
        assertEquals("/plex/library/metadata/7/thumb", client.resourceUrl("/plex/library/metadata/7/thumb")?.encodedPath)
        assertEquals("/plex/library/metadata/7/thumb",
            client.resourceUrl("https://media.test/plex/library/metadata/7/thumb")?.encodedPath)
        for (key in listOf(
            "//remote.test/file", "https://remote.test/file", "https://media.test/outside",
            "http://media.test/plex/file", "/library/../private", "/library/%2e%2e/private",
            "/library/%2fprivate", "/library/%5cprivate", "/file?x-plex-token=secret",
            "/file#secret", "/file\n", "/file ", "file:///private/test", "C:\\private\\test",
        )) assertNull(client.resourceUrl(key), key)
        assertFalse(client.toString().contains("server-fixture"))
    }

    private fun challenge(lifetime: Long = 60_000) =
        PlexAuthorization("https://app.plex.tv/auth#?code=fixture", 17, "fixture", lifetime)

    private val resources = """[
        {"provides":"server","clientIdentifier":"server-1","name":"Fixture","accessToken":"server-fixture",
         "connections":[{"protocol":"https","local":true,"uri":"https://media.test/plex"},
                        {"uri":"https://media.test/%2fprivate"},{"uri":"https://media.test?X-Plex-Token=unsafe"}]},
        {"provides":"player","clientIdentifier":"player","accessToken":"player-fixture","connections":[{"uri":"https://player.test"}]},
        {"provides":"server","clientIdentifier":"missing-token","connections":[{"uri":"https://media.test"}]}
    ]"""
}
