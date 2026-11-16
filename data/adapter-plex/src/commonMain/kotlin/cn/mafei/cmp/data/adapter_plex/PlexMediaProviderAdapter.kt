package cn.mafei.cmp.data.adapter_plex

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.HttpMethod
import cn.mafei.cmp.data.adapter_contract.*
import cn.mafei.cmp.providers.plex.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.random.Random

/** Plex video libraries with original-file playback; unsupported surfaces stay hidden. */
class PlexMediaProviderAdapter(
    private val client: PlexClient,
    override val serverId: ServerId,
    override val accountId: AccountId,
) : MediaProviderAdapter {
    override val provider = ProviderKind.Plex
    override val capabilities = AdapterCapabilities(
        search = true, watchedFilter = true, watchedEdit = true, playbackReporting = true,
    )
    private val mapper = PlexMapping(client, serverId)
    private val playbackLock = Mutex()
    private val playbacks = mutableListOf<Prepared>()

    override suspend fun collections(request: PageRequest): Page<MediaCollection> = mapped {
        if (request.cursor != null || !request.filter.isEmpty || request.sort != null) {
            throw ClientConfigError("Plex library listing does not accept filters or cursors")
        }
        Page(client.container("library/sections").rows("Directory").mapNotNull { row ->
            val type = when (row.text("type")) {
                "movie" -> MediaType.Movie
                "show" -> MediaType.Series
                else -> return@mapNotNull null
            }
            val id = row.text("key")?.takeIf(::plexId) ?: return@mapNotNull null
            MediaCollection(CollectionId(id), serverId, provider, row.text("title") ?: "Plex",
                setOf(type), mapper.image(row.text("thumb")))
        })
    }

    override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> = mapped {
        val path = collectionId?.let { "library/sections/${checked(it.value)}/all" } ?: "library/all"
        val query = listingQuery(request)
        if (request.filter.mediaTypes.size > 1) PlexCatalogPages(client, mapper).combined(path, request, query)
        else page(path, request, query)
    }

    override suspend fun search(term: String, request: PageRequest): Page<MediaItem> = mapped {
        if (term.isBlank()) return@mapped Page.empty()
        if (request.cursor != null || !request.filter.copy(mediaTypes = emptySet()).isEmpty || request.sort != null) {
            throw ClientConfigError("Plex search returns a single limited result window")
        }
        val container = client.container("library/search", mapOf(
            "query" to term, "searchTypes" to "movies,tv,otherVideos",
            "limit" to request.pageSize.toString(), "X-Plex-Container-Size" to request.pageSize.toString(),
        ))
        Page(container.rows("SearchResult").mapNotNull { it["Metadata"] as? JsonObject }
            .mapNotNull(mapper::item).filter { matchesType(it, request.filter) }
            .distinctBy { it.id }.take(request.pageSize))
    }

    override suspend fun item(id: MediaId): MediaItem = mapped {
        mapper.item(detail(id)) ?: throw ResourceNotFoundError()
    }

    override suspend fun seasons(seriesId: MediaId): Page<MediaItem> = mapped {
        Page(client.container("library/metadata/${checked(seriesId.value)}/children")
            .rows("Metadata").mapNotNull(mapper::item))
    }

    override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> = mapped {
        val path = if (seasonId != null) "library/metadata/${checked(seasonId.value)}/children"
        else "library/metadata/${checked(seriesId.value)}/allLeaves"
        page(path, request, listingQuery(request))
    }

    override suspend fun home(request: HomeFeedRequest): HomeFeed = mapped {
        val latest = items(null, PageRequest(pageSize = request.latestLimit,
            sort = MediaSort(MediaSortField.DateAdded, SortDirection.Descending))).items
        val continuing = continueWatching(maxOf(request.resumeLimit, request.nextUpLimit))
        HomeFeed(
            recommendations = latest.take(request.recommendationLimit),
            latest = latest,
            resume = continuing.filter { it.userData.hasProgress }.take(request.resumeLimit),
            nextUp = continuing.filter { !it.userData.hasProgress && !it.userData.isPlayed }.take(request.nextUpLimit),
        )
    }

    override suspend fun resumeItems(request: PageRequest): Page<MediaItem> = mapped {
        if (request.cursor != null || !request.filter.copy(mediaTypes = emptySet()).isEmpty || request.sort != null) {
            throw ClientConfigError("Plex continue watching is a single provider hub")
        }
        Page(continueWatching(request.pageSize).filter { it.userData.hasProgress && matchesType(it, request.filter) })
    }

    private suspend fun continueWatching(count: Int): List<MediaItem> =
        client.container("hubs", mapOf("identifier" to "home.continue,home.ondeck", "count" to count.toString()))
            .rows("Hub").flatMap { it.rows("Metadata") }.mapNotNull(mapper::item).distinctBy { it.id }

    override suspend fun setFavorite(id: MediaId, favorite: Boolean) {
        requireCapability(capabilities, AdapterCapability.FavoritesEdit)
    }

    override suspend fun setWatched(id: MediaId, watched: Boolean) = mapped {
        client.write(if (watched) ":/scrobble" else ":/unscrobble",
            mapOf("key" to checked(id.value), "identifier" to "com.plexapp.plugins.library"))
    }

    override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation = mapped {
        if (options.live || options.maxStreamingBitrate != null) {
            return@mapped PlaybackPreparation.Refused(RefusalReason.ServerRefused, "Plex 当前仅支持原文件播放")
        }
        val row = detail(item)
        if (row.text("type") !in setOf("movie", "episode")) {
            return@mapped PlaybackPreparation.Refused(RefusalReason.NoPlayableSource)
        }
        val media = row.rows("Media")
        val selected = if (options.mediaSourceId != null) media.firstOrNull { it.text("id") == options.mediaSourceId }
        else media.firstOrNull { it.rows("Part").size == 1 }
        if (selected == null) return@mapped PlaybackPreparation.Refused(RefusalReason.NoPlayableSource)
        val part = selected.rows("Part").singleOrNull()
            ?: return@mapped PlaybackPreparation.Refused(RefusalReason.NoPlayableSource, "暂不支持分段文件")
        val url = client.resourceUrl(part.text("key"))
            ?.takeIf { it.pathSegments.drop(client.baseUrl.pathSegments.size).take(2) == listOf("library", "parts") }
            ?: return@mapped PlaybackPreparation.Refused(RefusalReason.LinkRejected)
        val source = mapper.source(selected)
        val streams = part.rows("Stream")
        val audioStreams = source.streams.filter { it.kind == MediaStreamKind.Audio }
        val subtitleStreams = source.streams.filter { it.kind == MediaStreamKind.Subtitle }
        val audioIndex = options.audioStreamIndex
            ?: audioStreams.firstOrNull { it.isDefault }?.index ?: audioStreams.firstOrNull()?.index
        val subtitleIndex = options.subtitleStreamIndex ?: subtitleStreams.firstOrNull { it.isDefault }?.index
        if ((options.audioStreamIndex != null && audioStreams.none { it.index == audioIndex }) ||
            (subtitleIndex != null && subtitleIndex != -1 && subtitleStreams.none { it.index == subtitleIndex })
        ) return@mapped PlaybackPreparation.Refused(RefusalReason.ServerRefused, "所选音轨或字幕已不可用")
        val subtitle = subtitleIndex?.takeIf { it >= 0 }?.let { index ->
            streams.firstOrNull { (it.number("index") ?: it.number("id")) == index.toLong() && it.number("streamType") == 3L }
        }
        val externalKey = subtitle?.text("key")
        val subtitleUrl = externalKey?.let(client::resourceUrl)
        if (externalKey != null && subtitleUrl == null) return@mapped PlaybackPreparation.Refused(RefusalReason.LinkRejected)
        val session = buildString { repeat(32) { append("0123456789abcdef"[Random.nextInt(16)]) } }
        val ready = PlaybackPreparation.Ready(
            streamUrl = url.toEncodedString(), subtitleUrl = subtitleUrl?.toEncodedString(),
            mediaSourceId = selected.text("id"), transport = PlaybackTransport.DirectPlay,
            qualities = emptyList(), headers = client.playbackHeaders(session),
            playSessionId = PlaybackSessionId(session), reportingContext = session, engineSource = source,
            tracks = source.streams.mapNotNull { stream ->
                val index = stream.index ?: return@mapNotNull null
                val kind = when (stream.kind) {
                    MediaStreamKind.Audio -> PlaybackTrackKind.Audio
                    MediaStreamKind.Subtitle -> PlaybackTrackKind.Subtitle
                    else -> return@mapNotNull null
                }
                PlaybackTrack(index, kind, stream.displayTitle, stream.title, stream.language, stream.codec,
                    stream.channels, selected = index == if (kind == PlaybackTrackKind.Audio) audioIndex else subtitleIndex,
                    isExternal = stream.isExternal,
                    renegotiate = stream.isExternal || (kind == PlaybackTrackKind.Subtitle && subtitleUrl != null))
            },
        )
        playbackLock.withLock { playbacks += Prepared(ready, item, session, row.number("duration")) }
        ready
    }

    override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) = mapped {
        val prepared = playbackLock.withLock { playbacks.firstOrNull { it.ready === stream } }
            ?: throw ClientConfigError("Plex playback belongs to another preparation")
        client.write(":/timeline", buildMap {
            put("ratingKey", prepared.item.value)
            put("key", "/library/metadata/${prepared.item.value}")
            put("time", event.positionMs.toString())
            put("state", when {
                event.kind == PlaybackEventKind.Stop -> "stopped"
                event.isPaused == true -> "paused"
                else -> "playing"
            })
            (event.durationMs ?: prepared.durationMs)?.let { put("duration", it.toString()) }
        }, HttpMethod.Post, prepared.session)
    }

    override suspend fun releasePlayback(stream: PlaybackPreparation.Ready) {
        playbackLock.withLock {
            val index = playbacks.indexOfFirst { it.ready === stream }
            if (index < 0) throw ClientConfigError("Plex playback belongs to another preparation")
            playbacks.removeAt(index)
        }
    }

    private suspend fun detail(id: MediaId): JsonObject =
        client.container("library/metadata/${checked(id.value)}").rows("Metadata").singleOrNull()
            ?: throw ResourceNotFoundError()

    private suspend fun page(
        path: String, request: PageRequest, query: Map<String, String> = emptyMap(),
    ): Page<MediaItem> {
        val offset = request.cursor?.let(OffsetPageCursorCodec::decode) ?: 0
        val container = client.container(path, query + mapOf(
            "X-Plex-Container-Start" to offset.toString(), "X-Plex-Container-Size" to request.pageSize.toString(),
        ))
        val raw = (container["Metadata"] as? JsonArray).orEmpty()
        val rows = raw.mapNotNull { it as? JsonObject }
        val total = container.number("totalSize")?.takeIf { it in 0..Int.MAX_VALUE }?.toInt()
        val nextOffset = offset.toLong() + raw.size
        val hasMore = raw.isNotEmpty() && nextOffset <= Int.MAX_VALUE &&
            if (total != null) nextOffset < total else raw.size >= request.pageSize
        return Page(rows.mapNotNull(mapper::item).filter { matchesType(it, request.filter) },
            if (hasMore) OffsetPageCursorCodec.encode(nextOffset.toInt()) else null, total)
    }

    private fun listingQuery(request: PageRequest): Map<String, String> = buildMap {
        val filter = request.filter
        if (filter.favoritesOnly) throw ClientConfigError("Plex favorites are unsupported")
        filter.mediaTypes.forEach(::plexType)
        filter.mediaTypes.singleOrNull()?.let { type ->
            put("type", plexType(type))
        }
        if (filter.genres.isNotEmpty()) throw ClientConfigError("Plex genre identifiers are required")
        if (filter.years.isNotEmpty()) put("year", filter.years.joinToString(","))
        filter.searchTerm?.takeIf { it.isNotBlank() }?.let { throw ClientConfigError("Use Plex search for text queries") }
        if (filter.playedOnly) put("unwatched", "0")
        if (filter.unplayedOnly) put("unwatched", "1")
        request.sort?.let { sort ->
            val key = when (sort.field) {
                MediaSortField.Default, MediaSortField.Title -> "titleSort"
                MediaSortField.DateAdded -> "addedAt"
                MediaSortField.DatePlayed -> "lastViewedAt"
                MediaSortField.PremiereDate -> "originallyAvailableAt"
                MediaSortField.CommunityRating -> "rating"
                MediaSortField.ProductionYear -> "year"
                MediaSortField.PlayCount -> "viewCount"
                MediaSortField.Runtime -> "duration"
                MediaSortField.EpisodeNumber, MediaSortField.SeasonNumber -> "index"
                else -> throw ClientConfigError("Unsupported Plex sort order")
            }
            put("sort", "$key:${if (sort.direction == SortDirection.Descending) "desc" else "asc"}")
        }
    }

    private fun checked(id: String): String = id.also { if (!plexId(it)) throw ClientConfigError("Invalid Plex item identity") }
    private fun matchesType(item: MediaItem, filter: MediaFilter) = filter.mediaTypes.isEmpty() || item.mediaType in filter.mediaTypes
    private class Prepared(val ready: PlaybackPreparation.Ready, val item: MediaId, val session: String, val durationMs: Long?)
}

private suspend fun <T> mapped(block: suspend () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: PlexFailure) {
    throw when (failure.kind) {
        PlexFailureKind.Authentication, PlexFailureKind.Expired ->
            AuthenticationError(AuthFailureKind.TokenExpired, "Plex 授权已失效，请重新连接")
        PlexFailureKind.Authorization -> AuthorizationError("没有此 Plex 服务器的访问权限")
        PlexFailureKind.Missing -> ResourceNotFoundError()
        PlexFailureKind.Network -> NetworkUnreachableError("无法连接 Plex 服务器")
        PlexFailureKind.Timeout -> TimeoutError(message = "Plex 连接超时")
        PlexFailureKind.RateLimit -> RateLimitedError(message = "Plex 请求过于频繁")
        PlexFailureKind.Server -> ServerError(message = "Plex 服务器暂时不可用")
        PlexFailureKind.Protocol -> ProtocolDecodingError("Plex 响应不完整或不受支持")
    }
}
