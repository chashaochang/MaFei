package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.type
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.feature.connect.ServerDiscoverySuggestion
import cn.mafei.cmp.feature.connect.ServerSuggestionType
import cn.mafei.cmp.providers.jellyfin.discovery.discoverLocalJellyfinServers
import cn.mafei.cmp.session.*
import kotlinx.coroutines.flow.map

internal suspend fun savedJellyfinServerSuggestions(accounts: AccountAccess): List<ServerDiscoverySuggestion> =
    accounts
        .savedConnections()
        .asSequence()
        .filter { it.server.provider == ProviderKind.Jellyfin }
        .distinctBy { it.server.baseUrl }
        // ArkUI's loadPreviouslyUsedServers() reads the current server only. The
        // CMP catalog is already ordered by last activity, so after sign-out the
        // first Jellyfin row is the retained equivalent of currentServerId; do not
        // turn the discovery page into a second account-history list.
        .take(1)
        .map { connection ->
            // The ArkUI page deliberately displayed the saved hostname in both rows;
            // the friendly server name is reserved for an actual UDP discovery reply.
            ServerDiscoverySuggestion(type = ServerSuggestionType.Saved, name = connection.server.baseUrl, address = connection.server.baseUrl)
        }
        .toList()

internal suspend fun discoveredJellyfinServerSuggestions(): List<ServerDiscoverySuggestion> =
    discoverLocalJellyfinServers().map { server ->
        ServerDiscoverySuggestion(type = ServerSuggestionType.Discovered, name = server.name.orEmpty(), address = server.address.orEmpty())
    }
