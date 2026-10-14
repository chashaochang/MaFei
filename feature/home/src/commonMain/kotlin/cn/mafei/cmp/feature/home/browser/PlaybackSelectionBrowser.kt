package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.repositories.*
import kotlinx.coroutines.CancellationException

internal class PlaybackSelectionBrowser(context: BrowserContext, private val playbackPreferences: MediaPlaybackPreferences?) : BrowserController(context) {
    /** 用户在详情页指定播放版本；与旧版一致，按当前账号和媒体条目持久化。 */
    suspend fun selectMediaSource(sourceId: String?) {
        val detail = state.value.detail ?: return
        val selected = sourceId?.let { id ->
            detail.mediaSources.firstOrNull { it.id == id && it.playable != false }
        } ?: return
        mutableState.value = state.value.copy(
            selectedMediaSourceId = selected.id,
            selectedAudioStreamIndex = selected.legacyDefaultAudioStreamIndex(),
            selectedSubtitleStreamIndex = selected.legacyDefaultSubtitleStreamIndex(),
        )
        try {
            playbackPreferences?.setPreferredMediaSourceId(session.accountId, detail.id, sourceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 持久化失败只影响下次预选，不中断当前选择。
        }
    }

    fun selectAudioTrack(index: Int?) = mutatePlaybackSelection { copy(selectedAudioStreamIndex = index) }

    fun selectSubtitleTrack(index: Int?) = mutatePlaybackSelection { copy(selectedSubtitleStreamIndex = index) }

    private inline fun mutatePlaybackSelection(transform: MediaBrowserState.() -> MediaBrowserState) {
        mutableState.value = state.value.transform()
    }

    /** 当前详情页起播应携带的用户选择；未选择时为默认协商。 */
    fun playbackOptionsFor(item: MediaItem): cn.mafei.cmp.data.adapter_contract.PlaybackOptions {
        val current = state.value
        if (current.detailId != item.id) return cn.mafei.cmp.data.adapter_contract.PlaybackOptions()
        return cn.mafei.cmp.data.adapter_contract.PlaybackOptions(
            mediaSourceId = current.selectedMediaSourceId,
            audioStreamIndex = current.selectedAudioStreamIndex,
            subtitleStreamIndex = current.selectedSubtitleStreamIndex,
        )
    }

    /**
     * 起播 [item] 时的播放队列，等价于旧版 QueueManager.initializePlaybackQueue 的输入。
     *
     * 详情页/季详情页起播时集列表已经加载，直接用。**首页「继续观看」直接起播**时
     * 手里只有条目本身，那一季的集从来没取过 —— 这时按 series(+season) 拉一次，
     * 否则同一部剧从首页播就没有「选集」也没有「下一集」。
     */
    suspend fun playbackQueueFor(item: MediaItem): PlaybackQueue {
        val loaded = state.value.detailEpisodes
        if (item.mediaType != MediaType.Episode || loaded.any { it.id == item.id }) {
            return PlaybackQueue.forItem(item, loaded)
        }
        val seriesId = item.seriesId ?: return PlaybackQueue.forItem(item, emptyList())
        val episodes = try {
            repository.browse(
                session = session,
                query = MediaQuery.Episodes(seriesId, item.seasonId),
                // 一季通常几十集；MAX_PAGE_SIZE 一次拿完，不做游标循环。
                request = PageRequest(pageSize = PageRequest.MAX_PAGE_SIZE),
                refresh = false,
            ).value.items
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 拉不到就当单片播放：队列构造绝不能把起播挡下来。
            emptyList()
        }
        return PlaybackQueue.forItem(item, episodes)
    }
}
