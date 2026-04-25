package cn.xiaobai.mafei.data

import cn.xiaobai.mafei.data.jellyfin.JellyfinProvider
import cn.xiaobai.mafei.data.jellyfin.JellyfinErrorCode
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
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID

class AndroidJellyfinFavoritesRepository(
    private val providerFactory: () -> JellyfinProvider = ::createJellyfinProvider,
    private val fallbackRepository: FavoritesRepository = FakeFavoritesRepository(FakeVideoRepository()),
) : FavoritesRepository {
    override suspend fun loadFavorites(
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): FavoritesState {
        AppLogger.info(
            tag = "Favorites",
            message = "loadFavorites start: baseUrl=${summarizeBaseUrlForLog(defaultServer?.baseUrl)}, hasToken=${!session?.accessToken.isNullOrBlank()}."
        )
        val fallback = fallbackRepository.loadFavorites(defaultServer, session)
        val token = session?.accessToken
        if (defaultServer == null || token.isNullOrBlank()) {
            AppLogger.warn(
                tag = "Favorites",
                message = "loadFavorites fallback: missing server or token."
            )
            return fallback
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val api = providerFactory().requireAndroidProvider().jellyfin.createApi(
                    baseUrl = defaultServer.baseUrl,
                    accessToken = token,
                )
                val remoteItems = api.itemsApi
                    .getItems(
                        recursive = true,
                        isFavorite = true,
                        includeItemTypes = listOf(BaseItemKind.SERIES, BaseItemKind.MOVIE),
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        enableUserData = true,
                        limit = 60,
                    )
                    .content
                    .items
                    .mapNotNull {
                        it.toFavoriteLibraryItem(
                            baseUrl = defaultServer.baseUrl,
                            accessToken = token,
                        )
                    }
                    .distinctBy { it.itemId }
                val usedFallbackItems = remoteItems.isEmpty()

                FavoritesState(
                    items = if (remoteItems.isNotEmpty()) remoteItems else fallback.items,
                    loadIssue = null,
                ).also { result ->
                    AppLogger.info(
                        tag = "Favorites",
                        message = "loadFavorites success: count=${result.items.size}, usedFallbackItems=$usedFallbackItems."
                    )
                }
            }.getOrElse { error ->
                AppLogger.error(
                    tag = "Favorites",
                    message = "loadFavorites failed; fallback to placeholder content.",
                    throwable = error,
                )
                fallback.copy(
                    loadIssue = error.toJellyfinLoadIssue("收藏列表加载失败，已展示占位内容。"),
                )
            }
        }
    }

    override suspend fun updateFavorite(
        itemId: String,
        favorite: Boolean,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): FavoriteMutationResult {
        AppLogger.info(
            tag = "Favorites",
            message = "updateFavorite start: itemId=${itemId.shortIdForLog()}, targetFavorite=$favorite, baseUrl=${summarizeBaseUrlForLog(defaultServer?.baseUrl)}, hasToken=${!session?.accessToken.isNullOrBlank()}."
        )
        val token = session?.accessToken
        if (defaultServer == null || token.isNullOrBlank()) {
            AppLogger.warn(
                tag = "Favorites",
                message = "updateFavorite blocked: missing server or token."
            )
            return FavoriteMutationResult.Failure(
                targetFavorite = favorite,
                loadIssue = JellyfinLoadIssue(
                    errorCode = JellyfinErrorCode.AUTH_FAILED,
                    message = "未登录或无默认服务器，无法同步收藏状态。",
                    retryable = false,
                ),
            )
        }

        val favoriteItemId = runCatching { UUID.fromString(itemId) }.getOrNull()
            ?: return FavoriteMutationResult.Failure(
                targetFavorite = favorite,
                loadIssue = JellyfinLoadIssue(
                    errorCode = JellyfinErrorCode.INVALID_URL,
                    message = "当前内容 ID 无效，无法更新收藏状态。",
                    retryable = false,
                ),
            ).also {
                AppLogger.warn(
                    tag = "Favorites",
                    message = "updateFavorite blocked: invalid itemId=${itemId.shortIdForLog()}."
                )
            }

        return withContext(Dispatchers.IO) {
            runCatching {
                val api = providerFactory().requireAndroidProvider().jellyfin.createApi(
                    baseUrl = defaultServer.baseUrl,
                    accessToken = token,
                )
                if (favorite) {
                    api.userLibraryApi.markFavoriteItem(itemId = favoriteItemId)
                } else {
                    api.userLibraryApi.unmarkFavoriteItem(itemId = favoriteItemId)
                }
                FavoriteMutationResult.Success(
                    isFavorite = favorite,
                    message = if (favorite) "已加入收藏" else "已取消收藏",
                ).also {
                    AppLogger.info(
                        tag = "Favorites",
                        message = "updateFavorite success: itemId=${itemId.shortIdForLog()}, favorite=$favorite."
                    )
                }
            }.getOrElse { error ->
                AppLogger.error(
                    tag = "Favorites",
                    message = "updateFavorite failed: itemId=${itemId.shortIdForLog()}, favorite=$favorite.",
                    throwable = error,
                )
                FavoriteMutationResult.Failure(
                    targetFavorite = favorite,
                    loadIssue = error.toJellyfinLoadIssue(
                        defaultMessage = if (favorite) {
                            "收藏失败，请稍后重试。"
                        } else {
                            "取消收藏失败，请稍后重试。"
                        }
                    ),
                )
            }
        }
    }
}

private fun BaseItemDto.toFavoriteLibraryItem(
    baseUrl: String,
    accessToken: String,
): LibraryItem? {
    val targetId = id?.toString() ?: return null
    val title = name?.takeIf { it.isNotBlank() } ?: "Untitled"
    val typeLabel = when (type) {
        BaseItemKind.SERIES -> "剧集"
        BaseItemKind.MOVIE -> "电影"
        else -> "媒体"
    }
    val subtitle = listOfNotNull(
        "收藏",
        typeLabel,
        productionYear?.toString(),
    ).joinToString(" · ")

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

private fun String.shortIdForLog(): String {
    if (isBlank()) return "<empty>"
    return if (length <= 12) this else "${take(6)}...${takeLast(4)}"
}
