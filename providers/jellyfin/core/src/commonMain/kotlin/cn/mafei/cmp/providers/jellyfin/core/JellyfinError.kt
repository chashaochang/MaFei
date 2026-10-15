package cn.mafei.cmp.providers.jellyfin.core

/**
 * Stable Jellyfin SDK error taxonomy (mirrors the provider SDK design):
 * callers branch on these types instead of raw HTTP statuses.
 *
 * Cancellation never becomes a [JellyfinError]; cancelled calls propagate
 * `CancellationException` (frozen GLM-01 contract).
 */
sealed class JellyfinError(
    message: String?,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Base URL could not be used at all. */
class JellyfinInvalidAddressError(
    val url: String,
    message: String?,
    cause: Throwable? = null,
) : JellyfinError(message, cause)

/** Why the request never reached the server. */
enum class JellyfinConnectionKind { Dns, Connect, Tls, Timeout, Unknown }

/** Network-level failure; the only class allowed to trigger host fallback. */
class JellyfinConnectionError(
    val kind: JellyfinConnectionKind,
    message: String?,
    cause: Throwable? = null,
) : JellyfinError(message, cause)

/** Login rejected (bad credentials) or anonymous access to a protected call. */
class JellyfinAuthenticationError(
    message: String?,
    val problem: JellyfinProblemDetail? = null,
    cause: Throwable? = null,
) : JellyfinError(message, cause)

/** A stored token stopped working (401 on an authenticated call). */
class JellyfinAuthenticationExpiredError(
    message: String?,
    val problem: JellyfinProblemDetail? = null,
    cause: Throwable? = null,
) : JellyfinError(message, cause)

/** Authenticated but not permitted (403). */
class JellyfinAuthorizationError(
    message: String?,
    val problem: JellyfinProblemDetail? = null,
    cause: Throwable? = null,
) : JellyfinError(message, cause)

/** 404 — item/user/endpoint does not exist. */
class JellyfinNotFoundError(
    message: String?,
    cause: Throwable? = null,
) : JellyfinError(message, cause)

/** 429 — slow down; [retryAfterMs] is parsed from `Retry-After` when present. */
class JellyfinRateLimitedError(
    val retryAfterMs: Long?,
    message: String?,
    cause: Throwable? = null,
) : JellyfinError(message, cause)

/** 5xx server-side failure. */
class JellyfinServerError(
    val statusCode: Int,
    message: String?,
    cause: Throwable? = null,
) : JellyfinError(message, cause)

/** Other 4xx failures (400/409/422/...) with server-provided details. */
class JellyfinBusinessError(
    val statusCode: Int,
    message: String?,
    val problem: JellyfinProblemDetail? = null,
    cause: Throwable? = null,
) : JellyfinError(message, cause)

/** Response arrived but is not the JSON shape the protocol promises. */
class JellyfinDecodeError(
    message: String?,
    cause: Throwable? = null,
) : JellyfinError(message, cause)

/** Server tried to redirect unsafely (downgrade/loop/invalid target). */
class JellyfinRedirectRejectedError(
    message: String?,
    cause: Throwable? = null,
) : JellyfinError(message, cause)

/** Requested capability not supported by this SDK build. */
class JellyfinUnsupportedCapabilityError(
    message: String?,
) : JellyfinError(message)

/** Server-provided error detail (ProblemDetails subset), safe to surface. */
data class JellyfinProblemDetail(
    val title: String? = null,
    val detail: String? = null,
    val status: Int? = null,
)

/** Discovery: server version below the supported minimum. */
class JellyfinUnsupportedServerVersionError(
    val actualVersion: String?,
    val minimumSupported: String,
) : JellyfinError(
        message = "Jellyfin server version ${actualVersion ?: "unknown"} is below the supported minimum $minimumSupported",
    )
