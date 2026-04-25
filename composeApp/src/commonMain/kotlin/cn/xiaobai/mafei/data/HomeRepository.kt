package cn.xiaobai.mafei.data

import cn.xiaobai.mafei.screens.JellyfinServer
import cn.xiaobai.mafei.storage.SessionRecord

interface HomeRepository {
    suspend fun loadHomeState(
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): HomeState
}

class FakeHomeRepository(
    private val fakeVideoRepository: FakeVideoRepository,
) : HomeRepository {
    override suspend fun loadHomeState(
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): HomeState {
        return fakeVideoRepository.getHomeState()
    }
}
