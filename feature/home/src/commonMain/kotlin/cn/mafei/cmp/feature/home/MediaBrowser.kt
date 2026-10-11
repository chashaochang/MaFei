package cn.mafei.cmp.feature.home

import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.HomeFeed
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.repositories.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaBrowserState(
    val collections: List<MediaCollection> = emptyList(),
    val selectedCollection: CollectionId? = null,
    val items: List<MediaItem> = emptyList(),
    val homeFeed: HomeFeed = HomeFeed(),
    val homeLoading: Boolean = false,
    val homeLatestByCollection: Map<CollectionId, List<MediaItem>> = emptyMap(),
    val homeLatestLoading: Set<CollectionId> = emptySet(),
    val homeLatestErrors: Map<CollectionId, String> = emptyMap(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val stale: Boolean = false,
    val homeError: String? = null,
    val homeStale: Boolean = false,
    val nextCursor: PageCursor? = null,
    val totalEstimate: Int? = null,
    val librarySort: MediaSort = MediaSort(MediaSortField.DateAdded, SortDirection.Descending),
    val favoriteItems: List<MediaItem> = emptyList(),
    val favoriteLoading: Boolean = false,
    val favoriteRefreshing: Boolean = false,
    val favoriteLoadingMore: Boolean = false,
    val favoriteError: String? = null,
    val favoriteNextCursor: PageCursor? = null,
    val favoriteTotalEstimate: Int? = null,
    val favoriteSort: MediaSort = MediaSort(MediaSortField.DateAdded, SortDirection.Descending),
    val searchQuery: String = "",
    val searchItems: List<MediaItem> = emptyList(),
    val searchLoading: Boolean = false,
    val searchLoadingMore: Boolean = false,
    val searchError: String? = null,
    val searchNextCursor: PageCursor? = null,
    val searchTotalEstimate: Int? = null,
    val detailId: MediaId? = null,
    val detail: MediaItem? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val detailSeasons: List<MediaItem> = emptyList(),
    val selectedSeasonId: MediaId? = null,
    val selectedSeasonDetail: MediaItem? = null,
    val detailEpisodes: List<MediaItem> = emptyList(),
    /** Legacy series-detail play button target (provider target resolved to a real episode). */
    val detailPlayTarget: MediaItem? = null,
    /** Legacy season-detail play button target for the currently selected season. */
    val selectedSeasonPlayTarget: MediaItem? = null,
    val detailSectionsLoading: Boolean = false,
    val detailSectionsError: String? = null,
    val detailActionBusy: Boolean = false,
    val detailActionError: String? = null,
    /** 详情页上用户指定的播放版本/轨道（仅对当前 detail 生效）。 */
    val selectedMediaSourceId: String? = null,
    val selectedAudioStreamIndex: Int? = null,
    val selectedSubtitleStreamIndex: Int? = null,
    val personId: MediaId? = null,
    val person: MediaPerson? = null,
    val personLoading: Boolean = false,
    val personError: String? = null,
    val personMovies: List<MediaItem> = emptyList(),
    val personSeries: List<MediaItem> = emptyList(),
    val personEpisodes: List<MediaItem> = emptyList(),
    val personSectionsLoading: Boolean = false,
    val personFavoriteBusy: Boolean = false,
    /** 独立播放历史：resume（进行中）+ 已看完两路分页合并，剧集按剧去重。 */
    val historyItems: List<PlaybackHistoryItem> = emptyList(),
    val historyLoading: Boolean = false,
    val historyRefreshing: Boolean = false,
    val historyLoadingMore: Boolean = false,
    val historyLoadMoreFailed: Boolean = false,
    val historyCanLoadMore: Boolean = false,
    val historyError: String? = null,
    val historyResumeCursor: PageCursor? = null,
    val historyCompletedCursor: PageCursor? = null,
    /** null = 未探测；探测由 [refreshDeleteAvailability] 触发。 */
    val deleteAvailable: Boolean? = null,
    /** null = 尚未完成 Provider 用户角色探测。 */
    val administrator: Boolean? = null,
    val deleteBusy: Boolean = false,
    val deleteError: String? = null,
)

/** Durable playback selections keyed exactly like legacy: provider account + media item. */
interface MediaPlaybackPreferences {
    suspend fun preferredMediaSourceId(accountId: AccountId, mediaId: MediaId): String?
    suspend fun setPreferredMediaSourceId(accountId: AccountId, mediaId: MediaId, sourceId: String)
}

/**
 * The season document that was visible before an episode detail route took
 * over the provider detail slot. Returning to that route should reveal this
 * document immediately; a background refresh can update it later without
 * replacing the whole season page with a loading state.
 */
private data class SeasonRestoreSnapshot(
    val series: MediaItem,
    val seasons: List<MediaItem>,
    val seasonId: MediaId,
    val season: MediaItem?,
    val episodes: List<MediaItem>,
    val seriesPlayTarget: MediaItem?,
    val seasonPlayTarget: MediaItem?,
)

/** One screen and one account. Calls/state mutations run on the UI dispatcher.
 * Network suspends outside state updates; request tickets reject obsolete completions.
 * Repository still owns account isolation and cursor validation; no SDK leaks to UI.
 */
class MediaBrowser(
    private val repository: MediaRepository,
    private val session: MediaSession,
    val capabilities: AdapterCapabilities = AdapterCapabilities(),
    private val playbackPreferences: MediaPlaybackPreferences? = null,
) {
    private val mutableState = MutableStateFlow(MediaBrowserState(loading = true, homeLoading = true))
    val state: StateFlow<MediaBrowserState> = mutableState.asStateFlow()
    private var ticket = 0L
    private var homeTicket = 0L
    private var detailTicket = 0L
    private var detailRefreshTicket = 0L
    private var seasonRestoreSnapshot: SeasonRestoreSnapshot? = null
    private var personTicket = 0L
    private var searchTicket = 0L
    private var favoriteTicket = 0L
    private var historyTicket = 0L
    private var homeLatestSequence = 0L
    private val homeLatestTickets = mutableMapOf<CollectionId, Long>()
    private var initialized = false
    private var pages = MediaPageAccumulator()
    private var searchPages = MediaPageAccumulator()
    private var favoritePages = MediaPageAccumulator()
    private var historyResumePages = MediaPageAccumulator()
    private var historyCompletedPages = MediaPageAccumulator()
    private var historyResumeRetryInitial = false
    private var historyCompletedRetryInitial = false
    private var historyPageSize = HistoryPageSize
    private val historySeriesCache = mutableMapOf<MediaId, HistorySeriesCacheEntry>()
    private enum class UserDataAction { Watched, Favorite }
    private data class UserDataActionKey(val id: MediaId, val action: UserDataAction)
    private val userDataActionLocks = mutableSetOf<UserDataActionKey>()

    suspend fun start() {
        if (initialized) return
        refresh()
    }

    suspend fun refresh() {
        val operation = ++ticket
        val homeOperation = ++homeTicket
        mutableState.value = state.value.copy(
            loading = !initialized,
            refreshing = initialized,
            loadingMore = false,
            error = null,
            homeError = null,
            homeLoading = true,
        )
        try {
        // The home feed, the library list and the first library page are
        // independent requests. Run them as concurrent streams and publish
        // each result the moment it lands, so the hero, shelves and latest
        // grid render progressively. The old serial chain
        // (collections → home → browse) made the whole page wait for the
        // sum of all three roundtrips before anything settled.
        var cancellation: CancellationException? = null
        supervisorScope {
            launch {
                try {
                    // Cache-first cold start (the legacy client's strategy): paint
                    // the previous session's home immediately so the page never
                    // waits ~6s of provider latency. homeStale stays false — the
                    // network refresh below is still in flight and will replace
                    // this content in place; only publish when nothing better
                    // (a previous publish or pull-to-refresh result) is showing.
                    val snapshot = repository.homeSnapshot(session)
                    if (homeOperation != homeTicket) return@launch
                    if (snapshot != null && state.value.homeFeed == HomeFeed()) {
                        mutableState.value = state.value.copy(homeFeed = snapshot, homeError = null, homeStale = false)
                    }
                    val home = repository.home(session, refresh = true)
                    if (homeOperation != homeTicket) return@launch
                    publishHome(home)
                } catch (cancelled: CancellationException) {
                    // supervisorScope swallows child cancellation; record it so
                    // refresh still propagates the cancellation contract.
                    cancellation = cancelled
                    throw cancelled
                } catch (failure: Exception) {
                    if (homeOperation != homeTicket) return@launch
                    mutableState.value = state.value.copy(homeError = browseError(failure), homeStale = false)
                } finally {
                    if (homeOperation == homeTicket) {
                        mutableState.value = state.value.copy(homeLoading = false)
                    }
                }
            }
            launch {
                try {
                    val libraries = repository.collections(session, refresh = true)
                    if (operation != ticket) return@launch
                    val selected = state.value.selectedCollection?.takeIf { id -> libraries.value.items.any { it.id == id } }
                        ?: libraries.value.items.firstOrNull()?.id
                    if (selected != state.value.selectedCollection) {
                        pages = MediaPageAccumulator()
                        mutableState.value = state.value.copy(items = emptyList(), nextCursor = null, totalEstimate = null)
                    }
                    mutableState.value = state.value.copy(collections = libraries.value.items, selectedCollection = selected,
                        stale = libraries.origin == MediaLoadOrigin.StaleCache,
                        homeLatestByCollection = state.value.homeLatestByCollection.filterKeys { id ->
                            libraries.value.items.any { it.id == id }
                        },
                        homeLatestLoading = state.value.homeLatestLoading.filterTo(linkedSetOf()) { id ->
                            libraries.value.items.any { it.id == id }
                        },
                        homeLatestErrors = state.value.homeLatestErrors.filterKeys { id ->
                            libraries.value.items.any { it.id == id }
                        },
                    )
                    initialized = true
                    // An empty library response is a real empty state, not permission to list the entire server.
                    if (selected == null) {
                        pages = MediaPageAccumulator()
                        mutableState.value = state.value.copy(
                            items = emptyList(),
                            nextCursor = null,
                            totalEstimate = 0,
                            loading = false,
                            refreshing = false,
                        )
                    } else loadFirst(selected, operation, libraries.origin == MediaLoadOrigin.StaleCache)
                } catch (cancelled: CancellationException) {
                    if (operation == ticket) {
                        mutableState.value = state.value.copy(loading = false, refreshing = false)
                    }
                    cancellation = cancelled
                    throw cancelled
                } catch (failure: Exception) {
                    if (operation == ticket) {
                        mutableState.value = state.value.copy(
                            loading = false,
                            refreshing = false,
                            error = browseError(failure),
                        )
                    }
                }
            }
        }
        cancellation?.let { throw it }
        } catch (cancelled: CancellationException) {
            if (operation == ticket) {
                mutableState.value = state.value.copy(loading = false, refreshing = false)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (operation == ticket) {
                mutableState.value = state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = browseError(failure),
                )
            }
        }
    }

    /**
     * Refreshes only the provider-backed home feed while keeping the currently
     * presented home document intact.
     *
     * Returning from a detail, season, episode or person route is not a manual
     * pull-to-refresh. The old content therefore remains visible while this
     * request runs: no global busy flag changes, no library request is issued,
     * and no pagination or selection state is reset. On success, unchanged
     * shelves retain their list instances so Compose only has to update the
     * sections whose provider data actually changed.
     */
    suspend fun refreshHome() {
        val operation = ++homeTicket
        try {
            val home = repository.home(session, refresh = true)
            if (operation != homeTicket) return
            if (home.origin == MediaLoadOrigin.StaleCache && state.value.homeFeed != HomeFeed()) {
                // The repository has already fallen back to the content that
                // is on screen. Publishing `homeStale=true` here would insert
                // a status row and move the viewport even though no shelf data
                // changed. A later successful return refresh clears any prior
                // home error/stale state through [publishHome].
                return
            }
            publishHome(home)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (operation != homeTicket) return
            // A background refresh must not insert an error row above existing
            // content and shift the user's established viewport. With no home
            // content at all, exposing the failure remains the honest fallback.
            if (state.value.homeFeed == HomeFeed()) {
                mutableState.value = state.value.copy(
                    homeError = browseError(failure),
                    homeStale = false,
                )
            }
        } finally {
            if (operation == homeTicket) {
                mutableState.value = state.value.copy(homeLoading = false)
            }
        }
    }

    private fun publishHome(home: MediaLoad<HomeFeed>) {
        val before = state.value
        mutableState.value = before.copy(
            homeFeed = before.homeFeed.mergeUnchangedSections(home.value),
            homeError = null,
            homeStale = home.origin == MediaLoadOrigin.StaleCache,
        )
    }

    suspend fun select(id: CollectionId) {
        val collection = state.value.collections.firstOrNull { it.id == id } ?: return
        val operation = ++ticket
        pages = MediaPageAccumulator()
        val dedicatedCatalog = (capabilities.music && collection.mediaTypes.any { it in setOf(MediaType.Audio, MediaType.MusicAlbum, MediaType.MusicArtist) }) ||
            (capabilities.photos && MediaType.Photo in collection.mediaTypes) || (capabilities.liveTv && MediaType.LiveChannel in collection.mediaTypes)
        mutableState.value = state.value.copy(selectedCollection = id, items = emptyList(), nextCursor = null,
            totalEstimate = null,
            loading = !dedicatedCatalog, error = null, stale = false)
        if (dedicatedCatalog) return
        try { loadFirst(id, operation, false) }
        catch (cancelled: CancellationException) {
            if (operation == ticket) mutableState.value = state.value.copy(loading = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == ticket) mutableState.value = state.value.copy(loading = false, error = browseError(failure))
        }
    }

    /**
     * Loads one home-tab feed without changing the active media-library page.
     *
     * The legacy HarmonyOS home kept a latest-items map per library. Reusing
     * [select] here clears [MediaBrowserState.items], which also destroys the
     * lazy layout's visible anchor and makes a Floating Tab tap jump back to
     * the hero. Keep this request independent and cache it by collection.
     */
    suspend fun loadHomeLatest(id: CollectionId) {
        val collection = state.value.collections.firstOrNull { it.id == id } ?: return
        if (state.value.homeLatestByCollection.containsKey(id) || id in state.value.homeLatestLoading) return
        check(homeLatestSequence < Long.MAX_VALUE) { "Home latest request sequence exhausted" }
        val operation = ++homeLatestSequence
        homeLatestTickets[id] = operation
        mutableState.value = state.value.copy(
            homeLatestLoading = state.value.homeLatestLoading + id,
            homeLatestErrors = state.value.homeLatestErrors - id,
        )
        val request = collection.homeLatestRequest(PageRequest(pageSize = HomeLatestPageSize))
        try {
            val loaded = repository.browse(
                session = session,
                query = MediaQuery.Items(id),
                request = request,
                refresh = true,
            )
            if (homeLatestTickets[id] != operation) return
            mutableState.value = state.value.copy(
                homeLatestByCollection = state.value.homeLatestByCollection +
                    (id to loaded.value.items.distinctBy { it.id }),
                homeLatestLoading = state.value.homeLatestLoading - id,
                homeLatestErrors = state.value.homeLatestErrors - id,
            )
        } catch (cancelled: CancellationException) {
            if (homeLatestTickets[id] == operation) {
                mutableState.value = state.value.copy(homeLatestLoading = state.value.homeLatestLoading - id)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (homeLatestTickets[id] == operation) {
                mutableState.value = state.value.copy(
                    homeLatestLoading = state.value.homeLatestLoading - id,
                    homeLatestErrors = state.value.homeLatestErrors + (id to browseError(failure)),
                )
            }
        } finally {
            if (homeLatestTickets[id] == operation) homeLatestTickets.remove(id)
        }
    }

    private suspend fun loadFirst(id: CollectionId, operation: Long, staleLibraries: Boolean) {
        val loaded = repository.browse(session, MediaQuery.Items(id), libraryRequest(id), refresh = true)
        if (operation != ticket) return
        val freshPages = MediaPageAccumulator()
        val page = freshPages.append(null, loaded.value)
        pages = freshPages
        mutableState.value = state.value.copy(
            items = page.items,
            nextCursor = page.nextCursor,
            totalEstimate = page.totalEstimate,
            loading = false,
            refreshing = false,
            error = null,
            stale = staleLibraries || loaded.origin == MediaLoadOrigin.StaleCache,
        )
    }

    suspend fun loadMore() {
        val before = state.value
        val cursor = before.nextCursor ?: return
        val id = before.selectedCollection ?: return
        if (before.loading || before.refreshing || before.loadingMore) return
        val operation = ++ticket
        mutableState.value = before.copy(loadingMore = true, error = null)
        try {
            val loaded = repository.browse(session, MediaQuery.Items(id), libraryRequest(id, cursor))
            if (operation != ticket) return
            val page = pages.append(cursor, loaded.value)
            mutableState.value = state.value.copy(
                items = page.items,
                nextCursor = page.nextCursor,
                totalEstimate = page.totalEstimate,
                loadingMore = false,
                stale = before.stale || loaded.origin == MediaLoadOrigin.StaleCache,
            )
        } catch (cancelled: CancellationException) {
            if (operation == ticket) mutableState.value = state.value.copy(loadingMore = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == ticket) {
                mutableState.value = state.value.copy(loadingMore = false, error = browseError(failure))
            }
        }
    }

    /**
     * Legacy SearchViewModel semantics: one screen load, at most 800 visible
     * Movie/Series/Episode/Video results, with no UI-driven pagination.
     *
     * PageRequest is intentionally capped at 500 by the shared contract, so
     * Jellyfin's old `limit = 800` request is reproduced as two internal
     * segments. Feiniu remains a single endpoint call because its adapter
     * returns the provider's unpaged search result in one chunk.
     *
     * @return true only when the current request completed successfully. The
     * caller uses this to preserve the old "submit saves history after success"
     * behavior; incremental text changes ignore the return value.
     */
    suspend fun search(term: String): Boolean {
        val normalized = term.trim()
        val operation = ++searchTicket
        searchPages = MediaPageAccumulator()
        if (normalized.isEmpty()) {
            mutableState.value = state.value.copy(
                searchQuery = "",
                searchItems = emptyList(),
                searchLoading = false,
                searchLoadingMore = false,
                searchError = null,
                searchNextCursor = null,
                searchTotalEstimate = null,
            )
            return false
        }
        mutableState.value = state.value.copy(
            searchQuery = normalized,
            searchItems = emptyList(),
            searchLoading = true,
            searchLoadingMore = false,
            searchError = null,
            searchNextCursor = null,
            searchTotalEstimate = null,
        )
        try {
            val request = PageRequest(
                pageSize = SearchFirstSegmentSize,
                filter = MediaFilter(mediaTypes = SearchMediaTypes),
            )
            val first = repository.browse(
                session,
                MediaQuery.Search(normalized),
                request,
                refresh = true,
            )
            if (operation != searchTicket) return false
            var page = searchPages.append(null, first.value)
            val secondCursor = page.nextCursor
            val remaining = LegacySearchLimit - page.items.size
            if (secondCursor != null && remaining > 0) {
                val second = repository.browse(
                    session,
                    MediaQuery.Search(normalized),
                    PageRequest(
                        cursor = secondCursor,
                        pageSize = minOf(remaining, PageRequest.MAX_PAGE_SIZE),
                        filter = MediaFilter(mediaTypes = SearchMediaTypes),
                    ),
                    refresh = true,
                )
                if (operation != searchTicket) return false
                page = searchPages.append(secondCursor, second.value)
            }
            val items = page.items.take(LegacySearchLimit)
            mutableState.value = state.value.copy(
                searchItems = items,
                searchLoading = false,
                searchLoadingMore = false,
                searchError = null,
                // The old page displayed the number of mapped items, not the
                // server's possibly larger TotalRecordCount.
                searchNextCursor = null,
                searchTotalEstimate = items.size,
            )
            return true
        } catch (cancelled: CancellationException) {
            if (operation == searchTicket) mutableState.value = state.value.copy(searchLoading = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == searchTicket) {
                mutableState.value = state.value.copy(searchLoading = false, searchError = browseError(failure))
            }
            return false
        }
    }

    fun clearSearch() {
        ++searchTicket
        searchPages = MediaPageAccumulator()
        mutableState.value = state.value.copy(
            searchQuery = "",
            searchItems = emptyList(),
            searchLoading = false,
            searchLoadingMore = false,
            searchError = null,
            searchNextCursor = null,
            searchTotalEstimate = null,
        )
    }

    suspend fun setLibrarySort(sort: MediaSort) {
        if (state.value.librarySort == sort) return
        val id = state.value.selectedCollection ?: return
        mutableState.value = state.value.copy(librarySort = sort)
        select(id)
    }

    suspend fun refreshFavorites() {
        val operation = ++favoriteTicket
        if (!capabilities.favoritesRead) {
            favoritePages = MediaPageAccumulator()
            mutableState.value = state.value.copy(
                favoriteItems = emptyList(),
                favoriteLoading = false,
                favoriteRefreshing = false,
                favoriteLoadingMore = false,
                favoriteError = null,
                favoriteNextCursor = null,
                favoriteTotalEstimate = 0,
            )
            return
        }
        val alreadyLoaded = state.value.favoriteItems.isNotEmpty() || state.value.favoriteTotalEstimate != null
        mutableState.value = state.value.copy(
            favoriteLoading = !alreadyLoaded,
            favoriteRefreshing = alreadyLoaded,
            favoriteLoadingMore = false,
            favoriteError = null,
        )
        try {
            val loaded = repository.browse(
                session = session,
                query = MediaQuery.Items(null),
                request = favoriteRequest(),
                refresh = true,
            )
            if (operation != favoriteTicket) return
            val freshPages = MediaPageAccumulator()
            val page = freshPages.append(null, loaded.value)
            favoritePages = freshPages
            mutableState.value = state.value.copy(
                favoriteItems = page.items,
                favoriteLoading = false,
                favoriteRefreshing = false,
                favoriteLoadingMore = false,
                favoriteError = null,
                favoriteNextCursor = page.nextCursor,
                favoriteTotalEstimate = page.totalEstimate,
            )
        } catch (cancelled: CancellationException) {
            if (operation == favoriteTicket) {
                mutableState.value = state.value.copy(favoriteLoading = false, favoriteRefreshing = false)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (operation == favoriteTicket) {
                mutableState.value = state.value.copy(
                    favoriteLoading = false,
                    favoriteRefreshing = false,
                    favoriteError = browseError(failure),
                )
            }
        }
    }

    suspend fun loadMoreFavorites() {
        val before = state.value
        val cursor = before.favoriteNextCursor ?: return
        if (before.favoriteLoading || before.favoriteRefreshing || before.favoriteLoadingMore) return
        val operation = ++favoriteTicket
        mutableState.value = before.copy(favoriteLoadingMore = true, favoriteError = null)
        try {
            val loaded = repository.browse(
                session = session,
                query = MediaQuery.Items(null),
                request = favoriteRequest(cursor),
            )
            if (operation != favoriteTicket) return
            val page = favoritePages.append(cursor, loaded.value)
            mutableState.value = state.value.copy(
                favoriteItems = page.items,
                favoriteLoadingMore = false,
                favoriteNextCursor = page.nextCursor,
                favoriteTotalEstimate = page.totalEstimate,
            )
        } catch (cancelled: CancellationException) {
            if (operation == favoriteTicket) mutableState.value = state.value.copy(favoriteLoadingMore = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == favoriteTicket) {
                mutableState.value = state.value.copy(favoriteLoadingMore = false, favoriteError = browseError(failure))
            }
        }
    }

    suspend fun setFavoriteSort(sort: MediaSort) {
        if (state.value.favoriteSort == sort) return
        mutableState.value = state.value.copy(favoriteSort = sort)
        refreshFavorites()
    }

    private fun libraryRequest(id: CollectionId, cursor: PageCursor? = null): PageRequest = PageRequest(
        cursor = cursor,
        pageSize = CatalogPageSize,
        sort = state.value.librarySort,
        filter = MediaFilter(mediaTypes = state.value.collections.firstOrNull { it.id == id }?.mediaTypes.orEmpty()),
    )

    private fun favoriteRequest(cursor: PageCursor? = null): PageRequest = PageRequest(
        cursor = cursor,
        pageSize = CatalogPageSize,
        sort = state.value.favoriteSort,
        filter = MediaFilter(
            // Fixed page semantics from FavoriteListViewModel.queryTypes. This
            // is not an arbitrary user-composed filter, so Feiniu can forward
            // it even though it still advertises favoritesFilter=false.
            mediaTypes = FavoriteMediaTypes,
            favoritesOnly = true,
        ),
    )

    suspend fun openDetail(id: MediaId, initialItem: MediaItem? = null) {
        val previous = state.value
        val previousDetail = previous.detail
        val previousSeasonId = previous.selectedSeasonId
        if (
            previousDetail?.mediaType == MediaType.Series &&
            previous.detailEpisodes.any { it.id == id } &&
            previousSeasonId != null
        ) {
            seasonRestoreSnapshot = SeasonRestoreSnapshot(
                series = previousDetail,
                seasons = previous.detailSeasons,
                seasonId = previousSeasonId,
                season = previous.selectedSeasonDetail,
                episodes = previous.detailEpisodes,
                seriesPlayTarget = previous.detailPlayTarget,
                seasonPlayTarget = previous.selectedSeasonPlayTarget,
            )
        }
        val operation = ++detailTicket
        ++detailRefreshTicket
        mutableState.value = state.value.copy(
            detailId = id,
            // Keep the tapped card as the loading document. Besides avoiding a
            // blank hero while the request is in flight, this gives the shared
            // artwork transition a destination element during the route change.
            detail = initialItem?.takeIf { it.id == id },
            detailLoading = true,
            detailError = null,
            detailSeasons = emptyList(),
            selectedSeasonId = null,
            selectedSeasonDetail = null,
            detailEpisodes = emptyList(),
            detailPlayTarget = null,
            selectedSeasonPlayTarget = null,
            detailSectionsLoading = false,
            detailSectionsError = null,
            detailActionBusy = false,
            detailActionError = null,
            selectedMediaSourceId = null,
            selectedAudioStreamIndex = null,
            selectedSubtitleStreamIndex = null,
        )
        try {
            val loaded = repository.item(session, id, refresh = true)
            if (operation != detailTicket) return
            val playableSources = loaded.value.mediaSources.filter { it.playable != false }
            // 旧版按「账号 + 媒体条目」恢复版本；找不到时明确选择第一条可播源，
            // 起播时也会把这个 source id 交给 Provider，而不是交回服务器猜测。
            var preferredSourceId: String? = null
            if (playableSources.size > 1) {
                val remembered = try {
                    playbackPreferences?.preferredMediaSourceId(session.accountId, id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                preferredSourceId = remembered?.takeIf { candidate ->
                    playableSources.any { it.id == candidate }
                }
            }
            val selectedSource = playableSources.firstOrNull { it.id == preferredSourceId }
                ?: playableSources.firstOrNull()
            mutableState.value = state.value.copy(detail = loaded.value, detailLoading = false,
                detailError = if (loaded.origin == MediaLoadOrigin.StaleCache) "连接暂不可用，显示缓存详情" else null,
                selectedMediaSourceId = selectedSource?.id,
                selectedAudioStreamIndex = selectedSource?.legacyDefaultAudioStreamIndex(),
                selectedSubtitleStreamIndex = selectedSource?.legacyDefaultSubtitleStreamIndex(),
                detailPlayTarget = loaded.value.takeIf { it.mediaType != MediaType.Series && it.canPlay },
            )
            if (loaded.value.mediaType == MediaType.Series) {
                loadSeriesSections(id, loaded.value, operation)
            }
        } catch (cancelled: CancellationException) {
            if (operation == detailTicket) mutableState.value = state.value.copy(detailLoading = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == detailTicket) mutableState.value = state.value.copy(detailLoading = false, detailError = browseError(failure))
        }
    }

    /**
     * Refreshes the detail currently on screen without replacing its document
     * state. This is used when a child route returns to a movie/series/episode:
     * the old detail, scroll position, sections and actions remain available
     * while the provider catches up in the background.
     */
    suspend fun refreshDetail(id: MediaId) {
        val beforeRequest = state.value
        if (beforeRequest.detail?.id != id || beforeRequest.detailLoading) return
        val operation = ++detailRefreshTicket
        val detailGeneration = detailTicket
        try {
            val loaded = repository.item(session, id, refresh = true)
            if (
                operation != detailRefreshTicket ||
                detailGeneration != detailTicket ||
                state.value.detail?.id != id
            ) return
            // A transient network failure may be represented by the repository
            // as a stale-cache success. The same detail is already visible, so
            // publishing an error row would only move the page for no new data.
            if (loaded.origin == MediaLoadOrigin.StaleCache) return

            val current = state.value
            val detail = loaded.value
            val playableSources = detail.mediaSources.filter { it.playable != false }
            val selectedSource = playableSources.firstOrNull { it.id == current.selectedMediaSourceId }
                ?: playableSources.firstOrNull()
            val sourceUnchanged = selectedSource?.id == current.selectedMediaSourceId
            mutableState.value = current.copy(
                detail = detail,
                detailError = null,
                selectedMediaSourceId = selectedSource?.id,
                // A background data refresh must not silently undo a track the
                // user selected on this same source.
                selectedAudioStreamIndex = if (sourceUnchanged) {
                    current.selectedAudioStreamIndex
                } else {
                    selectedSource?.legacyDefaultAudioStreamIndex()
                },
                selectedSubtitleStreamIndex = if (sourceUnchanged) {
                    current.selectedSubtitleStreamIndex
                } else {
                    selectedSource?.legacyDefaultSubtitleStreamIndex()
                },
                detailPlayTarget = if (detail.mediaType == MediaType.Series) {
                    current.detailPlayTarget
                } else {
                    detail.takeIf { it.canPlay }
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Background refresh failures leave the presented detail exactly as
            // it was. Explicit retry still uses [openDetail] and exposes errors.
        }
    }

    /**
     * Restores the season that was visible immediately before an episode was
     * opened. This is intentionally synchronous and local: the season route
     * must not flash a full-page loading state just because its child route
     * used the shared detail slot.
     *
     * @return true when a matching in-memory season snapshot was restored.
     */
    fun restoreSeasonContext(seriesId: MediaId, seasonId: MediaId): Boolean {
        val snapshot = seasonRestoreSnapshot ?: return false
        if (snapshot.series.id != seriesId || snapshot.seasonId != seasonId) return false

        val currentEpisode = state.value.detail?.takeIf { item ->
            snapshot.episodes.any { it.id == item.id }
        }
        val episodes = if (currentEpisode == null) {
            snapshot.episodes
        } else {
            snapshot.episodes.map { episode ->
                if (episode.id == currentEpisode.id) currentEpisode else episode
            }
        }
        ++detailTicket
        ++detailRefreshTicket
        mutableState.value = state.value.copy(
            detailId = snapshot.series.id,
            detail = snapshot.series,
            detailLoading = false,
            detailError = null,
            detailSeasons = snapshot.seasons,
            selectedSeasonId = snapshot.seasonId,
            selectedSeasonDetail = snapshot.season
                ?: snapshot.seasons.firstOrNull { it.id == snapshot.seasonId },
            detailEpisodes = episodes,
            detailPlayTarget = snapshot.seriesPlayTarget,
            selectedSeasonPlayTarget = snapshot.seasonPlayTarget,
            detailSectionsLoading = false,
            detailSectionsError = null,
            detailActionBusy = false,
            detailActionError = null,
            selectedMediaSourceId = null,
            selectedAudioStreamIndex = null,
            selectedSubtitleStreamIndex = null,
        )
        seasonRestoreSnapshot = null
        return true
    }

    /** 用户在详情页指定播放版本；与旧版一致，按当前账号和媒体条目持久化。 */
    suspend fun selectMediaSource(sourceId: String?) {
        val detail = state.value.detail ?: return
        val selected = sourceId?.let { id ->
            detail.mediaSources.firstOrNull { it.id == id && it.playable != false }
        } ?: return
        mutableState.value = state.value.copy(
            selectedMediaSourceId = selected.id,
            selectedAudioStreamIndex = selected.legacyDefaultAudioStreamIndex(),
            selectedSubtitleStreamIndex = selected.legacyDefaultSubtitleStreamIndex(),
        )
        try {
            playbackPreferences?.setPreferredMediaSourceId(session.accountId, detail.id, sourceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 持久化失败只影响下次预选，不中断当前选择。
        }
    }

    fun selectAudioTrack(index: Int?) = mutatePlaybackSelection { copy(selectedAudioStreamIndex = index) }

    fun selectSubtitleTrack(index: Int?) = mutatePlaybackSelection { copy(selectedSubtitleStreamIndex = index) }

    private inline fun mutatePlaybackSelection(transform: MediaBrowserState.() -> MediaBrowserState) {
        mutableState.value = state.value.transform()
    }

    /** 当前详情页起播应携带的用户选择；未选择时为默认协商。 */
    fun playbackOptionsFor(item: MediaItem): cn.mafei.cmp.data.adapter_contract.PlaybackOptions {
        val current = state.value
        if (current.detailId != item.id) return cn.mafei.cmp.data.adapter_contract.PlaybackOptions()
        return cn.mafei.cmp.data.adapter_contract.PlaybackOptions(
            mediaSourceId = current.selectedMediaSourceId,
            audioStreamIndex = current.selectedAudioStreamIndex,
            subtitleStreamIndex = current.selectedSubtitleStreamIndex,
        )
    }

    /**
     * 起播 [item] 时的播放队列，等价于旧版 QueueManager.initializePlaybackQueue 的输入。
     *
     * 详情页/季详情页起播时集列表已经加载，直接用。**首页「继续观看」直接起播**时
     * 手里只有条目本身，那一季的集从来没取过 —— 这时按 series(+season) 拉一次，
     * 否则同一部剧从首页播就没有「选集」也没有「下一集」。
     */
    suspend fun playbackQueueFor(item: MediaItem): PlaybackQueue {
        val loaded = state.value.detailEpisodes
        if (item.mediaType != MediaType.Episode || loaded.any { it.id == item.id }) {
            return PlaybackQueue.forItem(item, loaded)
        }
        val seriesId = item.seriesId ?: return PlaybackQueue.forItem(item, emptyList())
        val episodes = try {
            repository.browse(
                session = session,
                query = MediaQuery.Episodes(seriesId, item.seasonId),
                // 一季通常几十集；MAX_PAGE_SIZE 一次拿完，不做游标循环。
                request = PageRequest(pageSize = PageRequest.MAX_PAGE_SIZE),
                refresh = false,
            ).value.items
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 拉不到就当单片播放：队列构造绝不能把起播挡下来。
            emptyList()
        }
        return PlaybackQueue.forItem(item, episodes)
    }

    /** Legacy CastDetailViewModel: detail first, then three independent ten-item work lists in parallel. */
    suspend fun openPerson(id: MediaId, name: String) {
        val operation = ++personTicket
        mutableState.value = state.value.copy(
            personId = id,
            person = null,
            personLoading = true,
            personError = null,
            personMovies = emptyList(),
            personSeries = emptyList(),
            personEpisodes = emptyList(),
            personSectionsLoading = false,
            personFavoriteBusy = false,
        )
        try {
            val loaded = repository.person(session, id, name, refresh = true)
            if (operation != personTicket) return
            mutableState.value = state.value.copy(
                person = loaded.value,
                personLoading = false,
                personError = if (loaded.origin == MediaLoadOrigin.StaleCache) {
                    "连接暂不可用，显示缓存人物详情"
                } else null,
                personSectionsLoading = true,
            )
            val works = coroutineScope {
                val movies = async { loadPersonWorks(id, MediaType.Movie) }
                val episodes = async { loadPersonWorks(id, MediaType.Episode) }
                val series = async { loadPersonWorks(id, MediaType.Series) }
                Triple(movies.await(), series.await(), episodes.await())
            }
            if (operation != personTicket) return
            mutableState.value = state.value.copy(
                personMovies = works.first,
                personSeries = works.second,
                personEpisodes = works.third,
                personSectionsLoading = false,
            )
        } catch (cancelled: CancellationException) {
            if (operation == personTicket) {
                mutableState.value = state.value.copy(personLoading = false, personSectionsLoading = false)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (operation == personTicket) {
                mutableState.value = state.value.copy(
                    personLoading = false,
                    personSectionsLoading = false,
                    personError = browseError(failure),
                )
            }
        }
    }

    private suspend fun loadPersonWorks(personId: MediaId, type: MediaType): List<MediaItem> {
        val request = PageRequest(
            pageSize = PersonWorkPageSize,
            sort = MediaSort(MediaSortField.Title, SortDirection.Ascending),
            filter = MediaFilter(mediaTypes = setOf(type)),
        )
        return try {
            val loaded = repository.browse(
                session = session,
                query = MediaQuery.PersonItems(personId),
                request = request,
                refresh = true,
            )
            loaded.value.items
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The legacy page keeps the other two work groups when one provider
            // query fails, so a section failure is represented by an empty group.
            emptyList()
        }
    }

    /** 旧版 CastDetailPage 可收藏人物本体（favorite 端点对任意 itemId 有效）。 */
    suspend fun togglePersonFavorite() {
        val person = state.value.person ?: return
        if (!capabilities.favoritesEdit || state.value.personFavoriteBusy) return
        mutableState.value = state.value.copy(personFavoriteBusy = true)
        try {
            repository.setFavorite(session, person.id, !person.isFavorite)
            if (state.value.person?.id == person.id) {
                mutableState.value = state.value.copy(
                    person = person.copy(isFavorite = !person.isFavorite),
                    personFavoriteBusy = false,
                )
            } else {
                mutableState.value = state.value.copy(personFavoriteBusy = false)
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = state.value.copy(personFavoriteBusy = false)
            throw cancelled
        } catch (failure: Exception) {
            mutableState.value = state.value.copy(personFavoriteBusy = false, personError = browseError(failure))
        }
    }

    fun clearPerson() {
        ++personTicket
        mutableState.value = state.value.copy(
            personId = null,
            person = null,
            personLoading = false,
            personError = null,
            personMovies = emptyList(),
            personSeries = emptyList(),
            personEpisodes = emptyList(),
            personSectionsLoading = false,
            personFavoriteBusy = false,
        )
    }

    suspend fun selectSeason(id: MediaId) {
        val current = state.value
        val seriesId = current.detail?.takeIf { it.mediaType == MediaType.Series }?.id ?: return
        if (current.detailSeasons.none { it.id == id }) return
        val operation = detailTicket
        mutableState.value = current.copy(
            selectedSeasonId = id,
            selectedSeasonDetail = null,
            detailEpisodes = emptyList(),
            selectedSeasonPlayTarget = null,
            detailSectionsLoading = true,
            detailSectionsError = null,
        )
        val seasonDetail = try {
            repository.item(session, id, refresh = true).value
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (operation != detailTicket || state.value.selectedSeasonId != id) return
        mutableState.value = state.value.copy(selectedSeasonDetail = seasonDetail)
        loadEpisodes(
            seriesId = seriesId,
            seasonId = id,
            operation = operation,
            playbackTarget = seasonDetail?.playbackTarget,
            preferProgressFallback = true,
            updateSeriesTarget = false,
        )
    }

    suspend fun toggleFavorite() {
        val detail = state.value.detail ?: return
        if (!capabilities.favoritesEdit) return
        mutateDetail(detail, favorite = !detail.userData.isFavorite, watched = null)
    }

    suspend fun toggleWatched() {
        val detail = state.value.detail ?: return
        if (!capabilities.watchedEdit) return
        mutateDetail(detail, favorite = null, watched = !detail.userData.isPlayed)
    }

    suspend fun toggleWatched(item: MediaItem) {
        if (!capabilities.watchedEdit) return
        mutateListItem(item, UserDataAction.Watched)
    }

    suspend fun toggleFavorite(item: MediaItem) {
        if (!capabilities.favoritesEdit) return
        mutateListItem(item, UserDataAction.Favorite)
    }

    // ------------------------------------------------------------- watch history

    private enum class HistorySource { Resume, Completed }

    private data class HistorySourceLoad(
        val requested: Boolean,
        val failed: Boolean,
    )

    private data class HistorySeriesPosition(
        val seasonNumber: Int,
        val episodeNumber: Int,
        val hasMultipleSeasons: Boolean,
    )

    private data class HistorySeriesCacheEntry(
        val position: HistorySeriesPosition?,
        val expiresAtEpochMs: Long,
    )

    /**
     * 独立播放历史（旧版 MineHistoryPage）：resume（进行中）与已看完（played）
     * 两路各自分页，浏览器层按 legacy 规则合并——剧集按所属剧去重（同一部剧只留
     * 最近一条），电影按条目去重，按最后播放时间倒序。与首页捎带的 resume 段
     * 解耦：这里有自己的分页与刷新，不再借用 homeFeed。
     */
    suspend fun loadHistory() = loadHistory(HistoryPageSize)

    /** Legacy Mine tab preview: each source requests 24 rows, then the UI shows 6/8. */
    suspend fun loadHistoryPreview() = loadHistory(MineHistoryPreviewSourceLimit)

    private suspend fun loadHistory(pageSize: Int) {
        val operation = ++historyTicket
        historyPageSize = pageSize
        historyResumePages = MediaPageAccumulator()
        historyCompletedPages = MediaPageAccumulator()
        historyResumeRetryInitial = false
        historyCompletedRetryInitial = false
        mutableState.value = state.value.copy(
            historyLoading = true,
            historyRefreshing = false,
            historyLoadingMore = false,
            historyLoadMoreFailed = false,
            historyCanLoadMore = true,
            historyError = null,
            historyItems = emptyList(),
            historyResumeCursor = null,
            historyCompletedCursor = null,
        )
        try {
            val (resume, completed) = coroutineScope {
                val resume = async {
                    loadHistorySource(operation, HistorySource.Resume, cursor = null, fetch = true)
                }
                val completed = async {
                    loadHistorySource(operation, HistorySource.Completed, cursor = null, fetch = true)
                }
                resume.await() to completed.await()
            }
            publishHistory(operation, initial = true, resume = resume, completed = completed)
        } catch (cancelled: CancellationException) {
            if (operation == historyTicket) {
                mutableState.value = state.value.copy(historyLoading = false, historyRefreshing = false)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (operation == historyTicket) {
                mutableState.value = state.value.copy(
                    historyLoading = false,
                    historyRefreshing = false,
                    historyCanLoadMore = false,
                    historyError = browseError(failure),
                )
            }
        }
    }

    /** 任一路还有下一页就继续翻；两路都翻完为止。已耗尽的源保持现有累积。 */
    suspend fun loadMoreHistory() {
        val before = state.value
        if (before.historyLoading || before.historyRefreshing || before.historyLoadingMore) return
        if (!before.historyCanLoadMore || before.historyLoadMoreFailed) return
        val resumeCursor = if (historyResumeRetryInitial) null else before.historyResumeCursor
        val completedCursor = if (historyCompletedRetryInitial) null else before.historyCompletedCursor
        val fetchResume = historyResumeRetryInitial || resumeCursor != null
        val fetchCompleted = historyCompletedRetryInitial || completedCursor != null
        if (!fetchResume && !fetchCompleted) return
        val operation = ++historyTicket
        mutableState.value = before.copy(
            historyLoadingMore = true,
            historyLoadMoreFailed = false,
            historyError = null,
        )
        try {
            val (resume, completed) = coroutineScope {
                val resume = async {
                    loadHistorySource(operation, HistorySource.Resume, resumeCursor, fetchResume)
                }
                val completed = async {
                    loadHistorySource(operation, HistorySource.Completed, completedCursor, fetchCompleted)
                }
                resume.await() to completed.await()
            }
            publishHistory(operation, initial = false, resume = resume, completed = completed)
        } catch (cancelled: CancellationException) {
            if (operation == historyTicket) {
                mutableState.value = state.value.copy(historyLoadingMore = false)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (operation == historyTicket) {
                mutableState.value = state.value.copy(historyLoadingMore = false, historyError = browseError(failure))
            }
        }
    }

    /** The old footer owns retry; reaching the end again must not auto-loop. */
    suspend fun retryLoadMoreHistory() {
        val before = state.value
        if (!before.historyLoadMoreFailed || before.historyLoadingMore) return
        mutableState.value = before.copy(historyLoadMoreFailed = false)
        loadMoreHistory()
    }

    private fun historyResumeQuery(cursor: PageCursor?) = PageRequest(
        cursor = cursor,
        pageSize = historyPageSize,
        filter = MediaFilter(mediaTypes = HistoryMediaTypes),
    )

    private fun historyCompletedQuery(cursor: PageCursor?) = PageRequest(
        cursor = cursor,
        pageSize = historyPageSize,
        sort = MediaSort(MediaSortField.DatePlayed, SortDirection.Descending),
        filter = MediaFilter(
            mediaTypes = HistoryMediaTypes,
            playedOnly = true,
        ),
    )

    /**
     * 拉取一路历史。失败返回 null（legacy 行为：一路失败不拖垮另一路，下次刷新重试）。
     * 累加器始终持有该路的完整累积，[publishHistory] 从快照合并，因此翻页时
     * 已耗尽的另一路不会丢内容。
     */
    private suspend fun loadHistorySource(
        operation: Long,
        source: HistorySource,
        cursor: PageCursor?,
        fetch: Boolean,
    ): HistorySourceLoad {
        if (!fetch) return HistorySourceLoad(requested = false, failed = false)
        return try {
            val page =
            when (source) {
                HistorySource.Resume -> repository.browse(
                    session,
                    MediaQuery.Resume,
                    historyResumeQuery(cursor),
                    refresh = cursor == null,
                ).value
                HistorySource.Completed -> repository.browse(
                    session,
                    MediaQuery.Items(null),
                    historyCompletedQuery(cursor),
                    refresh = cursor == null,
                ).value
            }
            if (operation != historyTicket) return HistorySourceLoad(requested = true, failed = false)
            when (source) {
                HistorySource.Resume -> {
                    historyResumePages.append(cursor, page)
                    if (cursor == null) historyResumeRetryInitial = false
                }
                HistorySource.Completed -> {
                    historyCompletedPages.append(cursor, page)
                    if (cursor == null) historyCompletedRetryInitial = false
                }
            }
            HistorySourceLoad(requested = true, failed = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (operation == historyTicket && cursor == null) {
                // A failed first page remains retryable from cursor=null. A
                // failed following page keeps the accumulator's old cursor.
                if (source == HistorySource.Resume) historyResumeRetryInitial = true
                else historyCompletedRetryInitial = true
            }
            HistorySourceLoad(requested = true, failed = true)
        }
    }

    private suspend fun publishHistory(
        operation: Long,
        initial: Boolean,
        resume: HistorySourceLoad,
        completed: HistorySourceLoad,
    ) {
        if (operation != historyTicket) return
        val merged = mergeHistory(historyResumePages.snapshot().items, historyCompletedPages.snapshot().items)
        val requested = listOf(resume, completed).filter { it.requested }
        val anyFailed = requested.any { it.failed }
        val allRequestedFailed = requested.isNotEmpty() && requested.all { it.failed }
        val pageError = initial && (allRequestedFailed || (merged.isEmpty() && anyFailed))
        val canLoadMore = historyResumeRetryInitial || historyCompletedRetryInitial ||
            historyResumePages.snapshot().nextCursor != null || historyCompletedPages.snapshot().nextCursor != null
        mutableState.value = state.value.copy(
            historyItems = merged,
            historyResumeCursor = historyResumePages.snapshot().nextCursor,
            historyCompletedCursor = historyCompletedPages.snapshot().nextCursor,
            historyLoading = false,
            historyRefreshing = false,
            historyLoadingMore = false,
            historyLoadMoreFailed = !pageError && anyFailed,
            historyCanLoadMore = canLoadMore,
            historyError = if (pageError) HistoryLoadError else null,
        )
        if (!pageError && merged.isNotEmpty()) enrichHistorySeries(operation, merged)
    }

    /** Legacy MineHistoryPlanner.merge：剧集按剧折叠保留最近一条，按播放时间倒序。 */
    private fun mergeHistory(vararg sources: List<MediaItem>): List<PlaybackHistoryItem> {
        val byKey = linkedMapOf<String, Pair<Long, MediaItem>>()
        sources.forEach { source ->
            source.forEach { item ->
                if (item.id.value.isEmpty()) return@forEach
                if (item.mediaType !in HistoryMediaTypes) return@forEach
                val playedAt = item.userData.lastPlayedEpochMs ?: return@forEach
                val key = if (item.mediaType == MediaType.Episode && item.seriesId != null) {
                    "series:${item.seriesId!!.value}"
                } else {
                    "item:${item.id.value}"
                }
                val current = byKey[key]
                if (current == null || playedAt > current.first) byKey[key] = playedAt to item
            }
        }
        return byKey.values.sortedByDescending { it.first }.map { (_, item) -> historyItem(item) }
    }

    private fun historyItem(item: MediaItem): PlaybackHistoryItem {
        val cached = item.seriesId?.let(historySeriesCache::get)
            ?.takeIf { it.expiresAtEpochMs > SystemEpochClock.nowEpochMs() }
            ?.position
        return PlaybackHistoryItem(
            media = item,
            latestSeasonNumber = cached?.seasonNumber,
            latestEpisodeNumber = cached?.episodeNumber,
            seriesHasMultipleSeasons = (item.seasonNumber ?: 0) > 1 || cached?.hasMultipleSeasons == true,
        )
    }

    private suspend fun enrichHistorySeries(operation: Long, visible: List<PlaybackHistoryItem>) {
        val seriesIds = visible.asSequence()
            .filter { it.media.mediaType == MediaType.Episode }
            .mapNotNull { it.media.seriesId }
            .distinct()
            .toList()
        if (seriesIds.isEmpty()) return
        val positions = mutableMapOf<MediaId, HistorySeriesPosition>()
        seriesIds.chunked(HistoryEnrichmentConcurrency).forEach { batch ->
            val loaded = coroutineScope {
                batch.map { seriesId ->
                    async {
                        try {
                            seriesId to latestHistorySeriesPosition(seriesId)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            seriesId to null
                        }
                    }
                }.map { it.await() }
            }
            if (operation != historyTicket) return
            loaded.forEach { (seriesId, position) ->
                if (position != null) positions[seriesId] = position
            }
        }
        if (operation != historyTicket || positions.isEmpty()) return
        mutableState.value = state.value.copy(
            historyItems = state.value.historyItems.map { item ->
                val position = item.media.seriesId?.let(positions::get) ?: return@map item
                item.copy(
                    latestSeasonNumber = position.seasonNumber,
                    latestEpisodeNumber = position.episodeNumber,
                    seriesHasMultipleSeasons = item.seriesHasMultipleSeasons || position.hasMultipleSeasons,
                )
            },
        )
    }

    private suspend fun latestHistorySeriesPosition(seriesId: MediaId): HistorySeriesPosition? {
        val now = SystemEpochClock.nowEpochMs()
        historySeriesCache[seriesId]?.let { cached ->
            if (cached.expiresAtEpochMs > now) return cached.position
            historySeriesCache.remove(seriesId)
        }
        val accumulator = MediaPageAccumulator()
        val first = repository.browse(
            session,
            MediaQuery.Episodes(seriesId, seasonId = null),
            PageRequest(pageSize = PageRequest.MAX_PAGE_SIZE),
            refresh = true,
        ).value
        var page = accumulator.append(null, first)
        page.nextCursor?.let { cursor ->
            val second = repository.browse(
                session,
                MediaQuery.Episodes(seriesId, seasonId = null),
                PageRequest(cursor = cursor, pageSize = PageRequest.MAX_PAGE_SIZE),
            ).value
            page = accumulator.append(cursor, second)
        }
        val position = latestHistorySeriesPosition(page.items)
        historySeriesCache[seriesId] = HistorySeriesCacheEntry(
            position = position,
            expiresAtEpochMs = now + HistorySeriesCacheTtlMs,
        )
        return position
    }

    private fun latestHistorySeriesPosition(episodes: List<MediaItem>): HistorySeriesPosition? {
        var latestSeason = -1
        var latestEpisode = -1
        val seasons = mutableSetOf<Int>()
        episodes.forEach { item ->
            val season = item.seasonNumber?.takeIf { it > 0 } ?: return@forEach
            val episode = item.episodeNumber?.takeIf { it > 0 } ?: return@forEach
            seasons += season
            if (season > latestSeason || (season == latestSeason && episode > latestEpisode)) {
                latestSeason = season
                latestEpisode = episode
            }
        }
        if (latestEpisode <= 0) return null
        return HistorySeriesPosition(
            seasonNumber = latestSeason,
            episodeNumber = latestEpisode,
            hasMultipleSeasons = seasons.size > 1 || latestSeason > 1,
        )
    }

    // ---------------------------------------------------------------- deletion

    /** 探测当前账号是否可删除媒体（管理员策略），供 UI 决定是否出现删除入口。 */
    suspend fun refreshDeleteAvailability() {
        if (state.value.deleteAvailable != null) return
        if (!capabilities.mediaDelete) {
            mutableState.value = state.value.copy(deleteAvailable = false)
            return
        }
        val available = try {
            repository.canDeleteMedia(session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        mutableState.value = state.value.copy(deleteAvailable = available)
    }

    /** 旧版“我的”页角色标签：Jellyfin Policy / 飞牛 user/info 是唯一来源。 */
    suspend fun refreshAdministratorStatus() {
        if (state.value.administrator != null) return
        val administrator = try {
            repository.isAdministrator(session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        mutableState.value = state.value.copy(administrator = administrator)
    }

    /** 管理员批量删除媒体（列表页多选 / 详情页单条）。成功后同步修剪各列表。 */
    suspend fun deleteMedia(ids: Collection<MediaId>) {
        val targets = ids.filter { it.value.isNotBlank() }.distinct()
        if (targets.isEmpty() || !capabilities.mediaDelete || state.value.deleteBusy) return
        mutableState.value = state.value.copy(deleteBusy = true, deleteError = null)
        try {
            repository.deleteItems(session, targets)
            if (state.value.deleteBusy) {
                mutableState.value = state.value.removeMedia(targets.toSet()).copy(deleteBusy = false)
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = state.value.copy(deleteBusy = false)
            throw cancelled
        } catch (failure: Exception) {
            mutableState.value = state.value.copy(deleteBusy = false, deleteError = browseError(failure))
        }
    }

    private suspend fun mutateDetail(
        detail: MediaItem,
        favorite: Boolean?,
        watched: Boolean?,
    ) {
        val operation = detailTicket
        if (state.value.detailActionBusy) return
        mutableState.value = state.value.copy(detailActionBusy = true, detailActionError = null)
        try {
            when {
                favorite != null -> repository.setFavorite(session, detail.id, favorite)
                else -> repository.setWatched(session, detail.id, checkNotNull(watched))
            }
            if (operation != detailTicket || !state.value.containsMedia(detail.id)) return
            val nextUserData = detail.userData.copy(
                isFavorite = favorite ?: detail.userData.isFavorite,
                isPlayed = watched ?: detail.userData.isPlayed,
            )
            mutableState.value = state.value.replaceUserData(detail.id, nextUserData).copy(
                detailActionBusy = false,
                detailActionError = null,
            )
        } catch (cancelled: CancellationException) {
            if (operation == detailTicket) {
                mutableState.value = state.value.copy(detailActionBusy = false)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (operation == detailTicket) {
                mutableState.value = state.value.copy(
                    detailActionBusy = false,
                    detailActionError = browseError(failure),
                )
            }
        }
    }

    /**
     * Mutates a card action without requiring the item to be the currently
     * opened detail. The old implementation reused [mutateDetail], whose
     * stale-detail guard silently discarded list actions because ordinary
     * library/home items are not part of the detail state. Keep one lock per
     * media/action pair so a favorite request cannot block a watched request
     * for the same card, and publish the optimistic state through every feed
     * before the provider round trip completes.
     */
    private suspend fun mutateListItem(item: MediaItem, action: UserDataAction) {
        val key = UserDataActionKey(item.id, action)
        if (!userDataActionLocks.add(key)) return
        val current = state.value.userDataFor(item.id) ?: item.userData
        val previous = when (action) {
            UserDataAction.Watched -> current.isPlayed
            UserDataAction.Favorite -> current.isFavorite
        }
        val target = !previous
        mutableState.value = state.value.replaceUserData(item.id) { data ->
            when (action) {
                UserDataAction.Watched -> data.copy(isPlayed = target)
                UserDataAction.Favorite -> data.copy(isFavorite = target)
            }
        }
        try {
            when (action) {
                UserDataAction.Watched -> repository.setWatched(session, item.id, target)
                UserDataAction.Favorite -> repository.setFavorite(session, item.id, target)
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = state.value.replaceUserData(item.id) { data ->
                when (action) {
                    UserDataAction.Watched -> data.copy(isPlayed = previous)
                    UserDataAction.Favorite -> data.copy(isFavorite = previous)
                }
            }
            throw cancelled
        } catch (failure: Exception) {
            mutableState.value = state.value.replaceUserData(item.id) { data ->
                when (action) {
                    UserDataAction.Watched -> data.copy(isPlayed = previous)
                    UserDataAction.Favorite -> data.copy(isFavorite = previous)
                }
            }.copy(
                detailActionError = browseError(failure).takeIf { state.value.detail?.id == item.id },
            )
        } finally {
            userDataActionLocks.remove(key)
        }
    }

    private suspend fun loadSeriesSections(seriesId: MediaId, detail: MediaItem, operation: Long) {
        mutableState.value = state.value.copy(detailSectionsLoading = true, detailSectionsError = null)
        try {
            val (loaded, providerNextUp) = coroutineScope {
                val seasons = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    repository.browse(session, MediaQuery.Seasons(seriesId), refresh = true)
                }
                val nextUp = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    if (detail.playbackTarget != null) return@async null
                    try {
                        repository.browse(session, MediaQuery.NextUp(seriesId), refresh = true).value.items.firstOrNull()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Legacy preferredSeriesTarget deliberately treats a
                        // failed Next Up lookup as "no target".
                        null
                    }
                }
                seasons.await() to nextUp.await()
            }
            if (operation != detailTicket) return
            val seasons = loaded.value.items.sortedWith(
                compareBy<MediaItem> { it.seasonNumber ?: Int.MAX_VALUE }.thenBy { it.title },
            )
            val playbackTarget = detail.playbackTarget ?: providerNextUp?.toPlaybackTarget()
            val selected = playbackTarget?.seasonNumber?.let { targetSeason ->
                seasons.firstOrNull { it.seasonNumber == targetSeason }
            } ?: seasons.firstOrNull()
            mutableState.value = state.value.copy(
                detailSeasons = seasons,
                selectedSeasonId = selected?.id,
                selectedSeasonDetail = null,
                detailSectionsLoading = selected != null,
                detailSectionsError = null,
            )
            if (selected != null) {
                loadEpisodes(
                    seriesId = seriesId,
                    seasonId = selected.id,
                    operation = operation,
                    playbackTarget = playbackTarget,
                    preferProgressFallback = false,
                    updateSeriesTarget = true,
                )
            } else {
                val directTarget = providerNextUp ?: playbackTarget?.id?.let { id ->
                    try {
                        repository.item(session, id, refresh = true).value
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                }
                if (operation == detailTicket) {
                    mutableState.value = state.value.copy(
                        detailPlayTarget = directTarget,
                        detailSectionsLoading = false,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (operation == detailTicket) {
                mutableState.value = state.value.copy(
                    detailSectionsLoading = false,
                    detailSectionsError = browseError(failure),
                )
            }
        }
    }

    private suspend fun loadEpisodes(
        seriesId: MediaId,
        seasonId: MediaId,
        operation: Long,
        playbackTarget: MediaPlaybackTarget?,
        preferProgressFallback: Boolean,
        updateSeriesTarget: Boolean,
    ) {
        try {
            // Legacy requests one 1000-item window. PageRequest is capped at
            // 500, so consume at most two provider pages and intentionally
            // ignore any remaining cursor rather than inventing UI pagination.
            val loaded = repository.browse(
                session,
                MediaQuery.Episodes(seriesId, seasonId),
                PageRequest(pageSize = 500),
                refresh = true,
            )
            if (operation != detailTicket || state.value.selectedSeasonId != seasonId) return
            val freshPages = MediaPageAccumulator()
            var page = freshPages.append(null, loaded.value)
            page.nextCursor?.let { secondCursor ->
                val second = repository.browse(
                    session,
                    MediaQuery.Episodes(seriesId, seasonId),
                    PageRequest(cursor = secondCursor, pageSize = 500),
                )
                if (operation != detailTicket || state.value.selectedSeasonId != seasonId) return
                page = freshPages.append(secondCursor, second.value)
            }
            val episodes = page.items.sortedWith(
                compareBy<MediaItem> { it.episodeNumber ?: Int.MAX_VALUE }.thenBy { it.title },
            )
            val playTarget = resolveEpisodePlaybackTarget(episodes, playbackTarget, preferProgressFallback)
            mutableState.value = state.value.copy(
                detailEpisodes = episodes,
                detailPlayTarget = if (updateSeriesTarget) playTarget else state.value.detailPlayTarget,
                selectedSeasonPlayTarget = playTarget,
                detailSectionsLoading = false,
                detailSectionsError = null,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (operation == detailTicket && state.value.selectedSeasonId == seasonId) {
                mutableState.value = state.value.copy(
                    detailSectionsLoading = false,
                    detailSectionsError = browseError(failure),
                )
            }
        }
    }

    fun back() {
        ++detailTicket
        ++detailRefreshTicket
        mutableState.value = state.value.copy(
            detailId = null,
            detail = null,
            detailLoading = false,
            detailError = null,
            detailSeasons = emptyList(),
            selectedSeasonId = null,
            selectedSeasonDetail = null,
            detailEpisodes = emptyList(),
            detailPlayTarget = null,
            selectedSeasonPlayTarget = null,
            detailSectionsLoading = false,
            detailSectionsError = null,
            detailActionBusy = false,
            detailActionError = null,
            selectedMediaSourceId = null,
            selectedAudioStreamIndex = null,
            selectedSubtitleStreamIndex = null,
        )
    }
}

private fun MediaItem.toPlaybackTarget(): MediaPlaybackTarget = MediaPlaybackTarget(
    id = id,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    playbackPositionMs = userData.playbackPositionMs,
    playedPercentage = userData.playedPercentage,
    durationMs = runtimeMs,
)

/**
 * Resolves the old ArkUI playback-target rules against real episode rows.
 * Series detail uses target -> first unwatched -> first; season detail inserts
 * "episode with partial progress" before the first-unwatched fallback.
 */
private fun resolveEpisodePlaybackTarget(
    episodes: List<MediaItem>,
    target: MediaPlaybackTarget?,
    preferProgressFallback: Boolean,
): MediaItem? {
    if (episodes.isEmpty()) return null
    var targetIndex = target?.id?.let { id -> episodes.indexOfFirst { it.id == id } } ?: -1
    if (targetIndex < 0 && target?.episodeNumber != null) {
        targetIndex = episodes.indexOfFirst { episode ->
            episode.episodeNumber == target.episodeNumber &&
                (target.seasonNumber == null || episode.seasonNumber == target.seasonNumber)
        }
    }
    val matchedTarget = targetIndex >= 0
    if (targetIndex < 0 && preferProgressFallback) {
        targetIndex = episodes.indexOfFirst { episode ->
            val percentage = episodePlaybackPercentage(episode)
            percentage > 0.0 && percentage < 100.0
        }
    }
    if (targetIndex < 0) targetIndex = episodes.indexOfFirst { !it.userData.isPlayed }
    if (targetIndex < 0) targetIndex = 0
    val selected = episodes[targetIndex]
    if (!matchedTarget || target == null) return selected
    val targetPercentage = target.playedPercentage ?: 0.0
    if (target.playbackPositionMs <= 0L && targetPercentage <= 0.0) return selected
    return selected.copy(
        runtimeMs = target.durationMs?.takeIf { it > 0L } ?: selected.runtimeMs,
        userData = selected.userData.copy(
            playbackPositionMs = target.playbackPositionMs.coerceAtLeast(0L),
            playedPercentage = target.playedPercentage,
        ),
    )
}

private fun episodePlaybackPercentage(item: MediaItem): Double {
    val explicit = item.userData.playedPercentage?.coerceIn(0.0, 100.0) ?: 0.0
    if (explicit > 0.0) return explicit
    val duration = item.runtimeMs ?: 0L
    return if (item.userData.playbackPositionMs > 0L && duration > 0L) {
        (item.userData.playbackPositionMs.toDouble() * 100.0 / duration.toDouble()).coerceIn(0.0, 100.0)
    } else {
        0.0
    }
}

/** Preserve stable shelf inputs so an in-place home refresh stays local. */
private fun HomeFeed.mergeUnchangedSections(fresh: HomeFeed): HomeFeed {
    val recommendations = recommendations.takeIf { it == fresh.recommendations } ?: fresh.recommendations
    val resume = resume.takeIf { it == fresh.resume } ?: fresh.resume
    val nextUp = nextUp.takeIf { it == fresh.nextUp } ?: fresh.nextUp
    val latest = latest.takeIf { it == fresh.latest } ?: fresh.latest
    return if (
        recommendations === this.recommendations &&
        resume === this.resume &&
        nextUp === this.nextUp &&
        latest === this.latest
    ) {
        this
    } else {
        HomeFeed(
            recommendations = recommendations,
            resume = resume,
            nextUp = nextUp,
            latest = latest,
        )
    }
}

private fun MediaBrowserState.replaceUserData(id: MediaId, userData: MediaUserData): MediaBrowserState {
    return replaceUserData(id) { userData }
}

private fun MediaBrowserState.replaceUserData(
    id: MediaId,
    transform: (MediaUserData) -> MediaUserData,
): MediaBrowserState {
    fun List<MediaItem>.replace() = map { item ->
        if (item.id == id) item.copy(userData = transform(item.userData)) else item
    }
    fun List<PlaybackHistoryItem>.replaceHistory() = map { item ->
        if (item.media.id == id) item.copy(media = item.media.copy(userData = transform(item.media.userData))) else item
    }
    return copy(
        items = items.replace(),
        favoriteItems = favoriteItems.replace(),
        searchItems = searchItems.replace(),
        detail = detail?.let { item -> if (item.id == id) item.copy(userData = transform(item.userData)) else item },
        selectedSeasonDetail = selectedSeasonDetail?.let { item ->
            if (item.id == id) item.copy(userData = transform(item.userData)) else item
        },
        detailPlayTarget = detailPlayTarget?.let { item ->
            if (item.id == id) item.copy(userData = transform(item.userData)) else item
        },
        selectedSeasonPlayTarget = selectedSeasonPlayTarget?.let { item ->
            if (item.id == id) item.copy(userData = transform(item.userData)) else item
        },
        detailSeasons = detailSeasons.replace(),
        detailEpisodes = detailEpisodes.replace(),
        homeFeed = homeFeed.copy(
            recommendations = homeFeed.recommendations.replace(),
            resume = homeFeed.resume.replace(),
            nextUp = homeFeed.nextUp.replace(),
            latest = homeFeed.latest.replace(),
        ),
        homeLatestByCollection = homeLatestByCollection.mapValues { (_, media) -> media.replace() },
        historyItems = historyItems.replaceHistory(),
    )
}

private fun MediaBrowserState.userDataFor(id: MediaId): MediaUserData? {
    fun List<MediaItem>.find() = firstOrNull { it.id == id }?.userData
    return items.find()
        ?: favoriteItems.find()
        ?: searchItems.find()
        ?: detail?.takeIf { it.id == id }?.userData
        ?: selectedSeasonDetail?.takeIf { it.id == id }?.userData
        ?: detailPlayTarget?.takeIf { it.id == id }?.userData
        ?: selectedSeasonPlayTarget?.takeIf { it.id == id }?.userData
        ?: detailSeasons.find()
        ?: detailEpisodes.find()
        ?: homeFeed.recommendations.find()
        ?: homeFeed.resume.find()
        ?: homeFeed.nextUp.find()
        ?: homeFeed.latest.find()
        ?: homeLatestByCollection.values.asSequence().flatMap { it.asSequence() }.firstOrNull { it.id == id }?.userData
        ?: historyItems.firstOrNull { it.media.id == id }?.media?.userData
}

private fun MediaBrowserState.containsMedia(id: MediaId): Boolean =
    detail?.id == id || selectedSeasonDetail?.id == id || detailSeasons.any { it.id == id } ||
        detailEpisodes.any { it.id == id }

/** 删除成功后的状态修剪：所有列表里被删条目一并移除，总数收紧。 */
private fun MediaBrowserState.removeMedia(ids: Set<MediaId>): MediaBrowserState {
    fun List<MediaItem>.prune() = filter { it.id !in ids }
    fun List<PlaybackHistoryItem>.pruneHistory() = filter { it.media.id !in ids }
    return copy(
        items = items.prune(),
        favoriteItems = favoriteItems.prune(),
        searchItems = searchItems.prune(),
        detailEpisodes = detailEpisodes.prune(),
        detailSeasons = detailSeasons.prune(),
        selectedSeasonDetail = selectedSeasonDetail?.takeIf { it.id !in ids },
        detailPlayTarget = detailPlayTarget?.takeIf { it.id !in ids },
        selectedSeasonPlayTarget = selectedSeasonPlayTarget?.takeIf { it.id !in ids },
        personMovies = personMovies.prune(),
        personSeries = personSeries.prune(),
        personEpisodes = personEpisodes.prune(),
        historyItems = historyItems.pruneHistory(),
        totalEstimate = totalEstimate?.let { total -> (total - items.count { it.id in ids }).coerceAtLeast(0) },
        favoriteTotalEstimate = favoriteTotalEstimate?.let { total ->
            (total - favoriteItems.count { it.id in ids }).coerceAtLeast(0)
        },
        homeFeed = homeFeed.copy(
            recommendations = homeFeed.recommendations.prune(),
            resume = homeFeed.resume.prune(),
            nextUp = homeFeed.nextUp.prune(),
            latest = homeFeed.latest.prune(),
        ),
        homeLatestByCollection = homeLatestByCollection.mapValues { (_, media) -> media.prune() },
    )
}

private fun MediaSourceInfo.legacyDefaultAudioStreamIndex(): Int? =
    defaultAudioIndex?.let { preferred -> audioStreams.firstOrNull { it.index == preferred }?.index }
        ?: audioStreams.firstOrNull { it.isDefault }?.index
        ?: audioStreams.firstOrNull()?.index

private fun MediaSourceInfo.legacyDefaultSubtitleStreamIndex(): Int = when (defaultSubtitleIndex) {
    -1 -> -1
    else -> defaultSubtitleIndex?.let { preferred ->
        subtitleStreams.firstOrNull { it.index == preferred }?.index
    } ?: subtitleStreams.firstOrNull { it.isDefault }?.index ?: -1
}

private const val HomeLatestPageSize = 30
private const val CatalogPageSize = 100
private const val LegacySearchLimit = 800
private const val SearchFirstSegmentSize = PageRequest.MAX_PAGE_SIZE
private val SearchMediaTypes = setOf(MediaType.Episode, MediaType.Movie, MediaType.Video, MediaType.Series)
private const val PersonWorkPageSize = 10
private const val HistoryPageSize = 10
private const val MineHistoryPreviewSourceLimit = 24
private const val HistoryEnrichmentConcurrency = 4
private const val HistorySeriesCacheTtlMs = 5L * 60L * 1_000L
private const val HistoryLoadError = "播放历史加载失败，点击重试"
private val HistoryMediaTypes = setOf(MediaType.Movie, MediaType.Episode)
private val FavoriteMediaTypes = setOf(MediaType.Movie, MediaType.Series, MediaType.Episode, MediaType.Video)

/** Never display exception messages, URLs, response bodies or credential-bearing causes. */
private fun browseError(error: Exception): String = when (error) {
    is AuthenticationError -> "登录已失效，请退出后重新登录"
    is AuthorizationError -> "当前账号没有访问权限"
    is NetworkUnreachableError, is TimeoutError -> "无法连接服务器，请检查网络后重试"
    is ResourceNotFoundError -> "内容已不存在或不可见"
    is ProtocolDecodingError -> "服务器返回了无法识别的数据"
    else -> "加载失败，请重试"
}
