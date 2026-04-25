package cn.xiaobai.mafei.logging

const val APP_LOG_TAG_PREFIX = "MaFei-"

enum class AppLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

expect object PlatformAppLogger {
    fun install()
    fun log(
        level: AppLogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )
}

object AppLogger {
    fun installDefault() {
        PlatformAppLogger.install()
    }

    fun debug(tag: String, message: String, throwable: Throwable? = null) {
        PlatformAppLogger.log(AppLogLevel.DEBUG, tag, message, throwable)
    }

    fun info(tag: String, message: String, throwable: Throwable? = null) {
        PlatformAppLogger.log(AppLogLevel.INFO, tag, message, throwable)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        PlatformAppLogger.log(AppLogLevel.WARN, tag, message, throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        PlatformAppLogger.log(AppLogLevel.ERROR, tag, message, throwable)
    }
}

fun summarizeBaseUrlForLog(rawBaseUrl: String?): String {
    val baseUrl = rawBaseUrl?.trim()?.trimEnd('/').orEmpty()
    if (baseUrl.isBlank()) return "<empty>"
    val schemeIndex = baseUrl.indexOf("://")
    if (schemeIndex < 0) return baseUrl.take(64)
    val scheme = baseUrl.substring(0, schemeIndex)
    val authority = baseUrl.substring(schemeIndex + 3)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .ifBlank { "<invalid>" }
    return "$scheme://$authority".take(64)
}

fun summarizeUsernameForLog(rawUsername: String?): String {
    val username = rawUsername?.trim().orEmpty()
    if (username.isBlank()) return "<empty>"
    if (username.length == 1) return "${username.first()}***"
    return "${username.take(2)}***${username.takeLast(1)}"
}
