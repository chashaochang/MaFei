package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Search jobs and history live with the browse session, not with its animated route entry. */
internal class BrowseSearchState(
    private val runSearch: suspend (String) -> Boolean,
    private val loadHistory: suspend () -> List<String>,
    private val saveHistory: suspend (List<String>) -> Unit,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    private var searchJob: Job? = null
    var searchHistories by mutableStateOf(emptyList<String>())
        private set

    suspend fun load() {
        searchHistories =
            try {
                loadHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
    }

    fun cancel() {
        searchJob?.cancel()
        searchJob = null
    }

    fun query(term: String, submitted: Boolean, onResults: suspend () -> Unit) {
        searchJob?.cancel()
        searchJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (runSearch(term)) {
                    onResults()
                    if (submitted) record(term)
                }
            }
    }

    fun record(raw: String) {
        val term = raw.trim()
        if (term.isNotEmpty()) {
            val updated = (listOf(term) + searchHistories.filterNot { it.equals(term, ignoreCase = true) }).take(10)
            searchHistories = updated
            scope.launch {
                try {
                    saveHistory(updated)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Unit
                }
            }
        }
    }

    fun clearHistory() {
        searchHistories = emptyList()
        scope.launch {
            try {
                saveHistory(emptyList())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Unit
            }
        }
    }
}
