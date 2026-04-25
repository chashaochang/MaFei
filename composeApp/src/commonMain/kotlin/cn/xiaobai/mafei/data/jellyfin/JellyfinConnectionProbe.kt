package cn.xiaobai.mafei.data.jellyfin

sealed interface JellyfinProbeResult {
    data class Success(
        val normalizedBaseUrl: String,
    ) : JellyfinProbeResult

    data class Failure(
        val message: String,
        val isCertificateIssue: Boolean = false,
        val errorCode: JellyfinErrorCode = if (isCertificateIssue) {
            JellyfinErrorCode.TLS_CERTIFICATE_ERROR
        } else {
            JellyfinErrorCode.UNKNOWN
        },
        val retryable: Boolean = errorCode.defaultRetryable(),
    ) : JellyfinProbeResult
}

interface JellyfinConnectionProbe {
    suspend fun probe(rawBaseUrl: String): JellyfinProbeResult
}

expect fun createJellyfinConnectionProbe(provider: JellyfinProvider): JellyfinConnectionProbe
