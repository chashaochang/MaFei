package cn.xiaobai.mafei.data.jellyfin

enum class JellyfinAuthFailureType {
    VALIDATION,
    INVALID_CREDENTIALS,
    SERVER_UNREACHABLE,
    CERTIFICATE_WARNING,
    UNKNOWN,
}

sealed interface JellyfinAuthResult {
    data class Success(
        val baseUrl: String,
        val userId: String?,
        val username: String,
        val accessToken: String,
    ) : JellyfinAuthResult

    data class Failure(
        val type: JellyfinAuthFailureType,
        val message: String,
        val errorCode: JellyfinErrorCode = JellyfinErrorCode.UNKNOWN,
        val retryable: Boolean = errorCode.defaultRetryable(),
    ) : JellyfinAuthResult
}

interface JellyfinAuthRepository {
    suspend fun login(
        baseUrl: String,
        username: String,
        password: String,
    ): JellyfinAuthResult
}

expect fun createJellyfinAuthRepository(provider: JellyfinProvider): JellyfinAuthRepository
