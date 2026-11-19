package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.feature.detail.legacyPlaybackResumePositionMs
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.home.PlaybackQueue
import cn.mafei.cmp.feature.music.MusicRepeatMode
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Session-owned playback intent, queue and user selections. No route or window state is captured. */
internal class BrowsePlayerState(
    val playback: SessionPlaybackRuntime?,
    private val browser: MediaBrowser,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val shortDramaEntrance: ShortDramaEntrance? = null,
    private val shortDramaAvailable: () -> Boolean = { false },
) {
    var playingMedia by mutableStateOf<MediaItem?>(null)
    var playerFullscreen by mutableStateOf(false)
    var playbackBusy by mutableStateOf(false)
    var playbackError by mutableStateOf<String?>(null)
    var playQueue by mutableStateOf<PlaybackQueue?>(null)
    var musicRepeatMode by mutableStateOf(MusicRepeatMode.Off)
    var requestedBitrate by mutableStateOf<Int?>(null)
    var playerExpanded by mutableStateOf(false)
    var pendingSeekPosition by mutableStateOf<Long?>(null)
    var shortDrama by mutableStateOf<ShortDramaRequest?>(null)
        private set
    var retryShortDramaEntry by mutableStateOf<(() -> Unit)?>(null)
        private set

    private var entryJob: Job? = null

    fun openMedia(item: MediaItem, libraryId: String?, fallback: () -> Unit, isCurrent: () -> Boolean = { true }) {
        val entrance = shortDramaEntrance
        entryJob?.cancel()
        retryShortDramaEntry = null
        if (entrance == null || !item.supportsShortDrama || playback == null) return fallback()
        entryJob = scope.launch {
            val matches = try {
                entrance.matches(item, libraryId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (!isCurrent()) return@launch
            if (!matches) {
                fallback()
                return@launch
            }
            val target = try {
                entrance.target(item)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (!isCurrent()) return@launch
            if (target == null) {
                retryShortDramaEntry = { openMedia(item, libraryId, fallback, isCurrent) }
            } else if (shortDramaAvailable()) {
                startShortDrama(target)
            } else {
                startPlaybackItem(target)
            }
        }
    }

    fun dismissShortDramaEntryFailure() { retryShortDramaEntry = null }

    suspend fun shortDramaLibraryMode(collection: MediaCollection): Boolean =
        shortDramaEntrance?.libraryMode(collection) ?: false

    suspend fun shortDramaWorkMode(item: MediaItem): Boolean? = shortDramaEntrance?.workMode(item)

    suspend fun setShortDramaLibrary(collection: MediaCollection, enabled: Boolean) {
        shortDramaEntrance?.setLibrary(collection, enabled)
    }

    suspend fun setShortDramaWork(item: MediaItem, enabled: Boolean?) {
        shortDramaEntrance?.setWork(item, enabled)
    }

    fun playFromEntry(item: MediaItem, libraryId: String?) = openMedia(item, libraryId, { startPlaybackItem(item) })

    private fun startShortDrama(item: MediaItem) {
        val runtime = playback ?: return
        if (playbackBusy || shortDrama != null) return
        playbackBusy = true
        playbackError = null
        val request = ShortDramaRequest(item, browser.playbackOptionsFor(item))
        scope.launch {
            try {
                runtime.stop()
                playingMedia = null
                playQueue = null
                playerExpanded = false
                playerFullscreen = false
                shortDrama = request
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                playbackError = "播放准备失败，请重试"
            } finally {
                playbackBusy = false
            }
        }
    }

    fun closeShortDrama() {
        shortDrama = null
    }

    fun startPlaybackItem(
        item: MediaItem,
        queueOverride: PlaybackQueue? = null,
        resumePositionMs: Long = if (item.mediaType == MediaType.LiveChannel) 0L else legacyPlaybackResumePositionMs(item),
        optionsOverride: cn.mafei.cmp.data.adapter_contract.PlaybackOptions? = null,
    ) {
        if (playback != null && !playbackBusy) {
            val previousMedia = playingMedia
            val previousQueue = playQueue
            val previouslyExpanded = playerExpanded
            playbackBusy = true
            playbackError = null
            playingMedia = item
            playerExpanded = if (item.mediaType == MediaType.Audio) previouslyExpanded && previousMedia?.mediaType == MediaType.Audio else true
            val existing = playQueue?.takeIf { queue -> queue.items.any { it.id == item.id } }
            val positionedQueue = queueOverride ?: existing?.movedTo(item.id)
            // 播放器先拿到一个立即可用的队列；首页缺少季列表时，出画后再异步扩充。
            playQueue = positionedQueue ?: PlaybackQueue.forItem(item, emptyList())
            scope.launch {
                var started = false
                try {
                    // 详情页上选过的版本/音轨/字幕只对当前详情条目生效。
                    val generation =
                        playback.play(
                            itemId = item.id,
                            resumePositionMs = resumePositionMs,
                            options =
                                (optionsOverride ?: browser.playbackOptionsFor(item)).copy(
                                    maxStreamingBitrate = requestedBitrate,
                                    live = item.mediaType == MediaType.LiveChannel,
                                ),
                        )
                    if (generation == null) {
                        playbackError = "当前媒体暂时无法播放"
                    } else {
                        started = true
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    println("[MaFeiPlayback] prepare failed: $failure")
                    playbackError = "播放准备失败，请重试"
                } finally {
                    playbackBusy = false
                    if (
                        !started &&
                            item.mediaType == MediaType.Audio &&
                            previousMedia?.mediaType == MediaType.Audio &&
                            playback.controller.state.value.resourceId == previousMedia.id.value
                    ) {
                        playingMedia = previousMedia
                        playQueue = previousQueue?.movedTo(previousMedia.id)
                        playerExpanded = previouslyExpanded
                    }
                }
                // 旧版也是在播放器完成 load 后扩充剧集队列。网络慢或失败都不阻塞出画；
                // 已有队列（上一集/下一集/选集）则直接复用，绝不重复拉整季。
                if (started && positionedQueue == null && item.mediaType == MediaType.Episode) {
                    val expanded = browser.playbackQueueFor(item)
                    if (playingMedia?.id == item.id && playerExpanded) {
                        playQueue = expanded
                    }
                }
            }
        }
    }

    val startPlayback: (MediaItem) -> Unit = { item -> openMedia(item, null, { startPlaybackItem(item) }) }
    val playNextEpisode: () -> Unit = {
        playQueue?.next()?.let { next ->
            playQueue = next
            startPlaybackItem(next.current)
        }
    }
    val playPreviousEpisode: () -> Unit = {
        playQueue?.previous()?.let { previous ->
            playQueue = previous
            startPlaybackItem(previous.current)
        }
    }
    val playEpisodeAt: (Int) -> Unit = { index ->
        playQueue?.select(index)?.let { selected ->
            playQueue = selected
            startPlaybackItem(selected.current)
        }
    }
    val playMusicQueue: (List<MediaItem>, Int) -> Unit = { tracks, index ->
        val target = tracks.getOrNull(index)
        if (target?.mediaType == MediaType.Audio && !playbackBusy) {
            if (playback?.controller?.state?.value?.resourceId == target.id.value && playingMedia?.id == target.id) {
                playerExpanded = true
            } else {
                startPlaybackItem(target, PlaybackQueue(tracks, index))
            }
        }
    }
    // 清晰度：0 = 自动（恢复直连），其余请求该码率的上限。
    // 只有协商真的产出了新流才提交请求值 —— 失败时标签继续跟随实际结果（自动/原档），
    // 下一集也不会把这个没兑现的上限带过去。
    val selectQuality: (Int) -> Unit = { bitrate ->
        val ceiling = bitrate.takeIf { it > 0 }
        // 已经是这个档：不重复协商、不重复起播（旧版同样直接返回）。
        if (ceiling != requestedBitrate)
            scope.launch {
                try {
                    if (playback?.selectQuality(ceiling) == null) {
                        // 协商没产出新流（被拒 / 被后续请求取代）：请求值不提交，
                        // 界面继续显示实际在播的那一档。
                        playbackError = "当前清晰度暂时无法切换，已保持原清晰度"
                    } else {
                        requestedBitrate = ceiling
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    println("[MaFeiPlayback] quality switch failed: $failure")
                    playbackError = "切换清晰度失败，请重试"
                }
            }
    }

    val togglePlayback: () -> Unit = {
        scope.launch {
            try {
                when (playback?.controller?.state?.value?.phase ?: PlayerPhase.Idle) {
                    PlayerPhase.Playing,
                    PlayerPhase.Buffering -> playback?.controller?.pause()
                    PlayerPhase.Ended,
                    PlayerPhase.Error -> playingMedia?.let { startPlaybackItem(it) }
                    PlayerPhase.Ready,
                    PlayerPhase.Paused -> playback?.controller?.play()
                    PlayerPhase.Idle,
                    PlayerPhase.Loading,
                    PlayerPhase.Released -> Unit
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                playbackError = "播放器操作失败，请重试"
            }
        }
    }
}
