package cn.xiaobai.mafei.data

import cn.xiaobai.mafei.data.jellyfin.JellyfinLoadIssue
import cn.xiaobai.mafei.screens.JellyfinServer
import cn.xiaobai.mafei.storage.SessionRecord

data class MediaViewBrowseResult(
    val view: HomeMediaView?,
    val loadIssue: JellyfinLoadIssue? = null,
    val hasMore: Boolean = false,
    val nextStartIndex: Int = 0,
    val totalCount: Int = view?.items?.size ?: 0,
)

enum class MediaViewSortOption(
    val label: String,
) {
    RECENT("最近新增"),
    TITLE("名称"),
}

interface MediaViewRepository {
    suspend fun loadMediaView(
        viewId: String,
        startIndex: Int,
        limit: Int,
        sortOption: MediaViewSortOption,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): MediaViewBrowseResult
}

class FakeMediaViewRepository(
    private val fakeVideoRepository: FakeVideoRepository,
) : MediaViewRepository {
    override suspend fun loadMediaView(
        viewId: String,
        startIndex: Int,
        limit: Int,
        sortOption: MediaViewSortOption,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): MediaViewBrowseResult {
        val baseView = fakeVideoRepository.getHomeState().mediaViews.firstOrNull { it.viewId == viewId }
        val allItems = when (sortOption) {
            MediaViewSortOption.RECENT -> baseView?.items.orEmpty()
            MediaViewSortOption.TITLE -> baseView?.items.orEmpty().sortedBy { it.title.lowercase() }
        }
        val slice = allItems.drop(startIndex).take(limit)
        return MediaViewBrowseResult(
            view = baseView?.copy(items = slice),
            hasMore = startIndex + slice.size < allItems.size,
            nextStartIndex = startIndex + slice.size,
            totalCount = allItems.size,
        )
    }
}
