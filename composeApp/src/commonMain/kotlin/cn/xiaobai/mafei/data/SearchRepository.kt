package cn.xiaobai.mafei.data

import cn.xiaobai.mafei.data.jellyfin.JellyfinLoadIssue
import cn.xiaobai.mafei.screens.JellyfinServer
import cn.xiaobai.mafei.storage.SessionRecord

data class SearchResult(
    val items: List<LibraryItem>,
    val loadIssue: JellyfinLoadIssue? = null,
)

interface SearchRepository {
    suspend fun search(
        keyword: String,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): SearchResult
}

class FakeSearchRepository(
    private val fakeVideoRepository: FakeVideoRepository,
) : SearchRepository {
    override suspend fun search(
        keyword: String,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): SearchResult {
        return SearchResult(items = fakeVideoRepository.search(keyword))
    }
}
