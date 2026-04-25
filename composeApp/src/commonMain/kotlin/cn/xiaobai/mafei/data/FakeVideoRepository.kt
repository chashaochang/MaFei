package cn.xiaobai.mafei.data

class FakeVideoRepository {
    private val videoDetails: List<VideoDetail> = listOf(
        VideoDetail(
            itemId = "foundation",
            title = "基地",
            metaLine = "科幻 · 剧集 · 2 季 · Jellyfin",
            synopsis = "一部围绕帝国衰落与文明重建展开的科幻史诗。这版骨架优先验证跨端续播、更新提示和剧集跳转链路。",
            isFavorite = true,
            continueEpisodeId = 4,
            continueProgressLabel = "S2E4 · 21:35",
            continueProgressPercent = 44,
            updateCount = 2,
            episodes = listOf(
                EpisodeItem(1, "基地的诞生", "45m", isNew = false),
                EpisodeItem(2, "谢顿计划", "47m", isNew = false),
                EpisodeItem(3, "帝国裂缝", "46m", isNew = false),
                EpisodeItem(4, "第二圣堂", "48m", isNew = true),
                EpisodeItem(5, "群星边界", "49m", isNew = true),
            ),
        ),
        VideoDetail(
            itemId = "three-body",
            title = "三体",
            metaLine = "科幻 · 剧集 · 1 季 · Jellyfin",
            synopsis = "用于模拟首页继续观看和详情页从头播放入口，也方便后续接真实的剧集数据和进度同步。",
            isFavorite = false,
            continueEpisodeId = 2,
            continueProgressLabel = "S1E2 · 08:10",
            continueProgressPercent = 17,
            updateCount = 1,
            episodes = listOf(
                EpisodeItem(1, "宇宙闪烁", "52m", isNew = false),
                EpisodeItem(2, "红岸往事", "51m", isNew = false),
                EpisodeItem(3, "不要回答", "50m", isNew = true),
            ),
        ),
        VideoDetail(
            itemId = "severance",
            title = "人生切割术",
            metaLine = "悬疑 · 剧集 · 1 季 · Jellyfin",
            synopsis = "用来承接追更提示、选集入口和播放器错误回退占位。",
            isFavorite = false,
            continueEpisodeId = 1,
            continueProgressLabel = "S1E1 · 33:20",
            continueProgressPercent = 71,
            updateCount = 0,
            episodes = listOf(
                EpisodeItem(1, "入职切割", "42m", isNew = false),
                EpisodeItem(2, "楼层协议", "41m", isNew = false),
                EpisodeItem(3, "记忆裂缝", "43m", isNew = false),
            ),
        ),
    )

    fun getHomeState(): HomeState {
        val continueWatching = videoDetails.map {
            val continueEpisode = it.episodes.firstOrNull { episode -> episode.id == it.continueEpisodeId }
            ContinueWatchingItem(
                itemId = it.itemId,
                title = it.title,
                episodeTitle = continueEpisode?.title ?: "第 ${it.continueEpisodeId} 集",
                progressLabel = "续播至 ${it.continueProgressLabel}",
                progressPercent = it.continueProgressPercent,
            )
        }

        val updates = videoDetails
            .filter { it.updateCount > 0 }
            .map { detail ->
                val latest = detail.episodes.lastOrNull { it.isNew } ?: detail.episodes.last()
                UpdateItem(
                    itemId = detail.itemId,
                    title = detail.title,
                    latestEpisodeTitle = latest.title,
                    newEpisodeCount = detail.updateCount,
                )
            }

        val nextUp = videoDetails.mapNotNull { detail ->
            val nextEpisode = detail.episodes.firstOrNull { episode -> episode.id > detail.continueEpisodeId }
                ?: detail.episodes.firstOrNull { episode -> episode.isNew }
                ?: return@mapNotNull null
            NextUpItem(
                itemId = detail.itemId,
                title = detail.title,
                episodeTitle = nextEpisode.title,
                subtitle = "下一集推荐 · ${nextEpisode.durationLabel}",
            )
        }

        val mediaViews = listOf(
            HomeMediaView(
                viewId = "all",
                title = "全部",
                subtitle = "最近常看和推荐内容",
                items = search("").take(8),
            ),
            HomeMediaView(
                viewId = "series",
                title = "剧集",
                subtitle = "按剧集视图浏览",
                items = search("").filter { it.itemId in listOf("foundation", "three-body", "severance", "shogun", "dark") },
            ),
            HomeMediaView(
                viewId = "movies",
                title = "电影",
                subtitle = "快速进入电影内容",
                items = listOf(
                    LibraryItem("dune-part-two", "沙丘 2", "电影 · 科幻史诗"),
                    LibraryItem("arrival", "降临", "电影 · 科幻剧情"),
                    LibraryItem("blade-runner-2049", "银翼杀手 2049", "电影 · 视觉科幻"),
                ),
            ),
        )

        return HomeState(
            continueWatching = continueWatching,
            nextUp = nextUp,
            mediaViews = mediaViews,
            updates = updates,
            latestAdded = listOf(
                LibraryItem("dune-part-two", "沙丘 2", "最新入库 · 电影 · 166 分钟"),
                LibraryItem("shogun", "幕府将军", "最新入库 · 剧集 · 10 集"),
                LibraryItem("dark", "暗黑", "经典补全 · 德语悬疑"),
            ),
        )
    }

    fun getUpdates(): List<UpdateItem> = getHomeState().updates

    fun search(keyword: String): List<LibraryItem> {
        val query = keyword.trim()
        val all = videoDetails.map { detail ->
            val firstEpisode = detail.episodes.firstOrNull()
            val subtitle = listOf(
                detail.metaLine,
                firstEpisode?.let { "首集 ${it.title}" },
            ).filterNotNull().joinToString(" · ")
            LibraryItem(
                itemId = detail.itemId,
                title = detail.title,
                subtitle = subtitle,
            )
        }
        if (query.isBlank()) return all.take(12)

        return all.filter { item ->
            item.title.contains(query, ignoreCase = true) ||
                item.subtitle.contains(query, ignoreCase = true)
        }
    }

    fun getDetail(itemId: String): VideoDetail? = videoDetails.firstOrNull { it.itemId == itemId }

    fun getEpisode(itemId: String, episodeId: Int): EpisodeItem? {
        return getDetail(itemId)?.episodes?.firstOrNull { it.id == episodeId }
    }
}
