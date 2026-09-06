package cn.mafei.cmp.providers.jellyfin.transport

import cn.mafei.cmp.core.http_contract.HttpBody
import cn.mafei.cmp.core.http_contract.HttpHeaders
import cn.mafei.cmp.core.http_contract.HttpMethod
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpRedirects
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.core.serialization.MafeiJson
import cn.mafei.cmp.providers.jellyfin.wire.ItemField
import cn.mafei.cmp.providers.jellyfin.wire.ImageType
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackInfoRequestDto
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackProgressInfoDto
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackStartInfoDto
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackStopInfoDto
import kotlinx.serialization.KSerializer

/**
 * Endpoint bindings for the P0 Jellyfin surface: every function builds a
 * ready-to-execute [HttpRequest] (or a resource [HttpUrl]) against a base
 * URL. Authentication headers are NOT attached here — the client pipeline
 * injects them so device info and tokens stay in one place.
 */
data object JellyfinRequests {

    // ---------------------------------------------------------- system/auth

    fun publicSystemInfo(base: HttpUrl): HttpRequest =
        base.newBuilder()
            .addPathSegments("System/Info/Public")
            .build()
            .let { HttpRequest.builder(HttpMethod.Get, it) }
            .build()

    fun systemInfo(base: HttpUrl): HttpRequest =
        base.newBuilder()
            .addPathSegments("System/Info")
            .build()
            .let { HttpRequest.builder(HttpMethod.Get, it) }
            .build()

    fun publicUsers(base: HttpUrl): HttpRequest =
        base.newBuilder()
            .addPathSegments("Users/Public")
            .build()
            .let { HttpRequest.builder(HttpMethod.Get, it) }
            .build()

    fun authenticateByName(base: HttpUrl, username: String, password: String): HttpRequest =
        base.newBuilder()
            .addPathSegments("Users/AuthenticateByName")
            .build()
            .let { url ->
                HttpRequest.builder(HttpMethod.Post, url)
                    .body(
                        HttpBody.Text(
                            MafeiJson.Default.encodeToString(AuthenticateByNameBody.serializer(), AuthenticateByNameBody(username, password)),
                            contentType = HttpHeaders.CONTENT_TYPE_JSON,
                        ),
                    )
            }
            .build()

    fun logout(base: HttpUrl): HttpRequest =
        base.newBuilder()
            .addPathSegments("Sessions/Logout")
            .build()
            .let { HttpRequest.builder(HttpMethod.Post, it) }
            .build()

    // -------------------------------------------------------------- catalog

    fun views(base: HttpUrl, userId: String): HttpRequest =
        base.newBuilder()
            .addPathSegments("Shows")
            .addPathSegment(userId)
            .addPathSegments("Views")
            .build()
            .let { HttpRequest.builder(HttpMethod.Get, it) }
            .build()

    fun items(base: HttpUrl, query: JellyfinItemQuery): HttpRequest =
        base.newBuilder()
            .addPathSegments("Items")
            .apply {
                addQueryParameter("UserId", query.userId)
                query.parentId?.let { addQueryParameter("ParentId", it) }
                addQueryParameter("StartIndex", query.startIndex.toString())
                addQueryParameter("Limit", query.limit.toString())
                addQueryParameterIfNotBlank("IncludeItemTypes", query.includeItemTypes.joinToString(",") { it.trim() })
                if (query.fields.isNotEmpty()) addQueryParameter("Fields", query.fields.joinToString(",") { it.wireName })
                if (query.sortBy.isNotEmpty()) addQueryParameter("SortBy", query.sortBy.joinToString(",") { it.wireName })
                addQueryParameter("SortOrder", query.sortOrder.wireName)
                query.recursive?.let { addQueryParameter("Recursive", it.toString()) }
                addQueryParameterIfNotBlank("SearchTerm", query.searchTerm)
                if (query.genres.isNotEmpty()) addQueryParameter("Genres", query.genres.joinToString("|"))
                if (query.years.isNotEmpty()) addQueryParameter("Years", query.years.joinToString(","))
                if (query.filters.isNotEmpty()) addQueryParameter("Filters", query.filters.joinToString(",") { it.wireName })
                addQueryParameter("ImageTypeLimit", query.imageTypeLimit.toString())
            }
            .build()
            .let { HttpRequest.builder(HttpMethod.Get, it) }
            .build()

    fun itemById(base: HttpUrl, userId: String, itemId: String, fields: List<ItemField> = emptyList()): HttpRequest =
        base.newBuilder()
            .addPathSegments("Users")
            .addPathSegment(userId)
            .addPathSegments("Items")
            .addPathSegment(itemId)
            .apply {
                if (fields.isNotEmpty()) addQueryParameter("Fields", fields.joinToString(",") { it.wireName })
            }
            .build()
            .let { HttpRequest.builder(HttpMethod.Get, it) }
            .build()

    fun seasons(base: HttpUrl, userId: String, seriesId: String, fields: List<ItemField> = emptyList()): HttpRequest =
        base.newBuilder()
            .addPathSegments("Shows")
            .addPathSegment(seriesId)
            .addPathSegments("Seasons")
            .apply {
                addQueryParameter("UserId", userId)
                if (fields.isNotEmpty()) addQueryParameter("Fields", fields.joinToString(",") { it.wireName })
            }
            .build()
            .let { HttpRequest.builder(HttpMethod.Get, it) }
            .build()

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
                addQueryParameter("UserId", userId)
                seasonId?.let { addQueryParameter("SeasonId", it) }
                addQueryParameter("StartIndex", startIndex.toString())
                addQueryParameter("Limit", limit.toString())
                if (fields.isNotEmpty()) addQueryParameter("Fields", fields.joinToString(",") { it.wireName })
            }
            .build()
            .let { HttpRequest.builder(HttpMethod.Get, it) }
            .build()

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
                addQueryParameter("UserId", userId)
                addQueryParameter("AutoOpenLiveStream", (body.autoOpenLiveStream ?: false).toString())
                body.maxStreamingBitrate?.let { addQueryParameter("MaxStreamingBitrate", it.toString()) }
            }
            .build()
            .let { url ->
                HttpRequest.builder(HttpMethod.Post, url)
                    .body(
                        HttpBody.Text(
                            MafeiJson.Default.encodeToString(PlaybackInfoRequestDto.serializer(), body),
                            contentType = HttpHeaders.CONTENT_TYPE_JSON,
                        ),
                    )
            }
            .build()

    fun playingStart(base: HttpUrl, info: PlaybackStartInfoDto): HttpRequest =
        postSessionsPlaying(base, "Playing", PlaybackStartInfoDto.serializer(), info)

    fun playingProgress(base: HttpUrl, info: PlaybackProgressInfoDto): HttpRequest =
        postSessionsPlaying(base, "Playing/Progress", PlaybackProgressInfoDto.serializer(), info)

    fun playingStopped(base: HttpUrl, info: PlaybackStopInfoDto): HttpRequest =
        postSessionsPlaying(base, "Playing/Stopped", PlaybackStopInfoDto.serializer(), info)

    private fun <T> postSessionsPlaying(
        base: HttpUrl,
        path: String,
        serializer: KSerializer<T>,
        body: T,
    ): HttpRequest =
        base.newBuilder()
            .addPathSegments("Sessions")
            .addPathSegments(path)
            .build()
            .let { url ->
                HttpRequest.builder(HttpMethod.Post, url)
                    .body(
                        HttpBody.Text(
                            MafeiJson.Default.encodeToString(serializer, body),
                            contentType = HttpHeaders.CONTENT_TYPE_JSON,
                        ),
                    )
            }
            .build()

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
                addQueryParameter("Static", "true")
                mediaSourceId?.let { addQueryParameter("MediaSourceId", it) }
                playSessionId?.let { addQueryParameter("PlaySessionId", it) }
                if (!token.isNullOrBlank()) addQueryParameter("api_key", token)
            }
            .build()

    /**
     * Absolutizes a server-provided relative transcoding URL (usually HLS)
     * and appends the `api_key` parameter when absent.
     */
    fun absolutizeTranscodingUrl(base: HttpUrl, transcodingUrl: String, token: String? = null): HttpUrl {
        // RFC-style resolution through the frozen GLM-01 contract; the core
        // HttpUrl itself has no resolve() to keep that API minimal.
        val resolved = HttpRedirects.resolveLocation(base, transcodingUrl)
            ?: throw IllegalArgumentException("Unresolvable transcoding URL: $transcodingUrl")
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

    fun userImageUrl(
        base: HttpUrl,
        userId: String,
        imageType: ImageType = ImageType.Primary,
        tag: String? = null,
        token: String? = null,
    ): HttpUrl =
        base.newBuilder()
            .addPathSegments("Users")
            .addPathSegment(userId)
            .addPathSegments("Images")
            .addPathSegment(imageType.name)
            .apply {
                tag?.let { addQueryParameter("tag", it) }
                if (!token.isNullOrBlank()) addQueryParameter("api_key", token)
            }
            .build()
}

/** Body of POST /Users/AuthenticateByName (Jellyfin field name `Pw` is protocol-frozen). */
@kotlinx.serialization.Serializable
private data class AuthenticateByNameBody(
    val Username: String,
    val Pw: String,
)
