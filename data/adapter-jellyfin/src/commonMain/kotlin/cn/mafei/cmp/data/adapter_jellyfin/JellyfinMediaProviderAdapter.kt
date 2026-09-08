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
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackErrorCode
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
 * - Stream URLs are only ever built by the facade's URL builders (same-origin
 *   validated); server-provided `Path` file locations never enter a Ready.
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
        return translate { facade.catalog.items(query) }.let { toPage(it, request, offset) }
    }

    override suspend fun search(term: String, request: PageRequest): Page<MediaItem> {
        requireCapability(capabilities, AdapterCapability.Search)
        val offset = decodeOffset(request.cursor)
        val (sortBy, sortOrder) = request.sort.toQuerySort()
        val query = JellyfinItemQueryFor(
            parentId = null,
            startIndex = offset,
            limit = request.pageSize,
            sortBy = sortBy,
            sortOrder = sortOrder,
            includeItemTypes = emptyList(),
            searchTerm = term.trim(),
            genres = emptyList(),
            years = emptyList(),
            filters = emptyList(),
        )
        return translate { facade.catalog.items(query) }.let { toPage(it, request, offset) }
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
        return toPage(result, request, offset)
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
        info.errorCode?.takeIf { it != PlaybackErrorCode.Unknown }?.let { code ->
            return PlaybackPreparation.Refused(RefusalReason.ServerRefused, detail = "Jellyfin playback error code ${code.name}")
        }
        val direct = info.mediaSources.firstOrNull { it.supportsDirectPlay == true || it.supportsDirectStream == true }
        val source = direct
            ?: info.mediaSources.firstOrNull { !it.transcodingUrl.isNullOrBlank() }
            ?: return PlaybackPreparation.Refused(
                RefusalReason.NoPlayableSource,
                detail = "PlaybackInfo offered ${info.mediaSources.size} media source(s) with no usable stream",
            )
        val streamUrl = try {
            if (direct != null) {
                facade.playback.directStreamUrl(
                    itemId = item.value,
                    mediaSourceId = source.id,
                    container = source.container,
                    playSessionId = info.playSessionId,
                ).toEncodedString()
            } else {
                facade.playback.transcodingUrl(source.transcodingUrl!!).toEncodedString()
            }
        } catch (e: IllegalArgumentException) {
            // Unparseable or cross-origin link: rejected instead of trusted.
            return PlaybackPreparation.Refused(RefusalReason.LinkRejected, detail = e.message)
        }
        // External text subtitles ride their own validated URL; a broken
        // subtitle link drops the subtitle, never the stream.
        val subtitleUrl = info.mediaSources
            .asSequence()
            .flatMap { it.mediaStreams.asSequence() }
            .firstOrNull { it.deliveryUrl != null && it.deliveryMethod == cn.mafei.cmp.providers.jellyfin.wire.SubtitleDeliveryMethod.External }
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
        val positionMs = event.positionMs.takeIf { it > 0L }
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
     * Page assembly with the frozen total semantics: `totalRecordCount == 0`
     * is "unknown" (the wire type cannot distinguish it), so paging advances
     * purely on returned items — a short page ends the walk; an aligned full
     * page earns a cursor and the (rare) empty follow-up page terminates it.
     */
    private fun toPage(
        result: cn.mafei.cmp.providers.jellyfin.wire.QueryResultDto<cn.mafei.cmp.providers.jellyfin.wire.BaseItemDto>,
        request: PageRequest,
        offset: Int,
    ): Page<MediaItem> {
        val items = result.items.mapNotNull { it.toMediaItem(serverId, ::imageUrl) }
        val nextCursor: PageCursor? = if (result.items.size >= request.pageSize && result.items.isNotEmpty()) {
            OffsetPageCursorCodec.encode(offset + result.items.size)
        } else {
            null
        }
        return Page.of(items, nextCursor = nextCursor, totalEstimate = result.totalRecordCount.takeIf { it > 0 })
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
