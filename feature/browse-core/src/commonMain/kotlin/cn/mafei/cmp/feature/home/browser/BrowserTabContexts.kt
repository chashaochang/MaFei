package cn.mafei.cmp.feature.home

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Capabilities only: focused controllers never capture one another. */
internal interface BrowserTabParticipant {
    fun switchTabContext(previous: String, target: String, retained: Set<String>)
    fun retainTabContexts(keys: Set<String>) = Unit
    fun pendingTabRequests(): Set<BrowserTabRequest> = emptySet()
    suspend fun resumeTabRequests(requests: Set<BrowserTabRequest>)
}

/** The inactive documents live in the same published model as the foreground document. */
internal class BrowserTabContexts(
    context: BrowserContext,
    private val participants: List<BrowserTabParticipant>,
) : BrowserController(context) {
    private var generation = 0L

    fun switchTabContext(key: String, retainedKeys: Set<String>? = null) {
        require(key.isNotBlank()) { "A Tab context needs a stable key" }
        val before = state.value
        val previous = before.activeTabContext
        if (previous == key) {
            if (retainedKeys != null) retainTabContexts(retainedKeys)
            return
        }
        if (previous == null) {
            // Existing callers can load before installing the navigation host.
            mutableState.value = before.copy(activeTabContext = key)
            return
        }
        ++generation
        val documents = LinkedHashMap(before.tabDocuments.filterKeys { retainedKeys == null || it in retainedKeys })
        val target = documents.remove(key) ?: BrowserTabDocument()
        documents.remove(previous)
        val document = before.captureTabDocument()
        if (retainedKeys == null || previous in retainedKeys) {
            documents[previous] = document.copy(tabPendingRequests = document.tabPendingRequests + participants.flatMap { it.pendingTabRequests() })
        }
        // Three dormant documents plus the foreground are the four retained slots.
        while (documents.size > 3) documents.remove(documents.keys.first())
        val retained = documents.keys + key
        participants.forEach { it.switchTabContext(previous, key, retained) }
        mutableState.value = target.applyTo(before).copy(activeTabContext = key, tabDocuments = documents)
    }

    fun stateForTab(key: String): StateFlow<MediaBrowserState> = TabStateProjection(state, key)

    fun retainTabContexts(keys: Set<String>) {
        val before = state.value
        val retained = keys + listOfNotNull(before.activeTabContext)
        mutableState.value = before.copy(tabDocuments = before.tabDocuments.filterKeys { it in retained })
        participants.forEach { it.retainTabContexts(retained) }
    }

    suspend fun resumeTabContext() {
        val operation = generation
        val revision = repository.refreshRevision(session)
        if (operation != generation) return
        val before = state.value
        val changed = before.tabRefreshRevision?.let { it != revision } == true
        val pending = before.tabPendingRequests.toMutableSet()
        if (changed) {
            // A playback Stop or provider mutation invalidates older user-data
            // documents. Keep the content visible and reconcile through its owner.
            if (before.selectedCollection != null) pending += BrowserTabRequest.Library
            if (before.detailId != null) pending += if (before.detail == null) BrowserTabRequest.Detail else BrowserTabRequest.DetailRefresh
            if (before.personId != null) pending += BrowserTabRequest.Person
            if (before.searchQuery.isNotBlank()) pending += BrowserTabRequest.Search
        }
        if (BrowserTabRequest.Library in pending) pending -= BrowserTabRequest.LibraryMore
        if (BrowserTabRequest.Detail in pending) pending.removeAll(setOf(BrowserTabRequest.DetailSections, BrowserTabRequest.DetailRefresh))
        mutableState.value = before.copy(
            // Each owner consumes its request when work actually starts. If the
            // host is cancelled before a child starts, the next visit retries it.
            tabPendingRequests = pending,
            tabRefreshRevision = revision,
            seasonRestoreDocument = if (changed) null else before.seasonRestoreDocument,
        )
        if (pending.isEmpty()) return
        coroutineScope {
            participants.forEach { participant ->
                launch { if (operation == generation) participant.resumeTabRequests(pending) }
            }
        }
    }
}

/** No store or subscription job: every value is a projection of the one source flow. */
@OptIn(InternalCoroutinesApi::class, ExperimentalForInheritanceCoroutinesApi::class)
private class TabStateProjection(
    private val source: StateFlow<MediaBrowserState>,
    private val key: String,
) : StateFlow<MediaBrowserState> {
    override val value: MediaBrowserState get() = source.value.forTab(key)
    override val replayCache: List<MediaBrowserState> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<MediaBrowserState>): Nothing {
        var previous: MediaBrowserState? = null
        return source.collect(object : FlowCollector<MediaBrowserState> {
            override suspend fun emit(value: MediaBrowserState) {
                val projected = value.forTab(key)
                if (projected != previous) {
                    previous = projected
                    collector.emit(projected)
                }
            }
        })
    }
}

private fun MediaBrowserState.forTab(key: String): MediaBrowserState {
    val document = if (activeTabContext == null || activeTabContext == key) this
    else (tabDocuments[key] ?: BrowserTabDocument()).applyTo(this)
    return document.copy(activeTabContext = key, tabDocuments = emptyMap())
}
