package cn.xiaobai.mafei.data

import cn.xiaobai.mafei.data.jellyfin.JellyfinProvider
import cn.xiaobai.mafei.data.jellyfin.createJellyfinProvider
import cn.xiaobai.mafei.data.jellyfin.requireAndroidProvider
import cn.xiaobai.mafei.data.jellyfin.JellyfinErrorCode
import cn.xiaobai.mafei.data.jellyfin.JellyfinLoadIssue
import cn.xiaobai.mafei.data.jellyfin.messageWithHint
import cn.xiaobai.mafei.data.jellyfin.toJellyfinLoadIssue
import cn.xiaobai.mafei.logging.AppLogger
import cn.xiaobai.mafei.logging.summarizeBaseUrlForLog
import cn.xiaobai.mafei.screens.JellyfinServer
import cn.xiaobai.mafei.storage.SessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import java.net.URLEncoder
import java.util.UUID

class AndroidJellyfinPlaybackRepository(
    private val providerFactory: () -> JellyfinProvider = ::createJellyfinProvider,
    private val fallbackRepository: PlaybackRepository = FakePlaybackRepository(FakeVideoRepository()),
) : PlaybackRepository {
    override suspend fun loadPlaybackContext(
        itemId: String,
        episodeId: Int,
        playbackItemId: String?,
        defaultServer: JellyfinServer?,
        session: SessionRecord?,
    ): PlaybackContext {
        AppLogger.info(
            tag = "Playback",
            message = "loadPlaybackContext start: itemId=${itemId.shortIdForLog()}, playbackItemId=${playbackItemId.shortIdForLog()}, episodeId=$episodeId, baseUrl=${summarizeBaseUrlForLog(defaultServer?.baseUrl)}, hasToken=${!session?.accessToken.isNullOrBlank()}."
        )
        val fallback = fallbackRepository.loadPlaybackContext(
            itemId = itemId,
            episodeId = episodeId,
            playbackItemId = playbackItemId,
            defaultServer = defaultServer,
            session = session,
        )
        val token = session?.accessToken
        if (defaultServer == null || token.isNullOrBlank()) {
            AppLogger.warn(
                tag = "Playback",
                message = "loadPlaybackContext fallback: missing server or token."
            )
            val issue = JellyfinLoadIssue(
                errorCode = JellyfinErrorCode.AUTH_FAILED,
                message = "未登录或无默认服务器，无法拉取真实播放信息。",
                retryable = false,
            )
            return fallback.copy(
                statusMessage = issue.messageWithHint(),
                loadIssue = issue,
            )
        }

        val rawTargetId = playbackItemId ?: itemId
        val parsedId = parseUuidOrNull(rawTargetId)
            ?: parseUuidOrNull(itemId)
            ?: run {
                AppLogger.warn(
                    tag = "Playback",
                    message = "loadPlaybackContext fallback: invalid ids itemId=${itemId.shortIdForLog()}, playbackItemId=${playbackItemId.shortIdForLog()}."
                )
                val issue = JellyfinLoadIssue(
                    errorCode = JellyfinErrorCode.INVALID_URL,
                    message = "播放项 ID 无效，无法构建真实播放上下文。",
                    retryable = false,
                )
                return fallback.copy(
                    statusMessage = issue.messageWithHint(),
                    loadIssue = issue,
                )
            }

        return withContext(Dispatchers.IO) {
            runCatching {
                val api = providerFactory().requireAndroidProvider().jellyfin.createApi(
                    baseUrl = defaultServer.baseUrl,
                    accessToken = token,
                )
                AppLogger.debug(
                    tag = "Playback",
                    message = "loadPlaybackContext remote fetch started: resolvedTargetId=${parsedId.toString().shortIdForLog()}."
                )
                val targetItem = api.userLibraryApi.getItem(itemId = parsedId).content
                val playableItem = resolvePlayableItem(api, targetItem)
                val playbackInfo = api.mediaInfoApi.getPlaybackInfo(itemId = playableItem.id).content
                val mediaSource = playbackInfo.mediaSources.firstOrNull()

                val rawStreamUrl = mediaSource?.transcodingUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.toAbsoluteUrl(defaultServer.baseUrl)
                    ?: api.videosApi.getVideoStreamUrl(
                        itemId = playableItem.id,
                        static = true,
                        mediaSourceId = mediaSource?.id,
                        playSessionId = playbackInfo.playSessionId,
                    )

                val displayStreamUrl = rawStreamUrl.appendApiToken(token)
                val runtimeLabel = formatRunTime(playableItem.runTimeTicks)
                val startPosition = formatPosition(playableItem.userData?.playbackPositionTicks ?: 0L)

                PlaybackContext(
                    itemId = itemId,
                    playbackItemId = playableItem.id.toString(),
                    showTitle = playableItem.seriesName?.takeIf { it.isNotBlank() }
                        ?: playableItem.name?.takeIf { it.isNotBlank() }
                        ?: fallback.showTitle,
                    episodeTitle = buildEpisodeTitle(playableItem, fallback.episodeTitle),
                    streamUrl = displayStreamUrl,
                    streamTypeLabel = buildStreamTypeLabel(mediaSource),
                    playSessionId = playbackInfo.playSessionId,
                    mediaSourceId = mediaSource?.id,
                    mediaContainer = mediaSource?.container,
                    runtimeLabel = runtimeLabel,
                    startPositionLabel = startPosition,
                    statusMessage = "已获取 Jellyfin 播放上下文（播放器内核仍为下一阶段）。",
                    loadIssue = null,
                ).also { context ->
                    AppLogger.info(
                        tag = "Playback",
                        message = "loadPlaybackContext success: playbackItemId=${context.playbackItemId.shortIdForLog()}, streamType=${context.streamTypeLabel}, hasPlaySession=${!context.playSessionId.isNullOrBlank()}, hasMediaSource=${!context.mediaSourceId.isNullOrBlank()}."
                    )
                }
            }.getOrElse { error ->
                AppLogger.error(
                    tag = "Playback",
                    message = "loadPlaybackContext failed; fallback to placeholder context.",
                    throwable = error,
                )
                val issue = error.toJellyfinLoadIssue("获取播放上下文失败，已回退占位上下文。")
                fallback.copy(
                    statusMessage = issue.messageWithHint(),
                    loadIssue = issue,
                )
            }
        }
    }
}

private suspend fun resolvePlayableItem(
    api: org.jellyfin.sdk.api.client.ApiClient,
    targetItem: BaseItemDto,
): BaseItemDto {
    return when (targetItem.type) {
        BaseItemKind.SERIES -> {
            api.tvShowsApi
                .getEpisodes(
                    seriesId = targetItem.id,
                    enableUserData = true,
                    sortBy = ItemSortBy.INDEX_NUMBER,
                    limit = 1,
                )
                .content
                .items
                .firstOrNull()
                ?: targetItem
        }

        BaseItemKind.SEASON -> {
            val seriesId = targetItem.parentId ?: return targetItem
            val allEpisodes = api.tvShowsApi
                .getEpisodes(
                    seriesId = seriesId,
                    enableUserData = true,
                    sortBy = ItemSortBy.INDEX_NUMBER,
                )
                .content
                .items

            val currentSeasonIndex = targetItem.indexNumber
            allEpisodes.firstOrNull { it.parentIndexNumber == currentSeasonIndex }
                ?: allEpisodes.firstOrNull()
                ?: targetItem
        }

        else -> targetItem
    }
}

private fun parseUuidOrNull(value: String?): UUID? {
    val candidate = value?.trim().orEmpty()
    if (candidate.isBlank()) return null
    return runCatching { UUID.fromString(candidate) }.getOrNull()
}

private fun buildEpisodeTitle(
    item: BaseItemDto,
    fallback: String,
): String {
    val name = item.name?.takeIf { it.isNotBlank() } ?: return fallback
    if (item.type != BaseItemKind.EPISODE) return name

    val prefix = buildString {
        item.parentIndexNumber?.let {
            append("S")
            append(it)
            append(" ")
        }
        item.indexNumber?.let {
            append("E")
            append(it)
            append(" ")
        }
    }.trim()

    return if (prefix.isBlank()) name else "$prefix · $name"
}

private fun buildStreamTypeLabel(mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo?): String {
    if (mediaSource == null) return "未知流类型"
    return when {
        !mediaSource.transcodingUrl.isNullOrBlank() -> "转码流"
        mediaSource.supportsDirectPlay -> "直连流"
        mediaSource.supportsDirectStream -> "直出流"
        else -> "标准流"
    }
}

private fun String.toAbsoluteUrl(baseUrl: String): String {
    if (startsWith("http://") || startsWith("https://")) return this
    val cleanBase = baseUrl.trimEnd('/')
    val cleanPath = if (startsWith("/")) this else "/$this"
    return "$cleanBase$cleanPath"
}

private fun String.appendApiToken(token: String): String {
    if (contains("api_key=") || contains("X-Emby-Token=")) return this
    val encoded = URLEncoder.encode(token, "UTF-8")
    val separator = if (contains("?")) "&" else "?"
    return "$this${separator}api_key=$encoded"
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

private fun String?.shortIdForLog(): String {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return "<empty>"
    return if (value.length <= 12) value else "${value.take(6)}...${value.takeLast(4)}"
}
