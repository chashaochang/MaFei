package cn.xiaobai.mafei.data.jellyfin

import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import java.io.IOException

internal fun Throwable.toJellyfinLoadIssue(defaultMessage: String): JellyfinLoadIssue {
    return when (this) {
        is InvalidStatusException -> when {
            status == 401 -> JellyfinLoadIssue(
                errorCode = JellyfinErrorCode.AUTH_FAILED,
                message = "会话无效或已过期，请重新登录。",
                retryable = false,
            )

            status == 403 -> JellyfinLoadIssue(
                errorCode = JellyfinErrorCode.FORBIDDEN,
                message = "当前账号无权限访问该内容。",
                retryable = false,
            )

            status in 500..599 -> JellyfinLoadIssue(
                errorCode = JellyfinErrorCode.SERVER_ERROR,
                message = "服务器错误（HTTP $status）。",
            )

            else -> JellyfinLoadIssue(
                errorCode = JellyfinErrorCode.UNKNOWN,
                message = message?.takeIf { it.isNotBlank() } ?: defaultMessage,
            )
        }

        is SecureConnectionException -> JellyfinLoadIssue(
            errorCode = JellyfinErrorCode.TLS_CERTIFICATE_ERROR,
            message = "TLS 连接失败，证书可能异常。",
            retryable = false,
        )

        is TimeoutException,
        is ApiClientException,
        is IOException -> JellyfinLoadIssue(
            errorCode = JellyfinErrorCode.NETWORK_UNREACHABLE,
            message = message?.takeIf { it.isNotBlank() } ?: "网络不可达，请检查连接。",
        )

        is IllegalArgumentException -> JellyfinLoadIssue(
            errorCode = JellyfinErrorCode.INVALID_URL,
            message = message?.takeIf { it.isNotBlank() } ?: "请求地址无效。",
            retryable = false,
        )

        else -> JellyfinLoadIssue(
            errorCode = JellyfinErrorCode.UNKNOWN,
            message = message?.takeIf { it.isNotBlank() } ?: defaultMessage,
        )
    }
}
