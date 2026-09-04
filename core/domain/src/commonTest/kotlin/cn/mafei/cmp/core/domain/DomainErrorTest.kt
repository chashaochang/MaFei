package cn.mafei.cmp.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainErrorTest {

    @Test
    fun networkErrorsAreRetryableAndQuiet() {
        val unreachable = NetworkUnreachableError(message = "offline", cause = null)
        assertEquals(ErrorSource.Network, unreachable.source)
        assertTrue(unreachable.isRetryable)
        assertEquals(UserVisibility.NonBlocking, unreachable.visibility)
        assertEquals("offline", unreachable.message)

        val timeout = TimeoutError(timeoutMs = 5_000)
        assertEquals(ErrorSource.Network, timeout.source)
        assertTrue(timeout.isRetryable)
        assertEquals(5_000L, timeout.timeoutMs)
    }

    @Test
    fun authErrorsNeedUserAction() {
        val invalid = AuthenticationError(AuthFailureKind.InvalidCredentials)
        assertEquals(ErrorSource.Authentication, invalid.source)
        assertFalse(invalid.isRetryable)
        assertEquals(AuthFailureKind.InvalidCredentials, invalid.kind)

        val expired = AuthenticationError(AuthFailureKind.TokenExpired)
        assertFalse(expired.isRetryable)
    }

    @Test
    fun serverAndRateLimitErrorsRetryWithBackoff() {
        val server = ServerError(statusCode = 503)
        assertTrue(server.isRetryable)
        assertEquals(ErrorSource.ProviderServer, server.source)

        val limited = RateLimitedError(retryAfterMs = 30_000)
        assertTrue(limited.isRetryable)
        assertEquals(30_000L, limited.retryAfterMs)
    }

    @Test
    fun protocolAndStorageErrorsAreNotBlindlyRetryable() {
        val decode = ProtocolDecodingError(message = "unexpected shape")
        assertEquals(ErrorSource.ProviderProtocol, decode.source)
        assertFalse(decode.isRetryable)

        val corrupted = StorageError(StorageFailureKind.Corrupted)
        assertFalse(corrupted.isRetryable)
        assertEquals(UserVisibility.Blocking, corrupted.visibility)

        val unavailable = StorageError(StorageFailureKind.Unavailable)
        assertTrue(unavailable.isRetryable)
    }

    @Test
    fun notFoundIsSilent() {
        val notFound = ResourceNotFoundError(resource = "media/42")
        assertEquals(ErrorSource.Client, notFound.source)
        assertFalse(notFound.isRetryable)
        assertEquals(UserVisibility.Silent, notFound.visibility)
    }

    @Test
    fun unknownErrorWrapsCause() {
        val cause = RuntimeException("boom")
        val error = UnknownDomainError(cause = cause)
        assertEquals(ErrorSource.Unknown, error.source)
        assertTrue(error.isRetryable)
        assertEquals(cause, error.cause)
    }

    @Test
    fun errorsFormOneSealedHierarchy() {
        val errors: List<DomainError> = listOf(
            NetworkUnreachableError(),
            TimeoutError(),
            AuthenticationError(AuthFailureKind.TokenRevoked),
            AuthorizationError(),
            ResourceNotFoundError(),
            RateLimitedError(),
            ServerError(),
            ProtocolDecodingError(),
            UnsupportedServerVersionError(),
            StorageError(StorageFailureKind.Unsupported),
            ClientConfigError(message = "bad url"),
            UnknownDomainError(),
        )
        assertEquals(12, errors.size)
        assertTrue(errors.all { it.visibility in UserVisibility.entries })

        val asThrowable: Throwable = errors.first()
        assertIs<DomainError>(asThrowable)
        assertNull(asThrowable.cause)
    }
}
