package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.providers.feiniu.core.FeiniuMediaItem

private const val MILLIS_PER_DAY = 86_400_000L

private data class FeiniuScoredRecommendation(
    val item: FeiniuMediaItem,
    val score: Double,
    val tieBreaker: Int,
)

private data class FeiniuCalendarDate(
    val year: Int,
    val month: Int,
    val day: Int,
)

/** Mechanical Kotlin port of legacy `FeiniuVideoRecommendationPlanner`. */
internal fun rankFeiniuRecommendations(
    latest: List<FeiniuMediaItem>,
    highestRated: List<FeiniuMediaItem>,
    unfinished: List<FeiniuMediaItem>,
    completed: List<FeiniuMediaItem>,
    favorites: List<FeiniuMediaItem>,
    resultLimit: Int,
    seed: String,
    currentYear: Int,
): List<FeiniuMediaItem> {
    val limit = resultLimit.coerceAtLeast(0)
    if (limit == 0) return emptyList()

    val unfinishedIds = unfinished.mapTo(mutableSetOf()) { it.guid.trim() }
    val completedIds = completed.mapTo(mutableSetOf()) { it.guid.trim() }
    val favoriteIds = favorites.mapTo(mutableSetOf()) { it.guid.trim() }
    val genreWeights = mutableMapOf<Int, Int>()
    addFeiniuGenreWeights(genreWeights, unfinished, 4)
    addFeiniuGenreWeights(genreWeights, favorites, 2)

    val latestWeights = feiniuSourceWeights(latest, firstWeight = 54.0, lastWeight = 30.0)
    val highestRatedWeights = feiniuSourceWeights(highestRated, firstWeight = 18.0, lastWeight = 6.0)
    val candidates = uniqueFeiniuRecommendationCandidates(latest + highestRated)
        .filter { it.guid !in unfinishedIds && it.guid !in completedIds }
    val scored = candidates.map { item ->
        var score = (latestWeights[item.guid] ?: 0.0) + (highestRatedWeights[item.guid] ?: 0.0)
        val rating = item.voteAverage?.trim()?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.coerceAtMost(10.0)
            ?: 0.0
        score += rating * 3.0
        val year = feiniuRecommendationYear(item)
        if (year > 0) {
            when ((currentYear - year).coerceAtLeast(0)) {
                in 0..2 -> score += 10.0
                in 3..6 -> score += 6.0
                in 7..12 -> score += 2.0
            }
        }
        val affinity = item.genres.sumOf { genreWeights[it] ?: 0 }
        score += minOf(30, affinity * 3).toDouble()
        if (item.guid in favoriteIds || (item.isFavorite ?: 0) > 0) score -= 8.0
        FeiniuScoredRecommendation(
            item = item,
            score = score,
            tieBreaker = stableFeiniuHash("$seed|${item.guid}"),
        )
    }.sortedWith(
        compareByDescending<FeiniuScoredRecommendation> { it.score }
            .thenByDescending { it.tieBreaker }
            .thenBy { it.item.guid },
    )

    val result = mutableListOf<FeiniuMediaItem>()
    val selectedIds = mutableSetOf<String>()
    val kindCounts = mutableMapOf<String, Int>()
    val genreCounts = mutableMapOf<Int, Int>()
    val maxKindCount = maxOf(1, kotlin.math.ceil(limit * 0.67).toInt())
    scored.forEach { candidate ->
        if (result.size >= limit) return@forEach
        val item = candidate.item
        val kind = if (item.type?.trim()?.lowercase() in setOf("tv", "series")) "series" else "movie"
        val primaryGenre = item.genres.firstOrNull { it > 0 }
        if ((kindCounts[kind] ?: 0) >= maxKindCount) return@forEach
        if (primaryGenre != null && (genreCounts[primaryGenre] ?: 0) >= 2) return@forEach
        result += item
        selectedIds += item.guid
        kindCounts[kind] = (kindCounts[kind] ?: 0) + 1
        if (primaryGenre != null) genreCounts[primaryGenre] = (genreCounts[primaryGenre] ?: 0) + 1
    }
    scored.forEach { candidate ->
        if (result.size < limit && selectedIds.add(candidate.item.guid)) result += candidate.item
    }
    return result
}

internal fun isFeiniuHeroCandidate(item: FeiniuMediaItem): Boolean {
    val type = item.type?.trim()?.lowercase()
    return item.guid.isNotBlank() && item.title.isNotBlank() && !item.poster.isNullOrBlank() &&
        (type == "movie" || type == "tv" || type == "series")
}

private fun uniqueFeiniuRecommendationCandidates(items: List<FeiniuMediaItem>): List<FeiniuMediaItem> {
    val result = mutableListOf<FeiniuMediaItem>()
    val indexes = mutableMapOf<String, Int>()
    items.forEach { item ->
        if (!isFeiniuHeroCandidate(item)) return@forEach
        val existingIndex = indexes[item.guid]
        if (existingIndex == null) {
            indexes[item.guid] = result.size
            result += item
        } else if (feiniuRecommendationMetadataWeight(item) >
            feiniuRecommendationMetadataWeight(result[existingIndex])
        ) {
            result[existingIndex] = item
        }
    }
    return result
}

private fun feiniuRecommendationMetadataWeight(item: FeiniuMediaItem): Int =
    (if (!item.voteAverage.isNullOrBlank()) 1 else 0) +
        (if (feiniuRecommendationYear(item) > 0) 1 else 0) +
        (if (item.genres.isNotEmpty()) 1 else 0) +
        (if (item.mediaStream?.resolutions.orEmpty().isNotEmpty()) 1 else 0)

private fun feiniuRecommendationYear(item: FeiniuMediaItem): Int {
    val source = listOf(item.releaseDate, item.firstAirDate, item.airDate, item.lastAirDate)
        .firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
        ?: return 0
    if (source.length < 4) return 0
    return source.substring(0, 4).toIntOrNull()?.takeIf { it > 0 } ?: 0
}

private fun feiniuSourceWeights(
    items: List<FeiniuMediaItem>,
    firstWeight: Double,
    lastWeight: Double,
): Map<String, Double> {
    val result = mutableMapOf<String, Double>()
    val denominator = maxOf(1, items.size - 1)
    items.forEachIndexed { index, item ->
        val id = item.guid.trim()
        if (id.isEmpty()) return@forEachIndexed
        val ratio = index.toDouble() / denominator.toDouble()
        val weight = firstWeight - (firstWeight - lastWeight) * ratio
        result[id] = maxOf(result[id] ?: 0.0, weight)
    }
    return result
}

private fun addFeiniuGenreWeights(
    target: MutableMap<Int, Int>,
    items: List<FeiniuMediaItem>,
    weight: Int,
) {
    items.forEach { item ->
        item.genres.filter { it > 0 }.forEach { genre ->
            target[genre] = (target[genre] ?: 0) + weight
        }
    }
}

private fun stableFeiniuHash(value: String): Int {
    var hash = 0L
    value.forEach { character ->
        hash = (hash * 31L + character.code.toLong()) % 2_147_483_647L
    }
    return hash.toInt()
}

private fun floorDiv(value: Long, divisor: Long): Long {
    val quotient = value / divisor
    val remainder = value % divisor
    return if (remainder != 0L && (value xor divisor) < 0L) quotient - 1L else quotient
}

internal fun feiniuRecommendationSeed(
    provider: ProviderKind,
    serverId: ServerId,
    accountId: AccountId,
    epochMs: Long,
): String {
    val date = epochCalendarDate(epochMs)
    // Legacy accountScope included provider/server/account. Keep those
    // identity boundaries explicit so equal fnOS user ids on two servers
    // never share a recommendation order.
    return "${provider.name}|${serverId.value}|${accountId.value}|${date.year}-${date.month}-${date.day}"
}

/** UTC calendar date used by the common recommendation clock. */
private fun epochCalendarDate(epochMs: Long): FeiniuCalendarDate {
    val days = floorDiv(epochMs, MILLIS_PER_DAY) + 719_468L
    val era = if (days >= 0L) days / 146_097L else (days - 146_096L) / 146_097L
    val dayOfEra = days - era * 146_097L
    val yearOfEra = (dayOfEra - dayOfEra / 1_460L + dayOfEra / 36_524L - dayOfEra / 146_096L) / 365L
    var year = yearOfEra + era * 400L
    val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
    val monthPrime = (5L * dayOfYear + 2L) / 153L
    val day = dayOfYear - (153L * monthPrime + 2L) / 5L + 1L
    val month = monthPrime + if (monthPrime < 10L) 3L else -9L
    if (month <= 2L) year += 1L
    return FeiniuCalendarDate(year.toInt(), month.toInt(), day.toInt())
}

/** UTC calendar year for recommendation recency; only the year is scored. */
internal fun epochYear(epochMs: Long): Int = epochCalendarDate(epochMs).year
