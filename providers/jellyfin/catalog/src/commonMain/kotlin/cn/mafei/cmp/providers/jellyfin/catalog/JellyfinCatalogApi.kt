package cn.mafei.cmp.providers.jellyfin.catalog

import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinItemQuery
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinRequests
import cn.mafei.cmp.providers.jellyfin.wire.BaseItemDto
import cn.mafei.cmp.providers.jellyfin.wire.ItemField
import cn.mafei.cmp.providers.jellyfin.wire.QueryResultDto
import cn.mafei.cmp.providers.jellyfin.wire.RecommendationDto
import kotlinx.serialization.builtins.ListSerializer

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

    suspend fun recommendations(
        userId: String,
        categoryLimit: Int = 4,
        itemLimit: Int = 4,
        fields: List<ItemField> = emptyList(),
    ): List<RecommendationDto>

    suspend fun resume(
        userId: String,
        startIndex: Int = 0,
        limit: Int,
        includeItemTypes: List<String>,
        fields: List<ItemField> = emptyList(),
    ): QueryResultDto<BaseItemDto>

    /**
     * "More like this" for one item via GET /Items/{itemId}/Similar; the
     * 10.10.7 operation answers a bare item array.
     */
    suspend fun similar(
        userId: String,
        itemId: String,
        limit: Int,
        fields: List<ItemField> = emptyList(),
    ): List<BaseItemDto>

    suspend fun nextUp(
        userId: String,
        limit: Int,
        fields: List<ItemField> = emptyList(),
        seriesId: String? = null,
        disableFirstEpisode: Boolean = false,
        enableResumable: Boolean = false,
        enableRewatching: Boolean = false,
        nextUpDateCutoffEpochMs: Long? = null,
    ): QueryResultDto<BaseItemDto>

    suspend fun latest(
        userId: String,
        parentId: String? = null,
        limit: Int,
        includeItemTypes: List<String>,
        fields: List<ItemField> = emptyList(),
    ): List<BaseItemDto>

    /**
     * Full detail for one item via the official GET /Items/{itemId}?userId=
     * operation (the OpenAPI operation carries no `fields` parameter).
     */
    suspend fun item(userId: String, itemId: String): BaseItemDto

    /** Person detail via GET /Persons/{name}. */
    suspend fun person(name: String): BaseItemDto

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
    private val recommendationListSerializer = ListSerializer(RecommendationDto.serializer())
    private val itemListSerializer = ListSerializer(BaseItemDto.serializer())

    override suspend fun views(userId: String): QueryResultDto<BaseItemDto> =
        client.executeDecoding(JellyfinRequests.views(client.config.baseUrl, userId), queryResultSerializer)

    override suspend fun items(query: JellyfinItemQuery): QueryResultDto<BaseItemDto> =
        client.executeDecoding(JellyfinRequests.items(client.config.baseUrl, query), queryResultSerializer)

    override suspend fun recommendations(
        userId: String,
        categoryLimit: Int,
        itemLimit: Int,
        fields: List<ItemField>,
    ): List<RecommendationDto> = client.executeDecoding(
        JellyfinRequests.recommendations(client.config.baseUrl, userId, categoryLimit, itemLimit, fields),
        recommendationListSerializer,
    )

    override suspend fun resume(
        userId: String,
        startIndex: Int,
        limit: Int,
        includeItemTypes: List<String>,
        fields: List<ItemField>,
    ): QueryResultDto<BaseItemDto> = client.executeDecoding(
        JellyfinRequests.resume(client.config.baseUrl, userId, startIndex, limit, includeItemTypes, fields),
        queryResultSerializer,
    )

    override suspend fun similar(
        userId: String,
        itemId: String,
        limit: Int,
        fields: List<ItemField>,
    ): List<BaseItemDto> = client.executeDecoding(
        JellyfinRequests.similar(client.config.baseUrl, userId, itemId, limit, fields),
        itemListSerializer,
    )

    override suspend fun nextUp(
        userId: String,
        limit: Int,
        fields: List<ItemField>,
        seriesId: String?,
        disableFirstEpisode: Boolean,
        enableResumable: Boolean,
        enableRewatching: Boolean,
        nextUpDateCutoffEpochMs: Long?,
    ): QueryResultDto<BaseItemDto> = client.executeDecoding(
        JellyfinRequests.nextUp(
            base = client.config.baseUrl,
            userId = userId,
            limit = limit,
            fields = fields,
            seriesId = seriesId,
            disableFirstEpisode = disableFirstEpisode,
            enableResumable = enableResumable,
            enableRewatching = enableRewatching,
            nextUpDateCutoffEpochMs = nextUpDateCutoffEpochMs,
        ),
        queryResultSerializer,
    )

    override suspend fun latest(
        userId: String,
        parentId: String?,
        limit: Int,
        includeItemTypes: List<String>,
        fields: List<ItemField>,
    ): List<BaseItemDto> = client.executeDecoding(
        JellyfinRequests.latest(client.config.baseUrl, userId, parentId, limit, includeItemTypes, fields),
        itemListSerializer,
    )

    override suspend fun item(userId: String, itemId: String): BaseItemDto =
        client.executeDecoding(
            JellyfinRequests.itemById(client.config.baseUrl, userId, itemId),
            cn.mafei.cmp.providers.jellyfin.wire.ItemWithOriginalDatesSerializer,
        )

    override suspend fun person(name: String): BaseItemDto =
        client.executeDecoding(
            JellyfinRequests.personByName(client.config.baseUrl, name),
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
