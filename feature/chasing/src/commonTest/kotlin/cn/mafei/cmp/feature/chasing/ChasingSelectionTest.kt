package cn.mafei.cmp.feature.chasing

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.MediaId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChasingSelectionTest {
    private val account = AccountId("account")
    private val series = MediaId("series")
    private class Store : ChasingSeriesStore {
        val lists = mutableMapOf<AccountId, List<MediaId>>()
        var failure: Exception? = null
        override suspend fun seriesIds(accountId: AccountId) = lists[accountId].orEmpty()
        override suspend fun replace(accountId: AccountId, seriesIds: List<MediaId>) {
            failure?.let { throw it }
            lists[accountId] = seriesIds
        }
    }

    @Test fun followsAndUnfollowsWithoutChangingAnotherAccount() = runBlocking {
        val store = Store()
        val other = AccountId("other")
        store.lists[other] = listOf(MediaId("untouched"))
        val selection = ChasingSelection(account, store)
        selection.load()
        selection.toggle(series)
        assertEquals(setOf(series), selection.state.value.ids)
        assertEquals(listOf(series), store.lists[account])
        selection.toggle(series)
        assertTrue(selection.state.value.ids.isEmpty())
        assertEquals(listOf(MediaId("untouched")), store.lists[other])
    }

    @Test fun failedWriteKeepsTheSavedSelection() = runBlocking {
        val store = Store().apply { lists[account] = listOf(series) }
        val selection = ChasingSelection(account, store)
        selection.load()
        store.failure = IllegalStateException("disk full")
        selection.toggle(series)
        assertEquals(setOf(series), selection.state.value.ids)
        assertNotNull(selection.state.value.error)
        assertFalse(selection.state.value.saving)
    }

    @Test fun concurrentChangesPreserveBothSeries() = runBlocking {
        val store = Store()
        val selection = ChasingSelection(account, store)
        val second = MediaId("second")
        val a = launch { selection.toggle(series) }
        val b = launch { selection.toggle(second) }
        a.join(); b.join()
        assertEquals(setOf(series, second), store.lists[account]?.toSet())
    }

    @Test fun storageCancellationDoesNotLeaveSavingStuck() = runBlocking {
        val store = Store().apply { failure = CancellationException("cancelled") }
        val selection = ChasingSelection(account, store)
        try { selection.toggle(series); error("Expected cancellation") } catch (_: CancellationException) { }
        assertFalse(selection.state.value.saving)
        assertTrue(selection.state.value.ids.isEmpty())
    }
}
