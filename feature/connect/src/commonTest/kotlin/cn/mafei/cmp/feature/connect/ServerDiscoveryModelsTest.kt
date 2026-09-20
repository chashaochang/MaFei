package cn.mafei.cmp.feature.connect

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerDiscoveryModelsTest {

    @Test
    fun discoveredServersArePrependedAndReplaceSavedAddressDuplicates() {
        val saved = listOf(
            ServerDiscoverySuggestion(ServerSuggestionType.Saved, "http://old:8096", "http://old:8096"),
            ServerDiscoverySuggestion(ServerSuggestionType.Saved, "http://saved:8096", "http://saved:8096"),
        )
        val discovered = listOf(
            ServerDiscoverySuggestion(ServerSuggestionType.Discovered, "Old", "http://old:8096"),
            ServerDiscoverySuggestion(ServerSuggestionType.Discovered, "New", "http://new:8096"),
        )

        assertEquals(
            listOf(
                ServerDiscoverySuggestion(ServerSuggestionType.Discovered, "New", "http://new:8096"),
                ServerDiscoverySuggestion(ServerSuggestionType.Discovered, "Old", "http://old:8096"),
                ServerDiscoverySuggestion(ServerSuggestionType.Saved, "http://saved:8096", "http://saved:8096"),
            ),
            mergeServerDiscoverySuggestions(saved, discovered),
        )
    }
}
