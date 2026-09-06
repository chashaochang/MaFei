package cn.mafei.cmp.providers.jellyfin.core

/**
 * Server version gate. The product supports Jellyfin 10.8+ (10.9/10.10 are
 * the primary targets); servers below the minimum are rejected with a clear
 * error instead of failing later on protocol differences.
 */
data object JellyfinVersionCompatibility {

    const val MINIMUM_SUPPORTED: String = "10.8.0"

    /**
     * Parses the leading `major[.minor[.patch]]` of a version string;
     * missing parts default to 0, trailing build suffixes are ignored.
     * Returns `null` when nothing numeric can be extracted.
     */
    fun parse(version: String?): Triple<Int, Int, Int>? {
        if (version.isNullOrBlank()) return null
        val core = version.trim().removePrefix("v").removePrefix("V")
        val parts = core.split('.', '-', '+', ' ').filter { it.isNotEmpty() }
        val numbers = parts.take(3).map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: return null
        }
        if (numbers.isEmpty()) return null
        val major = numbers.getOrElse(0) { 0 }
        val minor = numbers.getOrElse(1) { 0 }
        val patch = numbers.getOrElse(2) { 0 }
        return Triple(major, minor, patch)
    }

    fun isSupported(version: String?, minimum: String = MINIMUM_SUPPORTED): Boolean {
        val actual = parse(version) ?: return false
        val min = parse(minimum) ?: return false
        return actual.first > min.first ||
            (actual.first == min.first && actual.second > min.second) ||
            (actual.first == min.first && actual.second == min.second && actual.third >= min.third)
    }
}
