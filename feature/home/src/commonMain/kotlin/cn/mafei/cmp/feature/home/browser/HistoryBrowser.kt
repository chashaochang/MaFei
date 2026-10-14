package cn.mafei.cmp.feature.home

import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.repositories.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class HistoryBrowser(context: BrowserContext) : BrowserController(context) {
    private var historyTicket = 0L
    private var historyResumePages = MediaPageAccumulator()
    private var historyCompletedPages = MediaPageAccumulator()
    private var historyResumeRetryInitial = false
    private var historyCompletedRetryInitial = false
    private var historyPageSize = HistoryPageSize
    private val historySeriesCache = mutableMapOf<MediaId, HistorySeriesCacheEntry>()

    // ------------------------------------------------------------- watch history

    private enum class HistorySource { Resume, Completed }

    private data class HistorySourceLoad(
        val requested: Boolean,
        val failed: Boolean,
    )

    private data class HistorySeriesPosition(
        val seasonNumber: Int,
        val episodeNumber: Int,
        val hasMultipleSeasons: Boolean,
    )

    private data class HistorySeriesCacheEntry(
        val position: HistorySeriesPosition?,
        val expiresAtEpochMs: Long,
    )

    /**
     * 独立播放历史（旧版 MineHistoryPage）：resume（进行中）与已看完（played）
     * 两路各自分页，浏览器层按 legacy 规则合并——剧集按所属剧去重（同一部剧只留
     * 最近一条），电影按条目去重，按最后播放时间倒序。与首页捎带的 resume 段
     * 解耦：这里有自己的分页与刷新，不再借用 homeFeed。
     */
    suspend fun loadHistory() = loadHistory(HistoryPageSize)

    /** Legacy Mine tab preview: each source requests 24 rows, then the UI shows 6/8. */
    suspend fun loadHistoryPreview() = loadHistory(MineHistoryPreviewSourceLimit)

    private suspend fun loadHistory(pageSize: Int) {
        val operation = ++historyTicket
        historyPageSize = pageSize
        historyResumePages = MediaPageAccumulator()
        historyCompletedPages = MediaPageAccumulator()
        historyResumeRetryInitial = false
        historyCompletedRetryInitial = false
        mutableState.value = state.value.copy(
            historyLoading = true,
            historyRefreshing = false,
            historyLoadingMore = false,
            historyLoadMoreFailed = false,
            historyCanLoadMore = true,
            historyError = null,
            historyItems = emptyList(),
            historyResumeCursor = null,
            historyCompletedCursor = null,
        )
        try {
            val (resume, completed) = coroutineScope {
                val resume = async {
                    loadHistorySource(operation, HistorySource.Resume, cursor = null, fetch = true)
                }
                val completed = async {
                    loadHistorySource(operation, HistorySource.Completed, cursor = null, fetch = true)
                }
                resume.await() to completed.await()
            }
            publishHistory(operation, initial = true, resume = resume, completed = completed)
        } catch (cancelled: CancellationException) {
            if (operation == historyTicket) {
                mutableState.value = state.value.copy(historyLoading = false, historyRefreshing = false)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (operation == historyTicket) {
                mutableState.value = state.value.copy(
                    historyLoading = false,
                    historyRefreshing = false,
                    historyCanLoadMore = false,
                    historyError = browseError(failure),
                )
            }
        }
    }

    /** 任一路还有下一页就继续翻；两路都翻完为止。已耗尽的源保持现有累积。 */
    suspend fun loadMoreHistory() {
        val before = state.value
        if (before.historyLoading || before.historyRefreshing || before.historyLoadingMore) return
        if (!before.historyCanLoadMore || before.historyLoadMoreFailed) return
        val resumeCursor = if (historyResumeRetryInitial) null else before.historyResumeCursor
        val completedCursor = if (historyCompletedRetryInitial) null else before.historyCompletedCursor
        val fetchResume = historyResumeRetryInitial || resumeCursor != null
        val fetchCompleted = historyCompletedRetryInitial || completedCursor != null
        if (!fetchResume && !fetchCompleted) return
        val operation = ++historyTicket
        mutableState.value = before.copy(
            historyLoadingMore = true,
            historyLoadMoreFailed = false,
            historyError = null,
        )
        try {
            val (resume, completed) = coroutineScope {
                val resume = async {
                    loadHistorySource(operation, HistorySource.Resume, resumeCursor, fetchResume)
                }
                val completed = async {
                    loadHistorySource(operation, HistorySource.Completed, completedCursor, fetchCompleted)
                }
                resume.await() to completed.await()
            }
            publishHistory(operation, initial = false, resume = resume, completed = completed)
        } catch (cancelled: CancellationException) {
            if (operation == historyTicket) {
                mutableState.value = state.value.copy(historyLoadingMore = false)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (operation == historyTicket) {
                mutableState.value = state.value.copy(historyLoadingMore = false, historyError = browseError(failure))
            }
        }
    }

    /** The old footer owns retry; reaching the end again must not auto-loop. */
    suspend fun retryLoadMoreHistory() {
        val before = state.value
        if (!before.historyLoadMoreFailed || before.historyLoadingMore) return
        mutableState.value = before.copy(historyLoadMoreFailed = false)
        loadMoreHistory()
    }

    private fun historyResumeQuery(cursor: PageCursor?) = PageRequest(
        cursor = cursor,
        pageSize = historyPageSize,
        filter = MediaFilter(mediaTypes = HistoryMediaTypes),
    )

    private fun historyCompletedQuery(cursor: PageCursor?) = PageRequest(
        cursor = cursor,
        pageSize = historyPageSize,
        sort = MediaSort(MediaSortField.DatePlayed, SortDirection.Descending),
        filter = MediaFilter(
            mediaTypes = HistoryMediaTypes,
            playedOnly = true,
        ),
    )

    /**
     * 拉取一路历史。失败返回 null（legacy 行为：一路失败不拖垮另一路，下次刷新重试）。
     * 累加器始终持有该路的完整累积，[publishHistory] 从快照合并，因此翻页时
     * 已耗尽的另一路不会丢内容。
     */
    private suspend fun loadHistorySource(
        operation: Long,
        source: HistorySource,
        cursor: PageCursor?,
        fetch: Boolean,
    ): HistorySourceLoad {
        if (!fetch) return HistorySourceLoad(requested = false, failed = false)
        return try {
            val page =
            when (source) {
                HistorySource.Resume -> repository.browse(
                    session,
                    MediaQuery.Resume,
                    historyResumeQuery(cursor),
                    refresh = cursor == null,
                ).value
                HistorySource.Completed -> repository.browse(
                    session,
                    MediaQuery.Items(null),
                    historyCompletedQuery(cursor),
                    refresh = cursor == null,
                ).value
            }
            if (operation != historyTicket) return HistorySourceLoad(requested = true, failed = false)
            when (source) {
                HistorySource.Resume -> {
                    historyResumePages.append(cursor, page)
                    if (cursor == null) historyResumeRetryInitial = false
                }
                HistorySource.Completed -> {
                    historyCompletedPages.append(cursor, page)
                    if (cursor == null) historyCompletedRetryInitial = false
                }
            }
            HistorySourceLoad(requested = true, failed = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (operation == historyTicket && cursor == null) {
                // A failed first page remains retryable from cursor=null. A
                // failed following page keeps the accumulator's old cursor.
                if (source == HistorySource.Resume) historyResumeRetryInitial = true
                else historyCompletedRetryInitial = true
            }
            HistorySourceLoad(requested = true, failed = true)
        }
    }

    private suspend fun publishHistory(
        operation: Long,
        initial: Boolean,
        resume: HistorySourceLoad,
        completed: HistorySourceLoad,
    ) {
        if (operation != historyTicket) return
        val merged = mergeHistory(historyResumePages.snapshot().items, historyCompletedPages.snapshot().items)
        val requested = listOf(resume, completed).filter { it.requested }
        val anyFailed = requested.any { it.failed }
        val allRequestedFailed = requested.isNotEmpty() && requested.all { it.failed }
        val pageError = initial && (allRequestedFailed || (merged.isEmpty() && anyFailed))
        val canLoadMore = historyResumeRetryInitial || historyCompletedRetryInitial ||
            historyResumePages.snapshot().nextCursor != null || historyCompletedPages.snapshot().nextCursor != null
        mutableState.value = state.value.copy(
            historyItems = merged,
            historyResumeCursor = historyResumePages.snapshot().nextCursor,
            historyCompletedCursor = historyCompletedPages.snapshot().nextCursor,
            historyLoading = false,
            historyRefreshing = false,
            historyLoadingMore = false,
            historyLoadMoreFailed = !pageError && anyFailed,
            historyCanLoadMore = canLoadMore,
            historyError = if (pageError) HistoryLoadError else null,
        )
        if (!pageError && merged.isNotEmpty()) enrichHistorySeries(operation, merged)
    }

    /** Legacy MineHistoryPlanner.merge：剧集按剧折叠保留最近一条，按播放时间倒序。 */
    private fun mergeHistory(vararg sources: List<MediaItem>): List<PlaybackHistoryItem> {
        val byKey = linkedMapOf<String, Pair<Long, MediaItem>>()
        sources.forEach { source ->
            source.forEach { item ->
                if (item.id.value.isEmpty()) return@forEach
                if (item.mediaType !in HistoryMediaTypes) return@forEach
                val playedAt = item.userData.lastPlayedEpochMs ?: return@forEach
                val key = if (item.mediaType == MediaType.Episode && item.seriesId != null) {
                    "series:${item.seriesId!!.value}"
                } else {
                    "item:${item.id.value}"
                }
                val current = byKey[key]
                if (current == null || playedAt > current.first) byKey[key] = playedAt to item
            }
        }
        return byKey.values.sortedByDescending { it.first }.map { (_, item) -> historyItem(item) }
    }

    private fun historyItem(item: MediaItem): PlaybackHistoryItem {
        val cached = item.seriesId?.let(historySeriesCache::get)
            ?.takeIf { it.expiresAtEpochMs > SystemEpochClock.nowEpochMs() }
            ?.position
        return PlaybackHistoryItem(
            media = item,
            latestSeasonNumber = cached?.seasonNumber,
            latestEpisodeNumber = cached?.episodeNumber,
            seriesHasMultipleSeasons = (item.seasonNumber ?: 0) > 1 || cached?.hasMultipleSeasons == true,
        )
    }

    private suspend fun enrichHistorySeries(operation: Long, visible: List<PlaybackHistoryItem>) {
        val seriesIds = visible.asSequence()
            .filter { it.media.mediaType == MediaType.Episode }
            .mapNotNull { it.media.seriesId }
            .distinct()
            .toList()
        if (seriesIds.isEmpty()) return
        val positions = mutableMapOf<MediaId, HistorySeriesPosition>()
        seriesIds.chunked(HistoryEnrichmentConcurrency).forEach { batch ->
            val loaded = coroutineScope {
                batch.map { seriesId ->
                    async {
                        try {
                            seriesId to latestHistorySeriesPosition(seriesId)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            seriesId to null
                        }
                    }
                }.map { it.await() }
            }
            if (operation != historyTicket) return
            loaded.forEach { (seriesId, position) ->
                if (position != null) positions[seriesId] = position
            }
        }
        if (operation != historyTicket || positions.isEmpty()) return
        mutableState.value = state.value.copy(
            historyItems = state.value.historyItems.map { item ->
                val position = item.media.seriesId?.let(positions::get) ?: return@map item
                item.copy(
                    latestSeasonNumber = position.seasonNumber,
                    latestEpisodeNumber = position.episodeNumber,
                    seriesHasMultipleSeasons = item.seriesHasMultipleSeasons || position.hasMultipleSeasons,
                )
            },
        )
    }

    private suspend fun latestHistorySeriesPosition(seriesId: MediaId): HistorySeriesPosition? {
        val now = SystemEpochClock.nowEpochMs()
        historySeriesCache[seriesId]?.let { cached ->
            if (cached.expiresAtEpochMs > now) return cached.position
            historySeriesCache.remove(seriesId)
        }
        val accumulator = MediaPageAccumulator()
        val first = repository.browse(
            session,
            MediaQuery.Episodes(seriesId, seasonId = null),
            PageRequest(pageSize = PageRequest.MAX_PAGE_SIZE),
            refresh = true,
        ).value
        var page = accumulator.append(null, first)
        page.nextCursor?.let { cursor ->
            val second = repository.browse(
                session,
                MediaQuery.Episodes(seriesId, seasonId = null),
                PageRequest(cursor = cursor, pageSize = PageRequest.MAX_PAGE_SIZE),
            ).value
            page = accumulator.append(cursor, second)
        }
        val position = latestHistorySeriesPosition(page.items)
        historySeriesCache[seriesId] = HistorySeriesCacheEntry(
            position = position,
            expiresAtEpochMs = now + HistorySeriesCacheTtlMs,
        )
        return position
    }

    private fun latestHistorySeriesPosition(episodes: List<MediaItem>): HistorySeriesPosition? {
        var latestSeason = -1
        var latestEpisode = -1
        val seasons = mutableSetOf<Int>()
        episodes.forEach { item ->
            val season = item.seasonNumber?.takeIf { it > 0 } ?: return@forEach
            val episode = item.episodeNumber?.takeIf { it > 0 } ?: return@forEach
            seasons += season
            if (season > latestSeason || (season == latestSeason && episode > latestEpisode)) {
                latestSeason = season
                latestEpisode = episode
            }
        }
        if (latestEpisode <= 0) return null
        return HistorySeriesPosition(
            seasonNumber = latestSeason,
            episodeNumber = latestEpisode,
            hasMultipleSeasons = seasons.size > 1 || latestSeason > 1,
        )
    }
}
