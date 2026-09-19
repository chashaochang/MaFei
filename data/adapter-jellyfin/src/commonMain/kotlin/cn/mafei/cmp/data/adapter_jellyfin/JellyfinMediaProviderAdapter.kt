package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaPerson
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageCursor
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.PlaybackSessionId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ResourceNotFoundError
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.adapter_contract.AdapterCapability
import cn.mafei.cmp.data.adapter_contract.HomeFeed
import cn.mafei.cmp.data.adapter_contract.HomeFeedRequest
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.OffsetPageCursorCodec
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackEventKind
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.data.adapter_contract.RefusalReason
import cn.mafei.cmp.data.adapter_contract.requireCapability
import cn.mafei.cmp.providers.jellyfin.facade.JellyfinFacade
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/** 临时诊断打点：首页加载慢定位用，定位完成后可整体删除。 */
private fun timingLog(message: String) {
    println("[MaFeiTiming] $message")
}

private const val HomeRecentLibraryLimit = 16

/** 各媒体库 latest 的并发上限：够了（把延迟相加变成取最大值），又不会压垮 NAS。 */
private const val HomeLatestConcurrency = 4

/** 详情页「相似推荐」条数上限：旧版固定取一小横排，服务器通常默认 12。 */
private const val SimilarItemsLimit = 12

/**
 * Jellyfin implementation of the provider-neutral [MediaProviderAdapter]
 * (GLM-04B), consuming the frozen [JellyfinFacade]. All results are stamped
 * with the bound [serverId]/[accountId]; failures surface as `DomainError`
 * per the contract, with [CancellationException] passing through untouched.
 *
 * Frozen decisions (see docs/superpowers/plans/2026-09-07-glm04b-jellyfin-adapter.md):
 * - `totalRecordCount = 0` is "unknown", never "no media": paging advances on
 *   returned items, and [Page.totalEstimate] is null for 0.
 * - Identity-less wire items are dropped from listings, not failed.
 * - Stream URLs are built by the adapter from the configured base (or by
 *   facade builders for transcode/subtitle links) — same-origin by
 *   construction; server-provided `Path` file locations never enter a Ready.
 */
class JellyfinMediaProviderAdapter(
    private val facade: JellyfinFacade,
    override val serverId: ServerId,
    override val accountId: AccountId,
    /** Jellyfin user id the bound account maps to (from the restored session). */
    private val jellyfinUserId: String,
) : MediaProviderAdapter {

    override val provider: ProviderKind = ProviderKind.Jellyfin

    override val capabilities = AdapterCapabilities(
        search = true,
        favoritesRead = true,
        favoritesFilter = true,
        favoritesEdit = true, // POST/DELETE /Users/{id}/FavoriteItems/{itemId}
        watchedFilter = true,
        watchedEdit = true, // POST/DELETE /Users/{id}/PlayedItems/{itemId}
        playbackReporting = true,
        mediaDelete = true, // DELETE /Items?ids=... — the SERVER rejects non-admins
    )

    /** Streams this instance prepared; reportPlayback accepts only these. */
    private val preparedStreams = mutableListOf<PlaybackPreparation.Ready>()

    /**
     * Cached administrator verdict for [canDeleteMedia]; `null` until the
     * first probe. The server re-checks the policy at delete time — a stale
     * flag here only misplaces a UI affordance.
     */
    @Volatile
    private var administratorCache: Boolean? = null

    private fun imageUrl(itemId: String, imageType: cn.mafei.cmp.providers.jellyfin.wire.ImageType, tag: String?): String =
        facade.images.itemImageUrl(itemId = itemId, imageType = imageType, tag = tag).toEncodedString()

    private suspend fun <T> translate(block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: cn.mafei.cmp.providers.jellyfin.core.JellyfinError) {
            throw e.toDomainError()
        }

    override suspend fun collections(request: PageRequest): Page<MediaCollection> {
        // Single-chunk surface: a non-null cursor is a caller bug, never a
        // license to re-serve page one.
        request.cursor?.let { throw ClientConfigError("collections cannot honor cursor '$it' (single-chunk listing)") }
        val views = translate { facade.catalog.views(jellyfinUserId) }
        val collections = views.items.mapNotNull { it.toMediaCollection(serverId, ::imageUrl) }
        return Page.of(collections, nextCursor = null, totalEstimate = collections.size)
    }

    override suspend fun home(request: HomeFeedRequest): HomeFeed {
        val homeMark = TimeSource.Monotonic.markNow()
        val feed = coroutineScope {
            // 这四段彼此**独立**，原来是一条串行链：推荐 → 继续观看 → 接下来 → 各库最新。
            // 串行发就是把这些来回的延迟直接相加，首屏要等 4 + 库数 个请求。这里并发，
            // 并把每个媒体库的 latest 限流成每批 4 个 —— NAS 上的 Jellyfin 扛不住十几个并发，
            // 但 4 个并发已经能把"相加"变成"取最大值"。
            val recommendations = async {
                val mark = TimeSource.Monotonic.markNow()
                val items = translate {
                    facade.catalog.recommendations(
                        userId = jellyfinUserId,
                        categoryLimit = 4,
                        itemLimit = 4,
                        fields = JELLYFIN_LIST_FIELDS,
                    )
                }.flatMap { it.items }
                    .mapNotNull { it.toMediaItem(serverId, ::imageUrl) }
                    .filter { it.mediaType == cn.mafei.cmp.core.domain.MediaType.Movie }
                    .distinctBy { it.id }
                timingLog("jellyfin/home recommendations ${mark.elapsedNow().inWholeMilliseconds}ms (${items.size})")
                items.take(request.recommendationLimit)
            }
            val resume = async {
                val mark = TimeSource.Monotonic.markNow()
                val items = translate {
                    facade.catalog.resume(
                        userId = jellyfinUserId,
                        limit = request.resumeLimit,
                        includeItemTypes = listOf("Movie", "Episode", "Video"),
                        fields = JELLYFIN_LIST_FIELDS,
                    )
                }.items.mapNotNull { it.toMediaItem(serverId, ::imageUrl) }
                timingLog("jellyfin/home resume ${mark.elapsedNow().inWholeMilliseconds}ms (${items.size})")
                items
            }
            val nextUp = async {
                val mark = TimeSource.Monotonic.markNow()
                val items = translate {
                    facade.catalog.nextUp(jellyfinUserId, request.nextUpLimit, JELLYFIN_LIST_FIELDS)
                }.items.mapNotNull { it.toMediaItem(serverId, ::imageUrl) }
                timingLog("jellyfin/home nextUp ${mark.elapsedNow().inWholeMilliseconds}ms (${items.size})")
                items
            }
            val latest = async {
                val mark = TimeSource.Monotonic.markNow()
                // Keep the original HarmonyOS home contract: Jellyfin's global
                // /Items/Latest response is not the same feed. Read the user's views,
                // fetch a small page from every visual-media library, then interleave
                // those pages so one large library cannot monopolise "recently added".
                val latestViews = translate { facade.catalog.views(jellyfinUserId) }.items.filter { view ->
                    !view.id.isNullOrBlank() && view.collectionType?.trim()?.lowercase() !in setOf("livetv", "music")
                }
                timingLog("jellyfin/home latest-views ${mark.elapsedNow().inWholeMilliseconds}ms (${latestViews.size} libs)")
                val latestPages = latestViews.chunked(HomeLatestConcurrency).flatMap { group ->
                    coroutineScope {
                        group.map { view ->
                            async {
                                val collectionId = CollectionId(view.id!!.trim())
                                val libMark = TimeSource.Monotonic.markNow()
                                val page = try {
                                    translate {
                                        facade.catalog.latest(
                                            userId = jellyfinUserId,
                                            parentId = collectionId.value,
                                            limit = HomeRecentLibraryLimit,
                                            includeItemTypes = listOf("Movie", "Series", "Episode", "Video"),
                                            fields = JELLYFIN_LIST_FIELDS,
                                        )
                                    }.map { item -> collectionId to item }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    // One unavailable library did not blank the other libraries
                                    // in the legacy implementation either.
                                    emptyList()
                                }
                                timingLog(
                                    "jellyfin/home latest-lib=${collectionId.value} " +
                                        "${libMark.elapsedNow().inWholeMilliseconds}ms (${page.size})",
                                )
                                page
                            }
                        }.map { it.await() }
                    }
                }
                val items = interleave(latestPages, request.latestLimit)
                    .mapNotNull { (collectionId, item) -> item.toMediaItem(serverId, ::imageUrl, collectionId) }
                    .distinctBy { it.id }
                    .take(request.latestLimit)
                timingLog("jellyfin/home latest total ${mark.elapsedNow().inWholeMilliseconds}ms (${items.size})")
                items
            }
            HomeFeed(
                recommendations = recommendations.await(),
                resume = resume.await().take(request.resumeLimit),
                nextUp = nextUp.await().take(request.nextUpLimit),
                latest = latest.await(),
            )
        }
        timingLog("jellyfin/home TOTAL ${homeMark.elapsedNow().inWholeMilliseconds}ms")
        return feed
    }

    private fun <T> interleave(pages: List<List<T>>, limit: Int): List<T> {
        val result = ArrayList<T>(limit)
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

    override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> {
        val filter = request.filter
        if (!filter.searchTerm.isNullOrBlank()) {
            throw ClientConfigError("items() does not free-text search; use search()")
        }
        if (filter.favoritesOnly) requireCapability(capabilities, AdapterCapability.FavoritesRead)
        if (filter.playedOnly || filter.unplayedOnly) requireCapability(capabilities, AdapterCapability.WatchedFilter)
        val offset = decodeOffset(request.cursor)
        val (sortBy, sortOrder) = request.sort.toQuerySort()
        val query = JellyfinItemQueryFor(
            parentId = collectionId?.value,
            startIndex = offset,
            limit = request.pageSize,
            sortBy = sortBy,
            sortOrder = sortOrder,
            includeItemTypes = filter.mediaTypes.map { it.toIncludeItemTypes() },
            searchTerm = null,
            genres = filter.genres.toList(),
            years = filter.years.toList(),
            filters = filter.toQueryFilters(),
        )
        return translate { facade.catalog.items(query) }.let { toPage(it, request, offset, collectionId) }
    }

    override suspend fun search(term: String, request: PageRequest): Page<MediaItem> {
        requireCapability(capabilities, AdapterCapability.Search)
        // Search shares the /Items pipeline, so every expressible filter and
        // the sort travel along — dropping them silently would fake results.
        val filter = request.filter
        if (filter.favoritesOnly) requireCapability(capabilities, AdapterCapability.FavoritesRead)
        if (filter.playedOnly || filter.unplayedOnly) requireCapability(capabilities, AdapterCapability.WatchedFilter)
        val offset = decodeOffset(request.cursor)
        val (sortBy, sortOrder) = request.sort.toQuerySort()
        val query = JellyfinItemQueryFor(
            parentId = null,
            startIndex = offset,
            limit = request.pageSize,
            sortBy = sortBy,
            sortOrder = sortOrder,
            includeItemTypes = filter.mediaTypes.map { it.toIncludeItemTypes() },
            searchTerm = term.trim(),
            genres = filter.genres.toList(),
            years = filter.years.toList(),
            filters = filter.toQueryFilters(),
        )
        return translate { facade.catalog.items(query) }.let { toPage(it, request, offset, collectionId = null) }
    }

    override suspend fun item(id: MediaId): MediaItem =
        translate { facade.catalog.item(jellyfinUserId, id.value) }.toMediaItem(serverId, ::imageUrl)
            ?: throw ResourceNotFoundError(resource = "jellyfin item $id")

    override suspend fun person(id: MediaId): MediaPerson =
        translate { facade.catalog.item(jellyfinUserId, id.value) }.toMediaPerson(serverId, ::imageUrl)
            ?: throw ResourceNotFoundError(resource = "jellyfin person $id")

    override suspend fun itemsByPerson(personId: MediaId, request: PageRequest): Page<MediaItem> {
        val filter = request.filter
        if (!filter.searchTerm.isNullOrBlank()) {
            throw ClientConfigError("itemsByPerson() does not free-text search")
        }
        if (filter.favoritesOnly) requireCapability(capabilities, AdapterCapability.FavoritesRead)
        if (filter.playedOnly || filter.unplayedOnly) requireCapability(capabilities, AdapterCapability.WatchedFilter)
        val offset = decodeOffset(request.cursor)
        val (sortBy, sortOrder) = request.sort.toQuerySort()
        val query = JellyfinItemQueryFor(
            parentId = null,
            personIds = listOf(personId.value),
            startIndex = offset,
            limit = request.pageSize,
            sortBy = sortBy,
            sortOrder = sortOrder,
            includeItemTypes = filter.mediaTypes.map { it.toIncludeItemTypes() },
            searchTerm = null,
            genres = filter.genres.toList(),
            years = filter.years.toList(),
            filters = filter.toQueryFilters(),
        )
        return translate { facade.catalog.items(query) }.let {
            toPage(it, request, offset, collectionId = null)
        }
    }

    override suspend fun seasons(seriesId: MediaId): Page<MediaItem> {
        val result = translate { facade.catalog.seasons(jellyfinUserId, seriesId.value, JELLYFIN_LIST_FIELDS) }
        val seasons = result.items.mapNotNull { it.toMediaItem(serverId, ::imageUrl) }
        return Page.of(seasons, nextCursor = null, totalEstimate = result.totalRecordCount.takeIf { it > 0 } ?: seasons.size)
    }

    override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> {
        // The frozen /Shows/{id}/Episodes binding exposes no filter or sort
        // parameters (episodes follow natural season/episode order). Silent
        // drops would fake "unplayed-only" results, so expressible-nowhere
        // constraints are rejected instead.
        if (!request.filter.isEmpty || request.sort != null) {
            throw ClientConfigError(
                "episodes() cannot express sort or user-state/type filters on the Jellyfin shows endpoint",
            )
        }
        val offset = decodeOffset(request.cursor)
        val result = translate {
            facade.catalog.episodes(
                userId = jellyfinUserId,
                seriesId = seriesId.value,
                seasonId = seasonId?.value,
                startIndex = offset,
                limit = request.pageSize,
                fields = JELLYFIN_LIST_FIELDS,
            )
        }
        return toPage(result, request, offset, collectionId = null)
    }

    /**
     * Toggles favorite via POST/DELETE /Users/{id}/FavoriteItems/{itemId}.
     * The response body carries no payload; success is the 2xx itself.
     */
    override suspend fun setFavorite(id: MediaId, favorite: Boolean) {
        requireCapability(capabilities, AdapterCapability.FavoritesEdit)
        translate {
            if (favorite) facade.userdata.markFavorite(jellyfinUserId, id.value)
            else facade.userdata.unmarkFavorite(jellyfinUserId, id.value)
        }
    }

    /** Toggles watched via POST/DELETE /Users/{id}/PlayedItems/{itemId}. */
    override suspend fun setWatched(id: MediaId, watched: Boolean) {
        requireCapability(capabilities, AdapterCapability.WatchedEdit)
        translate {
            if (watched) facade.userdata.markPlayed(jellyfinUserId, id.value)
            else facade.userdata.unmarkPlayed(jellyfinUserId, id.value)
        }
    }

    override suspend fun similar(id: MediaId): List<MediaItem> =
        translate { facade.catalog.similar(jellyfinUserId, id.value, SimilarItemsLimit, JELLYFIN_LIST_FIELDS) }
            .mapNotNull { it.toMediaItem(serverId, ::imageUrl) }

    override suspend fun resumeItems(request: PageRequest): Page<MediaItem> {
        val filter = request.filter
        if (!filter.isEmpty || request.sort != null) {
            throw ClientConfigError(
                "resumeItems() follows the server's resume ordering; sort/filter cannot be expressed",
            )
        }
        val offset = decodeOffset(request.cursor)
        val result = translate {
            facade.catalog.resume(
                userId = jellyfinUserId,
                startIndex = offset,
                limit = request.pageSize,
                includeItemTypes = listOf("Movie", "Episode", "Video"),
                fields = JELLYFIN_LIST_FIELDS,
            )
        }
        return toPage(result, request, offset, collectionId = null)
    }

    override suspend fun deleteItems(ids: Collection<MediaId>) {
        requireCapability(capabilities, AdapterCapability.MediaDelete)
        val unique = ids.map { it.value }.filter { it.isNotBlank() }.distinct()
        if (unique.isEmpty()) return
        translate { facade.userdata.deleteItems(unique) }
    }

    override suspend fun canDeleteMedia(): Boolean {
        administratorCache?.let { return it }
        val policy = translate { facade.userdata.user(jellyfinUserId) }.policy
        val administrator = policy?.isAdministrator == true
        administratorCache = administrator
        return administrator
    }

    override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation {
        // 用户指定的版本/轨道随协商一起提交：服务端按选中的源/轨协商转码决策。
        val pinnedSourceId = options.mediaSourceId?.takeIf { it.isNotBlank() }
        val info = translate {
            facade.playback.playbackInfo(
                userId = jellyfinUserId,
                itemId = item.value,
                mediaSourceId = pinnedSourceId,
                audioStreamIndex = options.audioStreamIndex,
                subtitleStreamIndex = options.subtitleStreamIndex,
            )
        }
        // Any non-null error code refuses — including the SDK's Unknown
        // fallback: an unrecognized server refusal must never be read as
        // "no error".
        info.errorCode?.let { code ->
            return PlaybackPreparation.Refused(RefusalReason.ServerRefused, detail = "Jellyfin playback error code ${code.name}")
        }
        // Source selection honors the server's negotiation in strict order —
        // direct play of the original file, then direct stream, then
        // transcoding. Per official Jellyfin 10.10.7 semantics BOTH direct
        // play and direct stream serve with Static=true; the negotiation
        // decides WHICH source streams statically, it is never stripped.
        // An explicit options.mediaSourceId pins the candidate set to that
        // source; an id the server did not negotiate falls back to the
        // natural order over every negotiated source.
        val candidates: List<cn.mafei.cmp.providers.jellyfin.wire.MediaSourceInfoDto> =
            options.mediaSourceId?.takeIf { it.isNotBlank() }
                ?.let { pinnedId -> info.mediaSources.filter { it.id == pinnedId } }
                ?.takeIf { it.isNotEmpty() }
                ?: info.mediaSources
        val directPlay = candidates.firstOrNull { it.supportsDirectPlay == true }
        val directStream = candidates.firstOrNull { it.supportsDirectStream == true }
        val transcode = candidates.firstOrNull { !it.transcodingUrl.isNullOrBlank() }
        val source = directPlay ?: directStream ?: transcode
            ?: return PlaybackPreparation.Refused(
                RefusalReason.NoPlayableSource,
                detail = "PlaybackInfo offered ${info.mediaSources.size} media source(s) with no usable stream",
            )
        val streamUrl = when {
            directPlay != null || directStream != null ->
                buildStreamUrl(item.value, source, playSessionId = info.playSessionId)
            else -> try {
                facade.playback.transcodingUrl(source.transcodingUrl!!).toEncodedString()
            } catch (e: IllegalArgumentException) {
                // Unparseable or cross-origin link: rejected instead of trusted.
                return PlaybackPreparation.Refused(RefusalReason.LinkRejected, detail = e.message)
            }
        }
        // External text subtitles ride their own validated URL and MUST come
        // from the selected source only — another media version's tracks stay
        // out. An explicit options.subtitleStreamIndex wins (null = default
        // choice below, -1 = subtitles off); otherwise the server's
        // DefaultSubtitleStreamIndex, then the source's default external
        // track. A broken subtitle link drops the subtitle, never the stream.
        val subtitleUrl = selectSubtitleStream(source, options.subtitleStreamIndex)
            ?.deliveryUrl
            ?.let { raw ->
                try {
                    facade.playback.deliveryUrl(raw).toEncodedString()
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        val ready = PlaybackPreparation.Ready(
            streamUrl = streamUrl,
            subtitleUrl = subtitleUrl,
            headers = emptyMap(), // Jellyfin streams carry access in the URL itself
            playSessionId = info.playSessionId?.let(::PlaybackSessionId),
            reportingContext = JellyfinReportingContext(item.value, source.id).encode(),
        )
        preparedStreams.add(ready)
        return ready
    }

    override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) {
        requireCapability(capabilities, AdapterCapability.PlaybackReporting)
        if (preparedStreams.none { it === stream }) {
            throw ClientConfigError("reportPlayback rejected a stream not prepared by this adapter instance")
        }
        val context = JellyfinReportingContext.decode(stream.reportingContext)
            ?: throw ClientConfigError("Jellyfin reporting context is missing or malformed")
        val playSessionId = stream.playSessionId?.value
        // Position 0 is legal and load-bearing: it is how a user seek back to
        // the start clears a stale resume point on the server.
        val positionMs = event.positionMs
        translate {
            when (event.kind) {
                PlaybackEventKind.Start -> facade.playstate.reportStartMs(
                    itemId = context.itemId,
                    playSessionId = playSessionId,
                    mediaSourceId = context.mediaSourceId,
                    positionMs = positionMs,
                )
                PlaybackEventKind.Progress -> facade.playstate.reportProgressMs(
                    itemId = context.itemId,
                    playSessionId = playSessionId,
                    mediaSourceId = context.mediaSourceId,
                    positionMs = positionMs,
                    isPaused = event.isPaused,
                )
                PlaybackEventKind.Stop -> facade.playstate.reportStoppedMs(
                    itemId = context.itemId,
                    playSessionId = playSessionId,
                    mediaSourceId = context.mediaSourceId,
                    positionMs = positionMs,
                )
            }
        }
    }

    // ---------------------------------------------------------------- internals

    /**
     * Builds the `/Videos|Audio/{id}/stream[.container]?static=true` URL
     * against the configured base (same-origin by construction; server-provided
     * file paths never participate). Static=true serves direct play AND
     * direct stream alike, per official Jellyfin 10.10.7 semantics. Audio
     * selection follows the source's stream layout: no video stream plus an
     * audio stream means the `/Audio/` route.
     */
    private fun buildStreamUrl(
        itemId: String,
        source: cn.mafei.cmp.providers.jellyfin.wire.MediaSourceInfoDto,
        playSessionId: String?,
    ): String {
        val audio = source.mediaStreams.none { it.type == cn.mafei.cmp.providers.jellyfin.wire.MediaStreamType.Video } &&
            source.mediaStreams.any { it.type == cn.mafei.cmp.providers.jellyfin.wire.MediaStreamType.Audio }
        val token = facade.client.sessionState.current.value?.accessToken
        val url = facade.client.config.baseUrl.newBuilder()
            .addPathSegment(if (audio) "Audio" else "Videos")
            .addPathSegment(itemId)
            .addPathSegments(source.container?.takeIf { it.isNotBlank() }?.let { "stream.$it" } ?: "stream")
            .apply {
                addQueryParameter("static", "true")
                source.id?.let { addQueryParameter("mediaSourceId", it) }
                playSessionId?.let { addQueryParameter("playSessionId", it) }
                token?.let { addQueryParameter("api_key", it) }
            }
        return url.build().toEncodedString()
    }

    /**
     * Picks the external subtitle track for the SELECTED source. Precedence:
     * an explicit [requestedIndex] (from user-directed
     * [PlaybackOptions.subtitleStreamIndex]; `-1` = off), then the
     * server-mandated [cn.mafei.cmp.providers.jellyfin.wire.MediaSourceInfoDto.defaultSubtitleStreamIndex]
     * (`-1` = subtitles off), then the source's default external track, then
     * the first external one. A mandated embedded track never falls back to
     * a different external one.
     */
    private fun selectSubtitleStream(
        source: cn.mafei.cmp.providers.jellyfin.wire.MediaSourceInfoDto,
        requestedIndex: Int? = null,
    ): cn.mafei.cmp.providers.jellyfin.wire.MediaStreamDto? {
        val external = source.mediaStreams.filter {
            it.deliveryUrl != null &&
                it.deliveryMethod == cn.mafei.cmp.providers.jellyfin.wire.SubtitleDeliveryMethod.External
        }
        if (requestedIndex != null) {
            if (requestedIndex < 0) return null // user turned subtitles off
            // A requested index that is not an external track (embedded-only)
            // cannot be delivered via URL; keep null rather than substituting.
            return external.firstOrNull { it.index == requestedIndex }
        }
        val mandated = source.defaultSubtitleStreamIndex
        if (mandated != null) {
            if (mandated < 0) return null // server turned subtitles off
            return external.firstOrNull { it.index == mandated }
        }
        return external.firstOrNull { it.isDefault == true } ?: external.firstOrNull()
    }

    /**
     * Page assembly with the frozen total semantics: `totalRecordCount == 0`
     * is "unknown" (the wire type cannot distinguish it). With a positive
     * total, the cursor keeps advancing while the offset is short of the
     * total — servers that clamp the page size still complete the walk; an
     * empty page (or a claimed-exhausted total) terminates it, so paging
     * always ends. [collectionId] is the query context stamped onto the
     * returned items, not a wire field.
     */
    private fun toPage(
        result: cn.mafei.cmp.providers.jellyfin.wire.QueryResultDto<cn.mafei.cmp.providers.jellyfin.wire.BaseItemDto>,
        request: PageRequest,
        offset: Int,
        collectionId: CollectionId?,
    ): Page<MediaItem> {
        val items = result.items.mapNotNull { it.toMediaItem(serverId, ::imageUrl, collectionId) }
        val total = result.totalRecordCount
        val nextOffset = offset + result.items.size
        val nextCursor: PageCursor? = when {
            result.items.isEmpty() -> null // terminal empty page, regardless of totals
            total > 0 && nextOffset >= total -> null // server declares the end
            result.items.size >= request.pageSize -> OffsetPageCursorCodec.encode(nextOffset)
            total > 0 -> OffsetPageCursorCodec.encode(nextOffset) // clamped page, but the total says there is more
            else -> null // no total information: a short page is the last one
        }
        return Page.of(items, nextCursor = nextCursor, totalEstimate = total.takeIf { it > 0 })
    }

    private fun decodeOffset(cursor: PageCursor?): Int = cursor?.let(OffsetPageCursorCodec::decode) ?: 0

    private fun JellyfinItemQueryFor(
        parentId: String?,
        personIds: List<String> = emptyList(),
        startIndex: Int,
        limit: Int,
        sortBy: List<cn.mafei.cmp.providers.jellyfin.transport.JellyfinSortBy>,
        sortOrder: cn.mafei.cmp.providers.jellyfin.transport.JellyfinSortOrder,
        includeItemTypes: List<String>,
        searchTerm: String?,
        genres: List<String>,
        years: List<Int>,
        filters: List<cn.mafei.cmp.providers.jellyfin.transport.JellyfinItemFilter>,
    ): cn.mafei.cmp.providers.jellyfin.transport.JellyfinItemQuery =
        cn.mafei.cmp.providers.jellyfin.transport.JellyfinItemQuery(
            userId = jellyfinUserId,
            parentId = parentId,
            personIds = personIds,
            startIndex = startIndex,
            limit = limit,
            includeItemTypes = includeItemTypes,
            fields = JELLYFIN_LIST_FIELDS,
            sortBy = sortBy,
            sortOrder = sortOrder,
            recursive = true,
            searchTerm = searchTerm,
            genres = genres,
            years = years,
            filters = filters,
            imageTypeLimit = 1,
        )
}

/**
 * Opaque reporting context carried inside [PlaybackPreparation.Ready] —
 * Jellyfin progress reports need the item and negotiated media source beyond
 * the (nullable) play session id. Encoded as JSON; never persisted, never
 * derived from stream URLs.
 */
@Serializable
private data class JellyfinReportingContext(
    val itemId: String,
    val mediaSourceId: String? = null,
) {
    fun encode(): String = Json.Default.encodeToString(serializer(), this)

    companion object {
        fun decode(raw: String?): JellyfinReportingContext? = raw?.let {
            runCatching { Json.Default.decodeFromString(serializer(), it) }.getOrNull()
        }
    }
}
