package cn.mafei.cmp.feature.chasing

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.repositories.MediaQuery
import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.data.repositories.MediaSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** Account-scoped equivalent of legacy `AppPreference.getChasingSeriesIds()`. */
interface ChasingSeriesStore {
    suspend fun seriesIds(accountId: AccountId): List<MediaId>
    suspend fun replace(accountId: AccountId, seriesIds: List<MediaId>)
}

data class ChasingItem(
    val media: MediaItem,
    val title: String,
    val subtitle: String,
    val progressPercent: Int,
) {
    val seriesId: MediaId? get() = media.seriesId ?: media.id.takeIf { media.mediaType == MediaType.Series }
}

data class ChasingSection(
    val kind: String,
    val title: String,
    val subtitle: String,
    val items: List<ChasingItem>,
)

data class ChasingPlan(
    val summary: String,
    val totalCount: Int,
    val sections: List<ChasingSection>,
)

data class ChasingState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val followedCount: Int = 0,
    val summary: String = "暂无追剧内容",
    val totalCount: Int = 0,
    val sections: List<ChasingSection> = emptyList(),
)

/** Line-for-line semantic port of ArkTS `ChasePlanner`. */
class ChasePlanner {
    fun plan(
        continueItems: List<ChasingItem>,
        nextUpItems: List<ChasingItem>,
        followedSeriesItems: List<ChasingItem>,
    ): ChasingPlan {
        val normalizedContinue = continueItems.filter { it.media.id.value.trim().isNotEmpty() }.map(::normalize)
        val continueIds = normalizedContinue.mapTo(linkedSetOf()) { it.media.id.value }
        val normalizedNextUp = nextUpItems
            .filter { it.media.id.value.trim().isNotEmpty() && it.media.id.value !in continueIds }
            .map(::normalize)
        val activeSeriesKeys = linkedSetOf<String>().apply {
            normalizedContinue.forEach { add(seriesKey(it)) }
            normalizedNextUp.forEach { add(seriesKey(it)) }
        }
        val completedSeriesKeys = linkedSetOf<String>()
        val normalizedCompleted = followedSeriesItems.filter { item ->
            val key = seriesKey(item)
            key.isNotEmpty() && key !in activeSeriesKeys && isCompletedSeries(item) && completedSeriesKeys.add(key)
        }.map(::normalize)

        val sections = listOf(
            ChasingSection(
                kind = ContinueKind,
                title = "继续观看",
                subtitle = if (normalizedContinue.isEmpty()) "暂无需要续播的内容"
                else "${normalizedContinue.size} 个内容可继续播放",
                items = normalizedContinue,
            ),
            ChasingSection(
                kind = NextUpKind,
                title = "接下来",
                subtitle = if (normalizedNextUp.isEmpty()) "暂无接下来可播放的剧集"
                else "${normalizedNextUp.size} 集等待播放",
                items = normalizedNextUp,
            ),
            ChasingSection(
                kind = CompletedKind,
                title = "已看完",
                subtitle = if (normalizedCompleted.isEmpty()) "暂无已看完的剧集"
                else "${normalizedCompleted.size} 部剧集已看完",
                items = normalizedCompleted,
            ),
        )
        val parts = buildList {
            if (normalizedContinue.isNotEmpty()) add("继续观看 ${normalizedContinue.size}")
            if (normalizedNextUp.isNotEmpty()) add("接下来 ${normalizedNextUp.size}")
            if (normalizedCompleted.isNotEmpty()) add("已看完 ${normalizedCompleted.size}")
        }
        return ChasingPlan(
            summary = parts.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "暂无追剧内容",
            totalCount = normalizedContinue.size + normalizedNextUp.size + normalizedCompleted.size,
            sections = sections,
        )
    }

    private fun seriesKey(item: ChasingItem): String =
        (item.media.seriesId ?: item.media.id).value.trim()

    private fun isCompletedSeries(item: ChasingItem): Boolean =
        item.media.userData.unplayedItemCount == 0 ||
            (item.media.userData.unplayedItemCount == null && item.media.userData.isPlayed)

    private fun normalize(item: ChasingItem): ChasingItem = item.copy(
        title = item.title.ifBlank { "未命名内容" },
        progressPercent = item.progressPercent.coerceIn(0, 100),
    )

    companion object {
        const val ContinueKind = "continue"
        const val NextUpKind = "nextUp"
        const val CompletedKind = "completed"
    }
}

/** Loads the same three authoritative sources as legacy `ChasingViewModel`. */
class ChasingBrowser(
    private val repository: MediaRepository,
    private val session: MediaSession,
    private val capabilities: AdapterCapabilities,
    private val store: ChasingSeriesStore,
    private val nowEpochMs: () -> Long,
) {
    private val mutableState = MutableStateFlow(ChasingState())
    val state: StateFlow<ChasingState> = mutableState.asStateFlow()
    private var ticket = 0L

    suspend fun refresh() {
        val operation = ++ticket
        val hadContent = state.value.sections.isNotEmpty()
        mutableState.value = state.value.copy(
            loading = !hadContent,
            refreshing = hadContent,
            error = null,
        )
        if (!capabilities.chasing) {
            publish(operation, followedCount = 0, ChasePlanner().plan(emptyList(), emptyList(), emptyList()))
            return
        }
        try {
            val followedIds = store.seriesIds(session.accountId).distinct()
            if (followedIds.isEmpty()) {
                publish(operation, followedCount = 0, ChasePlanner().plan(emptyList(), emptyList(), emptyList()))
                return
            }
            val followedSet = followedIds.toSet()
            val cutoff = (nowEpochMs() - OneYearMilliseconds).coerceAtLeast(1L)
            val (resume, nextUp, followed) = coroutineScope {
                val resumeTask = async(start = CoroutineStart.UNDISPATCHED) {
                    repository.browse(
                        session,
                        MediaQuery.Resume,
                        PageRequest(pageSize = 100),
                        refresh = true,
                    ).value.items.filter { it.seriesId in followedSet }.map(::resumeItem)
                }
                val nextUpTask = async(start = CoroutineStart.UNDISPATCHED) {
                    followedIds.map { seriesId ->
                        async(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                repository.browse(
                                    session,
                                    MediaQuery.ChasingNextUp(seriesId, cutoff),
                                    refresh = true,
                                ).value.items.firstOrNull()?.let(::nextUpItem)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                // Legacy swallowed one-series Next Up failures and kept the rest.
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
                val followedTask = async(start = CoroutineStart.UNDISPATCHED) {
                    followedIds.map { seriesId ->
                        async(start = CoroutineStart.UNDISPATCHED) {
                            repository.item(session, seriesId, refresh = true).value.let(::followedItem)
                        }
                    }.awaitAll()
                }
                Triple(resumeTask.await(), nextUpTask.await(), followedTask.await())
            }
            publish(operation, followedIds.size, ChasePlanner().plan(resume, nextUp, followed))
        } catch (cancelled: CancellationException) {
            if (operation == ticket) {
                mutableState.value = state.value.copy(loading = false, refreshing = false)
            }
            throw cancelled
        } catch (_: Exception) {
            if (operation == ticket) {
                mutableState.value = state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = "追剧数据加载失败，请重试",
                )
            }
        }
    }

    private fun publish(operation: Long, followedCount: Int, plan: ChasingPlan) {
        if (operation != ticket) return
        mutableState.value = ChasingState(
            followedCount = followedCount,
            summary = plan.summary,
            totalCount = plan.totalCount,
            sections = plan.sections,
        )
    }

    private fun resumeItem(item: MediaItem): ChasingItem {
        val title = when (item.mediaType) {
            MediaType.Episode -> item.seriesTitle ?: item.title
            else -> item.title
        }
        val subtitle = when (item.mediaType) {
            MediaType.Movie -> "电影"
            MediaType.Episode -> listOfNotNull(item.seasonTitle?.takeIf(String::isNotBlank), item.title.takeIf(String::isNotBlank))
                .joinToString(" · ")
            else -> item.mediaType.name
        }
        return ChasingItem(item, title, subtitle, item.userData.playedPercentage?.roundToInt() ?: 0)
    }

    private fun nextUpItem(item: MediaItem): ChasingItem = ChasingItem(
        media = item,
        title = item.seriesTitle ?: item.title,
        subtitle = "S${item.seasonNumber ?: 0}:E${item.episodeNumber ?: 0} - ${item.title}",
        progressPercent = 0,
    )

    private fun followedItem(item: MediaItem): ChasingItem = ChasingItem(
        media = item,
        title = item.title,
        subtitle = "已看完",
        progressPercent = 100,
    )

    private companion object {
        const val OneYearMilliseconds = 365L * 24L * 60L * 60L * 1_000L
    }
}
