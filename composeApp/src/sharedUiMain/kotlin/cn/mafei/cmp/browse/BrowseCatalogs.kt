package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.feature.home.homeLatestRequest
import cn.mafei.cmp.feature.music.MusicCatalogStore
import cn.mafei.cmp.session.*

internal class BrowseCatalogs(
    val music: MusicCatalogStore,
    val live: cn.mafei.cmp.data.repositories.PagedMediaStore<Unit>,
    val photos: cn.mafei.cmp.data.repositories.PagedMediaStore<cn.mafei.cmp.data.adapter_contract.PhotoCatalogQuery>,
    val latest: cn.mafei.cmp.data.repositories.PagedMediaStore<cn.mafei.cmp.core.domain.MediaCollection>,
)

@Composable
internal fun rememberBrowseCatalogs(
    repository: cn.mafei.cmp.data.repositories.MediaRepository,
    session: cn.mafei.cmp.data.repositories.MediaSession,
): BrowseCatalogs {
    val musicStore =
        remember(session, repository) {
            MusicCatalogStore(
                loadPage = { query, request, refresh ->
                    repository.browse(session, cn.mafei.cmp.data.repositories.MediaQuery.Music(query), request, refresh).value
                },
                loadItem = { id, refresh -> repository.item(session, id, refresh).value },
            )
        }
    val liveStore =
        remember(session, repository) {
            cn.mafei.cmp.data.repositories.PagedMediaStore<Unit>(100) { _, request, refresh ->
                repository.browse(session, cn.mafei.cmp.data.repositories.MediaQuery.LiveChannels, request, refresh).value
            }
        }
    val photoStore =
        remember(session, repository) {
            cn.mafei.cmp.data.repositories.PagedMediaStore<cn.mafei.cmp.data.adapter_contract.PhotoCatalogQuery>(60) { query, request, refresh ->
                repository.browse(session, cn.mafei.cmp.data.repositories.MediaQuery.Photos(query), request, refresh).value
            }
        }
    val latestStore =
        remember(session, repository) {
            cn.mafei.cmp.data.repositories.PagedMediaStore<cn.mafei.cmp.core.domain.MediaCollection>(30) { collection, request, refresh ->
                repository.browse(session, cn.mafei.cmp.data.repositories.MediaQuery.Items(collection.id), collection.homeLatestRequest(request), refresh).value
            }
        }
    return remember(musicStore, liveStore, photoStore, latestStore) { BrowseCatalogs(musicStore, liveStore, photoStore, latestStore) }
}
