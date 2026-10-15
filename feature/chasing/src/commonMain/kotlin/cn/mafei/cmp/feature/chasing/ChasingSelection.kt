package cn.mafei.cmp.feature.chasing

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.MediaId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ChasingSelectionState(
    val ids: Set<MediaId> = emptySet(),
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

/** Legacy setChasingSeries semantics, scoped to a single authenticated account. */
class ChasingSelection(private val account: AccountId, private val store: ChasingSeriesStore) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ChasingSelectionState())
    val state = mutableState.asStateFlow()

    suspend fun load() = mutex.withLock {
        try {
            mutableState.value = ChasingSelectionState(ids = store.seriesIds(account).toSet(), loaded = true)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.value = state.value.copy(error = "追剧列表读取失败，请重试") }
    }

    suspend fun toggle(id: MediaId) = mutex.withLock {
        if (id.value.isBlank()) return@withLock
        currentCoroutineContext().ensureActive()
        mutableState.value = state.value.copy(saving = true, error = null)
        try {
            // Read the persisted list under the same writer lock before updating it.
            val current = store.seriesIds(account).toSet()
            val updated = if (id in current) current - id else current + id
            withContext(NonCancellable) {
                store.replace(account, updated.toList())
                mutableState.value = ChasingSelectionState(ids = updated, loaded = true)
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = state.value.copy(saving = false)
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = state.value.copy(saving = false, error = "追剧设置保存失败，请重试")
        }
    }
}
