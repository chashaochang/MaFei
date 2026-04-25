package cn.xiaobai.mafei.logging

import android.util.Log

actual object PlatformAppLogger {
    actual fun install() = Unit

    actual fun log(
        level: AppLogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        val androidTag = buildAndroidTag(tag)
        when (level) {
            AppLogLevel.DEBUG -> Log.d(androidTag, message, throwable)
            AppLogLevel.INFO -> Log.i(androidTag, message, throwable)
            AppLogLevel.WARN -> Log.w(androidTag, message, throwable)
            AppLogLevel.ERROR -> Log.e(androidTag, message, throwable)
        }
    }

    private fun buildAndroidTag(tag: String): String {
        val normalized = tag
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .trim('-')
            .ifBlank { "App" }
        return "$APP_LOG_TAG_PREFIX$normalized".take(23)
    }
}
