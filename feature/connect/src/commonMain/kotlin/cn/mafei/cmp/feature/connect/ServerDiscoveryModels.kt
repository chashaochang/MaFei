package cn.mafei.cmp.feature.connect

enum class ServerSuggestionType { Discovered, Saved }

data class ServerDiscoverySuggestion(
    val type: ServerSuggestionType,
    val name: String,
    val address: String,
)

/** Mirrors `ServerDiscoveryViewModel`: every discovered address replaces a saved duplicate and is prepended. */
fun mergeServerDiscoverySuggestions(
    saved: List<ServerDiscoverySuggestion>,
    discovered: List<ServerDiscoverySuggestion>,
): List<ServerDiscoverySuggestion> {
    var suggestions = saved
    discovered.forEach { server ->
        suggestions = listOf(server) + suggestions.filter { it.address != server.address }
    }
    return suggestions
}
