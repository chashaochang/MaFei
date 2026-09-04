package cn.mafei.cmp.core.http_contract

/**
 * Base of the transport error hierarchy.
 *
 * Layering, from outer to inner:
 * 1. [HttpTransportException] / [HttpTimeoutException] — no usable HTTP
 *    response existed (DNS, connect, TLS, socket, deadline).
 * 2. [HttpStatusCodeException] — an HTTP response with an error status;
 *    thrown only by [HttpResponse.ensureSuccess].
 * 3. [HttpContentDecodeException] — a response was received but its body
 *    cannot be decoded into the caller's model.
 *
 * Cancellation is not part of this hierarchy: cancelled calls throw
 * `CancellationException` so structured concurrency works unchanged.
 */
sealed class HttpException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Why a transport-level failure happened (no HTTP status was obtained). */
enum class TransportFailureKind {
    Dns,
    Connect,
    Tls,
    SocketWrite,
    Unknown,
}

/** The request never produced an HTTP response. */
class HttpTransportException(
    val url: HttpUrl,
    val kind: TransportFailureKind,
    message: String,
    cause: Throwable? = null,
) : HttpException(message = message, cause = cause)

/** The request exceeded the deadline configured via `HttpRequest.timeoutMillis`. */
class HttpTimeoutException(
    val url: HttpUrl,
    val timeoutMillis: Long,
    cause: Throwable? = null,
) : HttpException(
        message = "Request to $url timed out after ${timeoutMillis}ms",
        cause = cause,
    )

/** A response arrived with an error status. */
class HttpStatusCodeException(
    val response: HttpResponse,
) : HttpException(
        message = "HTTP ${response.statusCode} for ${response.effectiveUrl}",
    )

/** The response was received but its body cannot be decoded. */
class HttpContentDecodeException(
    val url: HttpUrl,
    val contentType: String?,
    message: String,
    cause: Throwable? = null,
) : HttpException(message = message, cause = cause)

/** More than [maxRedirects] redirects were attempted for a single call. */
class HttpRedirectLimitExceededException(
    val url: HttpUrl,
    val maxRedirects: Int,
    val lastStatusCode: Int,
) : HttpException(
        message = "Exceeded the redirect limit of $maxRedirects for $url (last status $lastStatusCode)",
    )

/** A redirect tried to downgrade from HTTPS to a non-HTTPS URL. */
class HttpInsecureRedirectException(
    val from: HttpUrl,
    val to: HttpUrl,
) : HttpException(
        message = "Refusing insecure redirect from $from to $to",
    )

/** The body exceeded the limit configured via [BodyReadPolicy.BufferedAtMost]. */
class HttpBodyLimitExceededException(
    val url: HttpUrl,
    val maxBytes: Long,
    val actualBytes: Long? = null,
) : HttpException(
        message = buildString {
            append("Response body exceeded the ")
            append(maxBytes)
            append(" byte limit for ")
            append(url)
            if (actualBytes != null) {
                append(" (received at least ")
                append(actualBytes)
                append(" bytes)")
            }
        },
)
