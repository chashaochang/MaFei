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
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

class AndroidJellyfinSearchRepository(
    private val providerFactory: () -> JellyfinProvider = ::createJellyfinProvider,
    private val fallbackRepository: SearchRepository = FakeSearchRepository(FakeVideoRepository()),
) : SearchRepository {
    override suspend fun search(
        keyword: String,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): SearchResult {
        val normalizedKeyword = keyword.trim()
        AppLogger.info(
            tag = "Search",
            message = "search start: keywordLength=${normalizedKeyword.length}, baseUrl=${summarizeBaseUrlForLog(defaultServer?.baseUrl)}, hasToken=${!session?.accessToken.isNullOrBlank()}."
        )
        val fallback = fallbackRepository.search(normalizedKeyword, defaultServer, session).items
        val token = session?.accessToken
        if (defaultServer == null || token.isNullOrBlank()) {
            AppLogger.warn(
                tag = "Search",
                message = "search fallback: missing server or token."
            )
            return SearchResult(items = fallback)
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val api = providerFactory().requireAndroidProvider().jellyfin.createApi(
                    baseUrl = defaultServer.baseUrl,
                    accessToken = token,
                )
                AppLogger.debug(
                    tag = "Search",
                    message = "search remote fetch started: keywordLength=${normalizedKeyword.length}."
                )

                val sortBy = if (normalizedKeyword.isBlank()) {
                    listOf(ItemSortBy.DATE_CREATED)
                } else {
                    listOf(ItemSortBy.SORT_NAME)
                }
                val sortOrder = if (normalizedKeyword.isBlank()) {
                    listOf(SortOrder.DESCENDING)
                } else {
                    listOf(SortOrder.ASCENDING)
                }

                val remote = api.itemsApi
                    .getItems(
                        recursive = true,
                        searchTerm = normalizedKeyword.takeIf { it.isNotBlank() },
                        includeItemTypes = listOf(
                            BaseItemKind.SERIES,
                            BaseItemKind.MOVIE,
                            BaseItemKind.EPISODE,
                        ),
                        sortBy = sortBy,
                        sortOrder = sortOrder,
                        enableUserData = true,
                        limit = 40,
                    )
                    .content
                    .items
                    .mapNotNull { item ->
                        item.toSearchItem(
                            baseUrl = defaultServer.baseUrl,
                            accessToken = token,
                        )
                    }
                    .distinctBy { item -> item.itemId }
                val usedFallbackItems = remote.isEmpty()

                SearchResult(
                    items = if (remote.isNotEmpty()) remote else fallback,
                    loadIssue = null,
                ).also { result ->
                    AppLogger.info(
                        tag = "Search",
                        message = "search success: keywordLength=${normalizedKeyword.length}, returned=${result.items.size}, usedFallbackItems=$usedFallbackItems."
                    )
                }
            }.getOrElse { error ->
                AppLogger.error(
                    tag = "Search",
                    message = "search failed; fallback to placeholder results.",
                    throwable = error,
                )
                SearchResult(
                    items = fallback,
                    loadIssue = error.toJellyfinLoadIssue("搜索失败，已展示占位结果。"),
                )
            }
        }
    }
}

private fun BaseItemDto.toSearchItem(
    baseUrl: String,
    accessToken: String,
): LibraryItem? {
    val targetId = (seriesId ?: id)?.toString() ?: return null
    val title = when {
        type == BaseItemKind.EPISODE && !seriesName.isNullOrBlank() -> seriesName
        !name.isNullOrBlank() -> name
        !seriesName.isNullOrBlank() -> seriesName
        else -> "Untitled"
    } ?: "Untitled"
    val typeLabel = when (type) {
        BaseItemKind.SERIES -> "剧集"
        BaseItemKind.MOVIE -> "电影"
        BaseItemKind.EPISODE -> "单集"
        BaseItemKind.SEASON -> "季"
        else -> "媒体"
    }
    val runtime = formatRunTimeLabel(runTimeTicks)
    val subtitle = listOfNotNull(
        typeLabel,
        productionYear?.toString(),
        runtime.takeIf { it != "--" },
        if (type == BaseItemKind.EPISODE) {
            name?.takeIf { !it.isNullOrBlank() }?.let { "剧集：$it" }
        } else {
            null
        },
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

private fun formatRunTimeLabel(ticks: Long?): String {
    val safeTicks = ticks ?: return "--"
    if (safeTicks <= 0L) return "--"
    val totalSeconds = safeTicks / 10_000_000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h${minutes}m" else "${minutes}m"
}
