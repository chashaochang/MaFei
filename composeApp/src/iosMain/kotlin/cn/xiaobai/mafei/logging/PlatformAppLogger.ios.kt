package cn.xiaobai.mafei.logging

import platform.Foundation.NSLog

actual object PlatformAppLogger {
    actual fun install() = Unit

    actual fun log(
        level: AppLogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        val renderedThrowable = throwable?.message?.takeIf { it.isNotBlank() }
            ?.let { " | error=$it" }
            .orEmpty()
        NSLog("[MaFei][KMP][${level.name}][$tag] $message$renderedThrowable")
    }
}
