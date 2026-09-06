package cn.mafei.cmp.providers.jellyfin.catalog

import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinItemQuery
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinRequests
import cn.mafei.cmp.providers.jellyfin.wire.BaseItemDto
import cn.mafei.cmp.providers.jellyfin.wire.ItemField
import cn.mafei.cmp.providers.jellyfin.wire.QueryResultDto
import kotlinx.serialization.builtins.serializer

/**
 * Stable catalog surface: home views, item pages (with offset paging),
 * details, seasons and episodes. All calls require an authenticated session
 * (401 maps to [cn.mafei.cmp.providers.jellyfin.core.JellyfinAuthenticationExpiredError]).
 */
interface JellyfinCatalogApi {

    /** Home screen libraries ("views"). */
    suspend fun views(userId: String): QueryResultDto<BaseItemDto>

    /** Offset-paged item query; cursor synthesis is the data adapter's job. */
    suspend fun items(query: JellyfinItemQuery): QueryResultDto<BaseItemDto>

    /** Full detail for one item (fields control payload richness). */
    suspend fun item(userId: String, itemId: String, fields: List<ItemField> = emptyList()): BaseItemDto

    suspend fun seasons(userId: String, seriesId: String, fields: List<ItemField> = emptyList()): QueryResultDto<BaseItemDto>

    suspend fun episodes(
        userId: String,
        seriesId: String,
        seasonId: String? = null,
        startIndex: Int = 0,
        limit: Int = 200,
        fields: List<ItemField> = emptyList(),
    ): QueryResultDto<BaseItemDto>
}

class DefaultJellyfinCatalogApi(
    private val client: JellyfinClient,
) : JellyfinCatalogApi {

    private val queryResultSerializer = QueryResultDto.serializer(BaseItemDto.serializer())

    override suspend fun views(userId: String): QueryResultDto<BaseItemDto> =
        client.executeDecoding(JellyfinRequests.views(client.config.baseUrl, userId), queryResultSerializer)

    override suspend fun items(query: JellyfinItemQuery): QueryResultDto<BaseItemDto> =
        client.executeDecoding(JellyfinRequests.items(client.config.baseUrl, query), queryResultSerializer)

    override suspend fun item(userId: String, itemId: String, fields: List<ItemField>): BaseItemDto =
        client.executeDecoding(
            JellyfinRequests.itemById(client.config.baseUrl, userId, itemId, fields),
            BaseItemDto.serializer(),
        )

    override suspend fun seasons(userId: String, seriesId: String, fields: List<ItemField>): QueryResultDto<BaseItemDto> =
        client.executeDecoding(JellyfinRequests.seasons(client.config.baseUrl, userId, seriesId, fields), queryResultSerializer)

    override suspend fun episodes(
        userId: String,
        seriesId: String,
        seasonId: String?,
        startIndex: Int,
        limit: Int,
        fields: List<ItemField>,
    ): QueryResultDto<BaseItemDto> =
        client.executeDecoding(
            JellyfinRequests.episodes(client.config.baseUrl, userId, seriesId, seasonId, startIndex, limit, fields),
            queryResultSerializer,
        )
}
