package cn.mafei.cmp.core.http_contract

/**
 * Platform HTTP engine boundary. Implementations live in `platform:http-*`
 * and are handed to providers via constructor injection.
 *
 * Contract every implementation must honor:
 * - execute [HttpRequest] and honor its timeout/read policies; the request
 *   timeout covers the whole call including any redirect hops;
 * - drive redirect following exclusively through [HttpRedirects.nextRequest]
 *   so hop budget, method/body rewriting, origin-based credential stripping
 *   and downgrade rejection behave identically on every platform;
 * - throw `CancellationException` promptly when the caller is cancelled;
 * - throw [HttpTimeoutException] when the deadline expires (anywhere in the
 *   redirect chain);
 * - map engine failures to [HttpTransportException] with a precise
 *   [TransportFailureKind];
 * - return any HTTP status as a normal [HttpResponse] (no throw-on-error);
 * - with [RedirectPolicy.DoNotFollow], return 3xx responses untouched.
 */
interface HttpTransport {

    suspend fun execute(request: HttpRequest): HttpResponse

    /** Releases engine resources; idempotent. */
    fun close() {}
}

/**
 * One link of the request pipeline. Implementations must either call
 * [HttpInterceptorChain.proceed] exactly once (possibly with a modified
 * request) or short-circuit with their own response.
 */
fun interface HttpInterceptor {

    suspend fun intercept(request: HttpRequest, chain: HttpInterceptorChain): HttpResponse
}

/** Continuation handle passed through the interceptor pipeline. */
fun interface HttpInterceptorChain {
    suspend fun proceed(request: HttpRequest): HttpResponse
}

/**
 * Marks interceptors that attach credentials (Authorization headers, access
 * tokens). Implementations must never write credential values into logs;
 * pair them with an [HttpLogRedactor].
 */
interface AuthorizationRequestInterceptor : HttpInterceptor

/**
 * Marks interceptors that attach client/device identification headers
 * (Client, Device, DeviceId, ...). Values here are not secret but must stay
 * stable per installation.
 */
interface DeviceInfoRequestInterceptor : HttpInterceptor

/**
 * Returns a transport that runs [interceptors] around [this] one, in list
 * order (first interceptor is outermost).
 */
fun HttpTransport.withInterceptors(interceptors: List<HttpInterceptor>): HttpTransport {
    if (interceptors.isEmpty()) return this
    return InterceptedHttpTransport(base = this, interceptors = interceptors)
}

private class InterceptedHttpTransport(
    private val base: HttpTransport,
    private val interceptors: List<HttpInterceptor>,
) : HttpTransport {

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val chain: HttpInterceptorChain = buildChain(0)
        return chain.proceed(request)
    }

    private fun buildChain(index: Int): HttpInterceptorChain {
        if (index == interceptors.size) {
            return HttpInterceptorChain { request -> base.execute(request) }
        }
        val interceptor = interceptors[index]
        return HttpInterceptorChain { request ->
            interceptor.intercept(request, buildChain(index + 1))
        }
    }

    override fun close() = base.close()
}
