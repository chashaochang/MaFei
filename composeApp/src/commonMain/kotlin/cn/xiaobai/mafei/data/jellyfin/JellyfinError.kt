package cn.xiaobai.mafei.data.jellyfin

enum class JellyfinErrorCode {
    INVALID_URL,
    NETWORK_UNREACHABLE,
    TLS_CERTIFICATE_ERROR,
    AUTH_FAILED,
    FORBIDDEN,
    SERVER_ERROR,
    UNKNOWN,
}

fun JellyfinErrorCode.defaultRetryable(): Boolean = when (this) {
    JellyfinErrorCode.INVALID_URL -> false
    JellyfinErrorCode.TLS_CERTIFICATE_ERROR -> false
    JellyfinErrorCode.FORBIDDEN -> false
    JellyfinErrorCode.NETWORK_UNREACHABLE -> true
    JellyfinErrorCode.AUTH_FAILED -> true
    JellyfinErrorCode.SERVER_ERROR -> true
    JellyfinErrorCode.UNKNOWN -> true
}

data class JellyfinLoadIssue(
    val errorCode: JellyfinErrorCode,
    val message: String,
    val retryable: Boolean = errorCode.defaultRetryable(),
)

fun JellyfinErrorCode.actionHint(retryable: Boolean = defaultRetryable()): String = when (this) {
    JellyfinErrorCode.INVALID_URL -> "请编辑服务器地址后重试。"
    JellyfinErrorCode.TLS_CERTIFICATE_ERROR -> "请修复证书或切换服务器。"
    JellyfinErrorCode.AUTH_FAILED -> "请重新登录。"
    JellyfinErrorCode.FORBIDDEN -> "当前账号无权限，请更换账号。"
    JellyfinErrorCode.NETWORK_UNREACHABLE,
    JellyfinErrorCode.SERVER_ERROR,
    JellyfinErrorCode.UNKNOWN -> if (retryable) "可直接重试。" else ""
}

fun JellyfinLoadIssue.messageWithHint(): String {
    val hint = errorCode.actionHint(retryable)
    return if (hint.isBlank()) message else "$message $hint"
}
