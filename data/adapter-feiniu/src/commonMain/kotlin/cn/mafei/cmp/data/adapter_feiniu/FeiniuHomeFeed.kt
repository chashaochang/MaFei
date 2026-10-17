package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.AuthenticationError
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.data.adapter_contract.HomeFeed
import cn.mafei.cmp.data.adapter_contract.HomeFeedRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuItemListData
import cn.mafei.cmp.providers.feiniu.core.FeiniuMediaItem
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayItem
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException

private const val LEGACY_RECOMMENDATION_CACHE_TTL_MS = 6L * 60L * 60L * 1_000L
private const val LEGACY_RECOMMENDATION_MIN_CANDIDATES = 24
private const val LEGACY_RECOMMENDATION_MAX_CANDIDATES = 60
private const val LEGACY_RECOMMENDATION_DETAIL_LIMIT = 6
private const val LEGACY_RECOMMENDATION_SIGNAL_DETAIL_LIMIT = 8
private const val LEGACY_HOME_LIBRARY_LIMIT = 16
private val LEGACY_RECOMMENDATION_TYPES = listOf("Movie", "TV")
private val LEGACY_HOME_LATEST_TYPES = listOf("Movie", "TV", "Episode", "Video")
private val LEGACY_HOME_RESUME_TYPES = setOf(MediaType.Movie, MediaType.Episode, MediaType.Video)

private data class FeiniuRecommendationSignals(
    val unfinished: List<FeiniuMediaItem>,
    val completed: List<FeiniuMediaItem>,
)

internal data class FeiniuLatestLibraryItem(
    val collectionId: CollectionId,
    val item: FeiniuMediaItem,
)


/** Owns home fan-out and the account-scoped recommendation cache. */
internal class FeiniuHomeFeed(
    private val adapterSession: FeiniuAdapterSession,
    private val details: FeiniuMediaDetails,
    private val nowEpochMs: () -> Long,
) {
    private val resolution get() = adapterSession.resolution
    private val serverId get() = adapterSession.serverId
    private val accountId get() = adapterSession.accountId
    private val provider get() = adapterSession.provider

    private var recommendationCacheItems: List<MediaItem> = emptyList()
    private var recommendationCacheLimit: Int = 0
    private var recommendationCacheExpiresAt: Long = 0L

    suspend fun home(request: HomeFeedRequest): HomeFeed {
        return coroutineScope {
            val now = nowEpochMs()
            val cachedRecommendations = recommendationCacheItems
                .takeIf {
                    now < recommendationCacheExpiresAt &&
                        recommendationCacheLimit >= request.recommendationLimit
                }
                ?.take(request.recommendationLimit)
            val candidateLimit = minOf(
                LEGACY_RECOMMENDATION_MAX_CANDIDATES,
                maxOf(LEGACY_RECOMMENDATION_MIN_CANDIDATES, request.recommendationLimit * 4),
            )

            // Home shelves are independent in the ArkUI provider. Start their
            // wire calls together, but keep separate payloads: "最近添加" first
            // reads mediadb/list and interleaves one page from every visible
            // library; recommendation candidates stay strictly Movie/TV.
            val latestDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                latestAcrossLibraries(request.latestLimit)
            }
            val playHistoryDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                adapterSession.translate { adapterSession.facade.catalog.playHistory(resolution) }
            }
            val recommendationLatestDeferred = if (cachedRecommendations == null) {
                async(start = CoroutineStart.UNDISPATCHED) {
                    optionalRecommendationPage {
                        adapterSession.facade.catalog.items(
                            resolution = resolution,
                            page = 1,
                            pageSize = candidateLimit,
                            sortColumn = "create_time",
                            sortType = "DESC",
                            itemTypes = LEGACY_RECOMMENDATION_TYPES,
                        )
                    }
                }
            } else {
                null
            }
            val highestRatedDeferred = if (cachedRecommendations == null) {
                async(start = CoroutineStart.UNDISPATCHED) {
                    optionalRecommendationPage {
                        adapterSession.facade.catalog.items(
                            resolution = resolution,
                            page = 1,
                            pageSize = candidateLimit,
                            sortColumn = "vote_average",
                            sortType = "DESC",
                            itemTypes = LEGACY_RECOMMENDATION_TYPES,
                        )
                    }
                }
            } else {
                null
            }
            val favoritesDeferred = if (cachedRecommendations == null) {
                async(start = CoroutineStart.UNDISPATCHED) {
                    optionalRecommendationPage {
                        adapterSession.facade.catalog.favorites(
                            resolution = resolution,
                            page = 1,
                            pageSize = candidateLimit,
                            sortColumn = "create_time",
                            sortType = "DESC",
                            itemTypes = LEGACY_RECOMMENDATION_TYPES,
                        )
                    }
                }
            } else {
                null
            }

            val latest = latestDeferred.await()
            val playHistory = playHistoryDeferred.await()
            val recommendations = cachedRecommendations ?: run {
                val recommendationLatest = recommendationLatestDeferred?.await().orEmpty()
                val highestRated = highestRatedDeferred?.await().orEmpty()
                val favorites = favoritesDeferred?.await().orEmpty()
                val selected = if ((recommendationLatest + highestRated).none(::isFeiniuHeroCandidate)) {
                    emptyList()
                } else {
                    val signals = recommendationSignals(
                        playItems = playHistory,
                        knownItems = recommendationLatest + highestRated + favorites,
                    )
                    rankFeiniuRecommendations(
                        latest = recommendationLatest,
                        highestRated = highestRated,
                        unfinished = signals.unfinished,
                        completed = signals.completed,
                        favorites = favorites,
                        resultLimit = request.recommendationLimit,
                        seed = recommendationSeed(now),
                        currentYear = epochYear(now),
                    )
                }
                val recommendationGenres = adapterSession.genreNamesFor(selected)
                val enriched = selected.mapIndexed { index, item ->
                    val mapped = item.toMediaItem(
                        serverId = serverId,
                        imageUrl = adapterSession::imageUrl,
                        genreNames = recommendationGenres,
                    ) ?: return@mapIndexed null
                    if (index < LEGACY_RECOMMENDATION_DETAIL_LIMIT) {
                        recommendationWithDetail(mapped, recommendationGenres)
                    } else {
                        mapped
                    }
                }.filterNotNull()
                recommendationCacheItems = enriched
                recommendationCacheLimit = request.recommendationLimit
                recommendationCacheExpiresAt = now + LEGACY_RECOMMENDATION_CACHE_TTL_MS
                enriched
            }
            val resume = details.enrichedResumeItems(playHistory, LEGACY_HOME_RESUME_TYPES)
                .take(request.resumeLimit)
            HomeFeed(
                recommendations = recommendations,
                resume = resume,
                // fnOS has no global next-up endpoint. The legacy client also kept
                // this home section empty instead of issuing an N+1 series walk.
                nextUp = emptyList(),
                latest = latest,
            )
        }
    }

    /**
     * Mechanical port of legacy `latestAcrossLibraries`: read media libraries,
     * fetch a small date-descending page from each one, then round-robin the
     * pages so a large library cannot monopolise the home shelf.
     */
    private suspend fun latestAcrossLibraries(limit: Int): List<MediaItem> {
        val libraries = adapterSession.translate { adapterSession.facade.catalog.mediaLibraries(resolution) }
            .filter { library ->
                library.guid.isNotBlank() &&
                    !library.category.isFeiniuLiveLibrary()
            }
        val pageSize = maxOf(limit, LEGACY_HOME_LIBRARY_LIMIT)
        val pages = coroutineScope {
            libraries.map { library ->
                async(start = CoroutineStart.UNDISPATCHED) {
                    val collectionId = CollectionId(library.guid)
                    val items = try {
                        adapterSession.translate {
                            adapterSession.facade.catalog.items(
                                resolution = resolution,
                                ancestorGuid = library.guid,
                                page = 1,
                                pageSize = pageSize,
                                sortColumn = "create_time",
                                sortType = "DESC",
                                itemTypes = LEGACY_HOME_LATEST_TYPES,
                            )
                        }.list
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (authentication: AuthenticationError) {
                        throw authentication
                    } catch (_: Exception) {
                        // One unavailable media library did not blank the
                        // remaining libraries in the ArkUI implementation.
                        emptyList()
                    }
                    items.map { item -> FeiniuLatestLibraryItem(collectionId, item) }
                }
            }.awaitAll()
        }
        val source = interleaveFeiniuLatest(pages, limit)
            .filter { it.item.guid.isNotBlank() && it.item.type.toFeiniuMediaType() in LEGACY_HOME_RESUME_TYPES + MediaType.Series }
        val latestGenres = adapterSession.genreNamesFor(source.map { it.item })
        return coroutineScope {
            source.map { entry ->
                async(start = CoroutineStart.UNDISPATCHED) { details.latestMediaItem(entry, latestGenres) }
            }.awaitAll()
        }.filterNotNull()
            .distinctBy { it.id }
            .take(limit)
    }

    private suspend fun optionalRecommendationPage(
        block: suspend () -> FeiniuItemListData,
    ): List<FeiniuMediaItem> = try {
        adapterSession.translate(block).list
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (authentication: AuthenticationError) {
        throw authentication
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun recommendationSignals(
        playItems: List<FeiniuPlayItem>,
        knownItems: List<FeiniuMediaItem>,
    ): FeiniuRecommendationSignals {
        val ordered = playItems.sortedWith(
            compareByDescending<FeiniuPlayItem> { maxOf(it.watchedEpochSeconds ?: 0L, it.epochSeconds ?: 0L) },
        )
        val signalItems = coroutineScope {
            ordered.map { item ->
                async(start = CoroutineStart.UNDISPATCHED) { recommendationSignalItem(item) }
            }.awaitAll()
        }
        val unfinishedBases = mutableListOf<FeiniuMediaItem>()
        val completed = mutableListOf<FeiniuMediaItem>()
        val seen = mutableSetOf<String>()
        ordered.forEachIndexed { index, playItem ->
            val item = signalItems[index]
            val id = item.guid.trim()
            if (id.isEmpty() || id in seen) return@forEachIndexed
            val progress = playItem.mediaStream?.progress
                ?.takeIf { it.isFinite() }
                ?.coerceIn(0.0, 100.0)
                ?: 0.0
            when {
                (playItem.watched ?: 0) > 0 || progress >= 95.0 -> {
                    completed += item
                    seen += id
                }
                progress > 0.0 -> {
                    unfinishedBases += item
                    seen += id
                }
            }
        }
        val knownById = knownItems.mapNotNull { item ->
            item.guid.trim().takeIf(String::isNotEmpty)?.let { it to item }
        }.toMap()
        val unfinished = coroutineScope {
            unfinishedBases.take(LEGACY_RECOMMENDATION_SIGNAL_DETAIL_LIMIT).map { item ->
                async(start = CoroutineStart.UNDISPATCHED) {
                    knownById[item.guid] ?: recommendationItemWithDetail(item)
                }
            }.awaitAll()
        }
        return FeiniuRecommendationSignals(unfinished = unfinished, completed = completed)
    }

    private suspend fun recommendationSignalItem(item: FeiniuPlayItem): FeiniuMediaItem {
        val fallback = item.toRecommendationMediaItem()
        if (item.toFeiniuPlayMediaType() != MediaType.Episode) return fallback

        var parentId = item.parentGuid?.trim().orEmpty()
        if (parentId.isEmpty()) {
            parentId = adapterSession.optionalItemDetail(item.guid)?.parentGuid?.trim().orEmpty()
        }
        val parent = adapterSession.optionalItemDetail(parentId) ?: return fallback
        val series = when (parent.type.toFeiniuMediaType()) {
            MediaType.Series -> parent
            MediaType.Season -> adapterSession.optionalItemDetail(parent.parentGuid)
            else -> null
        }
        return series?.toRecommendationMediaItem(fallback) ?: fallback
    }

    private suspend fun recommendationItemWithDetail(fallback: FeiniuMediaItem): FeiniuMediaItem =
        adapterSession.optionalItemDetail(fallback.guid)?.toRecommendationMediaItem(fallback) ?: fallback

    private fun recommendationSeed(now: Long): String =
        feiniuRecommendationSeed(provider, serverId, accountId, now)

    /** The old provider enriched the selected first six recommendations to obtain `backdrops`. */
    private suspend fun recommendationWithDetail(
        fallback: MediaItem,
        genreNames: Map<Int, String> = emptyMap(),
    ): MediaItem {
        val detail = try {
            adapterSession.translate { adapterSession.facade.catalog.itemDetail(resolution, fallback.id.value) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (authentication: AuthenticationError) {
            throw authentication
        } catch (_: Exception) {
            return fallback
        }
        val mapped = detail.toMediaItem(serverId, adapterSession::imageUrl, genreNames = genreNames)
            ?.takeIf { it.id == fallback.id }
            ?: return fallback
        val detailedKinds = mapped.images.mapTo(mutableSetOf()) { it.kind }
        val mappedUserData = mapped.userData
        val fallbackUserData = fallback.userData
        return fallback.copy(
            title = mapped.title.takeIf { it.isNotBlank() } ?: fallback.title,
            mediaType = mapped.mediaType.takeUnless { it == cn.mafei.cmp.core.domain.MediaType.Unknown }
                ?: fallback.mediaType,
            originalTitle = mapped.originalTitle ?: fallback.originalTitle,
            overview = mapped.overview?.takeIf { it.isNotBlank() } ?: fallback.overview,
            seriesId = mapped.seriesId ?: fallback.seriesId,
            seriesTitle = mapped.seriesTitle ?: fallback.seriesTitle,
            seasonId = mapped.seasonId ?: fallback.seasonId,
            seasonTitle = mapped.seasonTitle ?: fallback.seasonTitle,
            seasonNumber = mapped.seasonNumber ?: fallback.seasonNumber,
            episodeNumber = mapped.episodeNumber ?: fallback.episodeNumber,
            childCount = mapped.childCount ?: fallback.childCount,
            year = mapped.year ?: fallback.year,
            premiereEpochMs = mapped.premiereEpochMs ?: fallback.premiereEpochMs,
            runtimeMs = mapped.runtimeMs ?: fallback.runtimeMs,
            communityRating = mapped.communityRating ?: fallback.communityRating,
            officialRating = mapped.officialRating ?: fallback.officialRating,
            genres = mapped.genres.ifEmpty { fallback.genres },
            tags = mapped.tags.ifEmpty { fallback.tags },
            images = mapped.images + fallback.images.filter { it.kind !in detailedKinds },
            userData = mappedUserData.copy(
                isFavorite = mappedUserData.isFavorite || fallbackUserData.isFavorite,
                isPlayed = mappedUserData.isPlayed || fallbackUserData.isPlayed,
                playCount = mappedUserData.playCount ?: fallbackUserData.playCount,
                playbackPositionMs = mappedUserData.playbackPositionMs.takeIf { it > 0L }
                    ?: fallbackUserData.playbackPositionMs,
                playedPercentage = mappedUserData.playedPercentage ?: fallbackUserData.playedPercentage,
                lastPlayedEpochMs = mappedUserData.lastPlayedEpochMs ?: fallbackUserData.lastPlayedEpochMs,
            ),
            canPlay = detail.canPlay?.let { it > 0 && detail.playError.isNullOrBlank() } ?: fallback.canPlay,
        )
    }

}

private fun interleaveFeiniuLatest(
    pages: List<List<FeiniuLatestLibraryItem>>,
    limit: Int,
): List<FeiniuLatestLibraryItem> {
    val result = ArrayList<FeiniuLatestLibraryItem>(limit)
    var itemIndex = 0
    var appended = true
    while (result.size < limit && appended) {
        appended = false
        pages.forEach { page ->
            if (result.size < limit) {
                page.getOrNull(itemIndex)?.let { item ->
                    result += item
                    appended = true
                }
            }
        }
        itemIndex += 1
    }
    return result
}
