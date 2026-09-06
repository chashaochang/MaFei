package cn.mafei.cmp.providers.jellyfin.playstate

import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.core.JellyfinTicks
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinRequests
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackProgressInfoDto
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackStartInfoDto
import cn.mafei.cmp.providers.jellyfin.wire.PlaybackStopInfoDto
import cn.mafei.cmp.providers.jellyfin.wire.WirePlayMethod

/**
 * Stable playstate reporting surface (start / progress / stop). Positions
 * are reported in Jellyfin ticks; convenience millisecond overloads keep
 * player-side code free of the tick factor.
 */
interface JellyfinPlayStateApi {

    suspend fun reportStart(info: PlaybackStartInfoDto)

    suspend fun reportProgress(info: PlaybackProgressInfoDto)

    suspend fun reportStopped(info: PlaybackStopInfoDto)

    suspend fun reportStartMs(
        itemId: String,
        playSessionId: String?,
        mediaSourceId: String? = null,
        positionMs: Long? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        canSeek: Boolean = true,
        playMethod: WirePlayMethod? = null,
    ) = reportStart(
        PlaybackStartInfoDto(
            itemId = itemId,
            playSessionId = playSessionId,
            mediaSourceId = mediaSourceId,
            positionTicks = positionMs?.let(JellyfinTicks::fromMilliseconds),
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            canSeek = canSeek,
            playMethod = playMethod,
        ),
    )

    suspend fun reportProgressMs(
        itemId: String,
        playSessionId: String?,
        mediaSourceId: String? = null,
        positionMs: Long? = null,
        isPaused: Boolean? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        playMethod: WirePlayMethod? = null,
    ) = reportProgress(
        PlaybackProgressInfoDto(
            itemId = itemId,
            playSessionId = playSessionId,
            mediaSourceId = mediaSourceId,
            positionTicks = positionMs?.let(JellyfinTicks::fromMilliseconds),
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            isPaused = isPaused,
            playMethod = playMethod,
        ),
    )

    suspend fun reportStoppedMs(
        itemId: String,
        playSessionId: String?,
        mediaSourceId: String? = null,
        positionMs: Long? = null,
    ) = reportStopped(
        PlaybackStopInfoDto(
            itemId = itemId,
            playSessionId = playSessionId,
            mediaSourceId = mediaSourceId,
            positionTicks = positionMs?.let(JellyfinTicks::fromMilliseconds),
        ),
    )
}

class DefaultJellyfinPlayStateApi(
    private val client: JellyfinClient,
) : JellyfinPlayStateApi {

    override suspend fun reportStart(info: PlaybackStartInfoDto) {
        client.execute(JellyfinRequests.playingStart(client.config.baseUrl, info)) { /* 204 */ }
    }

    override suspend fun reportProgress(info: PlaybackProgressInfoDto) {
        client.execute(JellyfinRequests.playingProgress(client.config.baseUrl, info)) { /* 204 */ }
    }

    override suspend fun reportStopped(info: PlaybackStopInfoDto) {
        client.execute(JellyfinRequests.playingStopped(client.config.baseUrl, info)) { /* 204 */ }
    }
}
