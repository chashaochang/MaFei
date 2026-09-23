package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.AuthenticationError
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaPerson
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaUserData
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageCursor
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ResourceNotFoundError
import cn.mafei.cmp.core.domain.ServerError
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.domain.SortDirection
import cn.mafei.cmp.core.domain.MediaSort
import cn.mafei.cmp.core.domain.MediaSortField
import cn.mafei.cmp.core.domain.mediaLanguageDisplayName
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.adapter_contract.AdapterCapability
import cn.mafei.cmp.data.adapter_contract.HomeFeed
import cn.mafei.cmp.data.adapter_contract.HomeFeedRequest
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.OffsetPageCursorCodec
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.data.adapter_contract.PlaybackEventKind
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.data.adapter_contract.PlaybackQuality
import cn.mafei.cmp.data.adapter_contract.PlaybackTrack
import cn.mafei.cmp.data.adapter_contract.PlaybackTrackKind
import cn.mafei.cmp.data.adapter_contract.PlaybackTransport
import cn.mafei.cmp.data.adapter_contract.RefusalReason
import cn.mafei.cmp.data.adapter_contract.requireCapability
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuDigest
import cn.mafei.cmp.providers.feiniu.core.FeiniuIdentity
import cn.mafei.cmp.providers.feiniu.core.FeiniuItemListData
import cn.mafei.cmp.providers.feiniu.core.FeiniuItemDetail
import cn.mafei.cmp.providers.feiniu.core.FeiniuMediaItem
import cn.mafei.cmp.providers.feiniu.core.FeiniuOfficialStreamRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuOfficialStreamRequestHeader
import cn.mafei.cmp.providers.feiniu.core.FeiniuPersonItem
import cn.mafei.cmp.providers.feiniu.core.FeiniuPersonItemListRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayInfoData
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayItem
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayRecordRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuStreamListData
import cn.mafei.cmp.providers.feiniu.facade.FeiniuFacade
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val LEGACY_RECOMMENDATION_CACHE_TTL_MS = 6L * 60L * 60L * 1_000L
private const val LEGACY_RECOMMENDATION_MIN_CANDIDATES = 24
private const val LEGACY_RECOMMENDATION_MAX_CANDIDATES = 60
private const val LEGACY_RECOMMENDATION_DETAIL_LIMIT = 6
private const val LEGACY_RECOMMENDATION_SIGNAL_DETAIL_LIMIT = 8
private const val LEGACY_HOME_LIBRARY_LIMIT = 16
private const val MILLIS_PER_DAY = 86_400_000L

private val LEGACY_RECOMMENDATION_TYPES = listOf("Movie", "TV")
private val LEGACY_HOME_LATEST_TYPES = listOf("Movie", "TV", "Episode", "Video")
private val LEGACY_HOME_RESUME_TYPES = setOf(MediaType.Movie, MediaType.Episode, MediaType.Video)

@OptIn(ExperimentalTime::class)
private fun systemEpochMilliseconds(): Long = Clock.System.now().toEpochMilliseconds()

private data class FeiniuEpisodeCandidate(
    val seasonId: String?,
    val item: FeiniuMediaItem,
)

private data class FeiniuRecommendationSignals(
    val unfinished: List<FeiniuMediaItem>,
    val completed: List<FeiniuMediaItem>,
)

private data class FeiniuScoredRecommendation(
    val item: FeiniuMediaItem,
    val score: Double,
    val tieBreaker: Int,
)

private data class FeiniuLatestLibraryItem(
    val collectionId: CollectionId,
    val item: FeiniuMediaItem,
)

private data class FeiniuCalendarDate(
    val year: Int,
    val month: Int,
    val day: Int,
)

private fun FeiniuPlayItem.toRecommendationMediaItem(): FeiniuMediaItem = FeiniuMediaItem(
    guid = guid,
    title = title,
    type = if (toFeiniuPlayMediaType() == MediaType.Episode) "Episode" else type,
    poster = stillPath?.takeIf(String::isNotBlank) ?: poster,
    canPlay = canPlay,
    parentGuid = parentGuid,
    parentTitle = parentTitle,
    ancestorGuid = ancestorGuid,
    ancestorName = ancestorName,
    ancestorCategory = ancestorCategory,
    tvTitle = tvTitle,
    watched = watched,
    watchedEpochSeconds = watchedEpochSeconds,
    isFavorite = isFavorite,
    durationSeconds = durationSeconds,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    mediaStream = mediaStream,
)

private fun FeiniuItemDetail.toRecommendationMediaItem(fallback: FeiniuMediaItem): FeiniuMediaItem =
    FeiniuMediaItem(
        guid = guid?.trim()?.takeIf(String::isNotEmpty) ?: fallback.guid,
        title = title?.takeIf(String::isNotBlank) ?: fallback.title,
        type = type?.takeIf(String::isNotBlank) ?: fallback.type,
        poster = poster?.takeIf(String::isNotBlank)
            ?: posters?.takeIf(String::isNotBlank)
            ?: stillPath?.takeIf(String::isNotBlank)
            ?: fallback.poster,
        canPlay = canPlay ?: fallback.canPlay,
        parentGuid = parentGuid?.takeIf(String::isNotBlank) ?: fallback.parentGuid,
        parentTitle = parentTitle?.takeIf(String::isNotBlank) ?: fallback.parentTitle,
        ancestorGuid = ancestorGuid?.takeIf(String::isNotBlank) ?: fallback.ancestorGuid,
        ancestorName = ancestorName?.takeIf(String::isNotBlank) ?: fallback.ancestorName,
        ancestorCategory = ancestorCategory?.takeIf(String::isNotBlank) ?: fallback.ancestorCategory,
        tvTitle = tvTitle?.takeIf(String::isNotBlank) ?: fallback.tvTitle,
        watched = watched ?: isWatched ?: fallback.watched,
        watchedEpochSeconds = watchedEpochSeconds ?: fallback.watchedEpochSeconds,
        isFavorite = isFavorite ?: fallback.isFavorite,
        overview = overview?.takeIf(String::isNotBlank) ?: fallback.overview,
        status = status?.takeIf(String::isNotBlank) ?: fallback.status,
        voteAverage = voteAverage?.takeIf(String::isNotBlank) ?: fallback.voteAverage,
        durationSeconds = durationSeconds ?: fallback.durationSeconds,
        runtimeMinutes = runtimeMinutes ?: fallback.runtimeMinutes,
        seasonNumber = seasonNumber ?: fallback.seasonNumber,
        episodeNumber = episodeNumber ?: fallback.episodeNumber,
        numberOfSeasons = numberOfSeasons ?: fallback.numberOfSeasons,
        numberOfEpisodes = numberOfEpisodes ?: fallback.numberOfEpisodes,
        localNumberOfSeasons = localNumberOfSeasons ?: fallback.localNumberOfSeasons,
        localNumberOfEpisodes = localNumberOfEpisodes ?: fallback.localNumberOfEpisodes,
        singleChildGuid = singleChildGuid?.takeIf(String::isNotBlank) ?: fallback.singleChildGuid,
        playItemGuid = playItemGuid?.takeIf(String::isNotBlank) ?: fallback.playItemGuid,
        mediaGuid = mediaGuid?.takeIf(String::isNotBlank) ?: fallback.mediaGuid,
        videoGuid = videoGuid?.takeIf(String::isNotBlank) ?: fallback.videoGuid,
        audioGuid = audioGuid?.takeIf(String::isNotBlank) ?: fallback.audioGuid,
        subtitleGuid = subtitleGuid?.takeIf(String::isNotBlank) ?: fallback.subtitleGuid,
        fileName = fileName?.takeIf(String::isNotBlank) ?: fallback.fileName,
        releaseDate = releaseDate?.takeIf(String::isNotBlank) ?: fallback.releaseDate,
        airDate = airDate?.takeIf(String::isNotBlank) ?: fallback.airDate,
        firstAirDate = firstAirDate?.takeIf(String::isNotBlank) ?: fallback.firstAirDate,
        lastAirDate = lastAirDate?.takeIf(String::isNotBlank) ?: fallback.lastAirDate,
        genres = genres.ifEmpty { fallback.genres },
        logos = logos?.takeIf(String::isNotBlank) ?: fallback.logos,
        stillPath = stillPath?.takeIf(String::isNotBlank) ?: fallback.stillPath,
        mediaStream = mediaStream ?: fallback.mediaStream,
    )

/**
 * Feiniu implementation of the provider-neutral [MediaProviderAdapter]
 * (GLM-04C), consuming the frozen [FeiniuFacade] bound to one server +
 * account + host resolution. Failures surface as `DomainError` per the
 * contract; [CancellationException] passes through untouched.
 *
 * Frozen decisions (docs/superpowers/plans/2026-09-08-glm04c-feiniu-adapter.md):
 * - the dedicated favorites endpoint answers favorites-only listings. The
 *   fixed legacy Movie/Series/Episode/Video kinds and sort are forwarded, but
 *   arbitrary filter composition remains unavailable (`favoritesFilter=false`);
 * - watched filtering uses the legacy `"1"` / `"0"` tag literals from
 *   `FeiniuVideoCatalogProvider.itemListPayload`; genres (Int tag) and decade
 *   (string tag) still refuse because their request values remain unproven;
 * - `/episode/list/{guid}` takes a SEASON guid; the adapter recreates the
 *   legacy provider's whole-series walk when `seasonId == null`;
 * - progress reports convert position ms to seconds and skip ts<=0 entirely
 *   (legacy FeiniuVideoPlaybackProvider.ets:739-752).
 */
class FeiniuMediaProviderAdapter(
    private val facade: FeiniuFacade,
    override val serverId: ServerId,
    override val accountId: AccountId,
    /** Host candidates this adapter negotiates against (preferred + fallbacks). */
    private val resolution: FeiniuHostResolution,
    private val nowEpochMs: () -> Long = ::systemEpochMilliseconds,
) : MediaProviderAdapter {

    override val provider: ProviderKind = ProviderKind.Feiniu

    override val capabilities = AdapterCapabilities(
        search = true,
        favoritesRead = true,
        favoritesFilter = false,
        favoritesEdit = true,
        // Legacy FeiniuVideoCatalogProvider.itemListPayload writes
        // tags.watched as the proven "1" / "0" literals.
        watchedFilter = true,
        watchedEdit = true,
        playbackReporting = true,
    )

    /** Streams this instance prepared; reportPlayback accepts only these. */
    private val preparedStreams = mutableListOf<PlaybackPreparation.Ready>()
    private var genreNames: Map<Int, String>? = null
    private var recommendationCacheItems: List<MediaItem> = emptyList()
    private var recommendationCacheLimit: Int = 0
    private var recommendationCacheExpiresAt: Long = 0L
    // Construct after login/restore. A different identity needs a new adapter;
    // cookie revisions within the same identity remain usable.
    private val boundIdentity = facade.credentials.value.identityGeneration

    private fun currentOperation(): cn.mafei.cmp.providers.feiniu.core.FeiniuSessionSnapshot {
        val snapshot = facade.credentials.value
        if (snapshot.identityGeneration != boundIdentity) {
            throw CancellationException("Feiniu adapter identity was replaced")
        }
        return snapshot
    }

    private fun imageUrl(imagePath: String): String {
        val normalized = imagePath.trim()
        return if (normalized.startsWith("http://", ignoreCase = true) ||
            normalized.startsWith("https://", ignoreCase = true)
        ) {
            normalized
        } else {
            val requestPath = if (normalized.startsWith('/')) normalized else "/$normalized"
            facade.assets.imageUrl(resolution.preferred.baseUrl, requestPath)
        }
    }

    override suspend fun isAdministrator(): Boolean {
        val operation = currentOperation()
        return translate {
            facade.auth.userInfo(resolution, pinnedOperation = operation)
        }.isAdmin == 1
    }

    private suspend fun <T> translate(block: suspend () -> T): T =
        try {
            currentOperation()
            block().also { currentOperation() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: cn.mafei.cmp.providers.feiniu.core.FeiniuError) {
            throw e.toDomainError()
        }

    override suspend fun collections(request: PageRequest): Page<MediaCollection> {
        // Single-chunk surface: a non-null cursor is a caller bug, never a
        // license to re-serve page one.
        request.cursor?.let { throw ClientConfigError("collections cannot honor cursor '$it' (single-chunk listing)") }
        val libraries = translate { facade.catalog.mediaLibraries(resolution) }
            .filterNot { it.category.isFeiniuLiveLibrary() }
        val collections = libraries.mapNotNull { it.toMediaCollection(serverId, ::imageUrl) }
        return Page.of(collections, nextCursor = null, totalEstimate = collections.size)
    }

    override suspend fun home(request: HomeFeedRequest): HomeFeed {
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
                translate { facade.catalog.playHistory(resolution) }
            }
            val recommendationLatestDeferred = if (cachedRecommendations == null) {
                async(start = CoroutineStart.UNDISPATCHED) {
                    optionalRecommendationPage {
                        facade.catalog.items(
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
                        facade.catalog.items(
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
                        facade.catalog.favorites(
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
                val recommendationGenres = genreNamesFor(selected)
                val enriched = selected.mapIndexed { index, item ->
                    val mapped = item.toMediaItem(
                        serverId = serverId,
                        imageUrl = ::imageUrl,
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
            val resume = enrichedResumeItems(playHistory, LEGACY_HOME_RESUME_TYPES)
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
        val libraries = translate { facade.catalog.mediaLibraries(resolution) }
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
                        translate {
                            facade.catalog.items(
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
        val latestGenres = genreNamesFor(source.map { it.item })
        return coroutineScope {
            source.map { entry ->
                async(start = CoroutineStart.UNDISPATCHED) { latestMediaItem(entry, latestGenres) }
            }.awaitAll()
        }.filterNotNull()
            .distinctBy { it.id }
            .take(limit)
    }

    private suspend fun optionalRecommendationPage(
        block: suspend () -> FeiniuItemListData,
    ): List<FeiniuMediaItem> = try {
        translate(block).list
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
            parentId = optionalItemDetail(item.guid)?.parentGuid?.trim().orEmpty()
        }
        val parent = optionalItemDetail(parentId) ?: return fallback
        val series = when (parent.type.toFeiniuMediaType()) {
            MediaType.Series -> parent
            MediaType.Season -> optionalItemDetail(parent.parentGuid)
            else -> null
        }
        return series?.toRecommendationMediaItem(fallback) ?: fallback
    }

    private suspend fun recommendationItemWithDetail(fallback: FeiniuMediaItem): FeiniuMediaItem =
        optionalItemDetail(fallback.guid)?.toRecommendationMediaItem(fallback) ?: fallback

    private fun recommendationSeed(now: Long): String =
        feiniuRecommendationSeed(provider, serverId, accountId, now)

    /** The old provider enriched the selected first six recommendations to obtain `backdrops`. */
    private suspend fun recommendationWithDetail(
        fallback: MediaItem,
        genreNames: Map<Int, String> = emptyMap(),
    ): MediaItem {
        val detail = try {
            translate { facade.catalog.itemDetail(resolution, fallback.id.value) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (authentication: AuthenticationError) {
            throw authentication
        } catch (_: Exception) {
            return fallback
        }
        val mapped = detail.toMediaItem(serverId, ::imageUrl, genreNames = genreNames)
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

    override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> {
        val filter = request.filter
        if (!filter.searchTerm.isNullOrBlank()) {
            throw ClientConfigError("items() does not free-text search; use search()")
        }
        val position = FeiniuPagePosition.decode(request)
        val page = position.page
        val pageSize = request.pageSize
        if (filter.favoritesOnly) {
            requireCapability(capabilities, AdapterCapability.FavoritesRead)
            // Old FeiniuVideoCatalogProvider sent the same type and sort fields
            // to /favorite/list as /item/list. Keep those, but continue to
            // reject filters for which the legacy request had no proven wire value.
            if (filter.genres.isNotEmpty() || filter.years.isNotEmpty() ||
                filter.playedOnly || filter.unplayedOnly
            ) {
                throw ClientConfigError("Feiniu favorites listing cannot combine further filters (favoritesFilter unsupported)")
            }
            val (sortColumn, sortType) = request.sort.toFeiniuSort()
            val itemTypes = filter.mediaTypes.map { it.toFeiniuItemType() }
            val data = translate {
                facade.catalog.favorites(
                    resolution = resolution,
                    ancestorGuid = collectionId?.value,
                    page = page,
                    pageSize = pageSize,
                    sortColumn = sortColumn,
                    sortType = sortType,
                    itemTypes = itemTypes.takeIf { it.isNotEmpty() },
                )
            }
            val result = toPage(data, request, position, collectionId, genreNamesFor(data.list))
            return result.copy(
                items = result.items.filter { filter.mediaTypes.isEmpty() || it.mediaType in filter.mediaTypes },
            )
        }
        if (filter.playedOnly || filter.unplayedOnly) requireCapability(capabilities, AdapterCapability.WatchedFilter)
        if (filter.genres.isNotEmpty()) throwUnsupportedFeiniuFilter("genres")
        if (filter.years.isNotEmpty()) throwUnsupportedFeiniuFilter("decade")
        val itemTypes = filter.mediaTypes.map { it.toFeiniuItemType() }.takeIf { it.isNotEmpty() }
        val watchedTag = when {
            filter.playedOnly -> "1"
            filter.unplayedOnly -> "0"
            else -> null
        }
        val (sortColumn, sortType) = request.sort.toFeiniuSort()
        val data = translate {
            facade.catalog.items(
                resolution = resolution,
                ancestorGuid = collectionId?.value,
                category = null,
                itemTypes = itemTypes,
                page = page,
                pageSize = pageSize,
                sortColumn = sortColumn,
                sortType = sortType,
                watchedTag = watchedTag,
            )
        }
        val result = toPage(data, request, position, collectionId, genreNamesFor(data.list))
        return result.copy(
            items = result.items.filter { filter.mediaTypes.isEmpty() || it.mediaType in filter.mediaTypes },
        )
    }

    override suspend fun search(term: String, request: PageRequest): Page<MediaItem> {
        requireCapability(capabilities, AdapterCapability.Search)
        // Legacy FeiniuVideoCatalogProvider accepted `kinds` and filtered the
        // unpaged /search/list response locally. Preserve exactly that one
        // filter; the endpoint still cannot express the other catalog filters.
        val filter = request.filter
        if (filter.genres.isNotEmpty() || filter.years.isNotEmpty() ||
            !filter.searchTerm.isNullOrBlank() || filter.favoritesOnly ||
            filter.playedOnly || filter.unplayedOnly
        ) {
            throw ClientConfigError("Feiniu search only supports the legacy media type filter")
        }
        if (request.sort != null) {
            throw ClientConfigError("Feiniu search has no server-side sort")
        }
        val offset = request.cursor?.let(OffsetPageCursorCodec::decode) ?: 0
        val query = term.trim()
        if (query.isEmpty()) return Page.of(emptyList(), nextCursor = null, totalEstimate = 0)
        // `/search/list` itself is unpaged. The ArkUI provider re-queried it,
        // filtered by kind, then sliced startIndex/limit locally; the opaque
        // cursor carries exactly that startIndex across calls.
        val all = translate { facade.catalog.search(resolution, query) }
        val names = genreNamesFor(all)
        val items = all.mapNotNull { it.toMediaItem(serverId, ::imageUrl, genreNames = names) }
            .filter { filter.mediaTypes.isEmpty() || it.mediaType in filter.mediaTypes }
        val selected = items.drop(offset).take(request.pageSize)
        val nextOffset = offset.toLong() + selected.size.toLong()
        val next = nextOffset
            .takeIf { selected.isNotEmpty() && it < items.size.toLong() && it <= Int.MAX_VALUE.toLong() }
            ?.toInt()
            ?.let(OffsetPageCursorCodec::encode)
        return Page.of(selected, nextCursor = next, totalEstimate = items.size)
    }

    override suspend fun item(id: MediaId): MediaItem {
        val detail = translate { facade.catalog.itemDetail(resolution, id.value) }
        // Legacy guard: a detail payload for another guid is not this item.
        if (detail.guid?.trim()?.equals(id.value, ignoreCase = false) == false) {
            throw ResourceNotFoundError(resource = "feiniu item $id (server returned ${detail.guid})")
        }
        val mediaType = detail.type.toFeiniuMediaType()
        return coroutineScope {
            val peopleDeferred = async(start = CoroutineStart.UNDISPATCHED) { optionalPeople(id.value) }
            val playInfoDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                if (mediaType.supportsFeiniuPlayInfo()) optionalPlayInfo(id.value) else null
            }
            val streamDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                if (mediaType.supportsFeiniuDetailStreams()) {
                    detailStreams(detail, playInfoDeferred.await())
                } else {
                    cn.mafei.cmp.providers.feiniu.core.FeiniuStreamListData()
                }
            }
            val genresDeferred = async(start = CoroutineStart.UNDISPATCHED) { genreNames() }
            val contextDeferred = async(start = CoroutineStart.UNDISPATCHED) { detailContext(detail, mediaType) }
            detail.toMediaItem(
                serverId = serverId,
                imageUrl = ::imageUrl,
                people = peopleDeferred.await(),
                streams = streamDeferred.await(),
                itemFiles = emptyList(),
                playInfo = playInfoDeferred.await(),
                genreNames = genresDeferred.await(),
                context = contextDeferred.await(),
            )
        }
            ?: throw ResourceNotFoundError(resource = "feiniu item $id")
    }

    override suspend fun person(id: MediaId): MediaPerson =
        translate { facade.catalog.person(resolution, id.value) }.toMediaPerson(serverId, ::imageUrl)
            ?: throw ResourceNotFoundError(resource = "feiniu person $id")

    override suspend fun itemsByPerson(personId: MediaId, request: PageRequest): Page<MediaItem> {
        if (!request.filter.searchTerm.isNullOrBlank()) {
            throw ClientConfigError("itemsByPerson() does not free-text search")
        }
        if (request.filter.favoritesOnly || request.filter.playedOnly || request.filter.unplayedOnly ||
            request.filter.genres.isNotEmpty() || request.filter.years.isNotEmpty()
        ) {
            throw ClientConfigError("Feiniu person work endpoint cannot express user-state, genre, or year filters")
        }
        val allowedTypes = request.filter.mediaTypes
        val offset = request.cursor?.let(OffsetPageCursorCodec::decode) ?: 0
        val pageSize = request.pageSize
        val (sortColumn, sortType) = request.sort.toFeiniuPersonSort()
        val jobs = listOf("Actor", "Director", "Screenplay")
        val sources = mutableListOf<FeiniuPersonItem>()
        var total = 0
        for (job in jobs) {
            val page = personItemPage(
                personId = personId,
                job = job,
                startIndex = offset,
                pageSize = pageSize,
                sortColumn = sortColumn,
                sortType = sortType,
            )
            sources += page.items
            total += page.total
        }
        val items = sources.asSequence()
            .mapNotNull { it.toMediaItem(serverId, ::imageUrl) }
            .filter { allowedTypes.isEmpty() || it.mediaType in allowedTypes }
            .distinctBy { it.id }
            .take(pageSize)
            .toList()
        val sourceCount = minOf(pageSize, sources.size)
        val nextOffset = offset + sourceCount
        val next = nextOffset.takeIf { sourceCount >= pageSize && it < total }?.let(OffsetPageCursorCodec::encode)
        return Page.of(items, nextCursor = next, totalEstimate = total)
    }

    override suspend fun seasons(seriesId: MediaId): Page<MediaItem> {
        val seasons = translate { facade.catalog.seasons(resolution, seriesId.value) }
        val names = genreNamesFor(seasons)
        val items = seasons.mapNotNull { season ->
            season.toMediaItem(serverId, ::imageUrl, genreNames = names)?.copy(
                title = season.title.takeIf(String::isNotBlank)
                    ?: season.seasonNumber?.takeIf { it > 0 }?.let { "第 $it 季" }
                    ?: "剧季",
                mediaType = MediaType.Season,
                seriesId = seriesId,
            )
        }
        return Page.of(items, nextCursor = null, totalEstimate = items.size)
    }

    override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> {
        if (!request.filter.isEmpty || request.sort != null) {
            throw ClientConfigError("episodes() cannot express sort or user-state/type filters on the Feiniu episode endpoint")
        }
        // The wire endpoint takes one season guid, but the legacy provider's
        // public getEpisodes(seriesRef, seasonRef?) contract also supported a
        // whole-series walk by loading seasons and concatenating their episode
        // lists before applying startIndex/limit. Preserve that behavior here.
        val offset = request.cursor?.let(OffsetPageCursorCodec::decode) ?: 0
        val candidates = if (seasonId == null) {
            feiniuEpisodeCandidates(seriesId)
        } else {
            translate { facade.catalog.episodes(resolution, seasonId.value) }
                .map { FeiniuEpisodeCandidate(seasonId.value, it) }
        }
        val selected = candidates.drop(offset).take(request.pageSize)
        val names = genreNamesFor(selected.map { it.item })
        val items = selected.mapNotNull { candidate ->
            candidate.item.toMediaItem(serverId, ::imageUrl, genreNames = names)?.copy(
                title = candidate.item.title.takeIf(String::isNotBlank)
                    ?: candidate.item.episodeNumber?.takeIf { it > 0 }?.let { "第 $it 集" }
                    ?: "剧集",
                seriesId = seriesId,
                seasonId = candidate.seasonId?.let(::MediaId),
            )
        }
        val nextOffset = offset + selected.size
        val next = nextOffset.takeIf { selected.isNotEmpty() && it < candidates.size }
            ?.let(OffsetPageCursorCodec::encode)
        return Page.of(items, nextCursor = next, totalEstimate = candidates.size)
    }

    /**
     * Exact port of legacy `FeiniuVideoCatalogProvider.selectNextUpEpisode`:
     * resumable episode first, then the first unwatched episode after the
     * latest history evidence, then the first unwatched episode, finally
     * episode one for rewatching.
     */
    override suspend fun nextUp(seriesId: MediaId): MediaItem? = coroutineScope {
        val episodesDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            feiniuEpisodeCandidates(seriesId)
        }
        val historyDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            translate { facade.catalog.playHistory(resolution) }
        }
        val candidate = selectFeiniuNextUp(episodesDeferred.await(), historyDeferred.await()) ?: return@coroutineScope null
        val names = genreNamesFor(listOf(candidate.first.item))
        val mapped = candidate.first.item.toMediaItem(serverId, ::imageUrl, genreNames = names)
            ?: return@coroutineScope null
        val history = candidate.second
        val state = feiniuEpisodeUserData(candidate.first.item, history, mapped.userData)
        mapped.copy(
            seriesId = seriesId,
            seasonId = candidate.first.seasonId?.let(::MediaId),
            userData = state,
        )
    }

    private suspend fun feiniuEpisodeCandidates(seriesId: MediaId): List<FeiniuEpisodeCandidate> {
        val seasons = try {
            translate { facade.catalog.seasons(resolution, seriesId.value) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (authentication: AuthenticationError) {
            throw authentication
        } catch (_: Exception) {
            return translate { facade.catalog.episodes(resolution, seriesId.value) }
                .sortedBy { it.episodeNumber ?: 0 }
                .map { FeiniuEpisodeCandidate(null, it) }
        }
        if (seasons.isEmpty()) {
            return translate { facade.catalog.episodes(resolution, seriesId.value) }
                .sortedBy { it.episodeNumber ?: 0 }
                .map { FeiniuEpisodeCandidate(null, it) }
        }
        return coroutineScope {
            seasons.mapNotNull { season ->
                season.guid.trim().takeIf { it.isNotEmpty() }
            }.map { seasonId ->
                async(start = CoroutineStart.UNDISPATCHED) {
                    translate { facade.catalog.episodes(resolution, seasonId) }
                        .sortedBy { it.episodeNumber ?: 0 }
                        .map { FeiniuEpisodeCandidate(seasonId, it) }
                }
            }.awaitAll().flatten()
        }
    }

    override suspend fun setFavorite(id: MediaId, favorite: Boolean) {
        translate {
            if (favorite) facade.catalog.markFavorite(resolution, id.value)
            else facade.catalog.unmarkFavorite(resolution, id.value)
        }
    }

    override suspend fun setWatched(id: MediaId, watched: Boolean) {
        translate {
            if (watched) facade.catalog.markWatched(resolution, id.value)
            else facade.catalog.unmarkWatched(resolution, id.value)
        }
    }

    override suspend fun resumeItems(request: PageRequest): Page<MediaItem> {
        val filter = request.filter
        if (filter.genres.isNotEmpty() || filter.years.isNotEmpty() ||
            !filter.searchTerm.isNullOrBlank() || filter.favoritesOnly ||
            filter.playedOnly || filter.unplayedOnly || request.sort != null
        ) {
            throw ClientConfigError(
                "resumeItems() only supports the legacy media-type constraint; sort and other filters cannot be expressed",
            )
        }
        val offset = request.cursor?.let(OffsetPageCursorCodec::decode) ?: 0
        val source = translate { facade.catalog.playHistory(resolution) }
        // Exact legacy order: enrich the provider-owned resume rows first,
        // then dedupe and apply the requested startIndex/limit.
        val enriched = enrichedResumeItems(source, filter.mediaTypes)
        val selected = enriched.drop(offset).take(request.pageSize)
        val nextOffset = offset + selected.size
        val next = nextOffset.takeIf { selected.isNotEmpty() && it < enriched.size }
            ?.let(OffsetPageCursorCodec::encode)
        return Page.of(selected, nextCursor = next, totalEstimate = enriched.size)
    }

    private suspend fun enrichedResumeItems(
        source: List<FeiniuPlayItem>,
        mediaTypes: Set<MediaType>,
    ): List<MediaItem> {
        val enriched = coroutineScope {
            source.filter { item ->
                item.guid.isNotBlank() &&
                    (mediaTypes.isEmpty() || item.toFeiniuPlayMediaType() in mediaTypes)
            }.map { item ->
                async(start = CoroutineStart.UNDISPATCHED) { resumeMediaItem(item) }
            }.awaitAll()
        }.filterNotNull()
        val seen = mutableSetOf<String>()
        return enriched.filter { item ->
            val seriesId = item.seriesId
            val identity = if (item.mediaType == MediaType.Episode && seriesId != null) {
                "series:${seriesId.value}"
            } else {
                "item:${item.id.value}"
            }
            seen.add(identity)
        }
    }

    override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation {
        println("[MaFeiPlayback] feiniu prepare start item=${item.value}")
        val operation = currentOperation()
        val selectedMediaGuid = options.mediaSourceId?.trim()?.takeIf(String::isNotEmpty)
        val initialInfo = translate {
            facade.playback.playInfo(
                resolution,
                itemGuid = item.value,
                mediaGuid = selectedMediaGuid,
                pinnedOperation = operation,
            )
        }
        println("[MaFeiPlayback] feiniu play/info completed")
        val playableItemGuid = initialInfo.guid?.trim()?.takeIf(String::isNotEmpty) ?: item.value
        val info = if (playableItemGuid != item.value) {
            try {
                translate {
                    facade.playback.playInfo(
                        resolution,
                        itemGuid = playableItemGuid,
                        mediaGuid = selectedMediaGuid,
                        pinnedOperation = operation,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                initialInfo
            }
        } else {
            initialInfo
        }
        val streams = translate {
            facade.playback.streamList(resolution, playableItemGuid, beforePlay = 1)
        }
        println("[MaFeiPlayback] feiniu stream/list completed files=${streams.files.size}")
        val preferredMediaGuid = selectedMediaGuid
            ?: info.mediaGuid?.trim()?.takeIf(String::isNotEmpty)
        val file = streams.files.firstOrNull { it.guid?.trim() == preferredMediaGuid }
            ?: streams.files.firstOrNull()
        val video = streams.videoStreams.firstOrNull { stream ->
            val videoGuid = info.videoGuid?.trim()
            !videoGuid.isNullOrEmpty() && stream.guid?.trim() == videoGuid
        } ?: streams.videoStreams.firstOrNull { stream ->
            val fileGuid = file?.guid?.trim()
            !fileGuid.isNullOrEmpty() && stream.mediaGuid?.trim() == fileGuid
        } ?: streams.videoStreams.firstOrNull()
        val mediaGuid = file?.guid?.trim()?.takeIf(String::isNotEmpty)
            ?: video?.mediaGuid?.trim()?.takeIf(String::isNotEmpty)
            ?: info.mediaGuid?.trim()?.takeIf(String::isNotEmpty)
            ?: preferredMediaGuid
            ?: return PlaybackPreparation.Refused(
                RefusalReason.NoPlayableSource,
                detail = "play/info offered no media_guid for ${item.value}",
            )
        val fileName = file?.fileName?.trim().orEmpty().ifEmpty { file?.path?.trim().orEmpty() }
        if (fileName.lowercase().endsWith(".strm")) {
            println("[MaFeiPlayback] feiniu STRM detected")
            resolveLegacyStrmDirectLink(
                item = item,
                info = info,
                streams = streams,
                options = options,
                mediaGuid = mediaGuid,
                operation = operation,
            )?.let { ready ->
                println("[MaFeiPlayback] feiniu STRM official stream ready")
                preparedStreams.add(ready)
                return ready
            }
            println("[MaFeiPlayback] feiniu STRM official stream unavailable, using range fallback")
        }

        // Match the old HarmonyOS provider's default path exactly: no bitrate
        // limit means direct-original playback. It never calls /play/play;
        // ordinary files and failed STRM resolution use authenticated range.
        //
        // 这条路上没有档位可言：range 就是原文件字节，fnOS 不会为它转码。所以
        // `qualities` 报空表（界面因此不出现清晰度菜单），`appliedBitrate` 为 null。
        // 上一集带过来的码率上限在这里只是**不适用**，不假装生效、也不因此拒绝播放。
        val streamUrl = facade.playback.directRangeUrl(resolution.preferred.baseUrl, mediaGuid)
        if (options.maxStreamingBitrate != null) {
            println("[MaFeiPlayback] feiniu range route cannot apply a bitrate ceiling; serving the original file")
        }
        val ready = PlaybackPreparation.Ready(
            streamUrl = streamUrl,
            subtitleUrl = externalSubtitleUrl(streams, info, options.subtitleStreamIndex),
            tracks = playbackTracks(streams, info, options),
            transport = PlaybackTransport.DirectPlay,
            qualities = emptyList(),
            headers = facade.assets.playbackHeaders(
                operation.credentials,
                resolution.preferred.requiresRelayCookie,
            ),
            multiConnectionAllowed = !fileName.lowercase().endsWith(".strm"),
            playSessionId = null,
            reportingContext = FeiniuReportingContext(
                itemGuid = info.guid?.takeIf { it.isNotBlank() } ?: item.value,
                mediaGuid = mediaGuid,
                videoGuid = video?.guid?.takeIf(String::isNotBlank)
                    ?: info.videoGuid?.takeIf(String::isNotBlank)
                    ?: mediaGuid,
                audioGuid = info.audioGuid ?: "",
                subtitleGuid = info.subtitleGuid ?: "",
                resolution = video?.resolutionType.orEmpty(),
                bitrate = video?.bitRate?.toLong() ?: 0L,
                durationSeconds = info.item?.durationSeconds,
                playLink = "/v/api/v1/media/range/$mediaGuid",
            ).encode(),
        )
        preparedStreams.add(ready)
        return ready
    }

    /**
     * fnOS 的轨道目录来自 `/stream` 的 stream list，选中项来自 `play/info` 的
     * audio_guid / subtitle_guid。index 用 fnOS 自己的 stream index，重新协商时按
     * index 反查 guid。
     *
     * 直连（range 原文件）路径下，音轨/内嵌字幕都在播放器手里，重新请求同一 URL
     * 不会换轨 —— `renegotiate = false`，由原生 runtime 切换；外挂字幕有自己的
     * `/subtitle/dl` 文件，走 provider 重新协商。
     */
    private fun playbackTracks(
        streams: FeiniuStreamListData,
        info: FeiniuPlayInfoData,
        options: PlaybackOptions,
    ): List<PlaybackTrack> {
        val selectedAudioGuid = info.audioGuid?.takeIf { it.isNotBlank() }
        val selectedSubtitleGuid = info.subtitleGuid?.takeIf { it.isNotBlank() }
        val selectedAudioIndex = options.audioStreamIndex
            ?: streams.audioStreams.firstOrNull { it.guid?.trim() == selectedAudioGuid }?.index
            ?: streams.audioStreams.firstOrNull { it.isDefault == 1 }?.index
            ?: streams.audioStreams.firstOrNull()?.index
        val selectedSubtitleIndex = when (val requested = options.subtitleStreamIndex) {
            null -> streams.subtitleStreams.firstOrNull { it.guid?.trim() == selectedSubtitleGuid }?.index
                ?: streams.subtitleStreams.firstOrNull { it.isDefault == 1 }?.index
            else -> requested
        }
        val audioTracks = streams.audioStreams.mapNotNull { stream ->
            val index = stream.index ?: return@mapNotNull null
            PlaybackTrack(
                index = index,
                kind = PlaybackTrackKind.Audio,
                displayTitle = stream.title?.takeIf { it.isNotBlank() }
                    ?: feiniuTrackFallbackName(stream.language, stream.codecName, stream.channels),
                title = stream.title?.takeIf { it.isNotBlank() },
                language = stream.language?.takeIf { it.isNotBlank() },
                codec = stream.codecName?.takeIf { it.isNotBlank() },
                channels = stream.channels,
                selected = index == selectedAudioIndex,
                isExternal = false,
                // 直连原文件：换音轨只能在播放器内部完成。
                renegotiate = false,
            )
        }
        val subtitleTracks = streams.subtitleStreams.mapNotNull { stream ->
            val index = stream.index ?: return@mapNotNull null
            val external = stream.isExternal == 1
            PlaybackTrack(
                index = index,
                kind = PlaybackTrackKind.Subtitle,
                displayTitle = stream.title?.takeIf { it.isNotBlank() }
                    ?: feiniuTrackFallbackName(stream.language, stream.format ?: stream.codecName, null),
                title = stream.title?.takeIf { it.isNotBlank() },
                language = stream.language?.takeIf { it.isNotBlank() },
                codec = (stream.format ?: stream.codecName)?.takeIf { it.isNotBlank() },
                channels = null,
                selected = selectedSubtitleIndex != null && selectedSubtitleIndex >= 0 &&
                    index == selectedSubtitleIndex,
                isExternal = external,
                renegotiate = external,
            )
        }
        return audioTracks + subtitleTracks
    }

    /**
     * 外挂字幕 URL：按选中轨（或默认轨）的 guid 走 fnOS 的 `/subtitle/dl`。内嵌
     * 字幕没有独立文件，返回 null，由播放器从原文件里取。
     */
    private fun externalSubtitleUrl(
        streams: FeiniuStreamListData,
        info: FeiniuPlayInfoData,
        requestedSubtitleIndex: Int?,
    ): String? {
        if (requestedSubtitleIndex != null && requestedSubtitleIndex < 0) return null // 关闭字幕
        val selectedGuid = info.subtitleGuid?.takeIf { it.isNotBlank() }
        val external = streams.subtitleStreams.filter { it.isExternal == 1 && !it.guid.isNullOrBlank() }
        val track = when (requestedSubtitleIndex) {
            null -> external.firstOrNull { it.guid?.trim() == selectedGuid }
                ?: external.firstOrNull { it.isDefault == 1 }
                ?: external.firstOrNull()
            else -> external.firstOrNull { it.index == requestedSubtitleIndex }
        } ?: return null
        val guid = track.guid?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return facade.assets.subtitleDownloadUrl(resolution.preferred.baseUrl, guid)
    }

    private fun feiniuTrackFallbackName(language: String?, codec: String?, channels: Int?): String =
        buildList {
            language?.takeIf { it.isNotBlank() }?.let { add(mediaLanguageDisplayName(it)) }
            codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
            channels?.takeIf { it > 0 }?.let { add("${it}ch") }
        }.joinToString(" · ").takeIf { it.isNotEmpty() } ?: "默认轨道"

    /**
     * Mechanical equivalent of the old HarmonyOS STRM path. fnOS returns 8192
     * from play/play for direct-original STRM sources; the actual cloud URL is
     * obtained from /stream and must never receive Feiniu credentials.
     */
    private suspend fun resolveLegacyStrmDirectLink(
        item: MediaId,
        info: FeiniuPlayInfoData,
        streams: FeiniuStreamListData,
        options: PlaybackOptions,
        mediaGuid: String,
        operation: cn.mafei.cmp.providers.feiniu.core.FeiniuSessionSnapshot,
    ): PlaybackPreparation.Ready? {
        println("[MaFeiPlayback] feiniu STRM user/info start")
        val sourceName = try {
            translate { facade.auth.userInfo(resolution, pinnedOperation = operation) }
                .sources.firstOrNull()?.sourceName?.trim().orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (authentication: AuthenticationError) {
            throw authentication
        } catch (_: Exception) {
            return null
        }
        println("[MaFeiPlayback] feiniu STRM user/info completed")
        println("[MaFeiPlayback] feiniu STRM official /stream start")
        val official = try {
            translate {
                facade.playback.officialStream(
                    resolution = resolution,
                    request = FeiniuOfficialStreamRequest(
                        mediaGuid = mediaGuid,
                        ip = FeiniuDigest.md5(sourceName),
                        header = FeiniuOfficialStreamRequestHeader(listOf(FeiniuIdentity.USER_AGENT)),
                        level = 1,
                    ),
                    pinnedOperation = operation,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (authentication: AuthenticationError) {
            throw authentication
        } catch (_: Exception) {
            return null
        }
        println("[MaFeiPlayback] feiniu STRM official /stream completed")
        // fnOS 的清晰度就是上游给的一组签名直链（每个自带 resolution / bitrate）。
        // 我们不造档位表，只把能直连的那几条摆出来，然后按用户给的上限挑一条：
        // 不超过上限里最大的一条；都超上限就退到最小的一条（并如实上报实际档位，
        // 界面据此显示真正在播的档，而不是用户点的那一档）。
        val ladder = (official.directLinkQualities + official.qualities).httpQualities()
        val ceiling = options.maxStreamingBitrate?.takeIf { it > 0 }
        val quality = ladder.pickQuality(ceiling) ?: return null
        val streamUrl = quality.url?.trim().orEmpty()
        val appliedBitrate = ceiling?.let { quality.bitrate?.takeIf { bitrate -> bitrate > 0 }?.toInt() }
        return PlaybackPreparation.Ready(
            streamUrl = streamUrl,
            subtitleUrl = externalSubtitleUrl(streams, info, options.subtitleStreamIndex),
            tracks = playbackTracks(streams, info, options),
            headers = official.header.toPlaybackHeaders(),
            allowInsecureTls = streamUrl.startsWith("https://", ignoreCase = true),
            multiConnectionAllowed = false,
            playSessionId = null,
            // fnOS 不重编：它只是把上游那条现成的流交给播放器。
            transport = PlaybackTransport.DirectStream,
            appliedBitrate = appliedBitrate,
            qualities = ladder.mapNotNull { candidate ->
                candidate.bitrate?.takeIf { it > 0 }?.toInt()?.let { bitrate ->
                    PlaybackQuality(bitrate = bitrate, label = candidate.resolution?.takeIf(String::isNotBlank))
                }
            },
            reportingContext = FeiniuReportingContext(
                itemGuid = info.guid?.takeIf(String::isNotBlank) ?: item.value,
                mediaGuid = mediaGuid,
                videoGuid = info.videoGuid?.takeIf(String::isNotBlank) ?: mediaGuid,
                audioGuid = info.audioGuid.orEmpty(),
                subtitleGuid = info.subtitleGuid.orEmpty(),
                resolution = quality.resolution.orEmpty(),
                bitrate = quality.bitrate ?: 0L,
                durationSeconds = info.item?.durationSeconds,
                // Preserve the Feiniu resource identity for progress reports;
                // never send the signed third-party URL back to fnOS.
                playLink = "/v/api/v1/media/range/$mediaGuid",
            ).encode(),
        )
    }

    private fun List<cn.mafei.cmp.providers.feiniu.core.FeiniuDirectLinkQuality>.httpQualities() =
        filter { quality ->
            val url = quality.url?.trim().orEmpty()
            url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
        }

    /**
     * Ceiling selection over the upstream ladder. No ceiling keeps the legacy
     * behaviour (first reachable link); with one, the largest rendition at or
     * below it wins, and if every rendition exceeds it the smallest one is
     * served anyway — the caller reports its real bitrate, so the chrome shows
     * what is actually playing. Never re-request the same link and claim a
     * different quality.
     */
    private fun List<cn.mafei.cmp.providers.feiniu.core.FeiniuDirectLinkQuality>.pickQuality(
        ceiling: Int?,
    ): cn.mafei.cmp.providers.feiniu.core.FeiniuDirectLinkQuality? {
        if (isEmpty()) return null
        if (ceiling == null) return first()
        return filter { (it.bitrate ?: 0L) in 1..ceiling.toLong() }
            .maxByOrNull { it.bitrate ?: 0L }
            ?: minByOrNull { it.bitrate ?: 0L }
            ?: first()
    }

    private fun JsonObject?.toPlaybackHeaders(): Map<String, String> = buildMap {
        this@toPlaybackHeaders?.forEach { (rawName, rawValue) ->
            val name = rawName.trim()
            if (name.isEmpty()) return@forEach
            val values = when (rawValue) {
                is JsonArray -> rawValue.mapNotNull {
                    (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty)
                }
                is JsonPrimitive -> listOfNotNull(rawValue.content.trim().takeIf(String::isNotEmpty))
                else -> listOf(rawValue.toString())
            }
            if (values.isNotEmpty()) {
                put(name, values.joinToString(if (name.equals("Cookie", ignoreCase = true)) "; " else ", "))
            }
        }
    }

    override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) {
        currentOperation()
        requireCapability(capabilities, AdapterCapability.PlaybackReporting)
        if (preparedStreams.none { it === stream }) {
            throw ClientConfigError("reportPlayback rejected a stream not prepared by this adapter instance")
        }
        val context = FeiniuReportingContext.decode(stream.reportingContext)
            ?: throw ClientConfigError("Feiniu reporting context is missing or malformed")
        // Contract positions are ms; the wire reports seconds. Legacy protocol
        // skips reports entirely at ts <= 0 (no zero-position writes).
        val timestampSeconds = event.positionMs / 1000L
        if (timestampSeconds <= 0L) return
        val durationSeconds = event.durationMs?.let { it / 1000L } ?: context.durationSeconds ?: 0L
        translate {
            facade.playback.record(
                resolution,
                FeiniuPlayRecordRequest(
                    itemGuid = context.itemGuid,
                    mediaGuid = context.mediaGuid,
                    videoGuid = context.videoGuid,
                    audioGuid = context.audioGuid,
                    subtitleGuid = context.subtitleGuid.ifBlank { null },
                    resolution = context.resolution,
                    bitrate = context.bitrate,
                    timestampSeconds = timestampSeconds,
                    durationSeconds = durationSeconds,
                    playLink = context.playLink,
                ),
                pinnedOperation = currentOperation(),
            )
        }
    }

    private suspend fun optionalPeople(itemGuid: String) = try {
        translate { facade.catalog.people(resolution, itemGuid) }.list
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (authentication: AuthenticationError) {
        throw authentication
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun optionalPlayInfo(itemGuid: String) = try {
        translate { facade.playback.playInfo(resolution, itemGuid, pinnedOperation = currentOperation()) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (authentication: AuthenticationError) {
        throw authentication
    } catch (_: Exception) {
        null
    }

    private suspend fun optionalStreamList(itemGuid: String) = try {
        translate { facade.playback.streamList(resolution, itemGuid, beforePlay = 1) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (authentication: AuthenticationError) {
        throw authentication
    } catch (_: Exception) {
        cn.mafei.cmp.providers.feiniu.core.FeiniuStreamListData()
    }

    private suspend fun optionalItemFiles(itemGuid: String) = try {
        translate { facade.catalog.itemFiles(resolution, itemGuid) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (authentication: AuthenticationError) {
        throw authentication
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun detailStreams(
        detail: FeiniuItemDetail,
        playInfo: FeiniuPlayInfoData?,
    ): FeiniuStreamListData = coroutineScope {
        val listedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            optionalStreamList(detail.guid.orEmpty())
        }
        val itemFilesDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            optionalItemFiles(detail.guid.orEmpty())
        }
        val listed = mergeFeiniuItemFiles(listedDeferred.await(), itemFilesDeferred.await())
        val preferredMediaGuid = playInfo?.mediaGuid?.trim()?.takeIf(String::isNotEmpty)
            ?: detail.mediaGuid?.trim()?.takeIf(String::isNotEmpty)
        val mediaGuids = feiniuDetailMediaGuids(detail, listed, preferredMediaGuid)
        if (mediaGuids.isEmpty()) return@coroutineScope preferFeiniuMediaFile(listed, preferredMediaGuid)

        val operation = currentOperation()
        val ip = try {
            val userInfo = translate { facade.auth.userInfo(resolution, pinnedOperation = operation) }
            FeiniuDigest.md5(userInfo.sources.firstOrNull()?.sourceName?.trim().orEmpty())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@coroutineScope preferFeiniuMediaFile(listed, preferredMediaGuid)
        }
        val official = mediaGuids.map { mediaGuid ->
            async(start = CoroutineStart.UNDISPATCHED) {
                try {
                    mediaGuid to translate {
                        facade.playback.officialStream(
                            resolution = resolution,
                            request = FeiniuOfficialStreamRequest(
                                mediaGuid = mediaGuid,
                                ip = ip,
                                header = FeiniuOfficialStreamRequestHeader(listOf(FeiniuIdentity.USER_AGENT)),
                                level = 1,
                            ),
                            pinnedOperation = operation,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
        preferFeiniuMediaFile(mergeFeiniuOfficialStreams(detail, listed, official), preferredMediaGuid)
    }

    private data class FeiniuPersonPage(val items: List<FeiniuPersonItem>, val total: Int)

    /**
     * Reproduces the old unaligned-offset behavior: consume the remainder of
     * the calculated page, then request exactly one following page if needed.
     */
    private suspend fun personItemPage(
        personId: MediaId,
        job: String,
        startIndex: Int,
        pageSize: Int,
        sortColumn: String,
        sortType: String,
    ): FeiniuPersonPage {
        val pageNumber = startIndex / pageSize + 1
        val offset = startIndex % pageSize
        suspend fun fetch(page: Int) = translate {
            facade.catalog.personItems(
                resolution,
                FeiniuPersonItemListRequest(
                    personGuid = personId.value,
                    page = page,
                    pageSize = pageSize,
                    job = job,
                    sortColumn = sortColumn,
                    sortType = sortType,
                ),
            )
        }
        val first = fetch(pageNumber)
        val total = first.total?.takeIf { it > 0 } ?: first.list.size
        val selected = first.list.drop(offset).take(pageSize).toMutableList()
        if (selected.size < pageSize && startIndex + selected.size < total) {
            selected += fetch(pageNumber + 1).list.take(pageSize - selected.size)
        }
        return FeiniuPersonPage(selected, total)
    }

    private suspend fun optionalItemDetail(itemGuid: String?): FeiniuItemDetail? {
        val normalized = itemGuid?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        return try {
            translate { facade.catalog.itemDetail(resolution, normalized) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (authentication: AuthenticationError) {
            throw authentication
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun latestMediaItem(
        entry: FeiniuLatestLibraryItem,
        genreNames: Map<Int, String>,
    ): MediaItem? {
        val item = entry.item
        val kind = item.type.toFeiniuMediaType()
        val hierarchyNeedsDetail = kind in setOf(MediaType.Episode, MediaType.Season) && item.parentGuid.isNullOrBlank()
        val imageNeedsDetail = item.stillPath.isNullOrBlank() && item.poster.isNullOrBlank()
        val detail = if (hierarchyNeedsDetail || imageNeedsDetail) optionalItemDetail(item.guid) else null
        val effective = detail?.toRecommendationMediaItem(item) ?: item
        val base = effective.toMediaItem(serverId, ::imageUrl, entry.collectionId, genreNames) ?: return null
        val context = detailContext(effective.toContextDetail(detail), kind)
        return base.withFeiniuContext(context, useSeriesTitleForEpisode = true)
    }

    /** Mechanical equivalent of legacy `enrichCatalogItem` + mapper.resume. */
    private suspend fun resumeMediaItem(item: FeiniuPlayItem): MediaItem? {
        val stateBase = item.toMediaItem(serverId, ::imageUrl) ?: return null
        val fallback = item.toRecommendationMediaItem()
        val kind = stateBase.mediaType
        val hierarchyNeedsDetail = kind in setOf(MediaType.Episode, MediaType.Season) && fallback.parentGuid.isNullOrBlank()
        val imageNeedsDetail = fallback.stillPath.isNullOrBlank() && fallback.poster.isNullOrBlank()
        val detail = if (hierarchyNeedsDetail || imageNeedsDetail) optionalItemDetail(item.guid) else null
        val effective = detail?.toRecommendationMediaItem(fallback) ?: fallback
        val mapped = effective.toMediaItem(serverId, ::imageUrl)
        val base = (mapped ?: stateBase).copy(
            userData = stateBase.userData,
            runtimeMs = stateBase.runtimeMs ?: mapped?.runtimeMs,
            canPlay = if (detail != null) mapped?.canPlay ?: stateBase.canPlay else stateBase.canPlay,
        )
        val context = detailContext(effective.toContextDetail(detail), kind)
        return base.withFeiniuContext(context, useSeriesTitleForEpisode = true)
    }

    /** Same parent/series fallback walk as the legacy `itemContext()`. */
    private suspend fun detailContext(detail: FeiniuItemDetail, kind: cn.mafei.cmp.core.domain.MediaType): FeiniuDetailContext {
        var seriesId: String? = null
        var seasonId: String? = null
        var seriesTitle = detail.tvTitle?.trim()?.takeIf { it.isNotEmpty() }
        var seasonTitle: String? = null
        var primary = detail.feiniuPrimaryImagePath()
        var backdrop = detail.backdrops?.trim()?.takeIf { it.isNotEmpty() }
        var logo = detail.logos?.trim()?.takeIf { it.isNotEmpty() }
        var seriesDetail: FeiniuItemDetail? = null

        when (kind) {
            cn.mafei.cmp.core.domain.MediaType.Series -> {
                seriesId = detail.guid?.trim()?.takeIf { it.isNotEmpty() }
                seriesTitle = seriesTitle ?: detail.title?.trim()?.takeIf(String::isNotEmpty)
            }
            cn.mafei.cmp.core.domain.MediaType.Season -> {
                val parent = detail.parentGuid?.trim().orEmpty()
                val library = detail.ancestorGuid?.trim().orEmpty()
                seriesId = parent.takeIf { it.isNotEmpty() && it != library }
                seasonTitle = detail.title?.trim()?.takeIf(String::isNotEmpty)
            }
            cn.mafei.cmp.core.domain.MediaType.Episode -> {
                val parentId = detail.parentGuid?.trim().orEmpty()
                val parent = optionalItemDetail(parentId)
                if (parent?.type.toFeiniuMediaType() == cn.mafei.cmp.core.domain.MediaType.Series) {
                    seriesId = parent?.guid?.trim()?.takeIf { it.isNotEmpty() } ?: parentId.takeIf { it.isNotEmpty() }
                    seriesTitle = seriesTitle
                        ?: parent?.title?.trim()?.takeIf(String::isNotEmpty)
                        ?: detail.parentTitle?.trim()?.takeIf(String::isNotEmpty)
                    seriesDetail = parent
                } else {
                    seasonId = parentId.takeIf { it.isNotEmpty() }
                    seasonTitle = parent?.title?.trim()?.takeIf(String::isNotEmpty)
                    val seriesParent = parent?.parentGuid?.trim().orEmpty()
                    val library = parent?.ancestorGuid?.trim().orEmpty()
                    seriesId = seriesParent.takeIf { it.isNotEmpty() && it != library }
                    seriesTitle = seriesTitle ?: parent?.parentTitle?.trim()?.takeIf(String::isNotEmpty)
                    primary = primary ?: parent?.feiniuPrimaryImagePath()
                    backdrop = backdrop ?: parent?.backdrops?.trim()?.takeIf { it.isNotEmpty() }
                    logo = logo ?: parent?.logos?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
            else -> Unit
        }
        if (seriesDetail == null && !seriesId.isNullOrBlank() && seriesId != detail.guid) {
            seriesDetail = optionalItemDetail(seriesId)
        }
        seriesDetail?.let { series ->
            seriesTitle = seriesTitle ?: series.title?.trim()?.takeIf(String::isNotEmpty)
            primary = primary ?: series.feiniuPrimaryImagePath()
            backdrop = backdrop ?: series.backdrops?.trim()?.takeIf { it.isNotEmpty() }
            logo = logo ?: series.logos?.trim()?.takeIf { it.isNotEmpty() }
        }
        primary = primary ?: backdrop
        backdrop = backdrop ?: primary
        return FeiniuDetailContext(
            seriesId = seriesId,
            seasonId = seasonId,
            seriesTitle = seriesTitle,
            seasonTitle = seasonTitle,
            primaryImagePath = primary,
            backdropImagePath = backdrop,
            logoImagePath = logo,
        )
    }

    /** Builds the same fallback-filled shape that legacy `itemContext()` read. */
    private fun FeiniuMediaItem.toContextDetail(source: FeiniuItemDetail?): FeiniuItemDetail = FeiniuItemDetail(
        guid = guid,
        title = title,
        type = type,
        originalTitle = source?.originalTitle,
        overview = overview,
        poster = poster,
        stillPath = stillPath,
        status = status,
        parentGuid = parentGuid,
        parentTitle = parentTitle,
        ancestorGuid = ancestorGuid,
        ancestorName = ancestorName,
        ancestorCategory = ancestorCategory,
        tvTitle = tvTitle,
        posters = stillPath?.takeIf(String::isNotBlank)
            ?: poster?.takeIf(String::isNotBlank)
            ?: source?.posters,
        backdrops = source?.backdrops,
        logos = logos?.takeIf(String::isNotBlank) ?: source?.logos,
        voteAverage = voteAverage,
        releaseDate = releaseDate,
        airDate = airDate,
        firstAirDate = firstAirDate,
        lastAirDate = lastAirDate,
        contentRatings = source?.contentRatings,
        productionCountries = source?.productionCountries.orEmpty(),
        genres = genres,
        numberOfSeasons = numberOfSeasons,
        numberOfEpisodes = numberOfEpisodes,
        localNumberOfSeasons = localNumberOfSeasons,
        localNumberOfEpisodes = localNumberOfEpisodes,
        watched = watched,
        watchedEpochSeconds = watchedEpochSeconds,
        isFavorite = isFavorite,
        durationSeconds = durationSeconds,
        runtimeMinutes = runtimeMinutes,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        mediaGuid = mediaGuid,
        videoGuid = videoGuid,
        audioGuid = audioGuid,
        subtitleGuid = subtitleGuid,
        fileName = fileName,
        singleChildGuid = singleChildGuid,
        playItemGuid = playItemGuid,
        canPlay = canPlay,
        mediaStream = mediaStream,
    )

    private fun MediaItem.withFeiniuContext(
        context: FeiniuDetailContext,
        useSeriesTitleForEpisode: Boolean,
    ): MediaItem {
        val contextBackdrop = context.backdropImagePath?.trim()?.takeIf(String::isNotEmpty)
        val contextLogo = context.logoImagePath?.trim()?.takeIf(String::isNotEmpty)
        val retainedImages = images.filterNot { image ->
            (image.kind == ImageKind.Backdrop && contextBackdrop != null) ||
                (image.kind == ImageKind.Logo && contextLogo != null)
        }
        val additions = buildList {
            fun add(kind: ImageKind, path: String?) {
                val normalized = path?.trim()?.takeIf(String::isNotEmpty) ?: return
                if (retainedImages.none { it.kind == kind }) this.add(MediaImage(kind, imageUrl(normalized)))
            }
            add(ImageKind.Primary, context.primaryImagePath)
            add(ImageKind.Backdrop, contextBackdrop)
            add(ImageKind.Logo, contextLogo)
        }
        return copy(
            title = if (useSeriesTitleForEpisode && mediaType == MediaType.Episode) {
                context.seriesTitle?.takeIf(String::isNotBlank) ?: title
            } else {
                title
            },
            seriesId = context.seriesId?.takeIf(String::isNotBlank)?.let(::MediaId),
            seriesTitle = context.seriesTitle?.takeIf(String::isNotBlank),
            seasonId = context.seasonId?.takeIf(String::isNotBlank)?.let(::MediaId),
            seasonTitle = context.seasonTitle?.takeIf(String::isNotBlank),
            images = retainedImages + additions,
        )
    }

    private suspend fun genreNamesFor(items: Iterable<FeiniuMediaItem>): Map<Int, String> =
        if (items.any { it.genres.any { id -> id > 0 } }) genreNames() else emptyMap()

    private suspend fun genreNames(): Map<Int, String> {
        genreNames?.let { return it }
        val loaded = try {
            translate { facade.catalog.genres(resolution) }
                .mapNotNull { entry ->
                    val id = entry.id?.takeIf { it > 0 } ?: return@mapNotNull null
                    val name = entry.value?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    id to name
                }
                .toMap()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyMap()
        }
        genreNames = loaded
        return loaded
    }

    // ---------------------------------------------------------------- internals

    /**
     * Page assembly with the frozen total semantics (same as the Jellyfin
     * adapter): total<=0/absent is "unknown"; empty pages terminate; a
     * positive total keeps clamped short pages walking until exhausted.
     */
    private fun toPage(
        data: FeiniuItemListData,
        request: PageRequest,
        position: FeiniuPagePosition,
        collectionId: CollectionId?,
        genreNames: Map<Int, String> = emptyMap(),
    ): Page<MediaItem> {
        val items = data.list.mapNotNull { it.toMediaItem(serverId, ::imageUrl, collectionId, genreNames) }
        val total = data.total
        val nextOffset = position.seen + data.list.size
        fun next(): PageCursor = position.next(request.pageSize, data.list.size)
        val nextCursor: PageCursor? = when {
            data.list.isEmpty() -> null // terminal empty page, regardless of totals
            total != null && total > 0 && nextOffset >= total -> null // declared end
            data.list.size >= request.pageSize -> next()
            total != null && total > 0 -> next() // clamped page, total says more
            else -> null // no total information: a short page is the last one
        }
        return Page.of(items, nextCursor = nextCursor, totalEstimate = total?.takeIf { it > 0 })
    }

    /** Exact port of legacy `FeiniuVideoCatalogProvider.sortColumn`. */
    private fun MediaSort?.toFeiniuSort(): Pair<String, String> {
        val sort = this ?: return "create_time" to "DESC"
        val column = when (sort.field) {
            MediaSortField.Title,
            MediaSortField.SeriesSortName -> "sort_title"
            MediaSortField.PremiereDate,
            MediaSortField.ProductionYear -> "release_date"
            MediaSortField.CommunityRating,
            MediaSortField.CriticRating -> "vote_average"
            MediaSortField.DatePlayed,
            MediaSortField.PlayCount -> "watched_ts"
            MediaSortField.Runtime -> "runtime"
            MediaSortField.Default,
            MediaSortField.DateAdded,
            MediaSortField.OfficialRating,
            MediaSortField.Random,
            MediaSortField.Album,
            MediaSortField.Artist,
            MediaSortField.AlbumArtist,
            MediaSortField.AiredEpisodeOrder,
            MediaSortField.Studio,
            MediaSortField.IsFavoriteOrLiked,
            MediaSortField.DateLastContentAdded,
            MediaSortField.IsUnplayed,
            MediaSortField.IsPlayed,
            MediaSortField.SeasonNumber,
            MediaSortField.EpisodeNumber -> "create_time"
        }
        val type = if (sort.direction == SortDirection.Ascending) "ASC" else "DESC"
        return column to type
    }

    private fun MediaSort?.toFeiniuPersonSort(): Pair<String, String> = toFeiniuSort()

    private fun cn.mafei.cmp.core.domain.MediaType.supportsFeiniuPlayInfo(): Boolean = when (this) {
        cn.mafei.cmp.core.domain.MediaType.Movie,
        cn.mafei.cmp.core.domain.MediaType.Series,
        cn.mafei.cmp.core.domain.MediaType.Season,
        cn.mafei.cmp.core.domain.MediaType.Episode,
        cn.mafei.cmp.core.domain.MediaType.Video -> true
        else -> false
    }

    /** Exact legacy `itemTypes(kinds)` literals used by favorite/list. */
    private fun cn.mafei.cmp.core.domain.MediaType.toFeiniuItemType(): String = when (this) {
        cn.mafei.cmp.core.domain.MediaType.Movie -> "Movie"
        cn.mafei.cmp.core.domain.MediaType.Series -> "TV"
        cn.mafei.cmp.core.domain.MediaType.Season -> "Season"
        cn.mafei.cmp.core.domain.MediaType.Episode -> "Episode"
        cn.mafei.cmp.core.domain.MediaType.Folder -> "Directory"
        cn.mafei.cmp.core.domain.MediaType.Video -> "Video"
        else -> throw ClientConfigError("Feiniu favorite type filter cannot express $this")
    }

    private fun cn.mafei.cmp.core.domain.MediaType.supportsFeiniuDetailStreams(): Boolean = when (this) {
        cn.mafei.cmp.core.domain.MediaType.Movie,
        cn.mafei.cmp.core.domain.MediaType.Episode,
        cn.mafei.cmp.core.domain.MediaType.Video -> true
        else -> false
    }
}

private fun FeiniuItemDetail.feiniuPrimaryImagePath(): String? =
    listOf(stillPath, poster, posters)
        .firstNotNullOfOrNull { value -> value?.trim()?.takeIf(String::isNotEmpty) }

private fun String?.isFeiniuLiveLibrary(): Boolean =
    this?.trim()?.lowercase() in setOf("live", "livetv", "iptv")

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

private fun isFeiniuHeroCandidate(item: FeiniuMediaItem): Boolean {
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
private fun epochYear(epochMs: Long): Int = epochCalendarDate(epochMs).year

private fun selectFeiniuNextUp(
    episodes: List<FeiniuEpisodeCandidate>,
    historyItems: List<FeiniuPlayItem>,
): Pair<FeiniuEpisodeCandidate, FeiniuPlayItem?>? {
    if (episodes.isEmpty()) return null
    val historyByGuid = linkedMapOf<String, FeiniuPlayItem>()
    val historyOrder = mutableMapOf<String, Int>()
    historyItems.forEachIndexed { index, item ->
        val guid = item.guid.trim()
        if (guid.isNotEmpty() && guid !in historyByGuid) {
            historyByGuid[guid] = item
            historyOrder[guid] = index
        }
    }

    var resumableIndex = -1
    var resumableTimestamp = -1L
    var resumableOrder = Int.MAX_VALUE
    var latestPlayedIndex = -1
    var latestPlayedTimestamp = -1L
    var latestPlayedOrder = Int.MAX_VALUE
    var hasMatchedHistory = false
    var latestLocalEvidenceIndex = -1

    episodes.forEachIndexed { index, candidate ->
        val guid = candidate.item.guid.trim()
        val history = historyByGuid[guid]
        val watched = feiniuEpisodeWatched(candidate.item, history)
        val position = feiniuEpisodePositionEvidence(candidate.item, history)
        val timestamp = (history?.watchedEpochSeconds ?: 0L).coerceAtLeast(0L)
        val order = historyOrder[guid] ?: Int.MAX_VALUE
        if (history != null) {
            hasMatchedHistory = true
            if (timestamp > latestPlayedTimestamp ||
                (timestamp == latestPlayedTimestamp && order < latestPlayedOrder)
            ) {
                latestPlayedIndex = index
                latestPlayedTimestamp = timestamp
                latestPlayedOrder = order
            }
        } else if (watched || position > 0.0) {
            latestLocalEvidenceIndex = index
        }
        if (!watched && position > 0.0 &&
            (timestamp > resumableTimestamp ||
                (timestamp == resumableTimestamp && order < resumableOrder) ||
                (timestamp == resumableTimestamp && order == resumableOrder && index > resumableIndex))
        ) {
            resumableIndex = index
            resumableTimestamp = timestamp
            resumableOrder = order
        }
    }

    if (resumableIndex >= 0) {
        val candidate = episodes[resumableIndex]
        return candidate to historyByGuid[candidate.item.guid.trim()]
    }
    val evidenceIndex = if (hasMatchedHistory) latestPlayedIndex else latestLocalEvidenceIndex
    if (evidenceIndex >= 0) {
        for (index in evidenceIndex + 1 until episodes.size) {
            val candidate = episodes[index]
            val history = historyByGuid[candidate.item.guid.trim()]
            if (!feiniuEpisodeWatched(candidate.item, history)) return candidate to history
        }
        val first = episodes.first()
        return first to historyByGuid[first.item.guid.trim()]
    }
    val firstUnwatched = episodes.firstOrNull { candidate ->
        !feiniuEpisodeWatched(candidate.item, historyByGuid[candidate.item.guid.trim()])
    } ?: episodes.first()
    return firstUnwatched to historyByGuid[firstUnwatched.item.guid.trim()]
}

private fun feiniuEpisodeWatched(item: FeiniuMediaItem, history: FeiniuPlayItem?): Boolean {
    val watched = maxOf(history?.watched ?: 0, item.watched ?: 0)
    val progress = (history?.mediaStream?.progress ?: item.mediaStream?.progress)
        ?.takeIf(Double::isFinite)
        ?: 0.0
    return watched > 0 || progress >= 100.0
}

private fun feiniuEpisodePositionEvidence(item: FeiniuMediaItem, history: FeiniuPlayItem?): Double = maxOf(
    (history?.epochSeconds ?: 0L).toDouble(),
    history?.mediaStream?.currentTimeSeconds?.takeIf(Double::isFinite) ?: 0.0,
    (item.epochSeconds ?: 0L).toDouble(),
    item.mediaStream?.currentTimeSeconds?.takeIf(Double::isFinite) ?: 0.0,
    (history?.mediaStream?.progress ?: item.mediaStream?.progress)?.takeIf(Double::isFinite) ?: 0.0,
)

private fun feiniuEpisodeUserData(
    item: FeiniuMediaItem,
    history: FeiniuPlayItem?,
    base: MediaUserData,
): MediaUserData {
    val timestampSeconds = history?.epochSeconds ?: item.epochSeconds ?: 0L
    val streamSeconds = (history?.mediaStream?.currentTimeSeconds ?: item.mediaStream?.currentTimeSeconds)
        ?.takeIf(Double::isFinite)
        ?: 0.0
    val positionMs = (maxOf(timestampSeconds.toDouble(), streamSeconds).coerceAtLeast(0.0) * 1_000.0).toLong()
    val explicitPercentage = (history?.mediaStream?.progress ?: item.mediaStream?.progress)
        ?.takeIf(Double::isFinite)
        ?.coerceIn(0.0, 100.0)
    val durationMs = (history?.durationSeconds ?: item.durationSeconds)?.takeIf { it > 0L }?.times(1_000L)
    val isPlayed = feiniuEpisodeWatched(item, history)
    val percentage = when {
        explicitPercentage != null && explicitPercentage > 0.0 -> explicitPercentage
        durationMs != null && positionMs > 0L ->
            (positionMs.toDouble() * 100.0 / durationMs.toDouble()).coerceIn(0.0, 100.0)
        isPlayed -> 100.0
        else -> explicitPercentage ?: base.playedPercentage
    }
    return base.copy(
        isPlayed = isPlayed,
        playbackPositionMs = positionMs,
        playedPercentage = percentage,
        lastPlayedEpochMs = (history?.watchedEpochSeconds ?: item.watchedEpochSeconds)
            ?.takeIf { it > 0L }
            ?.times(1_000L),
    )
}

/**
 * Opaque reporting context carried inside [PlaybackPreparation.Ready] — the
 * record endpoint needs the full negotiation handle set (guids, resolution,
 * bitrate, duration and the expiring play link) beyond what the event carries.
 * Encoded as JSON; never persisted, never derived by re-parsing stream URLs.
 */
@Serializable
private data class FeiniuReportingContext(
    val itemGuid: String,
    val mediaGuid: String,
    val videoGuid: String,
    val audioGuid: String,
    val subtitleGuid: String,
    val resolution: String,
    val bitrate: Long,
    val durationSeconds: Long? = null,
    val playLink: String? = null,
) {
    fun encode(): String = Json.Default.encodeToString(serializer(), this)

    companion object {
        fun decode(raw: String?): FeiniuReportingContext? = raw?.let {
            runCatching { Json.Default.decodeFromString(serializer(), it) }.getOrNull()
        }
    }
}
