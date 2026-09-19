package cn.mafei.cmp.data.adapter_contract

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.DomainError
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaFilter
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaSort
import cn.mafei.cmp.core.domain.MediaSortField
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageCursor
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ResourceNotFoundError
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.domain.SortDirection
import kotlin.coroutines.cancellation.CancellationException

/**
 * Provider-neutral fake implementing [MediaProviderAdapter] for contract
 * boundary tests and (after promotion to a testkit) later data-layer tests.
 *
 * It enforces the declared contract, not just a happy path — downstream
 * repository tests must not pass against a looser fake than real adapters:
 * - `collections` rejects non-null cursors (single-chunk providers never
 *   restart from page one).
 * - `items` enforces the filter capabilities ([AdapterCapability.favoritesRead]/
 *   [AdapterCapability.watchedFilter]/[AdapterCapability.favoritesFilter]),
 *   honors [MediaFilter] user-state flags for real, and rejects free-text
 *   search terms (that is [MediaProviderAdapter.search]'s job).
 * - `reportPlayback` rejects any [PlaybackPreparation.Ready] this instance
 *   did not return from [preparePlayback] (instance-ownership check).
 * - [MediaSort] is applied deterministically over domain fields (nulls last
 *   per direction) so ordering assertions cannot pass by accident.
 *
 * Browsing slices [itemsSource]/[collectionsSource] via [OffsetPageCursorCodec],
 * exercising the cursor round-trip end to end. Gated methods call the shared
 * [requireCapability] gate before any state changes — unsupported
 * capabilities throw [ClientConfigError] and never fake success.
 *
 * Configurable outcomes:
 * - [failure] makes every call throw the given [DomainError] as-is.
 * - [cancellation] makes every call throw the given [CancellationException]
 *   untouched (never an "empty page success").
 * - [searchResults]/[playbackOutcome] preset the respective results.
 */
class FakeMediaProviderAdapter(
    override val provider: ProviderKind = ProviderKind.Jellyfin,
    override val serverId: ServerId = ServerId("fake-server"),
    override val accountId: AccountId = AccountId("fake-account"),
    override val capabilities: AdapterCapabilities = AdapterCapabilities(),
    collectionsSource: List<MediaCollection> = emptyList(),
    itemsSource: List<MediaItem> = emptyList(),
    var failure: DomainError? = null,
    var cancellation: CancellationException? = null,
    var searchResults: Page<MediaItem>? = null,
    var playbackOutcome: PlaybackPreparation = PlaybackPreparation.Refused(RefusalReason.NoPlayableSource),
) : MediaProviderAdapter {

    private val collectionsSource: List<MediaCollection> = collectionsSource
    private val itemsSource: List<MediaItem> = itemsSource

    /** Streams this instance handed out via [preparePlayback]; reportPlayback accepts only these. */
    private val preparedStreams: MutableList<PlaybackPreparation.Ready> = mutableListOf()

    /** Call log, one entry per contract method, for ordering assertions. */
    val calls: MutableList<String> = mutableListOf()
    val searchTerms: MutableList<String> = mutableListOf()
    val favoriteToggles: MutableList<Pair<MediaId, Boolean>> = mutableListOf()
    val watchedToggles: MutableList<Pair<MediaId, Boolean>> = mutableListOf()
    val reportedEvents: MutableList<Pair<PlaybackPreparation.Ready, PlaybackEvent>> = mutableListOf()
    var lastPlaybackOptions: PlaybackOptions? = null
        private set
    val deletedItems: MutableList<Collection<MediaId>> = mutableListOf()
    var similarResults: List<MediaItem>? = null
    var resumeResults: Page<MediaItem>? = null

    private fun throwIfConfigured() {
        cancellation?.let { throw it }
        failure?.let { throw it }
    }

    override suspend fun collections(request: PageRequest): Page<MediaCollection> {
        calls += "collections"
        // Single-chunk surface: a non-null cursor is a caller bug, never a
        // license to re-serve page one.
        request.cursor?.let { rejectCursorNotHonored(it, "collections") }
        throwIfConfigured()
        return Page.of(collectionsSource, nextCursor = null, totalEstimate = collectionsSource.size)
    }

    override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> {
        calls += "items"
        throwIfConfigured()
        val filtered = applyFilter(
            itemsSource.filter { collectionId == null || it.collectionId == collectionId },
            request.filter,
        )
        val sorted = applySort(filtered, request.sort)
        return slice(sorted, request)
    }

    override suspend fun search(term: String, request: PageRequest): Page<MediaItem> {
        calls += "search"
        requireCapability(capabilities, AdapterCapability.Search)
        throwIfConfigured()
        searchTerms += term
        return searchResults ?: Page.empty()
    }

    override suspend fun item(id: MediaId): MediaItem {
        calls += "item"
        throwIfConfigured()
        return itemsSource.firstOrNull { it.id == id }
            ?: throw ResourceNotFoundError(resource = "item $id")
    }

    override suspend fun seasons(seriesId: MediaId): Page<MediaItem> {
        calls += "seasons"
        throwIfConfigured()
        val seasons = itemsSource.filter { it.mediaType == MediaType.Season && it.seriesId == seriesId }
        return Page.of(seasons, nextCursor = null, totalEstimate = seasons.size)
    }

    override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> {
        calls += "episodes"
        throwIfConfigured()
        val episodes = itemsSource.filter {
            it.mediaType == MediaType.Episode && it.seriesId == seriesId &&
                (seasonId == null || it.seasonId == seasonId)
        }
        return slice(episodes, request)
    }

    override suspend fun setFavorite(id: MediaId, favorite: Boolean) {
        calls += "setFavorite"
        requireCapability(capabilities, AdapterCapability.FavoritesEdit)
        throwIfConfigured()
        favoriteToggles += id to favorite
    }

    override suspend fun setWatched(id: MediaId, watched: Boolean) {
        calls += "setWatched"
        requireCapability(capabilities, AdapterCapability.WatchedEdit)
        throwIfConfigured()
        watchedToggles += id to watched
    }

    override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation {
        calls += "preparePlayback"
        throwIfConfigured()
        lastPlaybackOptions = options
        (playbackOutcome as? PlaybackPreparation.Ready)?.let(preparedStreams::add)
        return playbackOutcome
    }

    override suspend fun similar(id: MediaId): List<MediaItem> {
        calls += "similar"
        throwIfConfigured()
        return similarResults ?: itemsSource.filter { it.id != id }.take(SimilarLimit)
    }

    override suspend fun resumeItems(request: PageRequest): Page<MediaItem> {
        calls += "resumeItems"
        throwIfConfigured()
        return resumeResults ?: Page.empty()
    }

    override suspend fun deleteItems(ids: Collection<MediaId>) {
        calls += "deleteItems"
        requireCapability(capabilities, AdapterCapability.MediaDelete)
        throwIfConfigured()
        deletedItems += ids
    }

    override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) {
        calls += "reportPlayback"
        requireCapability(capabilities, AdapterCapability.PlaybackReporting)
        if (preparedStreams.none { it === stream }) {
            throw ClientConfigError(
                "reportPlayback rejected a stream not prepared by this adapter instance",
            )
        }
        throwIfConfigured()
        reportedEvents += stream to event
    }

    // ------------------------------------------------------------------ internals

    /**
     * Reference filter semantics over [MediaFilter]: user-state flags map to
     * item user data, capability-gated before touching state.
     */
    private fun applyFilter(source: List<MediaItem>, filter: MediaFilter): List<MediaItem> {
        if (filter.isEmpty) return source
        val favoritesOnly = filter.favoritesOnly
        val watchedOnly = filter.playedOnly || filter.unplayedOnly
        val otherConstraints = filter.mediaTypes.isNotEmpty() || filter.genres.isNotEmpty() ||
            filter.years.isNotEmpty() || !filter.searchTerm.isNullOrBlank()

        if (favoritesOnly && !capabilities.favoritesRead && !capabilities.favoritesFilter) {
            throw ClientConfigError("Adapter cannot answer a favorites-only listing (favoritesRead/favoritesFilter unsupported)")
        }
        if (watchedOnly && !capabilities.watchedFilter) {
            throw ClientConfigError("Adapter cannot answer played/unplayed-filtered listings (watchedFilter unsupported)")
        }
        // Watched flags count as constraints too: a favorites listing that
        // cannot combine tags cannot carry played/unplayed either.
        if (favoritesOnly && (otherConstraints || watchedOnly) && !capabilities.favoritesFilter) {
            throw ClientConfigError("Adapter cannot combine favorites-only with further filters (favoritesFilter unsupported)")
        }
        if (!filter.searchTerm.isNullOrBlank()) {
            throw ClientConfigError("items() does not free-text search; use search()")
        }
        return source.filter { item ->
            (!favoritesOnly || item.userData.isFavorite) &&
                (!filter.playedOnly || item.userData.isPlayed) &&
                (!filter.unplayedOnly || !item.userData.isPlayed) &&
                (filter.mediaTypes.isEmpty() || item.mediaType in filter.mediaTypes) &&
                (filter.genres.isEmpty() || item.genres.any { it in filter.genres }) &&
                (filter.years.isEmpty() || item.year in filter.years)
        }
    }

    /** Reference ordering: direction flips only the non-null comparison; nulls stay last, id is the ascending tiebreak. */
    private fun applySort(source: List<MediaItem>, sort: MediaSort?): List<MediaItem> {
        if (sort == null) return source
        val descending = sort.direction == SortDirection.Descending
        val comparator = when (sort.field) {
            MediaSortField.Title -> nullableCompare(descending, { it.title }, String::compareTo)
            MediaSortField.DateAdded -> nullableCompare(descending, { it.dateAddedEpochMs }, Long::compareTo)
            MediaSortField.DatePlayed -> nullableCompare(descending, { it.userData.lastPlayedEpochMs }, Long::compareTo)
            MediaSortField.PremiereDate -> nullableCompare(descending, { it.premiereEpochMs }, Long::compareTo)
            MediaSortField.CommunityRating -> nullableCompare(descending, { it.communityRating }, Double::compareTo)
            MediaSortField.Runtime -> nullableCompare(descending, { it.runtimeMs }, Long::compareTo)
            MediaSortField.SeasonNumber -> nullableCompare(descending, { it.seasonNumber }, Int::compareTo)
            MediaSortField.EpisodeNumber -> nullableCompare(descending, { it.episodeNumber }, Int::compareTo)
        }.thenBy { it.id.value }
        return source.sortedWith(comparator)
    }

    private fun <K : Any> nullableCompare(
        descending: Boolean,
        selector: (MediaItem) -> K?,
        compare: (K, K) -> Int,
    ): Comparator<MediaItem> = Comparator { a, b ->
        val av = selector(a)
        val bv = selector(b)
        when {
            av == null && bv == null -> 0
            av == null -> 1 // nulls last in both directions
            bv == null -> -1
            else -> {
                val order = compare(av, bv)
                if (descending) -order else order
            }
        }
    }

    /** Offset slicing identical to what real adapters build on [OffsetPageCursorCodec]. */
    private fun slice(source: List<MediaItem>, request: PageRequest): Page<MediaItem> {
        val offset = request.cursor?.let(OffsetPageCursorCodec::decode) ?: 0
        val slice = source.drop(offset).take(request.pageSize)
        val nextOffset = offset + slice.size
        val nextCursor = if (slice.isNotEmpty() && nextOffset < source.size) {
            OffsetPageCursorCodec.encode(nextOffset)
        } else {
            null
        }
        return Page.of(slice, nextCursor = nextCursor, totalEstimate = source.size)
    }

    private fun rejectCursorNotHonored(cursor: PageCursor, method: String): Nothing =
        throw ClientConfigError("$method cannot honor cursor '$cursor' (single-chunk listing)")
}

private const val SimilarLimit = 6
