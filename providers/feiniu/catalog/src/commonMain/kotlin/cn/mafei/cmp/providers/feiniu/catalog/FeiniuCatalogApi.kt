package cn.mafei.cmp.providers.feiniu.catalog

import cn.mafei.cmp.core.serialization.MafeiJson
import cn.mafei.cmp.providers.feiniu.core.FeiniuApiCall
import cn.mafei.cmp.providers.feiniu.core.FeiniuClient
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuItemListData
import cn.mafei.cmp.providers.feiniu.core.FeiniuItemListRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuItemListTags
import cn.mafei.cmp.providers.feiniu.core.FeiniuMediaLibraryEntry
import cn.mafei.cmp.providers.feiniu.core.FeiniuMediaItem
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer

/**
 * Feiniu catalog surface: media libraries, paged item lists, search,
 * favorites, watched marks, item detail, seasons and episodes. Page/sort
 * parameters stay Feiniu-shaped; domain mapping happens in the data adapter.
 */
class FeiniuCatalogApi(private val client: FeiniuClient) {

    suspend fun mediaLibraries(resolution: FeiniuHostResolution): List<FeiniuMediaLibraryEntry> =
        client.execute(
            resolution,
            FeiniuApiCall("/v/api/v1/mediadb/list", FeiniuApiCall.FeiniuMethod.Get, authRequirement = FeiniuApiCall.AuthRequirement.Token),
        ) { envelope, _ ->
            decodeList(envelope, FeiniuMediaLibraryEntry.serializer())
        }

    /**
     * Offset-paged item list of one library (or folder via [ancestorGuid]).
     * Synthesizing opaque page cursors is the data adapter's job.
     */
    suspend fun items(
        resolution: FeiniuHostResolution,
        ancestorGuid: String? = null,
        category: String? = null,
        page: Int = 1,
        pageSize: Int = 36,
        sortColumn: String = "create_time",
        sortType: String = "DESC",
        genres: Int? = null,
        resolutionTag: String? = null,
        watchedTag: String? = null,
        decade: String? = null,
    ): FeiniuItemListData =
        client.execute(
            resolution,
            FeiniuApiCall(
                path = "/v/api/v1/item/list",
                method = FeiniuApiCall.FeiniuMethod.Post,
                bodyText = MafeiJson.Default.encodeToString(
                    FeiniuItemListRequest.serializer(),
                    FeiniuItemListRequest(
                        ancestorGuid = ancestorGuid?.trim()?.takeIf { it.isNotEmpty() },
                        excludeGroupedVideo = 1,
                        sortType = sortType.trim().uppercase(),
                        sortColumn = sortColumn,
                        pageSize = pageSize,
                        page = page,
                        tags = FeiniuItemListTags(
                            type = itemTypesByCategory(category),
                            genres = genres,
                            resolution = resolutionTag,
                            watched = watchedTag,
                            decade = decade,
                        ),
                    ),
                ),
                authRequirement = FeiniuApiCall.AuthRequirement.Token,
            ),
        ) { envelope, _ ->
            val data = envelope.data
                ?: return@execute FeiniuItemListData()
            MafeiJson.Default.decodeFromJsonElement(FeiniuItemListData.serializer(), data)
        }

    suspend fun favorites(
        resolution: FeiniuHostResolution,
        ancestorGuid: String? = null,
        category: String? = null,
        page: Int = 1,
        pageSize: Int = 36,
    ): FeiniuItemListData = itemsPage("/v/api/v1/favorite/list", resolution, ancestorGuid, category, page, pageSize)

    suspend fun search(resolution: FeiniuHostResolution, keyword: String): List<FeiniuMediaItem> =
        client.execute(
            resolution,
            FeiniuApiCall(
                path = "/v/api/v1/search/list",
                method = FeiniuApiCall.FeiniuMethod.Get,
                queryParameters = listOf("q" to keyword.trim()),
                authRequirement = FeiniuApiCall.AuthRequirement.Token,
            ),
        ) { envelope, _ -> decodeList(envelope, FeiniuMediaItem.serializer()) }

    suspend fun markFavorite(resolution: FeiniuHostResolution, guid: String) =
        guidCall(resolution, "/v/api/v1/item/favorite", FeiniuApiCall.FeiniuMethod.Put, guid)

    suspend fun unmarkFavorite(resolution: FeiniuHostResolution, guid: String) =
        guidCall(resolution, "/v/api/v1/item/favorite", FeiniuApiCall.FeiniuMethod.Delete, guid)

    suspend fun markWatched(resolution: FeiniuHostResolution, guid: String) =
        guidCall(resolution, "/v/api/v1/item/watched", FeiniuApiCall.FeiniuMethod.Post, guid)

    suspend fun unmarkWatched(resolution: FeiniuHostResolution, guid: String) =
        guidCall(resolution, "/v/api/v1/item/watched", FeiniuApiCall.FeiniuMethod.Delete, guid)

    suspend fun itemDetail(resolution: FeiniuHostResolution, guid: String): cn.mafei.cmp.providers.feiniu.core.FeiniuItemDetail =
        client.execute(
            resolution,
            FeiniuApiCall("/v/api/v1/item/${guid.trim()}", FeiniuApiCall.FeiniuMethod.Get, authRequirement = FeiniuApiCall.AuthRequirement.Token),
        ) { envelope, _ ->
            val data = envelope.data
                ?: throw cn.mafei.cmp.providers.feiniu.core.FeiniuDecodeError("Feiniu item detail is empty")
            MafeiJson.Default.decodeFromJsonElement(cn.mafei.cmp.providers.feiniu.core.FeiniuItemDetail.serializer(), data)
        }

    suspend fun seasons(resolution: FeiniuHostResolution, guid: String): List<FeiniuMediaItem> =
        client.execute(
            resolution,
            FeiniuApiCall("/v/api/v1/season/list/${guid.trim()}", FeiniuApiCall.FeiniuMethod.Get, authRequirement = FeiniuApiCall.AuthRequirement.Token),
        ) { envelope, _ -> decodeList(envelope, FeiniuMediaItem.serializer()) }

    suspend fun episodes(resolution: FeiniuHostResolution, guid: String): List<FeiniuMediaItem> =
        client.execute(
            resolution,
            FeiniuApiCall("/v/api/v1/episode/list/${guid.trim()}", FeiniuApiCall.FeiniuMethod.Get, authRequirement = FeiniuApiCall.AuthRequirement.Token),
        ) { envelope, _ -> decodeList(envelope, FeiniuMediaItem.serializer()) }

    // ------------------------------------------------------------------ internals

    private suspend fun itemsPage(
        path: String,
        resolution: FeiniuHostResolution,
        ancestorGuid: String?,
        category: String?,
        page: Int,
        pageSize: Int,
    ): FeiniuItemListData =
        client.execute(
            resolution,
            FeiniuApiCall(
                path = path,
                method = FeiniuApiCall.FeiniuMethod.Post,
                bodyText = MafeiJson.Default.encodeToString(
                    FeiniuItemListRequest.serializer(),
                    FeiniuItemListRequest(
                        ancestorGuid = ancestorGuid?.trim()?.takeIf { it.isNotEmpty() },
                        excludeGroupedVideo = 1,
                        sortType = "DESC",
                        sortColumn = "create_time",
                        pageSize = pageSize,
                        page = page,
                        tags = FeiniuItemListTags(type = itemTypesByCategory(category)),
                    ),
                ),
                authRequirement = FeiniuApiCall.AuthRequirement.Token,
            ),
        ) { envelope, _ ->
            val data = envelope.data
                ?: return@execute FeiniuItemListData()
            MafeiJson.Default.decodeFromJsonElement(FeiniuItemListData.serializer(), data)
        }

    private suspend fun guidCall(
        resolution: FeiniuHostResolution,
        path: String,
        method: FeiniuApiCall.FeiniuMethod,
        guid: String,
    ) {
        client.execute(
            resolution,
            FeiniuApiCall(
                path = path,
                method = method,
                bodyText = """{"item_guid":"${guid.trim()}"}""",
                authRequirement = FeiniuApiCall.AuthRequirement.Token,
            ),
        ) { _, _ -> true }
    }

    private fun <T> decodeList(envelope: cn.mafei.cmp.providers.feiniu.core.FeiniuEnvelope, serializer: KSerializer<T>): List<T> {
        val data = envelope.data ?: return emptyList()
        return try {
            MafeiJson.Default.decodeFromJsonElement(ListSerializer(serializer), data)
        } catch (e: SerializationException) {
            throw cn.mafei.cmp.providers.feiniu.core.FeiniuDecodeError("Feiniu list payload does not match the expected shape", e)
        } catch (e: IllegalArgumentException) {
            throw cn.mafei.cmp.providers.feiniu.core.FeiniuDecodeError("Feiniu list payload does not match the expected shape", e)
        }
    }

    private fun itemTypesByCategory(category: String?): List<String> = when (category?.trim()?.lowercase()) {
        "movie" -> listOf("Movie")
        "tv" -> listOf("TV")
        "directory" -> listOf("Directory")
        "video" -> listOf("Video")
        "live", "livetv", "iptv", "livechannel" -> listOf("LiveChannel")
        "others" -> listOf("Directory", "Video")
        else -> listOf("Movie", "TV", "Directory", "Video", "LiveChannel")
    }
}
