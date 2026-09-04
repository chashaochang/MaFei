package cn.mafei.cmp.core.serialization

/**
 * Pure-Kotlin ISO-8601 instant handling shared by provider adapters.
 *
 * Accepted on parse (all resolved to UTC epoch milliseconds):
 * - `2024-01-01T00:00:00.123Z`, `2024-01-01T00:00Z`, `2024-01-01 00:00:00Z`;
 * - offsets `+08:00`, `-0500`, `+08`, `Z`/`z` — an explicit offset is required
 *   when a time is present (a naive local time is ambiguous, never guessed);
 * - date-only `2024-01-01` (UTC midnight) and sub-millisecond fractions
 *   (truncated to milliseconds);
 * - pre-epoch dates and leap years per Gregorian rules.
 */
object IsoInstant {

    private val PATTERN = Regex(
        "^([+-]?\\d{4,6})-(\\d{2})-(\\d{2})" +
            "(?:[Tt ](\\d{2}):(\\d{2})(?::(\\d{2})(?:[.,](\\d+))?)?)?" +
            "(?:\\s*(Z|z|[+-]\\d{2}:?\\d{2}|[+-]\\d{2}))?$",
    )

    private val DAYS_IN_MONTH = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    fun parseOrNull(raw: String?): Long? {
        if (raw == null) return null
        val match = PATTERN.find(raw.trim()) ?: return null

        val yearText = match.groupValues[1]
        val year = yearText.toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null

        if (month !in 1..12) return null
        val maxDay = if (month == 2 && isLeapYear(year)) 29 else DAYS_IN_MONTH[month - 1]
        if (day !in 1..maxDay) return null

        val hasTime = match.groupValues[4].isNotEmpty()
        val hour = match.groupValues[4].ifEmpty { "0" }.toIntOrNull() ?: return null
        val minute = match.groupValues[5].ifEmpty { "0" }.toIntOrNull() ?: return null
        val second = match.groupValues[6].ifEmpty { "0" }.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
        val fraction = match.groupValues[7]
        val ms = when {
            fraction.isEmpty() -> 0L
            fraction.length >= 3 -> fraction.substring(0, 3).toLong()
            fraction.length == 2 -> fraction.substring(0, 2).toLong() * 10
            else -> fraction.substring(0, 1).toLong() * 100
        }

        val offsetText = match.groupValues[8]
        val hasOffset = offsetText.isNotEmpty()
        if (hasTime && !hasOffset) return null // naive local time is ambiguous
        if (!hasTime && hasOffset && !(offsetText == "Z" || offsetText == "z")) return null

        val offsetMs = when {
            offsetText.isEmpty() || offsetText == "Z" || offsetText == "z" -> 0L
            else -> {
                val sign = if (offsetText[0] == '-') -1L else 1L
                val digits = offsetText.substring(1).replace(":", "")
                val offsetHour = digits.substring(0, 2).toIntOrNull() ?: return null
                val offsetMinute = digits.drop(2).ifEmpty { "0" }.toIntOrNull() ?: return null
                if (offsetHour !in 0..23 || offsetMinute !in 0..59) return null
                sign * (offsetHour * 3_600_000L + offsetMinute * 60_000L)
            }
        }

        val epochDay = daysFromCivil(year, month, day)
        return epochDay * 86_400_000L + hour * 3_600_000L + minute * 60_000L + second * 1_000L + ms - offsetMs
    }

    /** Renders epoch milliseconds as `YYYY-MM-DDTHH:MM:SS[.mmm]Z` (UTC). */
    fun format(epochMs: Long): String {
        val days = epochMs.floorDiv(86_400_000L)
        val msOfDay = epochMs.mod(86_400_000L)

        val (year, month, day) = civilFromDays(days)
        val hour = msOfDay / 3_600_000L
        val minute = (msOfDay / 60_000L).mod(60L)
        val second = (msOfDay / 1_000L).mod(60L)
        val millis = msOfDay.mod(1_000L)

        return buildString(24) {
            appendPadded(year.toLong(), 4)
            append('-')
            appendPadded(month.toLong(), 2)
            append('-')
            appendPadded(day.toLong(), 2)
            append('T')
            appendPadded(hour, 2)
            append(':')
            appendPadded(minute, 2)
            append(':')
            appendPadded(second, 2)
            if (millis != 0L) {
                append('.')
                appendPadded(millis, 3)
            }
            append('Z')
        }
    }

    fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    /** Days since 1970-01-01 for a proleptic Gregorian civil date. */
    fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }

    /** Inverse of [daysFromCivil]. */
    fun civilFromDays(epochDay: Long): Triple<Int, Int, Int> {
        val z = epochDay + 719_468L
        val era = (if (z >= 0) z else z - 146_096) / 146_097
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val year = if (m <= 2) y + 1 else y
        return Triple(year.toInt(), m.toInt(), d.toInt())
    }
}

/** Parses an ISO-8601 instant; returns `null` for anything ambiguous or malformed. */
fun parseIsoInstantToEpochMs(raw: String?): Long? = IsoInstant.parseOrNull(raw)

/** Formats epoch milliseconds as a canonical UTC ISO-8601 instant. */
fun formatIsoInstant(epochMs: Long): String = IsoInstant.format(epochMs)

private fun StringBuilder.appendPadded(value: Long, width: Int) {
    var text = value.toString()
    if (text.startsWith("-")) {
        append('-')
        text = text.substring(1)
    }
    repeat(width - text.length) { append('0') }
    append(text)
}
