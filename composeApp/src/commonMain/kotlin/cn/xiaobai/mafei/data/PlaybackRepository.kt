package cn.xiaobai.mafei.data

import cn.xiaobai.mafei.data.jellyfin.JellyfinLoadIssue
import cn.xiaobai.mafei.screens.JellyfinServer
import cn.xiaobai.mafei.storage.SessionRecord

data class PlaybackContext(
    val itemId: String,
    val playbackItemId: String,
    val showTitle: String,
    val episodeTitle: String,
    val streamUrl: String,
    val streamTypeLabel: String,
    val playSessionId: String?,
    val mediaSourceId: String?,
    val mediaContainer: String?,
    val runtimeLabel: String,
    val startPositionLabel: String,
    val statusMessage: String,
    val loadIssue: JellyfinLoadIssue? = null,
) {
    companion object {
        fun placeholder(
            itemId: String,
            episodeId: Int,
            message: String = "正在准备播放上下文…",
        ): PlaybackContext {
            return PlaybackContext(
                itemId = itemId,
                playbackItemId = "$itemId#$episodeId",
                showTitle = "加载中",
                episodeTitle = "第 $episodeId 集",
                streamUrl = "N/A",
                streamTypeLabel = "占位",
                playSessionId = null,
                mediaSourceId = null,
                mediaContainer = null,
                runtimeLabel = "--",
                startPositionLabel = "00:00",
                statusMessage = message,
                loadIssue = null,
            )
        }
    }
}

interface PlaybackRepository {
    suspend fun loadPlaybackContext(
        itemId: String,
        episodeId: Int,
        playbackItemId: String?,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): PlaybackContext
}

class FakePlaybackRepository(
    private val fakeVideoRepository: FakeVideoRepository,
) : PlaybackRepository {
    override suspend fun loadPlaybackContext(
        itemId: String,
        episodeId: Int,
        playbackItemId: String?,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): PlaybackContext {
        val detail = fakeVideoRepository.getDetail(itemId)
        val episode = fakeVideoRepository.getEpisode(itemId, episodeId)
        val playbackId = playbackItemId ?: "${itemId}-ep-$episodeId"

        return PlaybackContext(
            itemId = itemId,
            playbackItemId = playbackId,
            showTitle = detail?.title ?: "未命名节目",
            episodeTitle = episode?.title ?: "第 $episodeId 集",
            streamUrl = "https://example.invalid/play/$playbackId.m3u8",
            streamTypeLabel = "占位流",
            playSessionId = null,
            mediaSourceId = null,
            mediaContainer = "m3u8",
            runtimeLabel = episode?.durationLabel ?: "--",
            startPositionLabel = detail?.continueProgressLabel ?: "00:00",
            statusMessage = "当前为占位播放上下文，后续可替换为真实播放器内核。",
            loadIssue = null,
        )
    }
}
