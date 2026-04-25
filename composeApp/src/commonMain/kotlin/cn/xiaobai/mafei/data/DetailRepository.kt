package cn.xiaobai.mafei.data

import cn.xiaobai.mafei.screens.JellyfinServer
import cn.xiaobai.mafei.storage.SessionRecord

interface DetailRepository {
    suspend fun loadDetail(
        itemId: String,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): VideoDetail?
}

class FakeDetailRepository(
    private val fakeVideoRepository: FakeVideoRepository,
) : DetailRepository {
    override suspend fun loadDetail(
        itemId: String,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): VideoDetail? {
        return fakeVideoRepository.getDetail(itemId)
    }
}
