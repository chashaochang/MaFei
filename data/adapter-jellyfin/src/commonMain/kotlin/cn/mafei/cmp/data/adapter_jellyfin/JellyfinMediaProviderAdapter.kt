package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageCursor
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.PlaybackSessionId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ResourceNotFoundError
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.adapter_contract.AdapterCapability
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.OffsetPageCursorCodec
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackEventKind
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.data.adapter_contract.RefusalReason
import cn.mafei.cmp.data.adapter_contract.requireCapability
import cn.mafei.cmp.providers.jellyfin.facade.JellyfinFacade
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

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
        favoritesEdit = false, // P0 SDK has no user-data write endpoints
        watchedFilter = true,
        watchedEdit = false,
        playbackReporting = true,
    )

    /** Streams this instance prepared; reportPlayback accepts only these. */
    private val preparedStreams = mutableListOf<PlaybackPreparation.Ready>()

    private fun imageUrl(itemId: String, imageType: cn.mafei.cmp.providers.jellyfin.wire.ImageType, tag: String): String =
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
     * P0 SDK has no user-data write endpoints: the capability is declared
     * false and the gate refuses before any provider call — never a faked
     * success.
     */
    override suspend fun setFavorite(id: MediaId, favorite: Boolean) {
        requireCapability(capabilities, AdapterCapability.FavoritesEdit)
    }

    override suspend fun setWatched(id: MediaId, watched: Boolean) {
        requireCapability(capabilities, AdapterCapability.WatchedEdit)
    }

    override suspend fun preparePlayback(item: MediaId): PlaybackPreparation {
        val info = translate { facade.playback.playbackInfo(userId = jellyfinUserId, itemId = item.value) }
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
        val directPlay = info.mediaSources.firstOrNull { it.supportsDirectPlay == true }
        val directStream = info.mediaSources.firstOrNull { it.supportsDirectStream == true }
        val transcode = info.mediaSources.firstOrNull { !it.transcodingUrl.isNullOrBlank() }
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
        // out. The server's DefaultSubtitleStreamIndex wins when present
        // (-1 = subtitles off); otherwise the source's default external track.
        // A broken subtitle link drops the subtitle, never the stream.
        val subtitleUrl = selectSubtitleStream(source)
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
     * Picks the external subtitle track for the SELECTED source. The
     * server-mandated [cn.mafei.cmp.providers.jellyfin.wire.MediaSourceInfoDto.defaultSubtitleStreamIndex]
     * wins when present: `-1` means subtitles are off (null), a track index
     * resolves to that exact external track or none at all (a mandated
     * embedded track never falls back to a different external one). Without
     * a server mandate, the source's default external track wins, then the
     * first external one.
     */
    private fun selectSubtitleStream(source: cn.mafei.cmp.providers.jellyfin.wire.MediaSourceInfoDto): cn.mafei.cmp.providers.jellyfin.wire.MediaStreamDto? {
        val external = source.mediaStreams.filter {
            it.deliveryUrl != null &&
                it.deliveryMethod == cn.mafei.cmp.providers.jellyfin.wire.SubtitleDeliveryMethod.External
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
