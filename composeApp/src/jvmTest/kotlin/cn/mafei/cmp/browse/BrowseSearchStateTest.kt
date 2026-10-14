package cn.mafei.cmp

import kotlinx.coroutines.*
import kotlin.test.*

class BrowseSearchStateTest {
    @Test fun replacementCancelsPreviousSearchAndRecordsOnlySuccessfulSubmission() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val saved = mutableListOf<List<String>>()
        val state = BrowseSearchState(
            runSearch = { term -> if (term == "slow") {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            } else term != "failed" },
            loadHistory = { emptyList() }, saveHistory = { saved += it },
            scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined),
        )
        var delivered = 0
        state.query("slow", true) { delivered++ }
        started.await()
        state.query("fresh", true) { delivered++ }
        cancelled.await()
        state.query("failed", true) { delivered++ }
        state.query("incremental", false) { delivered++ }
        assertEquals(2, delivered)
        assertEquals(listOf("fresh"), state.searchHistories)
        assertEquals(listOf(listOf("fresh")), saved)
    }

    @Test fun leavingSearchCancelsWithoutDeliveringResultsOrRecordingHistory() = runBlocking<Unit> {
        val cancelled = CompletableDeferred<Unit>()
        val state = BrowseSearchState(
            runSearch = { try { awaitCancellation() } finally { cancelled.complete(Unit) } },
            loadHistory = { emptyList() }, saveHistory = { error("cancelled query must not persist") },
            scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined),
        )
        state.query("term", true) { error("cancelled query must not deliver") }
        state.cancel(); cancelled.await()
        assertTrue(state.searchHistories.isEmpty())
    }

    @Test fun historyKeepsTenRecentCaseInsensitiveTermsAndClearPersistsEmpty() = runBlocking<Unit> {
        var persisted = emptyList<String>()
        val state = BrowseSearchState({ true }, { listOf("A", "B") }, { persisted = it },
            CoroutineScope(coroutineContext + Dispatchers.Unconfined))
        state.load()
        state.record(" a ")
        assertEquals(listOf("a", "B"), state.searchHistories)
        repeat(12) { state.record("term$it") }
        assertEquals(10, state.searchHistories.size)
        assertEquals("term11", state.searchHistories.first())
        assertEquals(state.searchHistories, persisted)
        state.clearHistory()
        assertTrue(state.searchHistories.isEmpty())
        assertTrue(persisted.isEmpty())
    }
}
