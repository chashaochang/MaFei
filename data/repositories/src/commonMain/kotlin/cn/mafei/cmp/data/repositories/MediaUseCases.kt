package cn.mafei.cmp.data.repositories

import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.PageRequest

/** Injectable feature entry points; provider selection/caching stays in the repository. */
class BrowseMediaUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(session: MediaSession, query: MediaQuery, request: PageRequest = PageRequest(), refresh: Boolean = false) =
        repository.browse(session, query, request, refresh)
}

class GetMediaDetailUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(session: MediaSession, id: MediaId, refresh: Boolean = false) = repository.item(session, id, refresh)
}
