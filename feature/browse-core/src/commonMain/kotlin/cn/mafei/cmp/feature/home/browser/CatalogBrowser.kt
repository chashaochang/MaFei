package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.HomeFeed
import cn.mafei.cmp.data.repositories.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal class CatalogBrowser(context: BrowserContext) : BrowserController(context), BrowserTabParticipant {
    private var ticket = 0L
    private var collectionsTicket = 0L
    private var homeTicket = 0L
    private var homeLatestSequence = 0L
    private val homeLatestTickets = mutableMapOf<CollectionId, Long>()
    private var initialized = false
    private var pages = MediaPageAccumulator()
    private val tabPages = mutableMapOf<String, MediaPageAccumulator>()

    override fun switchTabContext(previous: String, target: String, retained: Set<String>) {
        ++ticket
        tabPages[previous] = pages
        pages = tabPages.remove(target) ?: MediaPageAccumulator()
        tabPages.keys.retainAll(retained)
    }

    override suspend fun resumeTabRequests(requests: Set<BrowserTabRequest>) {
        val current = state.value
        if (BrowserTabRequest.Library in requests && !current.loading && !current.refreshing) {
            val id = current.selectedCollection ?: current.collections.firstOrNull()?.id
            if (id != null) select(id, keepContent = current.selectedCollection == id)
            else if (!initialized) refresh()
            else mutableState.value = current.copy(tabPendingRequests = current.tabPendingRequests - BrowserTabRequest.Library)
        } else if (BrowserTabRequest.LibraryMore in requests && !current.loadingMore) loadMore()
    }

    override fun retainTabContexts(keys: Set<String>) { tabPages.keys.retainAll(keys) }

    suspend fun start() {
        if (initialized) return
        refresh()
    }

    suspend fun refresh() {
        val operation = ++ticket
        val collectionsOperation = ++collectionsTicket
        val homeOperation = ++homeTicket
        mutableState.value =
            state.value.copy(loading = !initialized, refreshing = initialized, loadingMore = false, error = null, homeError = null, homeLoading = true,
                tabPendingRequests = state.value.tabPendingRequests - BrowserTabRequest.Library - BrowserTabRequest.LibraryMore)
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
                        if (collectionsOperation != collectionsTicket) return@launch
                        // Library discovery is account-wide even when the originating
                        // Tab's item request has been invalidated by a switch.
                        initialized = true
                        mutableState.value = state.value.copy(collections = libraries.value.items)
                        if (operation != ticket) return@launch
                        val selected =
                            state.value.selectedCollection?.takeIf { id -> libraries.value.items.any { it.id == id } }
                                ?: libraries.value.items.firstOrNull()?.id
                        if (selected != state.value.selectedCollection) {
                            pages = MediaPageAccumulator()
                            mutableState.value = state.value.copy(items = emptyList(), nextCursor = null, totalEstimate = null)
                        }
                        mutableState.value =
                            state.value.copy(
                                collections = libraries.value.items,
                                selectedCollection = selected,
                                stale = libraries.origin == MediaLoadOrigin.StaleCache,
                                homeLatestByCollection = state.value.homeLatestByCollection.filterKeys { id -> libraries.value.items.any { it.id == id } },
                                homeLatestLoading = state.value.homeLatestLoading.filterTo(linkedSetOf()) { id -> libraries.value.items.any { it.id == id } },
                                homeLatestErrors = state.value.homeLatestErrors.filterKeys { id -> libraries.value.items.any { it.id == id } },
                            )
                        // An empty library response is a real empty state, not permission to list the entire server.
                        if (selected == null) {
                            pages = MediaPageAccumulator()
                            mutableState.value =
                                state.value.copy(items = emptyList(), nextCursor = null, totalEstimate = 0, loading = false, refreshing = false)
                        } else loadFirst(selected, operation, libraries.origin == MediaLoadOrigin.StaleCache)
                    } catch (cancelled: CancellationException) {
                        retainCancelledRequest(operation, BrowserTabRequest.Library)
                        cancellation = cancelled
                        throw cancelled
                    } catch (failure: Exception) {
                        if (operation == ticket) {
                            mutableState.value = state.value.copy(loading = false, refreshing = false, error = browseError(failure))
                        }
                    }
                }
            }
            cancellation?.let { throw it }
        } catch (cancelled: CancellationException) {
            retainCancelledRequest(operation, BrowserTabRequest.Library)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == ticket) {
                mutableState.value = state.value.copy(loading = false, refreshing = false, error = browseError(failure))
            }
        }
    }

    /**
     * Refreshes only the provider-backed home feed while keeping the currently presented home document intact.
     *
     * Returning from a detail, season, episode or person route is not a manual pull-to-refresh. The old content therefore remains visible while this request
     * runs: no global busy flag changes, no library request is issued, and no pagination or selection state is reset. On success, unchanged shelves retain
     * their list instances so Compose only has to update the sections whose provider data actually changed.
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
            val message = browseError(failure)
            // A background refresh must not insert an error row above existing
            // content and shift the user's established viewport. With no home
            // content at all, exposing the failure remains the honest fallback.
            if (state.value.homeFeed == HomeFeed()) {
                mutableState.value = state.value.copy(homeError = message, homeStale = false)
            }
        } finally {
            if (operation == homeTicket) {
                mutableState.value = state.value.copy(homeLoading = false)
            }
        }
    }

    private fun publishHome(home: MediaLoad<HomeFeed>) {
        val before = state.value
        mutableState.value =
            before.copy(homeFeed = before.homeFeed.mergeUnchangedSections(home.value), homeError = null, homeStale = home.origin == MediaLoadOrigin.StaleCache)
    }

    suspend fun select(id: CollectionId, keepContent: Boolean = false) {
        val collection = state.value.collections.firstOrNull { it.id == id } ?: return
        val operation = ++ticket
        if (!keepContent) pages = MediaPageAccumulator()
        val dedicatedCatalog =
            (capabilities.music && collection.mediaTypes.any { it in setOf(MediaType.Audio, MediaType.MusicAlbum, MediaType.MusicArtist) }) ||
                (capabilities.photos && MediaType.Photo in collection.mediaTypes) ||
                (capabilities.liveTv && MediaType.LiveChannel in collection.mediaTypes)
        mutableState.value =
            state.value.copy(
                selectedCollection = id,
                tabPendingRequests = state.value.tabPendingRequests - BrowserTabRequest.Library - BrowserTabRequest.LibraryMore,
                items = if (keepContent) state.value.items else emptyList(),
                nextCursor = state.value.nextCursor.takeIf { keepContent },
                totalEstimate = state.value.totalEstimate.takeIf { keepContent },
                loading = !dedicatedCatalog && !keepContent,
                refreshing = !dedicatedCatalog && keepContent,
                error = null,
                stale = false,
            )
        if (dedicatedCatalog) return
        try {
            loadFirst(id, operation, false)
        } catch (cancelled: CancellationException) {
            retainCancelledRequest(operation, BrowserTabRequest.Library)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == ticket) mutableState.value = state.value.copy(loading = false, refreshing = false, error = browseError(failure))
        }
    }

    /**
     * Loads one home-tab feed without changing the active media-library page.
     *
     * The legacy HarmonyOS home kept a latest-items map per library. Reusing [select] here clears [MediaBrowserState.items], which also destroys the lazy
     * layout's visible anchor and makes a Floating Tab tap jump back to the hero. Keep this request independent and cache it by collection.
     */
    suspend fun loadHomeLatest(id: CollectionId) {
        val collection = state.value.collections.firstOrNull { it.id == id } ?: return
        if (state.value.homeLatestByCollection.containsKey(id) || id in state.value.homeLatestLoading) return
        check(homeLatestSequence < Long.MAX_VALUE) { "Home latest request sequence exhausted" }
        val operation = ++homeLatestSequence
        homeLatestTickets[id] = operation
        mutableState.value = state.value.copy(homeLatestLoading = state.value.homeLatestLoading + id, homeLatestErrors = state.value.homeLatestErrors - id)
        val request = collection.homeLatestRequest(PageRequest(pageSize = HomeLatestPageSize))
        try {
            val loaded = repository.browse(session = session, query = MediaQuery.Items(id), request = request, refresh = true)
            if (homeLatestTickets[id] != operation) return
            mutableState.value =
                state.value.copy(
                    homeLatestByCollection = state.value.homeLatestByCollection + (id to loaded.value.items.distinctBy { it.id }),
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
                val message = browseError(failure)
                mutableState.value =
                    state.value.copy(
                        homeLatestLoading = state.value.homeLatestLoading - id,
                        homeLatestErrors = if (message == null) state.value.homeLatestErrors - id else state.value.homeLatestErrors + (id to message),
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
        mutableState.value =
            state.value.copy(
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
        mutableState.value = before.copy(loadingMore = true, error = null,
            tabPendingRequests = before.tabPendingRequests - BrowserTabRequest.LibraryMore)
        try {
            val loaded = repository.browse(session, MediaQuery.Items(id), libraryRequest(id, cursor))
            if (operation != ticket) return
            // The page walk retains cursor history, while the published facts
            // may have been edited/deleted since these earlier pages were read.
            val visible = state.value.items.associateBy { it.id }
            val previousRows = pages.snapshot().items.associateBy { it.id }
            val removed = previousRows.keys - visible.keys
            val edited = visible.filter { (id, item) -> previousRows[id]?.userData != item.userData }
            val page = pages.append(cursor, loaded.value)
            mutableState.value =
                state.value.copy(
                    items = page.items.filterNot { it.id in removed }.map { item ->
                        edited[item.id]?.let { item.copy(userData = it.userData) } ?: item
                    },
                    nextCursor = page.nextCursor,
                    totalEstimate = page.totalEstimate,
                    loadingMore = false,
                    stale = before.stale || loaded.origin == MediaLoadOrigin.StaleCache,
                )
        } catch (cancelled: CancellationException) {
            retainCancelledRequest(operation, BrowserTabRequest.LibraryMore)
            throw cancelled
        } catch (failure: Exception) {
            if (operation == ticket) {
                mutableState.value = state.value.copy(loadingMore = false, error = browseError(failure))
            }
        }
    }

    suspend fun setLibrarySort(sort: MediaSort) {
        if (state.value.librarySort == sort) return
        val id = state.value.selectedCollection ?: return
        mutableState.value = state.value.copy(librarySort = sort)
        select(id)
    }

    private fun retainCancelledRequest(operation: Long, request: BrowserTabRequest) {
        if (operation != ticket) return
        val current = state.value
        if (request == BrowserTabRequest.Library && !current.loading && !current.refreshing) return
        mutableState.value = current.copy(loading = false, refreshing = false, loadingMore = false,
            tabPendingRequests = current.tabPendingRequests + request)
    }

    private fun libraryRequest(id: CollectionId, cursor: PageCursor? = null): PageRequest =
        PageRequest(
            cursor = cursor,
            pageSize = CatalogPageSize,
            sort = state.value.librarySort,
            filter = MediaFilter(mediaTypes = state.value.collections.firstOrNull { it.id == id }?.mediaTypes.orEmpty()),
        )
}
