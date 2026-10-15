package cn.mafei.cmp.common.core

/** Matches the legacy detail-page timestamp shape in the device's local time zone. */
fun formatLocalDateTime(epochMilliseconds: Long, includeTime: Boolean = true): String {
    if (epochMilliseconds <= 0L) return ""
    return formatLocalDateTimePlatform(epochMilliseconds, includeTime)
}

/**
 * Local `HH:mm` for status areas that show a clock (the player's fullscreen
 * chrome). Every platform actual of [formatLocalDateTimePlatform] renders the
 * `yyyy-MM-dd HH:mm` shape, so the time half is taken from there rather than
 * duplicating four more platform formatters for two digits.
 *
 * Returns an empty string when the platform cannot format the value at all, so
 * callers hide the slot instead of printing a broken label.
 */
fun formatLocalClockTime(epochMilliseconds: Long): String =
    formatLocalDateTime(epochMilliseconds, includeTime = true).substringAfter(' ', "")

internal expect fun formatLocalDateTimePlatform(epochMilliseconds: Long, includeTime: Boolean): String
