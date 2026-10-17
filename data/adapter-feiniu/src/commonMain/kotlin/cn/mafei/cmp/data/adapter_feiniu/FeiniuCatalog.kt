package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.AuthenticationError
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaPerson
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaUserData
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageCursor
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.ResourceNotFoundError
import cn.mafei.cmp.core.domain.SortDirection
import cn.mafei.cmp.core.domain.MediaSort
import cn.mafei.cmp.core.domain.MediaSortField
import cn.mafei.cmp.data.adapter_contract.AdapterCapability
import cn.mafei.cmp.data.adapter_contract.OffsetPageCursorCodec
import cn.mafei.cmp.data.adapter_contract.requireCapability
import cn.mafei.cmp.providers.feiniu.core.FeiniuItemListData
import cn.mafei.cmp.providers.feiniu.core.FeiniuMediaItem
import cn.mafei.cmp.providers.feiniu.core.FeiniuPersonItem
import cn.mafei.cmp.providers.feiniu.core.FeiniuPersonItemListRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayItem
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException

private data class FeiniuEpisodeCandidate(
    val seasonId: String?,
    val item: FeiniuMediaItem,
)


/** Catalog paging, search, person works, episode selection and media actions. */
internal class FeiniuCatalog(
    private val adapterSession: FeiniuAdapterSession,
    private val details: FeiniuMediaDetails,
) {
    private val resolution get() = adapterSession.resolution
    private val serverId get() = adapterSession.serverId
    private val provider get() = adapterSession.provider
    private val capabilities get() = adapterSession.capabilities

    suspend fun collections(request: PageRequest): Page<MediaCollection> {
        // Single-chunk surface: a non-null cursor is a caller bug, never a
        // license to re-serve page one.
        request.cursor?.let { throw ClientConfigError("collections cannot honor cursor '$it' (single-chunk listing)") }
        val libraries = adapterSession.translate { adapterSession.facade.catalog.mediaLibraries(resolution) }
            .filterNot { it.category.isFeiniuLiveLibrary() }
        val collections = libraries.mapNotNull { it.toMediaCollection(serverId, adapterSession::imageUrl) }
        return Page.of(collections, nextCursor = null, totalEstimate = collections.size)
    }

    suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> {
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
            val data = adapterSession.translate {
                adapterSession.facade.catalog.favorites(
                    resolution = resolution,
                    ancestorGuid = collectionId?.value,
                    page = page,
                    pageSize = pageSize,
                    sortColumn = sortColumn,
                    sortType = sortType,
                    itemTypes = itemTypes.takeIf { it.isNotEmpty() },
                )
            }
            val result = toPage(data, request, position, collectionId, adapterSession.genreNamesFor(data.list))
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
        val data = adapterSession.translate {
            adapterSession.facade.catalog.items(
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
        val result = toPage(data, request, position, collectionId, adapterSession.genreNamesFor(data.list))
        return result.copy(
            items = result.items.filter { filter.mediaTypes.isEmpty() || it.mediaType in filter.mediaTypes },
        )
    }

    suspend fun search(term: String, request: PageRequest): Page<MediaItem> {
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
        val all = adapterSession.translate { adapterSession.facade.catalog.search(resolution, query) }
        val names = adapterSession.genreNamesFor(all)
        val items = all.mapNotNull { it.toMediaItem(serverId, adapterSession::imageUrl, genreNames = names) }
            .filter { filter.mediaTypes.isEmpty() || it.mediaType in filter.mediaTypes }
        val selected = items.drop(offset).take(request.pageSize)
        val nextOffset = offset.toLong() + selected.size.toLong()
        val next = nextOffset
            .takeIf { selected.isNotEmpty() && it < items.size.toLong() && it <= Int.MAX_VALUE.toLong() }
            ?.toInt()
            ?.let(OffsetPageCursorCodec::encode)
        return Page.of(selected, nextCursor = next, totalEstimate = items.size)
    }

    suspend fun person(id: MediaId): MediaPerson =
        adapterSession.translate { adapterSession.facade.catalog.person(resolution, id.value) }.toMediaPerson(serverId, adapterSession::imageUrl)
            ?: throw ResourceNotFoundError(resource = "feiniu person $id")

    suspend fun itemsByPerson(personId: MediaId, request: PageRequest): Page<MediaItem> {
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
            .mapNotNull { it.toMediaItem(serverId, adapterSession::imageUrl) }
            .filter { allowedTypes.isEmpty() || it.mediaType in allowedTypes }
            .distinctBy { it.id }
            .take(pageSize)
            .toList()
        val sourceCount = minOf(pageSize, sources.size)
        val nextOffset = offset + sourceCount
        val next = nextOffset.takeIf { sourceCount >= pageSize && it < total }?.let(OffsetPageCursorCodec::encode)
        return Page.of(items, nextCursor = next, totalEstimate = total)
    }

    suspend fun seasons(seriesId: MediaId): Page<MediaItem> {
        val seasons = adapterSession.translate { adapterSession.facade.catalog.seasons(resolution, seriesId.value) }
        val names = adapterSession.genreNamesFor(seasons)
        val items = seasons.mapNotNull { season ->
            season.toMediaItem(serverId, adapterSession::imageUrl, genreNames = names)?.copy(
                title = season.title.takeIf(String::isNotBlank)
                    ?: season.seasonNumber?.takeIf { it > 0 }?.let { "第 $it 季" }
                    ?: "剧季",
                mediaType = MediaType.Season,
                seriesId = seriesId,
            )
        }
        return Page.of(items, nextCursor = null, totalEstimate = items.size)
    }

    suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> {
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
            adapterSession.translate { adapterSession.facade.catalog.episodes(resolution, seasonId.value) }
                .map { FeiniuEpisodeCandidate(seasonId.value, it) }
        }
        val selected = candidates.drop(offset).take(request.pageSize)
        val names = adapterSession.genreNamesFor(selected.map { it.item })
        val items = selected.mapNotNull { candidate ->
            candidate.item.toMediaItem(serverId, adapterSession::imageUrl, genreNames = names)?.copy(
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
    suspend fun nextUp(seriesId: MediaId): MediaItem? = coroutineScope {
        val episodesDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            feiniuEpisodeCandidates(seriesId)
        }
        val historyDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            adapterSession.translate { adapterSession.facade.catalog.playHistory(resolution) }
        }
        val candidate = selectFeiniuNextUp(episodesDeferred.await(), historyDeferred.await()) ?: return@coroutineScope null
        val names = adapterSession.genreNamesFor(listOf(candidate.first.item))
        val mapped = candidate.first.item.toMediaItem(serverId, adapterSession::imageUrl, genreNames = names)
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
            adapterSession.translate { adapterSession.facade.catalog.seasons(resolution, seriesId.value) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (authentication: AuthenticationError) {
            throw authentication
        } catch (_: Exception) {
            return adapterSession.translate { adapterSession.facade.catalog.episodes(resolution, seriesId.value) }
                .sortedBy { it.episodeNumber ?: 0 }
                .map { FeiniuEpisodeCandidate(null, it) }
        }
        if (seasons.isEmpty()) {
            return adapterSession.translate { adapterSession.facade.catalog.episodes(resolution, seriesId.value) }
                .sortedBy { it.episodeNumber ?: 0 }
                .map { FeiniuEpisodeCandidate(null, it) }
        }
        return coroutineScope {
            seasons.mapNotNull { season ->
                season.guid.trim().takeIf { it.isNotEmpty() }
            }.map { seasonId ->
                async(start = CoroutineStart.UNDISPATCHED) {
                    adapterSession.translate { adapterSession.facade.catalog.episodes(resolution, seasonId) }
                        .sortedBy { it.episodeNumber ?: 0 }
                        .map { FeiniuEpisodeCandidate(seasonId, it) }
                }
            }.awaitAll().flatten()
        }
    }

    suspend fun setFavorite(id: MediaId, favorite: Boolean) {
        adapterSession.translate {
            if (favorite) adapterSession.facade.catalog.markFavorite(resolution, id.value)
            else adapterSession.facade.catalog.unmarkFavorite(resolution, id.value)
        }
    }

    suspend fun setWatched(id: MediaId, watched: Boolean) {
        adapterSession.translate {
            if (watched) adapterSession.facade.catalog.markWatched(resolution, id.value)
            else adapterSession.facade.catalog.unmarkWatched(resolution, id.value)
        }
    }

    suspend fun resumeItems(request: PageRequest): Page<MediaItem> {
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
        val source = adapterSession.translate { adapterSession.facade.catalog.playHistory(resolution) }
        // Exact legacy order: enrich the provider-owned resume rows first,
        // then dedupe and apply the requested startIndex/limit.
        val enriched = details.enrichedResumeItems(source, filter.mediaTypes)
        val selected = enriched.drop(offset).take(request.pageSize)
        val nextOffset = offset + selected.size
        val next = nextOffset.takeIf { selected.isNotEmpty() && it < enriched.size }
            ?.let(OffsetPageCursorCodec::encode)
        return Page.of(selected, nextCursor = next, totalEstimate = enriched.size)
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
        suspend fun fetch(page: Int) = adapterSession.translate {
            adapterSession.facade.catalog.personItems(
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
        val items = data.list.mapNotNull { it.toMediaItem(serverId, adapterSession::imageUrl, collectionId, genreNames) }
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

}

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
