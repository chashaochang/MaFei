package cn.xiaobai.mafei.data

import cn.xiaobai.mafei.data.jellyfin.JellyfinProvider
import cn.xiaobai.mafei.data.jellyfin.createJellyfinProvider
import cn.xiaobai.mafei.data.jellyfin.requireAndroidProvider
import cn.xiaobai.mafei.data.jellyfin.toJellyfinLoadIssue
import cn.xiaobai.mafei.logging.AppLogger
import cn.xiaobai.mafei.logging.summarizeBaseUrlForLog
import cn.xiaobai.mafei.screens.JellyfinServer
import cn.xiaobai.mafei.storage.SessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import java.util.UUID
import kotlin.math.roundToInt

class AndroidJellyfinDetailRepository(
    private val providerFactory: () -> JellyfinProvider = ::createJellyfinProvider,
    private val fallbackRepository: DetailRepository = FakeDetailRepository(FakeVideoRepository()),
) : DetailRepository {
    override suspend fun loadDetail(
        itemId: String,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): VideoDetail? {
        AppLogger.info(
            tag = "Detail",
            message = "loadDetail start: itemId=${itemId.shortIdForLog()}, baseUrl=${summarizeBaseUrlForLog(defaultServer?.baseUrl)}, hasToken=${!session?.accessToken.isNullOrBlank()}."
        )
        val fallback = fallbackRepository.loadDetail(itemId, defaultServer, session)
        val fallbackOrPlaceholder = fallback ?: buildPlaceholderDetail(itemId)
        val token = session?.accessToken
        if (defaultServer == null || token.isNullOrBlank()) {
            AppLogger.warn(
                tag = "Detail",
                message = "loadDetail fallback: missing server or token, itemId=${itemId.shortIdForLog()}."
            )
            return fallbackOrPlaceholder
        }

        val detailItemId = runCatching { UUID.fromString(itemId) }.getOrNull()
            ?: return fallbackOrPlaceholder.also {
                AppLogger.warn(
                    tag = "Detail",
                    message = "loadDetail fallback: invalid itemId=${itemId.shortIdForLog()}."
                )
            }

        return withContext(Dispatchers.IO) {
            runCatching {
                val api = providerFactory().requireAndroidProvider().jellyfin.createApi(
                    baseUrl = defaultServer.baseUrl,
                    accessToken = token,
                )
                AppLogger.debug(
                    tag = "Detail",
                    message = "loadDetail remote fetch started: itemId=${itemId.shortIdForLog()}."
                )

                val targetItem = api.userLibraryApi.getItem(itemId = detailItemId).content
                val detailRoot = resolveDetailRootItem(api = api, item = targetItem)
                val episodeDtos = loadEpisodesIfSeries(api = api, detailRoot = detailRoot)

                val mappedEpisodes = if (episodeDtos.isNotEmpty()) {
                    episodeDtos.mapIndexed { index, episode ->
                        episode.toEpisodeItem(
                            displayIndex = index + 1,
                            baseUrl = defaultServer.baseUrl,
                            accessToken = token,
                        )
                    }
                } else {
                    listOf(
                        EpisodeItem(
                            id = 1,
                            title = "正片",
                            durationLabel = formatRunTime(detailRoot.runTimeTicks),
                            isNew = false,
                            playbackItemId = detailRoot.id.toString(),
                            thumbnailUrl = buildThumbImageUrl(
                                baseUrl = defaultServer.baseUrl,
                                itemId = detailRoot.id.toString(),
                                accessToken = token,
                                maxWidth = 480,
                                maxHeight = 270,
                            ),
                        )
                    )
                }

                val continueEpisodeIndex = episodeDtos.indexOfFirst { episode ->
                    val percent = episode.userData?.playedPercentage ?: 0.0
                    percent > 0.0 && percent < 100.0
                }.takeIf { it >= 0 } ?: 0

                val continueSource = episodeDtos.getOrNull(continueEpisodeIndex) ?: detailRoot
                val continuePercent = (continueSource.userData?.playedPercentage ?: 0.0)
                    .roundToInt()
                    .coerceIn(0, 100)

                VideoDetail(
                    itemId = itemId,
                    title = detailRoot.name?.takeIf { it.isNotBlank() }
                        ?: detailRoot.seriesName?.takeIf { it.isNotBlank() }
                        ?: fallback?.title
                        ?: "Untitled",
                    metaLine = detailRoot.toMetaLine(episodeDtos),
                    synopsis = detailRoot.overview?.takeIf { it.isNotBlank() }
                        ?: fallback?.synopsis
                        ?: "暂无简介。",
                    isFavorite = detailRoot.userData?.isFavorite ?: fallback?.isFavorite ?: false,
                    continueEpisodeId = mappedEpisodes.getOrNull(continueEpisodeIndex)?.id ?: 1,
                    continueProgressLabel = buildContinueLabel(
                        source = continueSource,
                        fallback = fallback?.continueProgressLabel ?: "未开始播放",
                    ),
                    continueProgressPercent = continuePercent,
                    updateCount = mappedEpisodes.count { it.isNew },
                    episodes = mappedEpisodes,
                    posterUrl = buildPrimaryImageUrl(
                        baseUrl = defaultServer.baseUrl,
                        itemId = detailRoot.id.toString(),
                        accessToken = token,
                        maxWidth = 480,
                        maxHeight = 720,
                    ),
                    backdropUrl = buildBackdropImageUrl(
                        baseUrl = defaultServer.baseUrl,
                        itemId = detailRoot.id.toString(),
                        accessToken = token,
                        maxWidth = 1280,
                        maxHeight = 720,
                    ),
                    loadIssue = null,
                ).also { detail ->
                    AppLogger.info(
                        tag = "Detail",
                        message = "loadDetail success: itemId=${itemId.shortIdForLog()}, episodes=${detail.episodes.size}, updates=${detail.updateCount}, favorite=${detail.isFavorite}."
                    )
                }
            }.getOrElse { error ->
                AppLogger.error(
                    tag = "Detail",
                    message = "loadDetail failed; fallback to placeholder content.",
                    throwable = error,
                )
                fallbackOrPlaceholder.copy(
                    loadIssue = error.toJellyfinLoadIssue("详情加载失败，已展示占位内容。"),
                )
            }
        }
    }
}

private suspend fun resolveDetailRootItem(
    api: org.jellyfin.sdk.api.client.ApiClient,
    item: BaseItemDto,
): BaseItemDto {
    return when (item.type) {
        BaseItemKind.EPISODE -> {
            val seriesId = item.seriesId ?: return item
            api.userLibraryApi.getItem(itemId = seriesId).content
        }

        BaseItemKind.SEASON -> {
            val parentId = item.parentId ?: return item
            api.userLibraryApi.getItem(itemId = parentId).content
        }

        else -> item
    }
}

private suspend fun loadEpisodesIfSeries(
    api: org.jellyfin.sdk.api.client.ApiClient,
    detailRoot: BaseItemDto,
): List<BaseItemDto> {
    if (detailRoot.type != BaseItemKind.SERIES) return emptyList()
    return api.tvShowsApi
        .getEpisodes(
            seriesId = detailRoot.id,
            enableUserData = true,
            sortBy = ItemSortBy.INDEX_NUMBER,
        )
        .content
        .items
        .sortedWith(
            compareBy<BaseItemDto> { it.parentIndexNumber ?: Int.MAX_VALUE }
                .thenBy { it.indexNumber ?: Int.MAX_VALUE }
                .thenBy { it.name ?: "" }
        )
}

private fun BaseItemDto.toEpisodeItem(
    displayIndex: Int,
    baseUrl: String,
    accessToken: String,
): EpisodeItem {
    val namePart = name?.takeIf { it.isNotBlank() } ?: "未命名剧集"
    val prefix = buildString {
        parentIndexNumber?.let { season ->
            append("S")
            append(season)
            append(" ")
        }
        indexNumber?.let { episode ->
            append("E")
            append(episode)
            append(" ")
        }
    }.trim()

    return EpisodeItem(
        id = displayIndex,
        title = if (prefix.isBlank()) namePart else "$prefix· $namePart",
        durationLabel = formatRunTime(runTimeTicks),
        isNew = !(userData?.played ?: false),
        playbackItemId = id.toString(),
        thumbnailUrl = buildThumbImageUrl(
            baseUrl = baseUrl,
            itemId = id.toString(),
            accessToken = accessToken,
            maxWidth = 480,
            maxHeight = 270,
        ),
    )
}

private fun BaseItemDto.toMetaLine(episodes: List<BaseItemDto>): String {
    val typeLabel = when (type) {
        BaseItemKind.SERIES -> "剧集"
        BaseItemKind.SEASON -> "季"
        BaseItemKind.EPISODE -> "单集"
        BaseItemKind.MOVIE -> "电影"
        else -> "媒体"
    }
    val year = productionYear?.toString()
    val runtime = formatRunTime(runTimeTicks).takeIf { it != "--" }
    val seasonCount = episodes.mapNotNull { it.parentIndexNumber }.distinct().size.takeIf { it > 0 }
    val episodeCount = episodes.size.takeIf { it > 0 }
    val seriesInfo = when {
        seasonCount != null && episodeCount != null -> "${seasonCount}季 · ${episodeCount}集"
        episodeCount != null -> "${episodeCount}集"
        else -> null
    }
    val base = listOf(typeLabel, year, runtime, seriesInfo, "Jellyfin")
        .filterNotNull()
        .filter { it.isNotBlank() }
    return base.joinToString(" · ")
}

private fun buildContinueLabel(
    source: BaseItemDto,
    fallback: String,
): String {
    val ticks = source.userData?.playbackPositionTicks ?: 0L
    val percent = (source.userData?.playedPercentage ?: 0.0)
        .roundToInt()
        .coerceIn(0, 100)
    if (ticks <= 0L && percent <= 0) {
        val title = source.name?.takeIf { it.isNotBlank() } ?: return fallback
        return "$title · 未开始"
    }

    val name = source.name?.takeIf { it.isNotBlank() } ?: "当前内容"
    return "$name · ${formatPosition(ticks)} · $percent%"
}

private fun buildPlaceholderDetail(itemId: String): VideoDetail {
    val shortId = itemId.take(8)
    return VideoDetail(
        itemId = itemId,
        title = "媒体详情 ($shortId)",
        metaLine = "详情占位 · 未登录或加载失败",
        synopsis = "当前无法从 Jellyfin 拉取详情数据，请先检查登录状态与服务器连接。",
        isFavorite = false,
        continueEpisodeId = 1,
        continueProgressLabel = "未开始播放",
        continueProgressPercent = 0,
        updateCount = 0,
        episodes = listOf(
            EpisodeItem(
                id = 1,
                title = "正片",
                durationLabel = "--",
                isNew = false,
                playbackItemId = itemId,
            )
        ),
    )
}

private fun formatRunTime(ticks: Long?): String {
    val safeTicks = ticks ?: return "--"
    if (safeTicks <= 0L) return "--"
    val totalSeconds = safeTicks / 10_000_000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h${minutes}m" else "${minutes}m"
}

private fun formatPosition(ticks: Long): String {
    if (ticks <= 0L) return "00:00"
    val totalSeconds = ticks / 10_000_000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun String.shortIdForLog(): String {
    if (isBlank()) return "<empty>"
    return if (length <= 12) this else "${take(6)}...${takeLast(4)}"
}
