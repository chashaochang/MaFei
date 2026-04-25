package cn.xiaobai.mafei.data

import cn.xiaobai.mafei.data.jellyfin.JellyfinProvider
import cn.xiaobai.mafei.data.jellyfin.createJellyfinProvider
import cn.xiaobai.mafei.data.jellyfin.requireAndroidProvider
import cn.xiaobai.mafei.data.jellyfin.toJellyfinLoadIssue
import cn.xiaobai.mafei.logging.AppLogger
import cn.xiaobai.mafei.logging.summarizeBaseUrlForLog
import cn.xiaobai.mafei.screens.JellyfinServer
import cn.xiaobai.mafei.storage.SessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import kotlin.math.roundToInt

class AndroidJellyfinHomeRepository(
    private val providerFactory: () -> JellyfinProvider = ::createJellyfinProvider,
    private val fallbackRepository: HomeRepository = FakeHomeRepository(FakeVideoRepository()),
) : HomeRepository {
    override suspend fun loadHomeState(
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): HomeState {
        AppLogger.info(
            tag = "Home",
            message = "loadHomeState start: baseUrl=${summarizeBaseUrlForLog(defaultServer?.baseUrl)}, hasSession=${session != null}, hasToken=${!session?.accessToken.isNullOrBlank()}."
        )
        val fallbackState = fallbackRepository.loadHomeState(defaultServer, session)
        val accessToken = session?.accessToken
        if (defaultServer == null || accessToken.isNullOrBlank()) {
            AppLogger.warn(
                tag = "Home",
                message = "loadHomeState fallback: missing server or token."
            )
            return fallbackState
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val api = providerFactory().requireAndroidProvider().jellyfin.createApi(
                    baseUrl = defaultServer.baseUrl,
                    accessToken = accessToken,
                )
                AppLogger.debug(
                    tag = "Home",
                    message = "loadHomeState remote fetch started."
                )

                val continueWatching = api.itemsApi
                    .getResumeItems(limit = 20, enableUserData = true)
                    .content
                    .items
                    .map { item ->
                        item.toContinueWatchingItem(
                            baseUrl = defaultServer.baseUrl,
                            accessToken = accessToken,
                        )
                    }
                    .distinctBy { item -> item.itemId }

                val latestAdded = api.userLibraryApi
                    .getLatestMedia(limit = 20, enableUserData = true)
                    .content
                    .map { item ->
                        item.toLatestLibraryItem(
                            baseUrl = defaultServer.baseUrl,
                            accessToken = accessToken,
                        )
                    }
                    .distinctBy { item -> item.itemId }

                val mediaViews = api.userViewsApi
                    .getUserViews(includeExternalContent = false, includeHidden = false)
                    .content
                    .items
                    .filter { view -> view.supportsMediaView() }
                    .take(4)
                    .mapNotNull { view ->
                        view.toHomeMediaViewOrNull(
                            api = api,
                            baseUrl = defaultServer.baseUrl,
                            accessToken = accessToken,
                        )
                    }

                val nextUp = api.tvShowsApi
                    .getNextUp(
                        limit = 20,
                        enableUserData = true,
                        enableResumable = true,
                        enableRewatching = false,
                    )
                    .content
                    .items
                    .mapNotNull { item ->
                        item.toNextUpItem(
                            baseUrl = defaultServer.baseUrl,
                            accessToken = accessToken,
                        )
                    }
                    .distinctBy { item -> item.itemId }

                val updates = api.itemsApi
                    .getItems(
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                        filters = listOf(ItemFilter.IS_UNPLAYED),
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        enableUserData = true,
                        limit = 40,
                    )
                    .content
                    .items
                    .mapNotNull { item ->
                        item.toUpdateSeed(
                            baseUrl = defaultServer.baseUrl,
                            accessToken = accessToken,
                        )
                    }
                    .groupBy { item -> item.itemId }
                    .map { (_, episodes) ->
                        val first = episodes.first()
                        UpdateItem(
                            itemId = first.itemId,
                            title = first.title,
                            latestEpisodeTitle = first.latestEpisodeTitle,
                            newEpisodeCount = episodes.size,
                            thumbnailUrl = first.thumbnailUrl,
                        )
                    }
                    .take(20)

                val usedContinueFallback = continueWatching.isEmpty()
                val usedNextUpFallback = nextUp.isEmpty()
                val usedMediaViewsFallback = mediaViews.isEmpty()
                val usedLatestFallback = latestAdded.isEmpty()
                val usedUpdatesFallback = updates.isEmpty()
                val result = fallbackState.copy(
                    continueWatching = if (continueWatching.isNotEmpty()) {
                        continueWatching
                    } else {
                        fallbackState.continueWatching
                    },
                    nextUp = if (nextUp.isNotEmpty()) {
                        nextUp
                    } else {
                        fallbackState.nextUp
                    },
                    mediaViews = if (mediaViews.isNotEmpty()) {
                        mediaViews
                    } else {
                        fallbackState.mediaViews
                    },
                    latestAdded = if (latestAdded.isNotEmpty()) {
                        latestAdded
                    } else {
                        fallbackState.latestAdded
                    },
                    updates = if (updates.isNotEmpty()) {
                        updates
                    } else {
                        fallbackState.updates
                    },
                    loadIssue = null,
                )
                AppLogger.info(
                    tag = "Home",
                    message = "loadHomeState success: continue=${result.continueWatching.size}, nextUp=${result.nextUp.size}, mediaViews=${result.mediaViews.size}, latest=${result.latestAdded.size}, updates=${result.updates.size}, sectionFallbacks={continue=$usedContinueFallback,nextUp=$usedNextUpFallback,media=$usedMediaViewsFallback,latest=$usedLatestFallback,updates=$usedUpdatesFallback}."
                )
                result
            }.getOrElse { error ->
                AppLogger.error(
                    tag = "Home",
                    message = "loadHomeState failed; fallback to placeholder state.",
                    throwable = error,
                )
                fallbackState.copy(
                    loadIssue = error.toJellyfinLoadIssue("首页数据加载失败，已回退到占位数据。"),
                )
            }
        }
    }
}

private suspend fun BaseItemDto.toHomeMediaViewOrNull(
    api: org.jellyfin.sdk.api.client.ApiClient,
    baseUrl: String,
    accessToken: String,
): HomeMediaView? {
    val viewId = id.toString()
    val viewTitle = name?.takeIf { it.isNotBlank() } ?: return null
    val queryResult = api.itemsApi
        .getItems(
            parentId = id,
            recursive = true,
            includeItemTypes = viewItemTypes(),
            sortBy = listOf(ItemSortBy.DATE_CREATED),
            sortOrder = listOf(SortOrder.DESCENDING),
            enableUserData = true,
            limit = 18,
        )
        .content

    val items = queryResult.items
        .mapNotNull { item ->
            item.toMediaViewLibraryItem(
                baseUrl = baseUrl,
                accessToken = accessToken,
            )
        }
        .distinctBy { item -> item.itemId }
        .take(12)
    if (items.isEmpty()) return null

    return HomeMediaView(
        viewId = viewId,
        title = viewTitle,
        subtitle = collectionType.toViewSubtitle(items.size),
        items = items,
    )
}

private fun BaseItemDto.supportsMediaView(): Boolean {
    return collectionType != null || type == BaseItemKind.FOLDER
}

private fun BaseItemDto.viewItemTypes(): List<BaseItemKind> = when (collectionType?.name) {
    "MOVIES" -> listOf(BaseItemKind.MOVIE)
    "TVSHOWS" -> listOf(BaseItemKind.SERIES)
    "MUSICVIDEOS" -> listOf(BaseItemKind.MUSIC_VIDEO)
    else -> listOf(BaseItemKind.SERIES, BaseItemKind.MOVIE)
}

private fun BaseItemDto.toMediaViewLibraryItem(
    baseUrl: String,
    accessToken: String,
): LibraryItem? {
    val targetId = id.toString()
    val title = name?.takeIf { it.isNotBlank() }
        ?: seriesName?.takeIf { it.isNotBlank() }
        ?: return null
    val typeLabel = when (type) {
        BaseItemKind.SERIES -> "剧集"
        BaseItemKind.MOVIE -> "电影"
        BaseItemKind.MUSIC_VIDEO -> "音乐视频"
        else -> "媒体"
    }
    val subtitle = listOfNotNull(
        typeLabel,
        productionYear?.toString(),
        if (userData?.isFavorite == true) "已收藏" else null,
    ).joinToString(" · ")

    return LibraryItem(
        itemId = targetId,
        title = title,
        subtitle = subtitle.ifBlank { "Jellyfin 媒体" },
        posterUrl = buildPrimaryImageUrl(
            baseUrl = baseUrl,
            itemId = targetId,
            accessToken = accessToken,
            maxWidth = 420,
            maxHeight = 620,
        ),
    )
}

private fun org.jellyfin.sdk.model.api.CollectionType?.toViewSubtitle(itemCount: Int): String {
    val typeLabel = when (this?.name) {
        "MOVIES" -> "电影分区"
        "TVSHOWS" -> "剧集分区"
        "MUSICVIDEOS" -> "音乐视频分区"
        else -> "媒体分区"
    }
    return "$typeLabel · $itemCount 项"
}

private fun BaseItemDto.toNextUpItem(
    baseUrl: String,
    accessToken: String,
): NextUpItem? {
    val targetId = (seriesId ?: id)?.toString() ?: return null
    val showTitle = seriesName?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: "Untitled"
    val episodeTitle = buildString {
        parentIndexNumber?.let {
            append("S")
            append(it)
            append(" ")
        }
        indexNumber?.let {
            append("E")
            append(it)
            append(" ")
        }
        append(name?.takeIf { it.isNotBlank() } ?: "未命名剧集")
    }.trim()
    val subtitle = listOfNotNull(
        "Next Up",
        formatTicks(runTimeTicks ?: 0L).takeIf { it != "00:00" },
    ).joinToString(" · ")

    return NextUpItem(
        itemId = targetId,
        title = showTitle,
        episodeTitle = episodeTitle,
        subtitle = subtitle.ifBlank { "下一集待播" },
        thumbnailUrl = buildThumbImageUrl(
            baseUrl = baseUrl,
            itemId = id.toString(),
            accessToken = accessToken,
            maxWidth = 480,
            maxHeight = 270,
        ),
    )
}

private fun BaseItemDto.toContinueWatchingItem(
    baseUrl: String,
    accessToken: String,
): ContinueWatchingItem {
    val percent = userData?.playedPercentage
        ?.roundToInt()
        ?.coerceIn(0, 100)
        ?: 0
    val playbackTicks = userData?.playbackPositionTicks ?: 0L
    val displayTitle = seriesName?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: "Untitled"
    val episodeTitle = if (!seriesName.isNullOrBlank() && !name.isNullOrBlank()) {
        name
    } else {
        "Continue playback"
    }

    return ContinueWatchingItem(
        itemId = (seriesId ?: id).toString(),
        title = displayTitle,
        episodeTitle = episodeTitle ?: "Continue playback",
        progressLabel = "续播至 ${formatTicks(playbackTicks)} · $percent%",
        progressPercent = percent,
        backdropUrl = buildBackdropImageUrl(
            baseUrl = baseUrl,
            itemId = (seriesId ?: id).toString(),
            accessToken = accessToken,
            maxWidth = 960,
            maxHeight = 540,
        ),
    )
}

private fun BaseItemDto.toLatestLibraryItem(
    baseUrl: String,
    accessToken: String,
): LibraryItem {
    val targetId = if (type == BaseItemKind.EPISODE) {
        (seriesId ?: id).toString()
    } else {
        id.toString()
    }
    val title = name?.takeIf { it.isNotBlank() }
        ?: seriesName?.takeIf { it.isNotBlank() }
        ?: "Untitled"
    val typeLabel = when (type.name) {
        "EPISODE" -> "剧集"
        "MOVIE" -> "电影"
        "SERIES" -> "剧集合集"
        else -> "媒体"
    }
    val year = productionYear?.toString().orEmpty()
    val subtitle = listOf("最新入库", typeLabel, year)
        .filter { part -> part.isNotBlank() }
        .joinToString(" · ")

    return LibraryItem(
        itemId = targetId,
        title = title,
        subtitle = subtitle,
        posterUrl = buildPrimaryImageUrl(
            baseUrl = baseUrl,
            itemId = targetId,
            accessToken = accessToken,
            maxWidth = 420,
            maxHeight = 620,
        ),
    )
}

private data class UpdateSeed(
    val itemId: String,
    val title: String,
    val latestEpisodeTitle: String,
    val thumbnailUrl: String?,
)

private fun BaseItemDto.toUpdateSeed(
    baseUrl: String,
    accessToken: String,
): UpdateSeed? {
    val rootItemId = (seriesId ?: id)?.toString() ?: return null
    val showTitle = seriesName?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: "Untitled"
    val episodeLabel = buildString {
        parentIndexNumber?.let { season ->
            append("S")
            append(season)
            append(" ")
        }
        indexNumber?.let { episode ->
            append("E")
            append(episode)
            append(" ")
        }
        append(name?.takeIf { it.isNotBlank() } ?: "未命名剧集")
    }.trim()
    return UpdateSeed(
        itemId = rootItemId,
        title = showTitle,
        latestEpisodeTitle = episodeLabel,
        thumbnailUrl = buildThumbImageUrl(
            baseUrl = baseUrl,
            itemId = id.toString(),
            accessToken = accessToken,
            maxWidth = 480,
            maxHeight = 270,
        ),
    )
}

private fun formatTicks(ticks: Long): String {
    if (ticks <= 0L) return "00:00"
    val totalSeconds = ticks / 10_000_000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
