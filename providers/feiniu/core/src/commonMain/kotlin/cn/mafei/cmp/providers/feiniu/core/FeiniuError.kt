package cn.mafei.cmp.providers.feiniu.core

/**
 * Feiniu SDK error taxonomy (mirrors the provider SDK design). Only
 * [FeiniuConnectionError] allows host-fallback retries; cancellation
 * propagates as `CancellationException` and never becomes a FeiniuError.
 */
sealed class FeiniuError(
    message: String?,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Input could not be parsed into a server address/FNID. */
class FeiniuInvalidAddressError(
    message: String,
    val input: String? = null,
) : FeiniuError(message)

enum class FeiniuConnectionKind { Dns, Connect, Tls, Timeout, Unknown }

/** Network-level failure; the only retryable-across-hosts class. */
class FeiniuConnectionError(
    val kind: FeiniuConnectionKind,
    message: String?,
    cause: Throwable? = null,
) : FeiniuError(message, cause)

/** Login rejected or anonymous access to a protected call (not expired). */
open class FeiniuAuthenticationError(
    message: String?,
    cause: Throwable? = null,
) : FeiniuError(message, cause)

/** Stored token stopped working (auth envelope with requireToken, or 401/403). */
class FeiniuAuthenticationExpiredError(
    message: String?,
    cause: Throwable? = null,
) : FeiniuAuthenticationError(message, cause)

/** Envelope reported a business failure (code != 0). */
class FeiniuBusinessError(
    val code: Int,
    message: String?,
    cause: Throwable? = null,
) : FeiniuError(message, cause)

/** Response was not the promised JSON envelope/shape. */
class FeiniuDecodeError(
    message: String?,
    cause: Throwable? = null,
) : FeiniuError(message, cause)

/** Manual redirect was missing, invalid, or violated the trust rules. */
class FeiniuRedirectRejectedError(
    message: String?,
    cause: Throwable? = null,
) : FeiniuError(message, cause)

class FeiniuUnsupportedCapabilityError(
    message: String?,
) : FeiniuError(message)
