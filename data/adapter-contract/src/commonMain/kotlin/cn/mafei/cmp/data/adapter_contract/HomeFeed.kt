package cn.mafei.cmp.data.adapter_contract

import cn.mafei.cmp.core.domain.MediaFilter
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaSort
import cn.mafei.cmp.core.domain.MediaSortField
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.SortDirection
import kotlinx.serialization.Serializable

/** Fixed-size, provider-neutral data required by the home destination. */
data class HomeFeedRequest(
    val recommendationLimit: Int = 6,
    val resumeLimit: Int = 12,
    val nextUpLimit: Int = 24,
    val latestLimit: Int = 30,
) {
    init {
        require(recommendationLimit > 0)
        require(resumeLimit > 0)
        require(nextUpLimit > 0)
        require(latestLimit > 0)
    }
}

/** Serializable so [cn.mafei.cmp.data.repositories.MediaRepository] can persist a home snapshot for cache-first cold starts. */
@Serializable
data class HomeFeed(
    val recommendations: List<MediaItem> = emptyList(),
    val resume: List<MediaItem> = emptyList(),
    val nextUp: List<MediaItem> = emptyList(),
    val latest: List<MediaItem> = emptyList(),
)

/**
 * Conservative fallback for test doubles and providers without dedicated
 * home endpoints. Real adapters should override this when their server has
 * authoritative resume, next-up or recommendation APIs.
 */
internal suspend fun MediaProviderAdapter.derivedHomeFeed(request: HomeFeedRequest): HomeFeed {
    val latest = items(
        collectionId = null,
        request = PageRequest(
            pageSize = request.latestLimit,
            sort = MediaSort(MediaSortField.DateAdded, SortDirection.Descending),
            filter = MediaFilter(),
        ),
    ).items
    return HomeFeed(
        recommendations = latest.take(request.recommendationLimit),
        resume = latest.filter { it.userData.hasProgress && !it.userData.isPlayed }.take(request.resumeLimit),
        nextUp = latest.filter {
            it.mediaType == cn.mafei.cmp.core.domain.MediaType.Episode &&
                !it.userData.isPlayed && !it.userData.hasProgress
        }.take(request.nextUpLimit),
        latest = latest,
    )
}
