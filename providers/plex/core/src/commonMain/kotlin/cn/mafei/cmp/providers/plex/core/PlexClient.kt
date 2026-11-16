package cn.mafei.cmp.providers.plex.core

import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.core.serialization.MafeiJson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

enum class PlexFailureKind { Authentication, Authorization, Missing, Expired, Timeout, Network, RateLimit, Server, Protocol }

/** Deliberately retains neither a response body nor a transport exception. */
class PlexFailure(val kind: PlexFailureKind) : Exception("Plex ${kind.name}")

fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
fun JsonObject.number(key: String): Long? = text(key)?.toLongOrNull()
fun JsonObject.rows(key: String): List<JsonObject> =
    (get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

internal suspend fun plexRequest(
    transport: HttpTransport,
    url: HttpUrl,
    clientId: String,
    token: String? = null,
    method: HttpMethod = HttpMethod.Get,
    headers: Map<String, String> = emptyMap(),
): HttpResponse {
    val request = HttpRequest.builder(method, url)
        .header("Accept", "application/json")
        .header("X-Plex-Product", "MaFei")
        .header("X-Plex-Version", "2.0.0")
        .header("X-Plex-Client-Identifier", clientId)
        .header("X-Plex-Device-Name", "MaFei")
        .redirectPolicy(RedirectPolicy.FollowSameOrigin)
        .timeoutMillis(12_000)
        .readPolicy(BodyReadPolicy.BufferedAtMost(8L * 1024 * 1024))
    token?.let { request.header("X-Plex-Token", it) }
    headers.forEach { (key, value) -> request.header(key, value) }
    val response = try {
        transport.execute(request.build())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: HttpTimeoutException) {
        throw PlexFailure(PlexFailureKind.Timeout)
    } catch (_: HttpTransportException) {
        throw PlexFailure(PlexFailureKind.Network)
    } catch (_: Exception) {
        throw PlexFailure(PlexFailureKind.Protocol)
    }
    if (!response.isSuccessful) throw PlexFailure(when (response.statusCode) {
        401 -> PlexFailureKind.Authentication
        403 -> PlexFailureKind.Authorization
        404 -> PlexFailureKind.Missing
        410 -> PlexFailureKind.Expired
        429 -> PlexFailureKind.RateLimit
        in 500..599 -> PlexFailureKind.Server
        else -> PlexFailureKind.Protocol
    })
    return response
}

internal fun HttpResponse.plexJson(): JsonElement = try {
    MafeiJson.Default.parseToJsonElement(bodyText())
} catch (_: Exception) {
    throw PlexFailure(PlexFailureKind.Protocol)
}

internal fun JsonElement.objectOrFail(): JsonObject =
    this as? JsonObject ?: throw PlexFailure(PlexFailureKind.Protocol)

/** One selected PMS endpoint. Cloud account tokens never enter this client. */
class PlexClient(
    val baseUrl: HttpUrl,
    val clientId: String,
    private val token: String,
    private val transport: HttpTransport,
) {
    init {
        require(baseUrl.queryParameters.size == 0 && baseUrl.fragment == null)
        require(baseUrl.pathSegments.none { it == "." || it == ".." || '/' in it || '\\' in it })
        require(token.isNotBlank() && token == token.trim() && token.none { it.isISOControl() })
        require(clientId.isNotBlank() && clientId.none { it.isISOControl() })
    }

    fun url(path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val builder = baseUrl.newBuilder()
        if (path.startsWith(":/")) {
            // PMS reserves the literal /:/ prefix for playback and watch-state commands.
            builder.encodedPath(baseUrl.encodedPath.trimEnd('/') + "/:")
                .addPathSegments(path.removePrefix(":/"))
        } else {
            builder.addPathSegments(path)
        }
        return builder.apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
    }

    suspend fun container(path: String, query: Map<String, String> = emptyMap()): JsonObject {
        val root = plexRequest(transport, url(path, query), clientId, token).plexJson().objectOrFail()
        return root["MediaContainer"] as? JsonObject ?: throw PlexFailure(PlexFailureKind.Protocol)
    }

    suspend fun write(path: String, query: Map<String, String>, method: HttpMethod = HttpMethod.Get, session: String? = null) {
        plexRequest(transport, url(path, query), clientId, token, method,
            session?.let { mapOf("X-Plex-Session-Identifier" to it) }.orEmpty())
    }

    /** Reject arbitrary remote URLs, traversal and local filesystem paths. */
    fun resourceUrl(key: String?): HttpUrl? {
        if (key.isNullOrBlank() || key != key.trim() || key.any { it.isISOControl() || it == '\\' } ||
            key.startsWith("//") || '#' in key
        ) return null
        val parsed = when {
            key.startsWith("/") -> HttpUrl.parseOrNull(
                baseUrl.newBuilder().encodedPath("/").build().toEncodedString().trimEnd('/') + key,
            )
            else -> HttpUrl.parseOrNull(key)
        } ?: return null
        // PMS keys are relative to its API root even when a proxy mounts it below /plex.
        val absolute = if (key.startsWith("/") &&
            parsed.pathSegments.take(baseUrl.pathSegments.size) != baseUrl.pathSegments
        ) parsed.newBuilder().encodedPath(baseUrl.encodedPath.trimEnd('/') + parsed.encodedPath).build()
        else parsed
        if (!samePlexOrigin(baseUrl, absolute) ||
            absolute.pathSegments.any { it == "." || it == ".." || '/' in it || '\\' in it } ||
            absolute.queryParameters.names.any { it.equals("X-Plex-Token", true) } ||
            absolute.pathSegments.take(baseUrl.pathSegments.size) != baseUrl.pathSegments
        ) return null
        return absolute
    }

    fun playbackHeaders(session: String): Map<String, String> = mapOf(
        "X-Plex-Token" to token,
        "X-Plex-Client-Identifier" to clientId,
        "X-Plex-Session-Identifier" to session,
    )

    override fun toString() = "PlexClient(credentials=redacted)"
}

internal fun samePlexOrigin(a: HttpUrl, b: HttpUrl) =
    a.scheme == b.scheme && a.host == b.host && a.port == b.port
