package cn.mafei.cmp.core.http_contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpInterceptorChainTest {

    private val baseResponse = HttpResponse(200, HttpHeaders.EMPTY, ByteArray(0), HttpUrl.parse("https://h/x")!!, HttpUrl.parse("https://h/x")!!)

    @Test
    fun interceptorsRunInOrderAroundTheBase() = runSuspendTest {
        val order = mutableListOf<String>()
        val transport = FakeHttpTransport(
            responseProvider = {
                order += "base"
                baseResponse
            },
        )

        val first = HttpInterceptor { request, chain ->
            order += "first-before"
            val result = chain.proceed(request)
            order += "first-after"
            result
        }
        val second = HttpInterceptor { request, chain ->
            order += "second-before"
            chain.proceed(request)
        }

        val response = transport.withInterceptors(listOf(first, second)).execute(sampleRequest())

        assertEquals(baseResponse, response)
        assertEquals(
            listOf("first-before", "second-before", "base", "first-after"),
            order,
        )
    }

    @Test
    fun interceptorCanShortCircuitWithoutHittingBase() = runSuspendTest {
        var baseCalled = false
        val transport = FakeHttpTransport(
            responseProvider = {
                baseCalled = true
                baseResponse
            },
        )
        val shortCircuit = HttpResponse(
            418,
            HttpHeaders.EMPTY,
            ByteArray(0),
            HttpUrl.parse("https://h/x")!!,
            HttpUrl.parse("https://h/x")!!,
        )

        val response = transport.withInterceptors(
            listOf(HttpInterceptor { _, _ -> shortCircuit }),
        ).execute(sampleRequest())

        assertEquals(418, response.statusCode)
        assertFalse(baseCalled)
    }

    @Test
    fun interceptorCanMutateTheRequest() = runSuspendTest {
        val seen = mutableListOf<String>()
        val transport = FakeHttpTransport(
            responseProvider = { request ->
                seen += request.headers.first("authorization") ?: "missing"
                baseResponse
            },
        )

        val auth = HttpInterceptor { request, chain ->
            chain.proceed(
                request.newBuilder()
                    .setHeader("Authorization", "MediaBrowser token")
                    .build(),
            )
        }

        transport.withInterceptors(listOf(auth)).execute(sampleRequest())

        assertEquals(listOf("MediaBrowser token"), seen)
    }

    @Test
    fun noInterceptorsFallsThroughToBase() = runSuspendTest {
        val transport = FakeHttpTransport(responseProvider = { baseResponse })
        val response = transport.withInterceptors(emptyList()).execute(sampleRequest())
        assertEquals(baseResponse, response)
    }

    @Test
    fun authorizationAndDeviceInfoMarkersAreInterceptors() {
        class AuthInterceptor : AuthorizationRequestInterceptor {
            override suspend fun intercept(request: HttpRequest, chain: HttpInterceptorChain): HttpResponse =
                chain.proceed(request.newBuilder().setHeader("X-Emby-Authorization", "x").build())
        }

        class DeviceInterceptor : DeviceInfoRequestInterceptor {
            override suspend fun intercept(request: HttpRequest, chain: HttpInterceptorChain): HttpResponse =
                chain.proceed(request)
        }

        val interceptors: List<HttpInterceptor> = listOf(AuthInterceptor(), DeviceInterceptor())
        assertTrue(interceptors.all { it is HttpInterceptor })
        assertIs<AuthorizationRequestInterceptor>(interceptors[0])
        assertIs<DeviceInfoRequestInterceptor>(interceptors[1])
    }

    private fun sampleRequest(): HttpRequest =
        HttpRequest.builder(HttpMethod.Get, HttpUrl.parse("https://h/x")!!).build()
}

/** Minimal in-memory transport honouring only the happy path; used by contract tests. */
private class FakeHttpTransport(
    private val responseProvider: suspend (HttpRequest) -> HttpResponse,
) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse = responseProvider(request)
}
