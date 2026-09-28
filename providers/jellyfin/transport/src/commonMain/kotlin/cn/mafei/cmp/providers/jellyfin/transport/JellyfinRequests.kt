package cn.mafei.cmp.providers.jellyfin.transport

import cn.mafei.cmp.core.http_contract.HttpBody
import cn.mafei.cmp.core.http_contract.HttpHeaders
import cn.mafei.cmp.core.http_contract.HttpMethod
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.core.serialization.MafeiJson
import cn.mafei.cmp.providers.jellyfin.core.JellyfinRequestMode
import cn.mafei.cmp.providers.jellyfin.wire.ImageType
import cn.mafei.cmp.providers.jellyfin.wire.ItemField
import cn.mafei.cmp.core.serialization.formatIsoInstant
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackInfoRequestDto
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackProgressInfoDto
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackStartInfoDto
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackStopInfoDto
import kotlinx.serialization.KSerializer

/**
 * Endpoint bindings for the P0 Jellyfin surface, modeled on the official
 * jellyfin-sdk-kotlin v1.6.8 OpenAPI (Jellyfin 10.10.7).
 *
 * Query parameter names use the official lowerCamel spellings. Requests are
 * tagged with [JellyfinRequestMode]: the three anonymous endpoints
 * (`System/Info/Public`, `Users/Public`, `Users/AuthenticateByName`) must
 * never carry a session token — the client pipeline enforces it.
 */
data object JellyfinRequests {

    // ---------------------------------------------------------- system/auth

    fun publicSystemInfo(base: HttpUrl): HttpRequest =
        get(base, "System/Info/Public", JellyfinRequestMode.ANONYMOUS)

    fun systemInfo(base: HttpUrl): HttpRequest = get(base, "System/Info")

    fun publicUsers(base: HttpUrl): HttpRequest = get(base, "Users/Public", JellyfinRequestMode.ANONYMOUS)

    fun authenticateByName(base: HttpUrl, username: String, password: String): HttpRequest =
        base.newBuilder()
            .addPathSegments("Users/AuthenticateByName")
            .build()
            .let { url ->
                request(HttpMethod.Post, url, JellyfinRequestMode.ANONYMOUS)
                    .body(textBody(AuthenticateByNameBody.serializer(), AuthenticateByNameBody(username, password)))
            }
            .build()

    fun logout(base: HttpUrl): HttpRequest = post(base, "Sessions/Logout")

    // -------------------------------------------------------------- catalog

    /** Official endpoint: GET /UserViews?userId=... */
    fun views(base: HttpUrl, userId: String): HttpRequest =
        get(base, "UserViews") {
            addQueryParameter("userId", userId)
        }

    /** Official endpoint: GET /Items with lowerCamel query parameters. */
    fun items(base: HttpUrl, query: JellyfinItemQuery): HttpRequest =
        get(base, "Items") {
            addQueryParameter("userId", query.userId)
            query.parentId?.let { addQueryParameter("parentId", it) }
            addQueryParameterIfNotBlank("personIds", query.personIds.joinToString(",") { it.trim() })
            addQueryParameter("startIndex", query.startIndex.toString())
            addQueryParameter("limit", query.limit.toString())
            addQueryParameterIfNotBlank("includeItemTypes", query.includeItemTypes.joinToString(",") { it.trim() })
            if (query.fields.isNotEmpty()) addQueryParameter("fields", query.fields.joinToString(",") { it.wireName })
            if (query.sortBy.isNotEmpty()) addQueryParameter("sortBy", query.sortBy.joinToString(",") { it.wireName })
            addQueryParameter("sortOrder", query.sortOrder.wireName)
            query.recursive?.let { addQueryParameter("recursive", it.toString()) }
            addQueryParameterIfNotBlank("searchTerm", query.searchTerm)
            if (query.genres.isNotEmpty()) addQueryParameter("genres", query.genres.joinToString("|"))
            if (query.years.isNotEmpty()) addQueryParameter("years", query.years.joinToString(","))
            if (query.filters.isNotEmpty()) addQueryParameter("filters", query.filters.joinToString(",") { it.wireName })
            addQueryParameter("enableImages", "true")
            addQueryParameter("enableImageTypes", "Primary,Backdrop,Thumb")
            addQueryParameter("imageTypeLimit", query.imageTypeLimit.toString())
        }

    /** Official endpoint: GET /Movies/Recommendations. */
    fun recommendations(
        base: HttpUrl,
        userId: String,
        categoryLimit: Int,
        itemLimit: Int,
        fields: List<ItemField> = emptyList(),
    ): HttpRequest = get(base, "Movies/Recommendations") {
        addQueryParameter("userId", userId)
        addQueryParameter("categoryLimit", categoryLimit.toString())
        addQueryParameter("itemLimit", itemLimit.toString())
        if (fields.isNotEmpty()) addQueryParameter("fields", fields.joinToString(",") { it.wireName })
        addQueryParameter("enableImages", "true")
        addQueryParameter("enableImageTypes", "Primary,Backdrop,Thumb")
        addQueryParameter("imageTypeLimit", "1")
    }

    /** Official endpoint: GET /UserItems/Resume. */
    fun resume(
        base: HttpUrl,
        userId: String,
        startIndex: Int = 0,
        limit: Int,
        includeItemTypes: List<String>,
        fields: List<ItemField> = emptyList(),
    ): HttpRequest = get(base, "UserItems/Resume") {
        addQueryParameter("userId", userId)
        addQueryParameter("startIndex", startIndex.toString())
        addQueryParameter("limit", limit.toString())
        addQueryParameterIfNotBlank("includeItemTypes", includeItemTypes.joinToString(","))
        if (fields.isNotEmpty()) addQueryParameter("fields", fields.joinToString(",") { it.wireName })
        addQueryParameter("enableUserData", "true")
        addQueryParameter("enableImages", "true")
        addQueryParameter("enableImageTypes", "Primary,Backdrop,Thumb")
        addQueryParameter("imageTypeLimit", "1")
    }

    /**
     * Official endpoint: GET /Items/{itemId}/Similar. The 10.10.7 operation
     * returns a bare item array (no query-result envelope).
     */
    fun similar(
        base: HttpUrl,
        userId: String,
        itemId: String,
        limit: Int,
        fields: List<ItemField> = emptyList(),
    ): HttpRequest =
        base.newBuilder()
            .addPathSegments("Items")
            .addPathSegment(itemId)
            .addPathSegments("Similar")
            .apply {
                addQueryParameter("userId", userId)
                addQueryParameter("limit", limit.toString())
                if (fields.isNotEmpty()) addQueryParameter("fields", fields.joinToString(",") { it.wireName })
                addQueryParameter("enableUserData", "true")
                addQueryParameter("enableImages", "true")
                addQueryParameter("enableImageTypes", "Primary,Backdrop,Thumb")
                addQueryParameter("imageTypeLimit", "1")
            }
            .build()
            .let { url -> request(HttpMethod.Get, url).build() }

    // ---------------------------------------------------------- user library

    /** Official endpoint: POST /Users/{userId}/FavoriteItems/{itemId}. */
    fun markFavorite(base: HttpUrl, userId: String, itemId: String): HttpRequest =
        userItemWrite(base, userId, "FavoriteItems", itemId, HttpMethod.Post)

    /** Official endpoint: DELETE /Users/{userId}/FavoriteItems/{itemId}. */
    fun unmarkFavorite(base: HttpUrl, userId: String, itemId: String): HttpRequest =
        userItemWrite(base, userId, "FavoriteItems", itemId, HttpMethod.Delete)

    /** Official endpoint: POST /Users/{userId}/PlayedItems/{itemId}. */
    fun markPlayed(base: HttpUrl, userId: String, itemId: String): HttpRequest =
        userItemWrite(base, userId, "PlayedItems", itemId, HttpMethod.Post)

    /** Official endpoint: DELETE /Users/{userId}/PlayedItems/{itemId}. */
    fun unmarkPlayed(base: HttpUrl, userId: String, itemId: String): HttpRequest =
        userItemWrite(base, userId, "PlayedItems", itemId, HttpMethod.Delete)

    /** Official endpoint: GET /Users/{userId} (self-readable; exposes the user policy). */
    fun user(base: HttpUrl, userId: String): HttpRequest =
        base.newBuilder()
            .addPathSegments("Users")
            .addPathSegment(userId)
            .build()
            .let { url -> request(HttpMethod.Get, url).build() }

    /**
     * Official endpoint: DELETE /Items?ids=... — administrator-scoped media
     * deletion. The server rejects non-admin callers with 401/403; the client
     * only uses its policy probe to hide the affordance.
     */
    fun deleteItems(base: HttpUrl, itemIds: Collection<String>): HttpRequest =
        base.newBuilder()
            .addPathSegments("Items")
            .apply { addQueryParameter("ids", itemIds.joinToString(",") { it.trim() }) }
            .build()
            .let { url -> request(HttpMethod.Delete, url).build() }

    private fun userItemWrite(
        base: HttpUrl,
        userId: String,
        segment: String,
        itemId: String,
        method: HttpMethod,
    ): HttpRequest =
        base.newBuilder()
            .addPathSegments("Users")
            .addPathSegment(userId)
            .addPathSegments(segment)
            .addPathSegment(itemId)
            .build()
            .let { url -> request(method, url).build() }

    /** Official endpoint: GET /Shows/NextUp. */
    fun nextUp(
        base: HttpUrl,
        userId: String,
        limit: Int,
        fields: List<ItemField> = emptyList(),
        seriesId: String? = null,
        disableFirstEpisode: Boolean = false,
        enableResumable: Boolean = false,
        enableRewatching: Boolean = false,
        nextUpDateCutoffEpochMs: Long? = null,
    ): HttpRequest = get(base, "Shows/NextUp") {
        addQueryParameter("userId", userId)
        addQueryParameter("startIndex", "0")
        addQueryParameter("limit", limit.toString())
        seriesId?.trim()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("seriesId", it) }
        if (fields.isNotEmpty()) addQueryParameter("fields", fields.joinToString(",") { it.wireName })
        addQueryParameter("enableImages", "true")
        addQueryParameter("enableImageTypes", "Primary,Backdrop,Thumb")
        addQueryParameter("enableUserData", "true")
        addQueryParameter("imageTypeLimit", "1")
        addQueryParameter("disableFirstEpisode", disableFirstEpisode.toString())
        addQueryParameter("enableResumable", enableResumable.toString())
        addQueryParameter("enableRewatching", enableRewatching.toString())
        nextUpDateCutoffEpochMs?.takeIf { it > 0L }?.let {
            addQueryParameter("nextUpDateCutoff", formatIsoInstant(it))
        }
    }

    /** Official endpoint: GET /Items/Latest. */
    fun latest(
        base: HttpUrl,
        userId: String,
        parentId: String?,
        limit: Int,
        includeItemTypes: List<String>,
        fields: List<ItemField> = emptyList(),
    ): HttpRequest = get(base, "Items/Latest") {
        addQueryParameter("userId", userId)
        parentId?.let { addQueryParameter("parentId", it) }
        addQueryParameter("limit", limit.toString())
        addQueryParameterIfNotBlank("includeItemTypes", includeItemTypes.joinToString(","))
        if (fields.isNotEmpty()) addQueryParameter("fields", fields.joinToString(",") { it.wireName })
        addQueryParameter("enableImages", "true")
        addQueryParameter("enableImageTypes", "Primary,Backdrop,Thumb")
        addQueryParameter("enableUserData", "true")
        addQueryParameter("imageTypeLimit", "1")
        addQueryParameter("groupItems", "true")
    }

    /**
     * Official endpoint: GET /Items/{itemId}?userId=... The OpenAPI operation
     * has no `fields` parameter, so none is sent.
     */
    fun itemById(base: HttpUrl, userId: String, itemId: String): HttpRequest =
        base.newBuilder()
            .addPathSegments("Items")
            .addPathSegment(itemId)
            .apply { addQueryParameter("userId", userId) }
            .build()
            .let { url -> request(HttpMethod.Get, url).build() }

    /** Official endpoint: GET /Videos/{itemId}/Trickplay/{width}/{index}.jpg. */
    fun trickplayTile(
        base: HttpUrl,
        itemId: String,
        width: Int,
        index: Int,
        mediaSourceId: String? = null,
    ): HttpRequest = base.newBuilder()
        .addPathSegments("Videos")
        .addPathSegment(itemId)
        .addPathSegments("Trickplay")
        .addPathSegment(width.toString())
        .addPathSegment("$index.jpg")
        .apply { mediaSourceId?.takeIf { it.isNotBlank() }?.let { addQueryParameter("MediaSourceId", it) } }
        .build()
        .let { url -> request(HttpMethod.Get, url).build() }

    /**
     * Jellyfin's person-specific lookup. Some 10.11 servers do not complete
     * GET /Items/{personId}, while this endpoint remains the authoritative
     * person detail route.
     */
    fun personByName(base: HttpUrl, name: String): HttpRequest =
        base.newBuilder()
            .addPathSegments("Persons")
            .addPathSegment(name)
            .build()
            .let { url -> request(HttpMethod.Get, url).build() }

    /** Official endpoint: GET /Shows/{seriesId}/Seasons?userId=... */
    fun seasons(base: HttpUrl, userId: String, seriesId: String, fields: List<ItemField> = emptyList()): HttpRequest =
        base.newBuilder()
            .addPathSegments("Shows")
            .addPathSegment(seriesId)
            .addPathSegments("Seasons")
            .apply {
                addQueryParameter("userId", userId)
                if (fields.isNotEmpty()) addQueryParameter("fields", fields.joinToString(",") { it.wireName })
            }
            .build()
            .let { url -> request(HttpMethod.Get, url).build() }

    /** Official endpoint: GET /Shows/{seriesId}/Episodes?userId=...&seasonId=... */
    fun episodes(
        base: HttpUrl,
        userId: String,
        seriesId: String,
        seasonId: String? = null,
        startIndex: Int = 0,
        limit: Int = 200,
        fields: List<ItemField> = emptyList(),
    ): HttpRequest =
        base.newBuilder()
            .addPathSegments("Shows")
            .addPathSegment(seriesId)
            .addPathSegments("Episodes")
            .apply {
                addQueryParameter("userId", userId)
                seasonId?.let { addQueryParameter("seasonId", it) }
                addQueryParameter("startIndex", startIndex.toString())
                addQueryParameter("limit", limit.toString())
                if (fields.isNotEmpty()) addQueryParameter("fields", fields.joinToString(",") { it.wireName })
            }
            .build()
            .let { url -> request(HttpMethod.Get, url).build() }

    // ------------------------------------------------------------- playback

    fun playbackInfo(
        base: HttpUrl,
        userId: String,
        itemId: String,
        body: PlaybackInfoRequestDto = PlaybackInfoRequestDto(),
    ): HttpRequest =
        base.newBuilder()
            .addPathSegments("Items")
            .addPathSegment(itemId)
            .addPathSegments("PlaybackInfo")
            .apply {
                addQueryParameter("userId", userId)
                addQueryParameter("autoOpenLiveStream", (body.autoOpenLiveStream ?: false).toString())
                body.maxStreamingBitrate?.let { addQueryParameter("maxStreamingBitrate", it.toString()) }
            }
            .build()
            .let { url ->
                request(HttpMethod.Post, url)
                    .body(textBody(PlaybackInfoRequestDto.serializer(), body))
            }
            .build()

    fun playingStart(base: HttpUrl, info: PlaybackStartInfoDto): HttpRequest =
        post(base, "Sessions/Playing") { body(textBody(PlaybackStartInfoDto.serializer(), info)) }

    fun playingProgress(base: HttpUrl, info: PlaybackProgressInfoDto): HttpRequest =
        post(base, "Sessions/Playing/Progress") { body(textBody(PlaybackProgressInfoDto.serializer(), info)) }

    fun playingStopped(base: HttpUrl, info: PlaybackStopInfoDto): HttpRequest =
        post(base, "Sessions/Playing/Stopped") { body(textBody(PlaybackStopInfoDto.serializer(), info)) }

    // ------------------------------------------------------- resource URLs

    /**
     * Direct (static) stream URL. `token` becomes the `api_key` query
     * parameter — required by servers with anonymous access disabled.
     */
    fun directStreamUrl(
        base: HttpUrl,
        itemId: String,
        mediaSourceId: String? = null,
        container: String? = null,
        playSessionId: String? = null,
        token: String? = null,
        audio: Boolean = false,
    ): HttpUrl =
        base.newBuilder()
            .addPathSegment(if (audio) "Audio" else "Videos")
            .addPathSegment(itemId)
            .apply {
                if (container.isNullOrBlank()) {
                    addPathSegments("stream")
                } else {
                    addPathSegments("stream.$container")
                }
            }
            .apply {
                addQueryParameter("static", "true")
                mediaSourceId?.let { addQueryParameter("mediaSourceId", it) }
                playSessionId?.let { addQueryParameter("playSessionId", it) }
                if (!token.isNullOrBlank()) addQueryParameter("api_key", token)
            }
            .build()

    /**
     * Absolutizes a server-provided relative or absolute transcoding URL
     * against the configured base (including any server base path such as
     * `/stable` — the API subpath is never dropped), enforces same-origin,
     * and appends `api_key` when absent.
     */
    fun absolutizeTranscodingUrl(base: HttpUrl, transcodingUrl: String, token: String? = null): HttpUrl {
        val trimmed = transcodingUrl.trim()
        require(trimmed.isNotEmpty()) { "TranscodingUrl must not be empty" }

        val resolved: HttpUrl = when {
            trimmed.contains("://") -> HttpUrl.parseOrNull(trimmed)
                ?: throw IllegalArgumentException("Unparseable transcoding URL: $transcodingUrl")
            trimmed.startsWith("//") -> HttpUrl.parseOrNull(base.scheme + ":" + trimmed)
                ?: throw IllegalArgumentException("Unparseable protocol-relative transcoding URL: $transcodingUrl")
            else -> {
                // Join with the server base path so reverse-proxy subpaths survive.
                val origin = base.scheme + "://" + hostWithPort(base)
                val basePath = base.encodedPath.trimEnd('/') // "" or "/stable"
                val candidate = if (trimmed.startsWith("/")) {
                    if (basePath.isNotEmpty() && (trimmed == basePath || trimmed.startsWith("$basePath/"))) {
                        trimmed // server already included the base path
                    } else {
                        basePath + trimmed
                    }
                } else {
                    // The configured base URL is the server API root. Treat it
                    // as a directory even when it has no trailing slash, so
                    // a reverse-proxy prefix such as `/stable` is retained
                    // for plain relative targets (`videos/master.m3u8`).
                    val baseDirectory = base.encodedPath.trimEnd('/')
                    if (baseDirectory.isEmpty()) "/$trimmed" else "$baseDirectory/$trimmed"
                }
                HttpUrl.parseOrNull("$origin$candidate")
                    ?: throw IllegalArgumentException("Unresolvable transcoding URL: $transcodingUrl")
            }
        }

        require(sameOrigin(base, resolved)) {
            "Refusing cross-origin TranscodingUrl: ${resolved.toEncodedString()} (base $base)"
        }

        if (token.isNullOrBlank() || resolved.queryParameters.contains("api_key")) return resolved
        return resolved.newBuilder().addQueryParameter("api_key", token).build()
    }

    fun imageUrl(
        base: HttpUrl,
        itemId: String,
        imageType: ImageType = ImageType.Primary,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        quality: Int? = null,
        tag: String? = null,
        token: String? = null,
    ): HttpUrl =
        base.newBuilder()
            .addPathSegments("Items")
            .addPathSegment(itemId)
            .addPathSegments("Images")
            .addPathSegment(imageType.name)
            .apply {
                maxWidth?.let { addQueryParameter("maxWidth", it.toString()) }
                maxHeight?.let { addQueryParameter("maxHeight", it.toString()) }
                quality?.let { addQueryParameter("quality", it.toString()) }
                tag?.let { addQueryParameter("tag", it) }
                if (!token.isNullOrBlank()) addQueryParameter("api_key", token)
            }
            .build()

    /** Official endpoint: GET /UserImage?userId=...&imageType=...&tag=... */
    fun userImageUrl(
        base: HttpUrl,
        userId: String,
        imageType: ImageType = ImageType.Primary,
        tag: String? = null,
        token: String? = null,
    ): HttpUrl =
        base.newBuilder()
            .addPathSegments("UserImage")
            .apply {
                addQueryParameter("userId", userId)
                addQueryParameter("imageType", imageType.name)
                tag?.let { addQueryParameter("tag", it) }
                if (!token.isNullOrBlank()) addQueryParameter("api_key", token)
            }
            .build()

    /** Absolutizes a server-provided track DeliveryUrl with the same rules as [absolutizeTranscodingUrl]. */
    fun absolutizeDeliveryUrl(base: HttpUrl, deliveryUrl: String, token: String? = null): HttpUrl =
        absolutizeTranscodingUrl(base, deliveryUrl, token)

    // -------------------------------------------------------------- helpers

    private fun get(base: HttpUrl, path: String, mode: String = JellyfinRequestMode.AUTHENTICATED, query: cn.mafei.cmp.core.http_contract.HttpUrl.Builder.() -> Unit = {}): HttpRequest =
        base.newBuilder()
            .addPathSegments(path)
            .apply(query)
            .build()
            .let { url -> request(HttpMethod.Get, url, mode) }
            .build()

    private fun post(base: HttpUrl, path: String, mode: String = JellyfinRequestMode.AUTHENTICATED, configure: cn.mafei.cmp.core.http_contract.HttpRequest.Builder.() -> Unit = {}): HttpRequest =
        base.newBuilder()
            .addPathSegments(path)
            .build()
            .let { url -> request(HttpMethod.Post, url, mode).apply(configure) }
            .build()

    private fun request(method: HttpMethod, url: HttpUrl, mode: String = JellyfinRequestMode.AUTHENTICATED): cn.mafei.cmp.core.http_contract.HttpRequest.Builder =
        HttpRequest.builder(method, url).attribute(JellyfinRequestMode.ATTRIBUTE, mode)

    private fun <T> textBody(serializer: KSerializer<T>, value: T): HttpBody.Text =
        HttpBody.Text(MafeiJson.Default.encodeToString(serializer, value), contentType = HttpHeaders.CONTENT_TYPE_JSON)

    private fun hostWithPort(url: HttpUrl): String = buildString {
        if (url.host.contains(':')) append('[').append(url.host).append(']') else append(url.host)
        if (url.port != HttpUrl.defaultPort(url.scheme)) append(':').append(url.port)
    }

    private fun sameOrigin(a: HttpUrl, b: HttpUrl): Boolean =
        a.scheme == b.scheme && a.host == b.host && a.port == b.port
}

/** Body of POST /Users/AuthenticateByName (field names are protocol-frozen, camelCase per OpenAPI). */
@kotlinx.serialization.Serializable
private data class AuthenticateByNameBody(
    val Username: String,
    val Pw: String,
)
