package cn.mafei.cmp.core.http_contract

/** HTTP methods used by media server APIs. */
enum class HttpMethod {
    Get,
    Head,
    Post,
    Put,
    Patch,
    Delete,
    Options,
}

/** Redirect handling requested from a transport. */
enum class RedirectPolicy {
    /** Transparently follow redirects (same or other host). */
    Follow,

    /** Follow redirects only while the host stays the same. */
    FollowSameHost,

    /** Return 3xx responses as-is; the caller manages redirects manually. */
    DoNotFollow,
}

/** How a transport should read the response body. */
sealed interface BodyReadPolicy {

    /** Buffer the whole body regardless of size (use only for trusted endpoints). */
    data object Buffered : BodyReadPolicy

    /** Buffer the body but fail with [HttpBodyLimitExceededException] beyond [maxBytes]. */
    data class BufferedAtMost(val maxBytes: Long) : BodyReadPolicy {
        companion object {
            const val DEFAULT_MAX_BYTES: Long = 16L * 1024 * 1024
        }
    }

    /** Do not read the body at all; transports deliver empty bytes. */
    data object Discarded : BodyReadPolicy
}

/** Request payload variants. Content type travels on the body for convenience. */
sealed class HttpBody {

    data object Empty : HttpBody()

    class Bytes(
        val data: ByteArray,
        val contentType: String? = null,
    ) : HttpBody() {
        override fun equals(other: Any?): Boolean =
            other is Bytes && other.contentType == contentType && other.data.contentEquals(data)

        override fun hashCode(): Int = 31 * data.contentHashCode() + (contentType?.hashCode() ?: 0)

        override fun toString(): String = "HttpBody.Bytes(size=${data.size}, contentType=$contentType)"
    }

    class Text(
        val text: String,
        val contentType: String? = null,
    ) : HttpBody() {
        val bytes: ByteArray get() = text.encodeToByteArray()

        override fun equals(other: Any?): Boolean =
            other is Text && other.contentType == contentType && other.text == text

        override fun hashCode(): Int = 31 * text.hashCode() + (contentType?.hashCode() ?: 0)

        override fun toString(): String = "HttpBody.Text(length=${text.length}, contentType=$contentType)"
    }
}

/**
 * An immutable, fully-specified HTTP request.
 *
 * Transport implementations must honor:
 * - [timeoutMillis] (`null` = transport default); expiry throws [HttpTimeoutException];
 * - cooperative cancellation: cancelling the caller's coroutine aborts the
 *   request with `CancellationException`;
 * - [redirectPolicy] when resolving 3xx responses;
 * - [bodyReadPolicy] when reading response bodies.
 */
class HttpRequest private constructor(
    val method: HttpMethod,
    val url: HttpUrl,
    val headers: HttpHeaders,
    val body: HttpBody,
    val timeoutMillis: Long?,
    val redirectPolicy: RedirectPolicy,
    val bodyReadPolicy: BodyReadPolicy,
    val attributes: Map<String, String>,
) {

    fun newBuilder(): Builder = Builder(
        method = method,
        url = url,
        headers = headers,
        body = body,
        timeoutMillis = timeoutMillis,
        redirectPolicy = redirectPolicy,
        bodyReadPolicy = bodyReadPolicy,
        attributes = attributes,
    )

    class Builder internal constructor(
        private val method: HttpMethod,
        private val url: HttpUrl,
        private var headers: HttpHeaders = HttpHeaders.EMPTY,
        private var body: HttpBody = HttpBody.Empty,
        private var timeoutMillis: Long? = null,
        private var redirectPolicy: RedirectPolicy = RedirectPolicy.Follow,
        private var bodyReadPolicy: BodyReadPolicy =
            BodyReadPolicy.BufferedAtMost(BodyReadPolicy.BufferedAtMost.DEFAULT_MAX_BYTES),
        private var attributes: Map<String, String> = emptyMap(),
    ) {

        fun header(name: String, value: String): Builder = apply {
            headers = headers + (name to value) // plus appends; use set semantics below
        }

        fun setHeader(name: String, value: String): Builder = apply {
            headers = headers.without(name) + (name to value)
        }

        fun removeHeader(name: String): Builder = apply {
            headers = headers.without(name)
        }

        fun body(value: HttpBody): Builder = apply {
            body = value
            val contentType = when (value) {
                is HttpBody.Bytes -> value.contentType
                is HttpBody.Text -> value.contentType
                HttpBody.Empty -> null
            }
            if (contentType != null && !headers.contains(HttpHeaders.CONTENT_TYPE)) {
                headers = headers + (HttpHeaders.CONTENT_TYPE to contentType)
            }
        }

        fun timeoutMillis(value: Long?): Builder = apply {
            require(value == null || value > 0) { "timeoutMillis must be positive or null" }
            timeoutMillis = value
        }

        fun redirectPolicy(value: RedirectPolicy): Builder = apply { redirectPolicy = value }

        fun readPolicy(value: BodyReadPolicy): Builder = apply { bodyReadPolicy = value }

        fun attribute(key: String, value: String): Builder = apply {
            attributes = attributes + (key to value)
        }

        fun build(): HttpRequest = HttpRequest(
            method = method,
            url = url,
            headers = headers,
            body = body,
            timeoutMillis = timeoutMillis,
            redirectPolicy = redirectPolicy,
            bodyReadPolicy = bodyReadPolicy,
            attributes = attributes,
        )
    }

    companion object {
        fun builder(method: HttpMethod, url: HttpUrl): Builder = Builder(method, url)
    }
}

/**
 * A buffered HTTP response. Transports read the body eagerly according to the
 * request's [BodyReadPolicy]; streaming responses are out of scope for this
 * contract (media streams go through the player runtimes).
 */
class HttpResponse(
    val statusCode: Int,
    val headers: HttpHeaders,
    val bodyBytes: ByteArray,
    /** URL of the original request. */
    val requestUrl: HttpUrl,
    /** URL after redirects were followed. */
    val effectiveUrl: HttpUrl,
) {

    val isSuccessful: Boolean get() = statusCode in 200..299
    val isRedirect: Boolean get() = statusCode in 300..399
    val isClientError: Boolean get() = statusCode in 400..499
    val isServerError: Boolean get() = statusCode in 500..599

    fun contentType(): String? = headers.first(HttpHeaders.CONTENT_TYPE)

    /** Charset parameter from `Content-Type`, lower-cased; `null` when absent. */
    fun charset(): String? = contentType()
        ?.split(';')
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.trim('"', ' ')
        ?.lowercase()

    fun bodyText(): String = bodyBytes.decodeToString()

    /** Throws [HttpStatusCodeException] for non-2xx responses; returns `this` otherwise. */
    fun ensureSuccess(): HttpResponse {
        if (!isSuccessful) throw HttpStatusCodeException(response = this)
        return this
    }

    override fun equals(other: Any?): Boolean =
        other is HttpResponse &&
            statusCode == other.statusCode &&
            headers == other.headers &&
            bodyBytes.contentEquals(other.bodyBytes) &&
            requestUrl == other.requestUrl &&
            effectiveUrl == other.effectiveUrl

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + headers.hashCode()
        result = 31 * result + bodyBytes.contentHashCode()
        result = 31 * result + requestUrl.hashCode()
        result = 31 * result + effectiveUrl.hashCode()
        return result
    }

    override fun toString(): String =
        "HttpResponse(status=$statusCode, url=$effectiveUrl, bytes=${bodyBytes.size})"
}
