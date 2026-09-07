package cn.mafei.cmp.providers.feiniu.facade

import cn.mafei.cmp.core.http_contract.HttpBody
import cn.mafei.cmp.core.http_contract.HttpHeaders
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.http_contract.HttpMethod
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.feiniu.auth.FeiniuAuthApi
import cn.mafei.cmp.providers.feiniu.catalog.FeiniuCatalogApi
import cn.mafei.cmp.providers.feiniu.connection.FeiniuConnectionApi
import cn.mafei.cmp.providers.feiniu.core.FeiniuApiCall
import cn.mafei.cmp.providers.feiniu.core.FeiniuAuthenticationError
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.FeiniuDecodeError
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostCandidate
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuClient
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoutePreference
import cn.mafei.cmp.providers.feiniu.core.FeiniuRuntimeSource
import cn.mafei.cmp.providers.feiniu.core.FeiniuSigner
import cn.mafei.cmp.providers.feiniu.testkit.FakeFeiniuTransport
import cn.mafei.cmp.providers.feiniu.testkit.FeiniuFixtures
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Mainline review regressions for 30866e6 (R1-R9). Placed in commonTest with
 * the synchronous trampoline: the staged "race" is deterministic because the
 * scripted transport interleaves at a suspension point, not via threads.
 */
class FeiniuReviewRegressionsTest {

    private val candidate = FeiniuHostCandidate("https://feiniu.test", FeiniuRoute.Direct)
    private val resolution = FeiniuHostResolution(candidate)
    private val fixed = object : FeiniuRuntimeSource {
        override fun nowEpochMs(): Long = 1_700_000_000_000L
        override fun random(): Double = 0.5
    }

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private fun response(request: HttpRequest, body: String = """{"code":0,"data":[]}""", status: Int = 200,
                         headers: HttpHeaders = HttpHeaders.EMPTY) =
        HttpResponse(status, headers, body.encodeToByteArray(), request.url, request.url)

    // R1
    @Test
    fun loginAcceptsLegacyNestedToken() = runTest {
        val transport = FakeFeiniuTransport()
        transport.enqueueJson(200, """{"code":0,"data":{"token":"new-user-token"}}""")
        val client = FeiniuClient(transport = transport)
        assertEquals("new-user-token", FeiniuAuthApi(client).login(resolution, "demo", "pw").token)
    }

    // R1
    @Test
    fun loginCannotSucceedWithPreviousAccountsToken() = runTest {
        val transport = FakeFeiniuTransport()
        transport.enqueueJson(200, """{"code":0}""")
        val client = FeiniuClient(transport = transport, sessionState = cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState(FeiniuCredentials("old-user")))
        assertFailsWith<FeiniuAuthenticationError> { FeiniuAuthApi(client).login(resolution, "new-user", "pw") }
    }

    // R3
    @Test
    fun malformedUserInfoIsNotAuthenticationExpiry() = runTest {
        val transport = FakeFeiniuTransport()
        transport.enqueueJson(200, """{"code":0,"data":{"is_admin":[]}}""")
        val client = FeiniuClient(transport = transport)
        assertFailsWith<FeiniuDecodeError> { FeiniuAuthApi(client).restore(resolution, FeiniuCredentials("valid-token")) }
        assertEquals("valid-token", client.sessionState.current.value.token)
    }

    // R7
    @Test
    fun probePreservesCancellation() = runTest {
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse = throw CancellationException("cancelled")
        }
        assertFailsWith<CancellationException> { FeiniuConnectionApi(transport).probe(candidate) }
    }

    // R2
    @Test
    fun lateResponseCannotResurrectLoggedOutSession() = runTest {
        val state = cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState(FeiniuCredentials("old-user"))
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                state.clear() // logout while the earlier request is in flight
                return response(request)
            }
        }
        val client = FeiniuClient(transport = transport, sessionState = state)
        FeiniuCatalogApi(client).search(resolution, "fixture")
        assertFalse(state.current.value.hasToken, "Late response must not restore old credentials")
    }

    // R4
    @Test
    fun searchPreservesUtf8Query() = runTest {
        val transport = FakeFeiniuTransport()
        transport.enqueueJson(200, FeiniuFixtures.SEARCH_LIST)
        val client = FeiniuClient(transport = transport, sessionState = cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState(FeiniuCredentials("t")))
        FeiniuCatalogApi(client).search(resolution, "电影")
        assertEquals("电影", transport.requests.single().url.queryParameters.first("q"))
    }

    @Test
    fun searchEncodesAccentsEmojiSpacesPercentAndMultiValue() = runTest {
        val transport = FakeFeiniuTransport()
        transport.enqueueJson(200, FeiniuFixtures.SEARCH_LIST)
        transport.enqueueJson(200, FeiniuFixtures.SEARCH_LIST)
        val client = FeiniuClient(transport = transport, sessionState = cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState(FeiniuCredentials("t")))
        val api = FeiniuCatalogApi(client)
        api.search(resolution, "café 🎬")
        api.search(resolution, "100%")
        val first = transport.requests[0].url.queryParameters.first("q")!!
        assertEquals("café 🎬", first)
        assertEquals("100%", transport.requests[1].url.queryParameters.first("q"))
        val wire = transport.requests[0].url.toEncodedString()
        assertTrue(wire.contains("q=caf%C3%A9%20%F0%9F%8E%AC"), wire)
    }

    // R5
    @Test
    fun redirectSignsTheActualQuery() = runTest {
        val sent = mutableListOf<HttpRequest>()
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                sent += request
                return if (sent.size == 1) response(request, status = 302,
                    headers = HttpHeaders.Builder().add("Location", "/v/api/v1/search/list?q=new").build())
                else response(request)
            }
        }
        val client = FeiniuClient(runtime = fixed, transport = transport,
            sessionState = cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState(FeiniuCredentials("t")))
        FeiniuCatalogApi(client).search(resolution, "old")
        assertEquals("new", sent.last().url.queryParameters.first("q"))
        assertEquals(
            FeiniuSigner.authx("/v/api/v1/search/list", FeiniuSigner.nonceFrom(0.5),
                "1700000000000", queryParameters = listOf("q" to "new")),
            sent.last().headers.first("Authx"),
        )
    }

    @Test
    fun redirect303ClearsTheSignedBody() = runTest {
        val sent = mutableListOf<HttpRequest>()
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                sent += request
                return if (sent.size == 1) response(request, status = 303,
                    headers = HttpHeaders.Builder().add("Location", "/v/api/v1/login").build())
                else response(request, body = """{"code":0,"data":{"token":"t"}}""")
            }
        }
        val client = FeiniuClient(runtime = fixed, transport = transport)
        val call = FeiniuApiCall(
            path = "/v/api/v1/login",
            method = FeiniuApiCall.FeiniuMethod.Post,
            bodyText = """{"username":"u"}""",
            authRequirement = FeiniuApiCall.AuthRequirement.None,
        )
        client.executeWithResult(resolution, call) { _, _ -> true }
        assertEquals(HttpMethod.Get, sent.last().method)
        assertEquals(HttpBody.Empty, sent.last().body)
    }

    // R8
    @Test
    fun nonceMatchesLegacySixDigitFormula() {
        assertEquals("550000", FeiniuSigner.nonceFrom(0.5))
        assertEquals("100000", FeiniuSigner.nonceFrom(0.0))
    }

    // R9
    @Test
    fun speedPreferenceSelectsFastestReachableRoute() = runTest {
        var time = 0L
        val runtime = object : FeiniuRuntimeSource {
            override fun nowEpochMs(): Long = time
            override fun random(): Double = 0.5
        }
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                if (request.url.host == "5ddd.com") return response(request,
                    """{"code":0,"data":{"ipv4":["192.0.2.1"],"fn":["relay.test:443"]}}""")
                time += if (request.url.host == "192.0.2.1") 1000 else 10
                return response(request)
            }
        }
        val result = FeiniuConnectionApi(transport, runtime).resolve("fixture", FeiniuRoutePreference.Speed)
        assertEquals(FeiniuRoute.Relay, result.preferred.route)
    }

    // R6
    @Test
    fun httpFailureMustNotReplayMutationOnAnotherHost() = runTest {
        val sent = mutableListOf<HttpRequest>()
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                sent += request
                return response(request, status = if (sent.size == 1) 500 else 200)
            }
        }
        val client = FeiniuClient(transport = transport,
            sessionState = cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState(FeiniuCredentials("t")))
        val hosts = FeiniuHostResolution(candidate, listOf(FeiniuHostCandidate("https://fallback.test", FeiniuRoute.Public)))
        runCatching { FeiniuCatalogApi(client).markWatched(hosts, "item-1") }
        assertEquals(1, sent.size, "HTTP 500 is not a connection failure")
    }
}

class FeiniuReviewContractRegressionsTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    // R10: play() keeps the actual serving route after host fallback
    @Test
    fun playSessionCarriesTheActualServingRoute() = runTest {
        val preferred = FeiniuHostCandidate("https://lan.test", FeiniuRoute.Local)
        val resolution = FeiniuHostResolution(preferred, listOf(FeiniuHostCandidate("https://relay.test", FeiniuRoute.Relay)))
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                // preferred LAN host fails at connection level; relay serves
                if (request.url.host == "lan.test") {
                    throw cn.mafei.cmp.providers.feiniu.core.FeiniuConnectionError(
                        cn.mafei.cmp.providers.feiniu.core.FeiniuConnectionKind.Connect, "lan down")
                }
                return HttpResponse(
                    200, HttpHeaders.EMPTY,
                    """{"code":0,"data":{"play_link":"/videos/m1/master.m3u8","hls_time":6}}""".encodeToByteArray(),
                    request.url, request.url,
                )
            }
        }
        val client = FeiniuClient(transport = transport,
            sessionState = cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState(FeiniuCredentials("t")))
        val session = cn.mafei.cmp.providers.feiniu.playback.FeiniuPlaybackApi(client).play(
            resolution,
            cn.mafei.cmp.providers.feiniu.core.FeiniuPlayPlayRequest(
                mediaGuid = "md", videoGuid = "vd", videoEncoder = "h264", resolution = "1080p",
                bitrate = 8_000_000, startTimestamp = 0, audioEncoder = "aac",
                audioGuid = "au", subtitleGuid = "sb", channels = 2, forcedSdr = 0,
            ),
        )
        assertEquals("https://relay.test", session.baseUrl)
        assertEquals(FeiniuRoute.Relay, session.route)
        assertEquals(true, session.requiresRelayCookie)
        assertEquals("https://relay.test/videos/m1/master.m3u8", session.resolvePlayLink(baseUrlFallback = "https://lan.test"))
    }

    // R11: stream/list endpoint is callable and decodes
    @Test
    fun streamListEndpointIsCallable() = runTest {
        val transport = FakeFeiniuTransport()
        transport.enqueueJson(200, """{"code":0,"msg":"","data":{
            "video_streams":[{"guid":"vd-1","codec_name":"hevc","width":3840,"height":2160}],
            "audio_streams":[{"guid":"au-1","language":"eng","channels":6}],
            "subtitle_streams":[{"guid":"sb-1","format":"srt","is_external":1}]
        }}""")
        val client = FeiniuClient(transport = transport,
            sessionState = cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState(FeiniuCredentials("t")))
        val data = cn.mafei.cmp.providers.feiniu.playback.FeiniuPlaybackApi(client).streamList(
            FeiniuHostResolution(FeiniuHostCandidate("https://feiniu.test", FeiniuRoute.Direct)),
            guid = "md-0001",
            beforePlay = 1,
        )
        assertEquals("https://feiniu.test/v/api/v1/stream/list/md-0001?before_play=1",
            transport.requests.single().url.toEncodedString())
        // raw passthrough preserved for typed follow-up
        assertEquals(true, data.videoStreams.toString().contains("hevc"))
        assertEquals(true, data.subtitleStreams.toString().contains("srt"))
    }
}
