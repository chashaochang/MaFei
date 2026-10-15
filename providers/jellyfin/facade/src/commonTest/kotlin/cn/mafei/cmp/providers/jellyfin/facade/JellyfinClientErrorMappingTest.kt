package cn.mafei.cmp.providers.jellyfin.facade
import cn.mafei.cmp.providers.jellyfin.core.InMemoryJellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.core.JellyfinAuthenticationError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinAuthenticationExpiredError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinAuthorizationError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinBusinessError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.core.JellyfinDecodeError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinNotFoundError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinRateLimitedError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerConfig
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSession
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSessionState

import cn.mafei.cmp.core.http_contract.HttpHeaders
import cn.mafei.cmp.core.http_contract.HttpMethod
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import cn.mafei.cmp.providers.jellyfin.testkit.JellyfinFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import cn.mafei.cmp.providers.jellyfin.wire.SystemInfoDto
import kotlinx.serialization.json.Json

class JellyfinClientErrorMappingTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private val base: HttpUrl = HttpUrl.parse("https://jellyfin.test")

    private fun request(path: String = "System/Info"): HttpRequest =
        HttpRequest.builder(HttpMethod.Get, base.newBuilder().addPathSegments(path).build()).build()

    private fun client(vararg session: JellyfinSession): Triple<JellyfinClient, FakeJellyfinTransport, InMemoryJellyfinSessionState> {
        val transport = FakeJellyfinTransport()
        val state = InMemoryJellyfinSessionState(session.firstOrNull())
        val client = JellyfinClient(
            config = JellyfinServerConfig(base, clientName = "MaFei", deviceName = "Test", deviceId = "device-1", appVersion = "2.0.0"),
            transport = transport,
            sessionState = state,
        )
        return Triple(client, transport, state)
    }

    @Test
    fun unauthorizedWithoutSessionMapsToAuthenticationErrorWithProblemDetails() = runTest {
        val (client, transport, _) = client()
        transport.enqueueJson(401, JellyfinFixtures.PROBLEM_DETAILS_401, base)

        val error = assertFailsWith<JellyfinAuthenticationError> {
            client.executeDecoding(request(), SystemInfoDto.serializer())
        }
        assertEquals("Unauthorized", error.problem?.title)
        assertEquals("Invalid token.", error.problem?.detail)
        assertEquals(401, error.problem?.status)
    }

    @Test
    fun unauthorizedWithSessionMapsToAuthenticationExpired() = runTest {
        val (client, transport, state) = client(JellyfinSession(userId = "u1", accessToken = "stale"))
        transport.enqueueJson(401, "{}", base)

        assertFailsWith<JellyfinAuthenticationExpiredError> {
            client.executeDecoding(request(), SystemInfoDto.serializer())
        }
        assertEquals("stale", state.current.value?.accessToken) // expiry itself does not clear; callers decide
    }

    @Test
    fun statusMappingCoversTheFrozenTable() = runTest {
        run {
            val (client, transport, _) = client()
            transport.enqueueJson(403, "", base)
            assertFailsWith<JellyfinAuthorizationError> { client.executeDecoding(request(), SystemInfoDto.serializer()) }
        }
        run {
            val (client, transport, _) = client()
            transport.enqueueJson(404, "", base)
            assertFailsWith<JellyfinNotFoundError> { client.executeDecoding(request(), SystemInfoDto.serializer()) }
        }
        run {
            val (client, transport, _) = client()
            transport.enqueue(
                FakeJellyfinTransport.jsonResponse(
                    429,
                    "",
                    base,
                    headers = HttpHeaders.of("Retry-After" to "30"),
                ),
            )
            val error = assertFailsWith<JellyfinRateLimitedError> { client.executeDecoding(request(), SystemInfoDto.serializer()) }
            assertEquals(30_000L, error.retryAfterMs)
        }
        run {
            val (client, transport, _) = client()
            transport.enqueueJson(503, "", base)
            val error = assertFailsWith<JellyfinServerError> { client.executeDecoding(request(), SystemInfoDto.serializer()) }
            assertEquals(503, error.statusCode)
        }
        run {
            val (client, transport, _) = client()
            transport.enqueueJson(409, """{"title":"Conflict"}""", base)
            val error = assertFailsWith<JellyfinBusinessError> { client.executeDecoding(request(), SystemInfoDto.serializer()) }
            assertEquals(409, error.statusCode)
            assertEquals("Conflict", error.problem?.title)
        }
    }

    @Test
    fun corruptedJsonMapsToDecodeError() = runTest {
        val (client, transport, _) = client()
        transport.enqueueJson(200, "{not json", base)
        assertFailsWith<JellyfinDecodeError> { client.executeDecoding(request(), SystemInfoDto.serializer()) }
    }

    @Test
    fun authorizedRequestsCarryMediaBrowserHeaderAndToken() = runTest {
        val (client, transport, _) = client(JellyfinSession(userId = "u1", accessToken = "tok-1"))
        transport.enqueueJson(200, JellyfinFixtures.SYSTEM_INFO, base)

        client.executeDecoding(request(), SystemInfoDto.serializer())

        val sent = transport.requests.single()
        val header = sent.headers.first("Authorization")!!
        assertTrue(header.startsWith("MediaBrowser "), header)
        assertTrue(header.contains("Client=\"MaFei\""), header)
        assertTrue(header.contains("DeviceId=\"device-1\""), header)
        assertTrue(header.contains("Token=\"tok-1\""), header)
    }

    @Test
    fun anonymousRequestsOmitTokenPart() = runTest {
        val (client, transport, _) = client()
        transport.enqueueJson(200, JellyfinFixtures.SYSTEM_INFO, base)

        client.executeDecoding(request(), SystemInfoDto.serializer())
        val header = transport.requests.single().headers.first("Authorization")!!
        assertNull(header.split(", ").firstOrNull { it.startsWith("Token") }, header)
    }

    @Test
    fun sessionStateTransitions() {
        val state = InMemoryJellyfinSessionState()
        assertNull(state.current.value)
        state.set(JellyfinSession("u1", "t1"))
        assertEquals("u1", state.current.value?.userId)
        state.clear()
        assertNull(state.current.value)
        // session toString never leaks the token
        val rendered = JellyfinSession("u1", "secret-token").toString()
        assertTrue(!rendered.contains("secret-token"))
    }
}
