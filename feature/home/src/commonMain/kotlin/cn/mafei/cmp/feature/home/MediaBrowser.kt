package cn.mafei.cmp.feature.home

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
    val detailEpisodes: List<MediaItem> = emptyList(),
    val detailEpisodesNextCursor: PageCursor? = null,
    val detailEpisodesLoadingMore: Boolean = false,
    val detailSectionsLoading: Boolean = false,
    val detailSectionsError: String? = null,
    val detailActionBusy: Boolean = false,
    val detailActionError: String? = null,
    val detailSimilar: List<MediaItem> = emptyList(),
    val detailSimilarLoading: Boolean = false,
    val detailSimilarError: String? = null,
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
    val personCursors: Map<MediaType, PageCursor> = emptyMap(),
    val personLoadingMore: Set<MediaType> = emptySet(),
    val personFavoriteBusy: Boolean = false,
    /** 独立播放历史：resume（进行中）+ 已看完两路分页合并，剧集按剧去重。 */
    val historyItems: List<MediaItem> = emptyList(),
    val historyLoading: Boolean = false,
    val historyRefreshing: Boolean = false,
    val historyLoadingMore: Boolean = false,
    val historyError: String? = null,
    val historyResumeCursor: PageCursor? = null,
    val historyCompletedCursor: PageCursor? = null,
    /** null = 未探测；探测由 [refreshDeleteAvailability] 触发。 */
    val deleteAvailable: Boolean? = null,
    val deleteBusy: Boolean = false,
    val deleteError: String? = null,
)

/**
 * Durable playback selections (legacy `AppPreference.preferredMediaSourceId`).
 * One global "last picked version" — the picker on the next detail page
 * preselects it when the item carries a matching source id.
 */
interface MediaPlaybackPreferences {
    suspend fun preferredMediaSourceId(): String?
    suspend fun setPreferredMediaSourceId(id: String?)
}

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
    private val mutableState = MutableStateFlow(MediaBrowserState())
    val state: StateFlow<MediaBrowserState> = mutableState.asStateFlow()
    private var ticket = 0L
    private var detailTicket = 0L
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
    private var episodePages = MediaPageAccumulator()
    private val personPages = mutableMapOf<MediaType, MediaPageAccumulator>()
    private var historyResumePages = MediaPageAccumulator()
    private var historyCompletedPages = MediaPageAccumulator()

    suspend fun start() {
        if (initialized) return
        refresh()
    }

    suspend fun refresh() {
        val operation = ++ticket
        mutableState.value = state.value.copy(
            loading = !initialized,
            refreshing = initialized,
            loadingMore = false,
            error = null,
            homeError = null,
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
                    if (operation != ticket) return@launch
                    if (snapshot != null && state.value.homeFeed == HomeFeed()) {
                        println(
                            "[MaFeiTiming] home snapshot painted " +
                                "(${snapshot.recommendations.size + snapshot.resume.size + snapshot.nextUp.size + snapshot.latest.size} items)",
                        )
                        mutableState.value = state.value.copy(homeFeed = snapshot, homeError = null, homeStale = false)
                    }
                    val home = repository.home(session, refresh = true)
                    if (operation != ticket) return@launch
                    mutableState.value = state.value.copy(
                        homeFeed = home.value,
                        homeError = null,
                        homeStale = home.origin == MediaLoadOrigin.StaleCache,
                    )
                } catch (cancelled: CancellationException) {
                    // supervisorScope swallows child cancellation; record it so
                    // refresh still propagates the cancellation contract.
                    cancellation = cancelled
                    throw cancelled
                } catch (failure: Exception) {
                    if (operation != ticket) return@launch
                    mutableState.value = state.value.copy(homeError = browseError(failure), homeStale = false)
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

    suspend fun select(id: CollectionId) {
        if (state.value.collections.none { it.id == id }) return
        val operation = ++ticket
        pages = MediaPageAccumulator()
        mutableState.value = state.value.copy(selectedCollection = id, items = emptyList(), nextCursor = null,
            totalEstimate = null,
            loading = true, error = null, stale = false)
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
        val request = PageRequest(
            pageSize = HomeLatestPageSize,
            sort = MediaSort(MediaSortField.DateAdded, SortDirection.Descending),
            filter = MediaFilter(mediaTypes = collection.mediaTypes),
        )
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

    suspend fun search(term: String) {
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
            return
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
            val loaded = repository.browse(
                session,
                MediaQuery.Search(normalized),
                PageRequest(pageSize = SearchPageSize),
                refresh = true,
            )
            if (operation != searchTicket) return
            val page = searchPages.append(null, loaded.value)
            mutableState.value = state.value.copy(
                searchItems = page.items,
                searchLoading = false,
                searchError = null,
                searchNextCursor = page.nextCursor,
                searchTotalEstimate = page.totalEstimate,
            )
        } catch (cancelled: CancellationException) {
            if (operation == searchTicket) mutableState.value = state.value.copy(searchLoading = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == searchTicket) {
                mutableState.value = state.value.copy(searchLoading = false, searchError = browseError(failure))
            }
        }
    }

    suspend fun loadMoreSearch() {
        val before = state.value
        val cursor = before.searchNextCursor ?: return
        val term = before.searchQuery.takeIf { it.isNotBlank() } ?: return
        if (before.searchLoading || before.searchLoadingMore) return
        val operation = ++searchTicket
        mutableState.value = before.copy(searchLoadingMore = true, searchError = null)
        try {
            val loaded = repository.browse(
                session,
                MediaQuery.Search(term),
                PageRequest(cursor = cursor, pageSize = SearchPageSize),
            )
            if (operation != searchTicket) return
            val page = searchPages.append(cursor, loaded.value)
            mutableState.value = state.value.copy(
                searchItems = page.items,
                searchLoadingMore = false,
                searchNextCursor = page.nextCursor,
                searchTotalEstimate = page.totalEstimate,
            )
        } catch (cancelled: CancellationException) {
            if (operation == searchTicket) mutableState.value = state.value.copy(searchLoadingMore = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == searchTicket) {
                mutableState.value = state.value.copy(searchLoadingMore = false, searchError = browseError(failure))
            }
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
            mediaTypes = if (capabilities.favoritesFilter) FavoriteMediaTypes else emptySet(),
            favoritesOnly = true,
        ),
    )

    suspend fun openDetail(id: MediaId) {
        val operation = ++detailTicket
        episodePages = MediaPageAccumulator()
        mutableState.value = state.value.copy(
            detailId = id,
            detail = null,
            detailLoading = true,
            detailError = null,
            detailSeasons = emptyList(),
            selectedSeasonId = null,
            detailEpisodes = emptyList(),
            detailEpisodesNextCursor = null,
            detailEpisodesLoadingMore = false,
            detailSectionsLoading = false,
            detailSectionsError = null,
            detailActionBusy = false,
            detailActionError = null,
            detailSimilar = emptyList(),
            detailSimilarLoading = false,
            detailSimilarError = null,
            selectedMediaSourceId = null,
            selectedAudioStreamIndex = null,
            selectedSubtitleStreamIndex = null,
        )
        try {
            val loaded = repository.item(session, id, refresh = true)
            if (operation != detailTicket) return
            // 偏好版本预选：上次手动挑过的媒体源在这一条数据上也存在时直接选中
            //（legacy preferredMediaSourceId 的行为）。
            var preferredSourceId: String? = null
            if (loaded.value.mediaSources.size > 1) {
                val remembered = try {
                    playbackPreferences?.preferredMediaSourceId()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                preferredSourceId = remembered?.takeIf { candidate ->
                    loaded.value.mediaSources.any { it.id == candidate }
                }
            }
            mutableState.value = state.value.copy(detail = loaded.value, detailLoading = false,
                detailError = if (loaded.origin == MediaLoadOrigin.StaleCache) "连接暂不可用，显示缓存详情" else null,
                selectedMediaSourceId = preferredSourceId)
            if (loaded.value.mediaType == MediaType.Series) {
                loadSeriesSections(id, operation)
            } else {
                loadSimilar(id, operation)
            }
        } catch (cancelled: CancellationException) {
            if (operation == detailTicket) mutableState.value = state.value.copy(detailLoading = false)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == detailTicket) mutableState.value = state.value.copy(detailLoading = false, detailError = browseError(failure))
        }
    }

    /**
     * Legacy VideoDetailPage 「相似推荐」: one small row under the cast
     * section. Loaded for every playable/detailable item; a failure leaves
     * the section absent instead of failing the page.
     */
    private suspend fun loadSimilar(id: MediaId, operation: Long) {
        mutableState.value = state.value.copy(detailSimilarLoading = true, detailSimilarError = null)
        try {
            val loaded = repository.browse(session, MediaQuery.Similar(id), refresh = true)
            if (operation != detailTicket) return
            mutableState.value = state.value.copy(
                detailSimilar = loaded.value.items,
                detailSimilarLoading = false,
                detailSimilarError = null,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (operation == detailTicket) {
                mutableState.value = state.value.copy(
                    detailSimilar = emptyList(),
                    detailSimilarLoading = false,
                    detailSimilarError = null, // 相似区失败不提示，区块直接消失
                )
            }
        }
    }

    /** 用户在详情页指定播放版本；选择持久化为全局「最近一次选择」。 */
    suspend fun selectMediaSource(sourceId: String?) {
        val detail = state.value.detail ?: return
        if (sourceId != null && detail.mediaSources.none { it.id == sourceId }) return
        mutableState.value = state.value.copy(
            selectedMediaSourceId = sourceId,
            // 换版本后旧轨道索引不再有意义（不同源的流布局不同）。
            selectedAudioStreamIndex = null,
            selectedSubtitleStreamIndex = null,
        )
        if (sourceId == null) return
        try {
            playbackPreferences?.setPreferredMediaSourceId(sourceId)
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

    /** Legacy CastDetailViewModel: detail first, then three independent ten-item work lists in parallel. */
    suspend fun openPerson(id: MediaId) {
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
            personCursors = emptyMap(),
            personLoadingMore = emptySet(),
            personFavoriteBusy = false,
        )
        personPages.clear()
        try {
            val loaded = repository.person(session, id, refresh = true)
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
                personMovies = works.first.items,
                personSeries = works.second.items,
                personEpisodes = works.third.items,
                personCursors = mapOf(
                    MediaType.Movie to works.first.cursor,
                    MediaType.Series to works.second.cursor,
                    MediaType.Episode to works.third.cursor,
                ).filterValues { it != null }.mapValues { it.value!! },
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

    private class PersonWorkPage(val items: List<MediaItem>, val cursor: PageCursor?)

    private suspend fun loadPersonWorks(personId: MediaId, type: MediaType): PersonWorkPage {
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
            val accumulator = MediaPageAccumulator()
            val page = accumulator.append(null, loaded.value)
            personPages[type] = accumulator
            PersonWorkPage(page.items, page.nextCursor)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The legacy page keeps the other two work groups when one provider
            // query fails, so a section failure is represented by an empty group.
            personPages.remove(type)
            PersonWorkPage(emptyList(), null)
        }
    }

    /** 人物作品横滑到尾部时追加下一页（契约的 PersonItems 分页能力首次被 UI 消费）。 */
    suspend fun loadMorePersonWorks(type: MediaType) {
        val current = state.value
        val personId = current.personId ?: return
        val cursor = current.personCursors[type] ?: return
        if (type in current.personLoadingMore) return
        if (current.personMovies.size + current.personSeries.size + current.personEpisodes.size == 0) return
        val operation = personTicket
        mutableState.value = current.copy(personLoadingMore = current.personLoadingMore + type)
        try {
            val loaded = repository.browse(
                session,
                MediaQuery.PersonItems(personId),
                PageRequest(
                    cursor = cursor,
                    pageSize = PersonWorkPageSize,
                    sort = MediaSort(MediaSortField.Title, SortDirection.Ascending),
                    filter = MediaFilter(mediaTypes = setOf(type)),
                ),
            )
            if (operation != personTicket) return
            val accumulator = personPages[type] ?: MediaPageAccumulator().also { personPages[type] = it }
            val page = accumulator.append(cursor, loaded.value)
            mutableState.value = state.value.copy(
                personMovies = if (type == MediaType.Movie) page.items else state.value.personMovies,
                personSeries = if (type == MediaType.Series) page.items else state.value.personSeries,
                personEpisodes = if (type == MediaType.Episode) page.items else state.value.personEpisodes,
                personCursors = if (page.nextCursor != null) {
                    state.value.personCursors + (type to page.nextCursor!!)
                } else {
                    state.value.personCursors - type
                },
                personLoadingMore = state.value.personLoadingMore - type,
            )
        } catch (cancelled: CancellationException) {
            if (operation == personTicket) {
                mutableState.value = state.value.copy(personLoadingMore = state.value.personLoadingMore - type)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (operation == personTicket) {
                mutableState.value = state.value.copy(personLoadingMore = state.value.personLoadingMore - type)
            }
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
        personPages.clear()
        mutableState.value = state.value.copy(
            personId = null,
            person = null,
            personLoading = false,
            personError = null,
            personMovies = emptyList(),
            personSeries = emptyList(),
            personEpisodes = emptyList(),
            personSectionsLoading = false,
            personCursors = emptyMap(),
            personLoadingMore = emptySet(),
            personFavoriteBusy = false,
        )
    }

    suspend fun selectSeason(id: MediaId) {
        val current = state.value
        val seriesId = current.detail?.takeIf { it.mediaType == MediaType.Series }?.id ?: return
        if (current.detailSeasons.none { it.id == id }) return
        val operation = detailTicket
        episodePages = MediaPageAccumulator()
        mutableState.value = current.copy(
            selectedSeasonId = id,
            detailEpisodes = emptyList(),
            detailEpisodesNextCursor = null,
            detailEpisodesLoadingMore = false,
            detailSectionsLoading = true,
            detailSectionsError = null,
        )
        loadEpisodes(seriesId, id, operation)
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
        mutateDetail(item, favorite = null, watched = !item.userData.isPlayed)
    }

    // ------------------------------------------------------------- watch history

    private enum class HistorySource { Resume, Completed }

    /**
     * 独立播放历史（旧版 MineHistoryPage）：resume（进行中）与已看完（played）
     * 两路各自分页，浏览器层按 legacy 规则合并——剧集按所属剧去重（同一部剧只留
     * 最近一条），电影按条目去重，按最后播放时间倒序。与首页捎带的 resume 段
     * 解耦：这里有自己的分页与刷新，不再借用 homeFeed。
     */
    suspend fun loadHistory() {
        val operation = ++historyTicket
        historyResumePages = MediaPageAccumulator()
        historyCompletedPages = MediaPageAccumulator()
        val alreadyLoaded = state.value.historyItems.isNotEmpty() || state.value.historyResumeCursor != null ||
            state.value.historyCompletedCursor != null
        mutableState.value = state.value.copy(
            historyLoading = !alreadyLoaded,
            historyRefreshing = alreadyLoaded,
            historyLoadingMore = false,
            historyError = null,
            historyItems = if (alreadyLoaded) state.value.historyItems else emptyList(),
            historyResumeCursor = null,
            historyCompletedCursor = null,
        )
        try {
            coroutineScope {
                val resume = async { loadHistorySource(HistorySource.Resume, cursor = null, fetch = true) }
                val completed = async { loadHistorySource(HistorySource.Completed, cursor = null, fetch = true) }
                resume.await()
                completed.await()
            }
            publishHistory(operation)
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
                    historyError = browseError(failure),
                )
            }
        }
    }

    /** 任一路还有下一页就继续翻；两路都翻完为止。已耗尽的源保持现有累积。 */
    suspend fun loadMoreHistory() {
        val before = state.value
        if (before.historyLoading || before.historyRefreshing || before.historyLoadingMore) return
        val resumeCursor = before.historyResumeCursor
        val completedCursor = before.historyCompletedCursor
        if (resumeCursor == null && completedCursor == null) return
        val operation = ++historyTicket
        mutableState.value = before.copy(historyLoadingMore = true, historyError = null)
        try {
            coroutineScope {
                val resume = async { loadHistorySource(HistorySource.Resume, cursor = resumeCursor, fetch = resumeCursor != null) }
                val completed = async { loadHistorySource(HistorySource.Completed, cursor = completedCursor, fetch = completedCursor != null) }
                resume.await()
                completed.await()
            }
            publishHistory(operation)
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

    private fun historyResumeQuery(cursor: PageCursor?) = PageRequest(
        cursor = cursor,
        pageSize = HistoryPageSize,
        filter = MediaFilter(mediaTypes = setOf(MediaType.Movie, MediaType.Episode, MediaType.Video)),
    )

    private fun historyCompletedQuery(cursor: PageCursor?) = PageRequest(
        cursor = cursor,
        pageSize = HistoryPageSize,
        sort = MediaSort(MediaSortField.DatePlayed, SortDirection.Descending),
        filter = MediaFilter(
            mediaTypes = setOf(MediaType.Movie, MediaType.Episode, MediaType.Video),
            playedOnly = true,
        ),
    )

    /**
     * 拉取一路历史。失败返回 null（legacy 行为：一路失败不拖垮另一路，下次刷新重试）。
     * 累加器始终持有该路的完整累积，[publishHistory] 从快照合并，因此翻页时
     * 已耗尽的另一路不会丢内容。
     */
    private suspend fun loadHistorySource(source: HistorySource, cursor: PageCursor?, fetch: Boolean) {
        if (!fetch) return
        try {
            when (source) {
                HistorySource.Resume -> repository.browse(
                    session,
                    MediaQuery.Resume,
                    historyResumeQuery(cursor),
                    refresh = cursor == null,
                ).value.let { historyResumePages.append(cursor, it) }
                HistorySource.Completed -> repository.browse(
                    session,
                    MediaQuery.Items(null),
                    historyCompletedQuery(cursor),
                    refresh = cursor == null,
                ).value.let { historyCompletedPages.append(cursor, it) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (cursor == null) {
                // 首页失败：重置该路，等待下次刷新；翻页失败则保留旧累积。
                if (source == HistorySource.Resume) historyResumePages = MediaPageAccumulator()
                else historyCompletedPages = MediaPageAccumulator()
            }
        }
    }

    private suspend fun publishHistory(operation: Long) {
        if (operation != historyTicket) return
        val merged = mergeHistory(historyResumePages.snapshot().items, historyCompletedPages.snapshot().items)
        mutableState.value = state.value.copy(
            historyItems = merged,
            historyResumeCursor = historyResumePages.snapshot().nextCursor,
            historyCompletedCursor = historyCompletedPages.snapshot().nextCursor,
            historyLoading = false,
            historyRefreshing = false,
            historyLoadingMore = false,
            historyError = null,
        )
    }

    /** Legacy MineHistoryPlanner.merge：剧集按剧折叠保留最近一条，按播放时间倒序。 */
    private fun mergeHistory(vararg sources: List<MediaItem>): List<MediaItem> {
        val byKey = linkedMapOf<String, Pair<Long, MediaItem>>()
        sources.forEach { source ->
            source.forEach { item ->
                if (item.id.value.isEmpty()) return@forEach
                val playedAt = item.userData.lastPlayedEpochMs ?: 0L
                val key = if (item.mediaType == MediaType.Episode && item.seriesId != null) {
                    "series:${item.seriesId!!.value}"
                } else {
                    "item:${item.id.value}"
                }
                val current = byKey[key]
                if (current == null || playedAt > current.first) byKey[key] = playedAt to item
            }
        }
        return byKey.values.sortedByDescending { it.first }.map { it.second }
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

    private suspend fun loadSeriesSections(seriesId: MediaId, operation: Long) {
        mutableState.value = state.value.copy(detailSectionsLoading = true, detailSectionsError = null)
        try {
            val loaded = repository.browse(session, MediaQuery.Seasons(seriesId), refresh = true)
            if (operation != detailTicket) return
            val seasons = loaded.value.items.sortedWith(
                compareBy<MediaItem> { it.seasonNumber ?: Int.MAX_VALUE }.thenBy { it.title },
            )
            val selected = seasons.firstOrNull()?.id
            mutableState.value = state.value.copy(
                detailSeasons = seasons,
                selectedSeasonId = selected,
                detailSectionsLoading = selected != null,
                detailSectionsError = null,
            )
            if (selected != null) loadEpisodes(seriesId, selected, operation)
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

    private suspend fun loadEpisodes(seriesId: MediaId, seasonId: MediaId, operation: Long) {
        try {
            // 首页拉 200：一季极少超过这个量，超出部分由 [loadMoreEpisodes] 续页
            //（旧版是一次拉全 + 30 集一组；200 首页在体感上等价，且尊重服务端分页）。
            val loaded = repository.browse(
                session,
                MediaQuery.Episodes(seriesId, seasonId),
                PageRequest(pageSize = 200),
                refresh = true,
            )
            if (operation != detailTicket || state.value.selectedSeasonId != seasonId) return
            val freshPages = MediaPageAccumulator()
            val page = freshPages.append(null, loaded.value)
            episodePages = freshPages
            mutableState.value = state.value.copy(
                detailEpisodes = page.items.sortedWith(
                    compareBy<MediaItem> { it.episodeNumber ?: Int.MAX_VALUE }.thenBy { it.title },
                ),
                detailEpisodesNextCursor = page.nextCursor,
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

    /** 超长季（>200 集）续页；与 30 集分组 UI 正交——新页合并后分组自动扩展。 */
    suspend fun loadMoreEpisodes() {
        val before = state.value
        val cursor = before.detailEpisodesNextCursor ?: return
        val seriesId = before.detail?.id ?: return
        val seasonId = before.selectedSeasonId ?: return
        if (before.detailSectionsLoading || before.detailEpisodesLoadingMore) return
        val operation = detailTicket
        mutableState.value = before.copy(detailEpisodesLoadingMore = true)
        try {
            val loaded = repository.browse(
                session,
                MediaQuery.Episodes(seriesId, seasonId),
                PageRequest(cursor = cursor, pageSize = 200),
            )
            if (operation != detailTicket || state.value.selectedSeasonId != seasonId) return
            val page = episodePages.append(cursor, loaded.value)
            mutableState.value = state.value.copy(
                detailEpisodes = page.items.sortedWith(
                    compareBy<MediaItem> { it.episodeNumber ?: Int.MAX_VALUE }.thenBy { it.title },
                ),
                detailEpisodesNextCursor = page.nextCursor,
                detailEpisodesLoadingMore = false,
            )
        } catch (cancelled: CancellationException) {
            if (operation == detailTicket) {
                mutableState.value = state.value.copy(detailEpisodesLoadingMore = false)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (operation == detailTicket) {
                mutableState.value = state.value.copy(detailEpisodesLoadingMore = false)
            }
        }
    }

    fun back() {
        ++detailTicket
        episodePages = MediaPageAccumulator()
        mutableState.value = state.value.copy(
            detailId = null,
            detail = null,
            detailLoading = false,
            detailError = null,
            detailSeasons = emptyList(),
            selectedSeasonId = null,
            detailEpisodes = emptyList(),
            detailEpisodesNextCursor = null,
            detailEpisodesLoadingMore = false,
            detailSectionsLoading = false,
            detailSectionsError = null,
            detailActionBusy = false,
            detailActionError = null,
            detailSimilar = emptyList(),
            detailSimilarLoading = false,
            detailSimilarError = null,
            selectedMediaSourceId = null,
            selectedAudioStreamIndex = null,
            selectedSubtitleStreamIndex = null,
        )
    }
}

private fun MediaBrowserState.replaceUserData(id: MediaId, userData: MediaUserData): MediaBrowserState {
    fun List<MediaItem>.replace() = map { item -> if (item.id == id) item.copy(userData = userData) else item }
    return copy(
        items = items.replace(),
        favoriteItems = favoriteItems.replace(),
        searchItems = searchItems.replace(),
        detail = detail?.let { item -> if (item.id == id) item.copy(userData = userData) else item },
        detailSeasons = detailSeasons.replace(),
        detailEpisodes = detailEpisodes.replace(),
        homeFeed = homeFeed.copy(
            recommendations = homeFeed.recommendations.replace(),
            resume = homeFeed.resume.replace(),
            nextUp = homeFeed.nextUp.replace(),
            latest = homeFeed.latest.replace(),
        ),
        homeLatestByCollection = homeLatestByCollection.mapValues { (_, media) -> media.replace() },
    )
}

private fun MediaBrowserState.containsMedia(id: MediaId): Boolean =
    detail?.id == id || detailSeasons.any { it.id == id } || detailEpisodes.any { it.id == id }

/** 删除成功后的状态修剪：所有列表里被删条目一并移除，总数收紧。 */
private fun MediaBrowserState.removeMedia(ids: Set<MediaId>): MediaBrowserState {
    fun List<MediaItem>.prune() = filter { it.id !in ids }
    return copy(
        items = items.prune(),
        favoriteItems = favoriteItems.prune(),
        searchItems = searchItems.prune(),
        detailEpisodes = detailEpisodes.prune(),
        detailSeasons = detailSeasons.prune(),
        detailSimilar = detailSimilar.prune(),
        personMovies = personMovies.prune(),
        personSeries = personSeries.prune(),
        personEpisodes = personEpisodes.prune(),
        historyItems = historyItems.prune(),
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

private const val HomeLatestPageSize = 30
private const val CatalogPageSize = 100
private const val SearchPageSize = 500
private const val PersonWorkPageSize = 10
private const val HistoryPageSize = 50
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
