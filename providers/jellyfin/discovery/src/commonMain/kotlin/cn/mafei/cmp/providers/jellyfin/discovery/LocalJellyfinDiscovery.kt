package cn.mafei.cmp.providers.jellyfin.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Jellyfin's UDP discovery reply. Field names match the legacy SDK/OpenAPI model. */
@Serializable
data class LocalJellyfinServer(
    @SerialName("Address") val address: String? = null,
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("EndpointAddress") val endpointAddress: String? = null,
)

/**
 * Performs the same LAN discovery used by the ArkUI application:
 *
 * - bind `0.0.0.0:9990`;
 * - broadcast `who is JellyfinServer?` to `255.255.255.255:7359`;
 * - wait 500 ms for each reply, up to 15 replies;
 * - ignore malformed datagrams and de-duplicate by Jellyfin server id.
 */
suspend fun discoverLocalJellyfinServers(
    timeoutMs: Int = LOCAL_DISCOVERY_TIMEOUT_MS,
    maxServers: Int = LOCAL_DISCOVERY_MAX_SERVERS,
): List<LocalJellyfinServer> {
    require(timeoutMs > 0) { "Discovery timeout must be positive" }
    require(maxServers in 1..LOCAL_DISCOVERY_MAX_SERVERS) { "Discovery result limit is invalid" }
    val messages = withContext(Dispatchers.Default) {
        platformReceiveLocalJellyfinResponses(timeoutMs, maxServers)
    }
    return decodeLocalJellyfinResponses(messages)
}

internal fun decodeLocalJellyfinResponses(messages: List<String>): List<LocalJellyfinServer> {
    val foundIds = mutableSetOf<String?>()
    return buildList {
        messages.forEach { message ->
            val info = runCatching {
                LocalDiscoveryJson.decodeFromString(LocalJellyfinServer.serializer(), message)
            }.getOrNull() ?: return@forEach
            if (foundIds.add(info.id)) add(info)
        }
    }
}

internal expect fun platformReceiveLocalJellyfinResponses(
    timeoutMs: Int,
    maxServers: Int,
): List<String>

internal const val LOCAL_DISCOVERY_MESSAGE: String = "who is JellyfinServer?"
internal const val LOCAL_DISCOVERY_PORT: Int = 7359
internal const val LOCAL_DISCOVERY_BIND_PORT: Int = 9990
internal const val LOCAL_DISCOVERY_TIMEOUT_MS: Int = 500
internal const val LOCAL_DISCOVERY_MAX_SERVERS: Int = 15
internal const val LOCAL_DISCOVERY_RECEIVE_BUFFER_BYTES: Int = 1024

private val LocalDiscoveryJson = Json { ignoreUnknownKeys = true }
