package cn.mafei.cmp.providers.plex.core

import cn.mafei.cmp.core.http_contract.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Ephemeral authorization; neither PINs nor cloud tokens are persisted. */
class PlexAuthorization internal constructor(
    val browserUrl: String,
    internal val pinId: Long,
    internal val clientId: String,
    internal val lifetimeMs: Long,
) {
    internal val started = TimeSource.Monotonic.markNow()
    override fun toString() = "PlexAuthorization(redacted)"
}

class PlexServerOption internal constructor(
    val id: String,
    val name: String,
    internal val token: String,
    internal val endpoints: List<HttpUrl>,
) {
    override fun toString() = "PlexServerOption(credentials=redacted)"
}

class PlexAuthorizedAccount internal constructor(
    val userId: String,
    val username: String,
    val servers: List<PlexServerOption>,
    internal val clientId: String,
) {
    override fun toString() = "PlexAuthorizedAccount(credentials=redacted)"
}

class PlexServerAccess internal constructor(
    val baseUrl: HttpUrl,
    val serverId: String,
    val serverName: String,
    val userId: String,
    val username: String,
    val clientId: String,
    val serverToken: String,
) {
    override fun toString() = "PlexServerAccess(credentials=redacted)"
}

class PlexAuthorizationApi(private val transport: HttpTransport) {
    suspend fun begin(clientId: String): PlexAuthorization {
        val pin = cloud("plex.tv", "pins", clientId, method = HttpMethod.Post,
            query = mapOf("strong" to "true")).objectOrFail()
        val id = pin.number("id")?.takeIf { it > 0 } ?: protocol()
        val code = pin.text("code")?.takeIf { it.isNotBlank() } ?: protocol()
        val query = QueryParameters.of("clientID" to clientId, "code" to code,
            "context[device][product]" to "MaFei").toEncodedString()
        return PlexAuthorization("https://app.plex.tv/auth#?$query", id, clientId,
            (pin.number("expiresIn") ?: 120L).coerceIn(1, 900) * 1000)
    }

    suspend fun awaitAccount(challenge: PlexAuthorization): PlexAuthorizedAccount {
        val remaining = challenge.lifetimeMs - challenge.started.elapsedNow().inWholeMilliseconds
        if (remaining <= 0) throw PlexFailure(PlexFailureKind.Expired)
        return withTimeoutOrNull(remaining) {
            var backoff = 1.seconds
            var cloudToken: String? = null
            while (cloudToken == null) {
                currentCoroutineContext().ensureActive()
                try {
                    cloudToken = cloud("plex.tv", "pins/${challenge.pinId}", challenge.clientId)
                        .objectOrFail().text("authToken")?.takeIf { it.isNotBlank() }
                } catch (failure: PlexFailure) {
                    if (failure.kind == PlexFailureKind.Missing || failure.kind == PlexFailureKind.Expired) {
                        throw PlexFailure(PlexFailureKind.Expired)
                    }
                    if (failure.kind !in setOf(PlexFailureKind.Network, PlexFailureKind.Timeout,
                            PlexFailureKind.Server, PlexFailureKind.RateLimit)) throw failure
                }
                if (cloudToken == null) {
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(5.seconds)
                }
            }
            val user = cloud("plex.tv", "user", challenge.clientId, cloudToken).objectOrFail()
            val userId = user.text("id")?.takeIf { it.isNotBlank() } ?: protocol()
            val username = user.text("username")?.takeIf { it.isNotBlank() }
                ?: user.text("title")?.takeIf { it.isNotBlank() } ?: userId
            val resources = try {
                resources("clients.plex.tv", challenge.clientId, cloudToken)
            } catch (failure: PlexFailure) {
                if (failure.kind !in setOf(PlexFailureKind.Network, PlexFailureKind.Timeout, PlexFailureKind.Server)) throw failure
                resources("plex.tv", challenge.clientId, cloudToken)
            }
            val servers = resources.mapNotNull { entry ->
                val row = entry as? JsonObject ?: return@mapNotNull null
                if ("server" !in row.text("provides").orEmpty().split(',')) return@mapNotNull null
                val id = row.text("clientIdentifier")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val serverToken = row.text("accessToken")?.takeIf { validToken(it) } ?: return@mapNotNull null
                val endpoints = row.rows("connections").sortedBy { connection ->
                    // Prefer local HTTPS, then remote HTTPS, then HTTP and relay.
                    (if (connection.text("relay") in listOf("true", "1")) 8 else 0) +
                        (if (connection.text("protocol") == "https") 0 else 4) +
                        (if (connection.text("local") in listOf("true", "1")) 0 else 1)
                }.mapNotNull { connection ->
                    val raw = connection.text("uri") ?: return@mapNotNull null
                    HttpUrl.parseOrNull(raw)?.takeIf {
                        it.queryParameters.size == 0 && it.fragment == null &&
                            it.pathSegments.none { part -> part == "." || part == ".." || '/' in part || '\\' in part }
                    }
                }.distinct().take(16)
                if (endpoints.isEmpty()) null else PlexServerOption(id, row.text("name") ?: "Plex", serverToken, endpoints)
            }.distinctBy { it.id }
            PlexAuthorizedAccount(userId, username, servers, challenge.clientId)
        } ?: throw PlexFailure(PlexFailureKind.Expired)
    }

    /** Server identity is checked anonymously before its scoped token is sent. */
    suspend fun connect(account: PlexAuthorizedAccount, serverId: String): PlexServerAccess {
        val server = account.servers.firstOrNull { it.id == serverId } ?: protocol()
        var lastFailure: PlexFailure? = null
        for (endpoint in server.endpoints) {
            currentCoroutineContext().ensureActive()
            try {
                withTimeout(15_000) {
                    val identity = plexRequest(transport, endpoint.newBuilder().addPathSegment("identity").build(),
                        account.clientId).plexJson().objectOrFail()["MediaContainer"] as? JsonObject ?: protocol()
                    if (identity.text("machineIdentifier") != server.id) protocol()
                    val client = PlexClient(endpoint, account.clientId, server.token, transport)
                    val authenticated = client.container("")
                    if (authenticated.text("machineIdentifier") != server.id) protocol()
                }
                return PlexServerAccess(endpoint, server.id, server.name, account.userId,
                    account.username, account.clientId, server.token)
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                lastFailure = PlexFailure(PlexFailureKind.Timeout)
            } catch (failure: PlexFailure) {
                lastFailure = failure
            }
        }
        throw lastFailure ?: PlexFailure(PlexFailureKind.Network)
    }

    private suspend fun resources(host: String, clientId: String, token: String): JsonArray =
        cloud(host, "resources", clientId, token, query = mapOf("includeHttps" to "1", "includeRelay" to "1"))
            as? JsonArray ?: protocol()

    private suspend fun cloud(
        host: String, path: String, clientId: String, token: String? = null,
        method: HttpMethod = HttpMethod.Get, query: Map<String, String> = emptyMap(),
    ): JsonElement {
        val url = HttpUrl.Builder("https", host).addPathSegments("api/v2/$path")
            .apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        return plexRequest(transport, url, clientId, token, method).plexJson()
    }
}

private fun protocol(): Nothing = throw PlexFailure(PlexFailureKind.Protocol)
private fun validToken(value: String) =
    value.isNotBlank() && value == value.trim() && value.none { it.isISOControl() }
