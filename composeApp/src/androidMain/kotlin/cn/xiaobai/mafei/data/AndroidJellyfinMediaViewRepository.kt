package cn.xiaobai.mafei.data

import cn.xiaobai.mafei.data.jellyfin.JellyfinProvider
import cn.xiaobai.mafei.data.jellyfin.JellyfinLoadIssue
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
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID

class AndroidJellyfinMediaViewRepository(
    private val providerFactory: () -> JellyfinProvider = ::createJellyfinProvider,
    private val fallbackRepository: MediaViewRepository = FakeMediaViewRepository(FakeVideoRepository()),
) : MediaViewRepository {
    override suspend fun loadMediaView(
        viewId: String,
        startIndex: Int,
        limit: Int,
        sortOption: MediaViewSortOption,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): MediaViewBrowseResult {
        AppLogger.info(
            tag = "MediaView",
            message = "loadMediaView start: viewId=${viewId.shortIdForLog()}, start=$startIndex, limit=$limit, sort=$sortOption, baseUrl=${summarizeBaseUrlForLog(defaultServer?.baseUrl)}, hasToken=${!session?.accessToken.isNullOrBlank()}."
        )
        val fallback = fallbackRepository.loadMediaView(
            viewId = viewId,
            startIndex = startIndex,
            limit = limit,
            sortOption = sortOption,
            defaultServer = defaultServer,
            session = session,
        )
        val accessToken = session?.accessToken
        if (defaultServer == null || accessToken.isNullOrBlank()) {
            AppLogger.warn(
                tag = "MediaView",
                message = "loadMediaView fallback: missing server or token."
            )
            return fallback
        }

        val parsedViewId = runCatching { UUID.fromString(viewId) }.getOrNull()
            ?: return fallback.copy(
                loadIssue = JellyfinLoadIssue(
                    errorCode = cn.xiaobai.mafei.data.jellyfin.JellyfinErrorCode.INVALID_URL,
                    message = "分区 ID 无效，已回退到本地占位内容。",
                    retryable = false,
                ),
            ).also {
                AppLogger.warn(
                    tag = "MediaView",
                    message = "loadMediaView fallback: invalid viewId=${viewId.shortIdForLog()}."
                )
            }

        return withContext(Dispatchers.IO) {
            runCatching {
                val api = providerFactory().requireAndroidProvider().jellyfin.createApi(
                    baseUrl = defaultServer.baseUrl,
                    accessToken = accessToken,
                )
                AppLogger.debug(
                    tag = "MediaView",
                    message = "loadMediaView remote fetch started: viewId=${viewId.shortIdForLog()}."
                )
                val viewRoot = api.userViewsApi
                    .getUserViews(includeExternalContent = false, includeHidden = false)
                    .content
                    .items
                    .firstOrNull { it.id == parsedViewId }

                if (viewRoot == null) {
                    fallback.copy(
                        loadIssue = JellyfinLoadIssue(
                            errorCode = cn.xiaobai.mafei.data.jellyfin.JellyfinErrorCode.UNKNOWN,
                            message = "未找到对应媒体分区，已回退到占位内容。",
                        ),
                    ).also {
                        AppLogger.warn(
                            tag = "MediaView",
                            message = "loadMediaView fallback: remote view not found, viewId=${viewId.shortIdForLog()}."
                        )
                    }
                } else {
                    val (sortBy, sortOrder) = sortOption.toRemoteSort()
                    val items = api.itemsApi
                        .getItems(
                            parentId = parsedViewId,
                            recursive = true,
                            includeItemTypes = viewRoot.browseItemTypes(),
                            sortBy = listOf(sortBy),
                            sortOrder = listOf(sortOrder),
                            enableUserData = true,
                            startIndex = startIndex,
                            limit = limit,
                        )
                        .content
                    val mappedItems = items.items
                        .mapNotNull { item ->
                            item.toBrowseLibraryItem(
                                baseUrl = defaultServer.baseUrl,
                                accessToken = accessToken,
                            )
                        }
                        .distinctBy { it.itemId }
                    val usedFallbackItems = mappedItems.isEmpty()

                    MediaViewBrowseResult(
                        view = HomeMediaView(
                            viewId = viewId,
                            title = viewRoot.name?.takeIf { it.isNotBlank() } ?: fallback.view?.title ?: "内容分区",
                            subtitle = viewRoot.collectionType.toBrowseSubtitle(items.totalRecordCount),
                            items = if (mappedItems.isNotEmpty()) mappedItems else fallback.view?.items.orEmpty(),
                        ),
                        loadIssue = null,
                        hasMore = items.startIndex + items.items.size < items.totalRecordCount,
                        nextStartIndex = items.startIndex + items.items.size,
                        totalCount = items.totalRecordCount,
                    ).also { result ->
                        AppLogger.info(
                            tag = "MediaView",
                            message = "loadMediaView success: viewId=${viewId.shortIdForLog()}, returned=${result.view?.items?.size ?: 0}, total=${result.totalCount}, hasMore=${result.hasMore}, usedFallbackItems=$usedFallbackItems."
                        )
                    }
                }
            }.getOrElse { error ->
                AppLogger.error(
                    tag = "MediaView",
                    message = "loadMediaView failed; fallback to placeholder content.",
                    throwable = error,
                )
                fallback.copy(
                    loadIssue = error.toJellyfinLoadIssue("分区内容加载失败，已回退到占位内容。"),
                )
            }
        }
    }
}

private fun String.shortIdForLog(): String {
    if (isBlank()) return "<empty>"
    return if (length <= 12) this else "${take(6)}...${takeLast(4)}"
}

private fun MediaViewSortOption.toRemoteSort(): Pair<ItemSortBy, SortOrder> {
    return when (this) {
        MediaViewSortOption.RECENT -> ItemSortBy.DATE_CREATED to SortOrder.DESCENDING
        MediaViewSortOption.TITLE -> ItemSortBy.SORT_NAME to SortOrder.ASCENDING
    }
}

private fun BaseItemDto.browseItemTypes(): List<BaseItemKind> = when (collectionType?.name) {
    "MOVIES" -> listOf(BaseItemKind.MOVIE)
    "TVSHOWS" -> listOf(BaseItemKind.SERIES)
    "MUSICVIDEOS" -> listOf(BaseItemKind.MUSIC_VIDEO)
    else -> listOf(BaseItemKind.SERIES, BaseItemKind.MOVIE)
}

private fun BaseItemDto.toBrowseLibraryItem(
    baseUrl: String,
    accessToken: String,
): LibraryItem? {
    val title = name?.takeIf { it.isNotBlank() }
        ?: seriesName?.takeIf { it.isNotBlank() }
        ?: return null
    val subtitle = listOfNotNull(
        when (type) {
            BaseItemKind.SERIES -> "剧集"
            BaseItemKind.MOVIE -> "电影"
            BaseItemKind.MUSIC_VIDEO -> "音乐视频"
            else -> "媒体"
        },
        productionYear?.toString(),
        if (userData?.isFavorite == true) "已收藏" else null,
    ).joinToString(" · ")

    return LibraryItem(
        itemId = id.toString(),
        title = title,
        subtitle = subtitle.ifBlank { "Jellyfin 媒体" },
        posterUrl = buildPrimaryImageUrl(
            baseUrl = baseUrl,
            itemId = id.toString(),
            accessToken = accessToken,
            maxWidth = 420,
            maxHeight = 620,
        ),
    )
}

private fun org.jellyfin.sdk.model.api.CollectionType?.toBrowseSubtitle(itemCount: Int): String {
    val typeLabel = when (this?.name) {
        "MOVIES" -> "电影分区"
        "TVSHOWS" -> "剧集分区"
        "MUSICVIDEOS" -> "音乐视频分区"
        else -> "媒体分区"
    }
    return "$typeLabel · 共 $itemCount 项"
}
