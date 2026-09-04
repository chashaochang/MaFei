package cn.mafei.cmp.core.domain

/**
 * Which layer produced a [DomainError].
 */
enum class ErrorSource {
    Network,
    Transport,
    Authentication,
    Authorization,
    ProviderProtocol,
    ProviderServer,
    Client,
    Storage,
    Unknown,
}

/**
 * How a [DomainError] should be surfaced by the UI layer.
 */
enum class UserVisibility {
    /** Never shown directly; callers log or silently fall back. */
    Silent,

    /** Suitable for toast/banner/snackbar style feedback. */
    NonBlocking,

    /** Requires a dialog or full-screen state (e.g. corrupted storage). */
    Blocking,
}

/** Detailed reason recorded by [AuthenticationError]. */
enum class AuthFailureKind {
    InvalidCredentials,
    TokenExpired,
    TokenRevoked,
    AccountLocked,
    MfaRequired,
}

/** Detailed reason recorded by [StorageError]. */
enum class StorageFailureKind {
    Corrupted,
    Unavailable,
    Unsupported,
    QuotaExceeded,
}

/**
 * Base class of the closed domain error hierarchy.
 *
 * Every error carries its [source], whether a plain retry can help
 * ([isRetryable]) and how the UI should present it ([visibility]). Cancellation
 * is deliberately *not* a [DomainError]: cancelled operations propagate
 * `CancellationException` so structured concurrency keeps working.
 */
sealed class DomainError(
    val source: ErrorSource,
    val isRetryable: Boolean,
    val visibility: UserVisibility,
    override val message: String? = null,
    override val cause: Throwable? = null,
) : Exception(message, cause)

/** Could not reach the server at all (offline, DNS, connection refused). */
class NetworkUnreachableError(
    message: String? = null,
    cause: Throwable? = null,
) : DomainError(ErrorSource.Network, isRetryable = true, visibility = UserVisibility.NonBlocking, message = message, cause = cause)

/** A request exceeded its deadline. */
class TimeoutError(
    val timeoutMs: Long? = null,
    message: String? = null,
    cause: Throwable? = null,
) : DomainError(ErrorSource.Network, isRetryable = true, visibility = UserVisibility.NonBlocking, message = message ?: timeoutMs?.let { "Request timed out after ${it}ms" }, cause = cause)

/** Login rejected or saved session invalid; user action is required. */
class AuthenticationError(
    val kind: AuthFailureKind,
    message: String? = null,
    cause: Throwable? = null,
) : DomainError(ErrorSource.Authentication, isRetryable = false, visibility = UserVisibility.NonBlocking, message = message, cause = cause)

/** Authenticated but not permitted to perform the operation (e.g. HTTP 403). */
class AuthorizationError(
    message: String? = null,
    cause: Throwable? = null,
) : DomainError(ErrorSource.Authorization, isRetryable = false, visibility = UserVisibility.NonBlocking, message = message, cause = cause)

/** A specific resource does not exist (e.g. HTTP 404); usually not shown. */
class ResourceNotFoundError(
    val resource: String? = null,
    message: String? = null,
    cause: Throwable? = null,
) : DomainError(ErrorSource.Client, isRetryable = false, visibility = UserVisibility.Silent, message = message ?: resource?.let { "Not found: $it" }, cause = cause)

/** The provider asked us to slow down (e.g. HTTP 429). */
class RateLimitedError(
    val retryAfterMs: Long? = null,
    message: String? = null,
    cause: Throwable? = null,
) : DomainError(ErrorSource.ProviderServer, isRetryable = true, visibility = UserVisibility.NonBlocking, message = message, cause = cause)

/** Provider-side failure (e.g. HTTP 5xx); usually worth one retry. */
class ServerError(
    val statusCode: Int? = null,
    message: String? = null,
    cause: Throwable? = null,
) : DomainError(ErrorSource.ProviderServer, isRetryable = true, visibility = UserVisibility.NonBlocking, message = message ?: statusCode?.let { "Server error $it" }, cause = cause)

/** Provider responded with something we cannot decode into the contract. */
class ProtocolDecodingError(
    message: String? = null,
    cause: Throwable? = null,
) : DomainError(ErrorSource.ProviderProtocol, isRetryable = false, visibility = UserVisibility.NonBlocking, message = message, cause = cause)

/** Server is reachable but its version/protocol is not supported by the app. */
class UnsupportedServerVersionError(
    val serverVersion: String? = null,
    val minimumSupported: String? = null,
    cause: Throwable? = null,
) : DomainError(
        ErrorSource.ProviderProtocol,
        isRetryable = false,
        visibility = UserVisibility.Blocking,
        message = "Unsupported server version${serverVersion?.let { " $it" } ?: ""}",
        cause = cause,
    )

/** Local persistence failed. */
class StorageError(
    val kind: StorageFailureKind,
    message: String? = null,
    cause: Throwable? = null,
) : DomainError(
        ErrorSource.Storage,
        isRetryable = kind == StorageFailureKind.Unavailable,
        visibility = if (kind == StorageFailureKind.Corrupted) UserVisibility.Blocking else UserVisibility.NonBlocking,
        message = message,
        cause = cause,
    )

/** The request itself is wrong (malformed URL, missing parameter, ...). */
class ClientConfigError(
    message: String? = null,
    cause: Throwable? = null,
) : DomainError(ErrorSource.Client, isRetryable = false, visibility = UserVisibility.Blocking, message = message, cause = cause)

/** Anything not covered by the other branches. */
class UnknownDomainError(
    message: String? = null,
    cause: Throwable? = null,
) : DomainError(ErrorSource.Unknown, isRetryable = true, visibility = UserVisibility.NonBlocking, message = message, cause = cause)
