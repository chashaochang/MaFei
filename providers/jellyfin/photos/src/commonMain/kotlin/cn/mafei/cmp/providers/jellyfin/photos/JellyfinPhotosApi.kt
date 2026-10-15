package cn.mafei.cmp.providers.jellyfin.photos

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.transport.*
import cn.mafei.cmp.providers.jellyfin.wire.*

/** PhotoRepository/PhotoQueryPlanner's exact library and album requests. */
fun photoItemQuery(userId: String, parentId: String, albums: Boolean, offset: Int, limit: Int) = JellyfinItemQuery(
    userId = userId, parentId = parentId, startIndex = offset, limit = limit,
    recursive = !albums, includeItemTypes = if (albums) listOf("PhotoAlbum", "Folder") else listOf("Photo"),
    fields = listOf(ItemField.CanDownload, ItemField.ChildCount, ItemField.DateCreated, ItemField.Height,
        ItemField.Overview, ItemField.ParentId, ItemField.Path, ItemField.PrimaryImageAspectRatio,
        ItemField.RecursiveItemCount, ItemField.SortName, ItemField.Width),
    sortBy = if (albums) listOf(JellyfinSortBy.SortName) else listOf(JellyfinSortBy.PremiereDate, JellyfinSortBy.DateCreated, JellyfinSortBy.SortName),
    sortOrders = if (albums) listOf(JellyfinSortOrder.Ascending)
        else listOf(JellyfinSortOrder.Descending, JellyfinSortOrder.Descending, JellyfinSortOrder.Ascending),
    enableImageTypes = listOf("Primary"), enableUserData = true, enableTotalRecordCount = true,
)

interface JellyfinPhotosApi {
    suspend fun items(userId: String, parentId: String, albums: Boolean, offset: Int = 0, limit: Int = 60): QueryResultDto<BaseItemDto>
    fun originalUrl(itemId: String): HttpUrl
}

class DefaultJellyfinPhotosApi(private val client: JellyfinClient) : JellyfinPhotosApi {
    override suspend fun items(userId: String, parentId: String, albums: Boolean, offset: Int, limit: Int) =
        client.executeDecoding(JellyfinRequests.items(client.config.baseUrl, photoItemQuery(userId, parentId, albums, offset, limit)),
            QueryResultDto.serializer(ItemWithOriginalDatesSerializer))
    override fun originalUrl(itemId: String): HttpUrl = client.config.baseUrl.newBuilder()
        .addPathSegment("Items").addPathSegment(itemId).addPathSegment("Download")
        .apply { client.sessionState.current.value?.accessToken?.let { addQueryParameter("api_key", it) } }.build()
}
