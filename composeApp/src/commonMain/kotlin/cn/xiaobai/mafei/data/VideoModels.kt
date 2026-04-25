package cn.xiaobai.mafei.data

import cn.xiaobai.mafei.data.jellyfin.JellyfinLoadIssue

data class HomeState(
    val continueWatching: List<ContinueWatchingItem>,
    val nextUp: List<NextUpItem>,
    val mediaViews: List<HomeMediaView>,
    val updates: List<UpdateItem>,
    val latestAdded: List<LibraryItem>,
    val loadIssue: JellyfinLoadIssue? = null,
)

data class ContinueWatchingItem(
    val itemId: String,
    val title: String,
    val episodeTitle: String,
    val progressLabel: String,
    val progressPercent: Int,
    val backdropUrl: String? = null,
)

data class UpdateItem(
    val itemId: String,
    val title: String,
    val latestEpisodeTitle: String,
    val newEpisodeCount: Int,
    val thumbnailUrl: String? = null,
)

data class NextUpItem(
    val itemId: String,
    val title: String,
    val episodeTitle: String,
    val subtitle: String,
    val thumbnailUrl: String? = null,
)

data class HomeMediaView(
    val viewId: String,
    val title: String,
    val subtitle: String,
    val items: List<LibraryItem>,
)

data class LibraryItem(
    val itemId: String,
    val title: String,
    val subtitle: String,
    val posterUrl: String? = null,
)

data class VideoDetail(
    val itemId: String,
    val title: String,
    val metaLine: String,
    val synopsis: String,
    val isFavorite: Boolean = false,
    val continueEpisodeId: Int,
    val continueProgressLabel: String,
    val continueProgressPercent: Int,
    val updateCount: Int,
    val episodes: List<EpisodeItem>,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val loadIssue: JellyfinLoadIssue? = null,
)

data class EpisodeItem(
    val id: Int,
    val title: String,
    val durationLabel: String,
    val isNew: Boolean,
    val playbackItemId: String? = null,
    val thumbnailUrl: String? = null,
)
