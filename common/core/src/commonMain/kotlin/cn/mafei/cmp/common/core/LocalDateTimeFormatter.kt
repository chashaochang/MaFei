package cn.mafei.cmp.common.core

/** Matches the legacy detail-page timestamp shape in the device's local time zone. */
fun formatLocalDateTime(epochMilliseconds: Long, includeTime: Boolean = true): String {
    if (epochMilliseconds <= 0L) return ""
    return formatLocalDateTimePlatform(epochMilliseconds, includeTime)
}

internal expect fun formatLocalDateTimePlatform(epochMilliseconds: Long, includeTime: Boolean): String
