package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.AuthenticationError
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageCursor
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ResourceNotFoundError
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.domain.SortDirection
import cn.mafei.cmp.core.domain.MediaSort
import cn.mafei.cmp.core.domain.MediaSortField
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.adapter_contract.AdapterCapability
import cn.mafei.cmp.data.adapter_contract.HomeFeed
import cn.mafei.cmp.data.adapter_contract.HomeFeedRequest
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.data.adapter_contract.PlaybackEventKind
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.data.adapter_contract.RefusalReason
import cn.mafei.cmp.data.adapter_contract.requireCapability
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuItemListData
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayPlayRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayRecordRequest
import cn.mafei.cmp.providers.feiniu.facade.FeiniuFacade
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/** 临时诊断打点：首页加载慢定位用，定位完成后可整体删除。 */
private fun timingLog(message: String) {
    println("[MaFeiTiming] $message")
}

private const val LEGACY_RECOMMENDATION_DETAIL_LIMIT = 6

/**
 * Feiniu implementation of the provider-neutral [MediaProviderAdapter]
 * (GLM-04C), consuming the frozen [FeiniuFacade] bound to one server +
 * account + host resolution. Failures surface as `DomainError` per the
 * contract; [CancellationException] passes through untouched.
 *
 * Frozen decisions (docs/superpowers/plans/2026-09-08-glm04c-feiniu-adapter.md):
 * - the dedicated favorites endpoint answers favorites-only listings but
 *   cannot combine further filters (`favoritesFilter=false` refuses combos);
 * - the watched tag wire literal has no evidence anywhere (legacy never used
 *   it), so played/unplayed filters refuse (`watchedFilter=false`) instead of
 *   guessing values — genres (Int tag) and decade (string tag) likewise;
 * - `/episode/list/{guid}` takes the SEASON guid (legacy FeiniuVideoCatalogProvider.ets:1377);
 * - progress reports convert position ms to seconds and skip ts<=0 entirely
 *   (legacy FeiniuVideoPlaybackProvider.ets:739-752).
 */
class FeiniuMediaProviderAdapter(
    private val facade: FeiniuFacade,
    override val serverId: ServerId,
    override val accountId: AccountId,
    /** Host candidates this adapter negotiates against (preferred + fallbacks). */
    private val resolution: FeiniuHostResolution,
) : MediaProviderAdapter {

    override val provider: ProviderKind = ProviderKind.Feiniu

    override val capabilities = AdapterCapabilities(
        search = true,
        favoritesRead = true,
        favoritesFilter = false,
        favoritesEdit = true,
        watchedFilter = false,
        watchedEdit = true,
        playbackReporting = true,
    )

    /** Streams this instance prepared; reportPlayback accepts only these. */
    private val preparedStreams = mutableListOf<PlaybackPreparation.Ready>()
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

    private fun imageUrl(imagePath: String): String =
        facade.assets.imageUrl(resolution.preferred.baseUrl, imagePath)

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
        val collections = libraries.mapNotNull { it.toMediaCollection(serverId, ::imageUrl) }
        return Page.of(collections, nextCursor = null, totalEstimate = collections.size)
    }

    override suspend fun home(request: HomeFeedRequest): HomeFeed {
        val homeMark = TimeSource.Monotonic.markNow()
        val feed = coroutineScope {
            val candidateLimit = maxOf(request.latestLimit, request.recommendationLimit * 4)
            // 三个请求彼此独立：串行发就是把延迟相加。并发后首屏只等最慢的那一个。
            val latestDataDeferred = async {
            val mark = TimeSource.Monotonic.markNow()
            val data = translate {
                facade.catalog.items(
                    resolution = resolution,
                    page = 1,
                    pageSize = candidateLimit,
                    sortColumn = "create_time",
                    sortType = "DESC",
                )
            }
            timingLog("feiniu/home latest ${mark.elapsedNow().inWholeMilliseconds}ms (${data.list.size})")
            data
        }
        val highestRatedDataDeferred = async {
            val mark = TimeSource.Monotonic.markNow()
            val data = translate {
                facade.catalog.items(
                    resolution = resolution,
                    page = 1,
                    pageSize = candidateLimit,
                    sortColumn = "vote_average",
                    sortType = "DESC",
                )
            }
            timingLog("feiniu/home highestRated ${mark.elapsedNow().inWholeMilliseconds}ms (${data.list.size})")
            data
        }
        val playHistoryDeferred = async {
            val mark = TimeSource.Monotonic.markNow()
            val data = translate { facade.catalog.playHistory(resolution) }
            timingLog("feiniu/home playHistory ${mark.elapsedNow().inWholeMilliseconds}ms (${data.size})")
            data
        }
            val latestData = latestDataDeferred.await()
            val highestRatedData = highestRatedDataDeferred.await()
            val playHistory = playHistoryDeferred.await()
            val latest = latestData.list.mapNotNull { it.toMediaItem(serverId, ::imageUrl) }
            val resume = playHistory
                .mapNotNull { it.toMediaItem(serverId, ::imageUrl) }
                .filter { it.userData.hasProgress && !it.userData.isPlayed }
                .distinctBy { it.id }
                .take(request.resumeLimit)
            val seen = playHistory.mapTo(mutableSetOf()) { it.guid }
            val recommendationCandidates = (highestRatedData.list + latestData.list)
                .mapNotNull { it.toMediaItem(serverId, ::imageUrl) }
                .filter { it.mediaType == cn.mafei.cmp.core.domain.MediaType.Movie || it.mediaType == cn.mafei.cmp.core.domain.MediaType.Series }
                .filter { it.id.value !in seen }
                .distinctBy { it.id }
                .take(request.recommendationLimit)
            val detailMark = TimeSource.Monotonic.markNow()
            val recommendations = coroutineScope {
                recommendationCandidates.mapIndexed { index, item ->
                    async {
                        val mark = TimeSource.Monotonic.markNow()
                        val enriched = if (index < LEGACY_RECOMMENDATION_DETAIL_LIMIT) recommendationWithDetail(item) else item
                        if (index < LEGACY_RECOMMENDATION_DETAIL_LIMIT) {
                            timingLog("feiniu/home recommendation-detail[${item.id.value}] ${mark.elapsedNow().inWholeMilliseconds}ms")
                        }
                        enriched
                    }
                }.awaitAll()
            }
            timingLog("feiniu/home recommendation-details ${detailMark.elapsedNow().inWholeMilliseconds}ms (concurrency)")
            HomeFeed(
                recommendations = recommendations,
                resume = resume,
                // fnOS has no global next-up endpoint. The legacy client also kept
                // this home section empty instead of issuing an N+1 series walk.
                nextUp = emptyList(),
                latest = latest.take(request.latestLimit),
            )
        }
        timingLog("feiniu/home TOTAL ${homeMark.elapsedNow().inWholeMilliseconds}ms")
        return feed
    }

    /** The old provider enriched the selected first six recommendations to obtain `backdrops`. */
    private suspend fun recommendationWithDetail(fallback: MediaItem): MediaItem {
        val detail = try {
            translate { facade.catalog.itemDetail(resolution, fallback.id.value) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (authentication: AuthenticationError) {
            throw authentication
        } catch (_: Exception) {
            return fallback
        }
        val mapped = detail.toMediaItem(serverId, ::imageUrl)
            ?.takeIf { it.id == fallback.id }
            ?: return fallback
        val detailedKinds = mapped.images.mapTo(mutableSetOf()) { it.kind }
        return fallback.copy(
            title = mapped.title.takeIf { it.isNotBlank() } ?: fallback.title,
            mediaType = mapped.mediaType.takeUnless { it == cn.mafei.cmp.core.domain.MediaType.Unknown }
                ?: fallback.mediaType,
            originalTitle = mapped.originalTitle ?: fallback.originalTitle,
            overview = mapped.overview?.takeIf { it.isNotBlank() } ?: fallback.overview,
            year = mapped.year ?: fallback.year,
            premiereEpochMs = mapped.premiereEpochMs ?: fallback.premiereEpochMs,
            communityRating = mapped.communityRating ?: fallback.communityRating,
            officialRating = mapped.officialRating ?: fallback.officialRating,
            images = mapped.images + fallback.images.filter { it.kind !in detailedKinds },
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
            if (request.sort != null && request.sort != MediaSort(MediaSortField.DateAdded, SortDirection.Descending)) {
                throw ClientConfigError("Feiniu favorites endpoint only supports its default DateAdded descending sort")
            }
            // The dedicated endpoint carries no tags: combined constraints
            // would be silently dropped, so they refuse (favoritesFilter=false).
            if (filter.mediaTypes.isNotEmpty() || filter.genres.isNotEmpty() || filter.years.isNotEmpty() ||
                filter.playedOnly || filter.unplayedOnly
            ) {
                throw ClientConfigError("Feiniu favorites listing cannot combine further filters (favoritesFilter unsupported)")
            }
            val data = translate {
                facade.catalog.favorites(
                    resolution = resolution,
                    ancestorGuid = collectionId?.value,
                    category = null,
                    page = page,
                    pageSize = pageSize,
                )
            }
            return toPage(data, request, position, collectionId)
        }
        if (filter.playedOnly || filter.unplayedOnly) requireCapability(capabilities, AdapterCapability.WatchedFilter)
        if (filter.genres.isNotEmpty()) throwUnsupportedFeiniuFilter("genres")
        if (filter.years.isNotEmpty()) throwUnsupportedFeiniuFilter("decade")
        val category = when {
            filter.mediaTypes.isEmpty() -> null
            filter.mediaTypes.size == 1 -> filter.mediaTypes.first().toFeiniuCategory()
            else -> throw ClientConfigError("Feiniu category filter cannot express multiple media types")
        }
        val (sortColumn, sortType) = request.sort.toFeiniuSort()
        val data = translate {
            facade.catalog.items(
                resolution = resolution,
                ancestorGuid = collectionId?.value,
                category = category,
                page = page,
                pageSize = pageSize,
                sortColumn = sortColumn,
                sortType = sortType,
            )
        }
        return toPage(data, request, position, collectionId)
    }

    override suspend fun search(term: String, request: PageRequest): Page<MediaItem> {
        requireCapability(capabilities, AdapterCapability.Search)
        // The keyword endpoint takes nothing else — silent drops would fake
        // results, local reordering would fake sorts.
        if (!request.filter.isEmpty) {
            throw ClientConfigError("Feiniu search accepts a keyword only; filters are not expressible")
        }
        if (request.sort != null) {
            throw ClientConfigError("Feiniu search has no server-side sort")
        }
        // The endpoint is unpaged and the adapter is stateless: every call is
        // one fresh request served as a single chunk. A cursor cannot be
        // honored (it would re-query, not page) and is refused, mirroring the
        // single-chunk collections semantics.
        request.cursor?.let { throw ClientConfigError("search cannot honor cursor '$it' (single-chunk listing)") }
        val all = translate { facade.catalog.search(resolution, term.trim()) }
        val items = all.mapNotNull { it.toMediaItem(serverId, ::imageUrl) }
        return Page.of(items, nextCursor = null, totalEstimate = items.size)
    }

    override suspend fun item(id: MediaId): MediaItem {
        val detail = translate { facade.catalog.itemDetail(resolution, id.value) }
        // Legacy guard: a detail payload for another guid is not this item.
        if (detail.guid?.trim()?.equals(id.value, ignoreCase = false) == false) {
            throw ResourceNotFoundError(resource = "feiniu item $id (server returned ${detail.guid})")
        }
        return detail.toMediaItem(serverId, ::imageUrl)
            ?: throw ResourceNotFoundError(resource = "feiniu item $id")
    }

    override suspend fun seasons(seriesId: MediaId): Page<MediaItem> {
        val seasons = translate { facade.catalog.seasons(resolution, seriesId.value) }
        val items = seasons.mapNotNull { it.toMediaItem(serverId, ::imageUrl) }
        return Page.of(items, nextCursor = null, totalEstimate = items.size)
    }

    override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> {
        // The endpoint takes the SEASON guid only (legacy behavior) — a
        // whole-series walk and filter/sort parameters are not expressible.
        val season = seasonId ?: throw ClientConfigError("episodes() requires a season id on Feiniu (the endpoint takes a season guid)")
        if (request.cursor != null) {
            throw ClientConfigError("episodes cannot honor cursor '${request.cursor}' (single-chunk listing)")
        }
        if (!request.filter.isEmpty || request.sort != null) {
            throw ClientConfigError("episodes() cannot express sort or user-state/type filters on the Feiniu episode endpoint")
        }
        val episodes = translate { facade.catalog.episodes(resolution, season.value) }
        val items = episodes.mapNotNull { it.toMediaItem(serverId, ::imageUrl) }
        return Page.of(items, nextCursor = null, totalEstimate = items.size)
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

    override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation {
        val info = translate { facade.playback.playInfo(resolution, itemGuid = item.value, pinnedOperation = currentOperation()) }
        val mediaGuid = info.mediaGuid?.takeIf { it.isNotBlank() }
            ?: return PlaybackPreparation.Refused(
                RefusalReason.NoPlayableSource,
                detail = "play/info offered no media_guid for ${item.value}",
            )
        // P0: stream/list track typing is still raw JSON, so the play request
        // uses the legacy default negotiation values exactly like the old
        // client when no usable track info exists (codec h264/aac, 2 channels,
        // forcedSdr 0, bitrate 0, empty resolution). startTimestamp stays 0 —
        // the contract has no resume-position parameter yet (04D/player batch).
        val playRequest = FeiniuPlayPlayRequest(
            mediaGuid = mediaGuid,
            videoGuid = info.videoGuid ?: "",
            videoEncoder = "h264",
            resolution = "",
            bitrate = 0L,
            startTimestamp = 0L,
            audioEncoder = "aac",
            audioGuid = info.audioGuid ?: "",
            subtitleGuid = info.subtitleGuid ?: "",
            channels = 2,
            forcedSdr = 0,
        )
        val session = translate { facade.playback.play(resolution, playRequest, pinnedOperation = currentOperation()) }
        val playLink = session.data.playLink
        if (playLink.isNullOrBlank()) {
            return PlaybackPreparation.Refused(RefusalReason.LinkMissing, detail = "play/play returned no play_link")
        }
        // Server-issued, EXPIRING link resolved against the serving origin;
        // cross-origin/unparseable targets are rejected, never trusted.
        val streamUrl = session.resolvePlayLink(resolution.preferred.baseUrl)
            ?: return PlaybackPreparation.Refused(
                RefusalReason.LinkRejected,
                detail = "play_link failed same-origin validation",
            )
        // A rejected subtitle link drops the subtitle, never the stream.
        val subtitleUrl = session.resolveSubtitleLink(resolution.preferred.baseUrl)
        val ready = PlaybackPreparation.Ready(
            streamUrl = streamUrl,
            subtitleUrl = subtitleUrl,
            // Authorization + token cookie (and mode=relay on relay routes).
            headers = facade.assets.playbackHeaders(currentOperation().credentials, session.requiresRelayCookie),
            playSessionId = null, // Feiniu issues no play session id
            reportingContext = FeiniuReportingContext(
                itemGuid = info.guid?.takeIf { it.isNotBlank() } ?: item.value,
                mediaGuid = mediaGuid,
                videoGuid = info.videoGuid ?: "",
                audioGuid = info.audioGuid ?: "",
                subtitleGuid = info.subtitleGuid ?: "",
                resolution = "",
                bitrate = 0L,
                durationSeconds = info.item?.durationSeconds,
                playLink = streamUrl,
            ).encode(),
        )
        preparedStreams.add(ready)
        return ready
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
    ): Page<MediaItem> {
        val items = data.list.mapNotNull { it.toMediaItem(serverId, ::imageUrl, collectionId) }
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

    /** Provider default is create_time DESC; only DateAdded has wire evidence. */
    private fun MediaSort?.toFeiniuSort(): Pair<String, String> {
        val sort = this ?: return "create_time" to "DESC"
        if (sort.field != MediaSortField.DateAdded) {
            throw ClientConfigError("Feiniu cannot sort by ${sort.field}; no wire evidence for that sort column")
        }
        val type = if (sort.direction == SortDirection.Ascending) "ASC" else "DESC"
        return "create_time" to type
    }
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
