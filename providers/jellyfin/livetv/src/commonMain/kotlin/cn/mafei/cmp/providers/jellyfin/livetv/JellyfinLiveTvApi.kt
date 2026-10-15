package cn.mafei.cmp.providers.jellyfin.livetv

import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinRequests
import cn.mafei.cmp.providers.jellyfin.wire.BaseItemDto
import cn.mafei.cmp.providers.jellyfin.wire.QueryResultDto

interface JellyfinLiveTvApi {
    suspend fun channels(userId: String, startIndex: Int = 0, limit: Int = 100): QueryResultDto<BaseItemDto>
}

class DefaultJellyfinLiveTvApi(private val client: JellyfinClient) : JellyfinLiveTvApi {
    override suspend fun channels(userId: String, startIndex: Int, limit: Int): QueryResultDto<BaseItemDto> =
        client.executeDecoding(JellyfinRequests.liveChannels(client.config.baseUrl, userId, startIndex, limit),
            QueryResultDto.serializer(BaseItemDto.serializer()))
}
