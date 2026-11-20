package cn.mafei.cmp.player.runtime

import cn.mafei.cmp.player.api.PlayableResource
import cn.mafei.cmp.player.api.PlayerFailure
import cn.mafei.cmp.player.api.PlayerRuntimeInfo
import cn.mafei.cmp.player.api.PlayerTrack
import cn.mafei.cmp.player.api.TrackKind
import org.mfplayer.cmp.PlayerExternalSubtitle
import org.mfplayer.cmp.PlayerRenderActualMode
import org.mfplayer.cmp.PlayerSnapshot
import org.mfplayer.cmp.PlayerSource
import org.mfplayer.cmp.PlayerTrackKind
import org.mfplayer.cmp.PlayerFailure as SdkFailure
import org.mfplayer.cmp.PlayerTrack as SdkTrack

internal fun PlayableResource.toSdkSource() = PlayerSource(
    uri = url,
    headers = headers,
    allowInsecureTls = allowInsecureTls,
    externalSubtitles = listOfNotNull(subtitleUrl?.let { PlayerExternalSubtitle(it, isDefault = true) }),
)

internal fun SdkFailure.toMaFeiFailure(): PlayerFailure = when (this) {
    is SdkFailure.InvalidSource, is SdkFailure.SubtitleUnsupported, is SdkFailure.Unsupported ->
        PlayerFailure.UnsupportedMedia
    SdkFailure.RuntimeClosed -> PlayerFailure.RuntimeClosed
    else -> PlayerFailure.Engine
}

internal fun SdkTrack.toMaFeiTrack() = PlayerTrack(
    id = id,
    kind = when (kind) {
        PlayerTrackKind.AUDIO -> TrackKind.Audio
        PlayerTrackKind.SUBTITLE -> TrackKind.Subtitle
        PlayerTrackKind.VIDEO -> TrackKind.Video
    },
    label = label,
    language = language,
    selected = isSelected,
)

internal fun PlayerSnapshot.toMaFeiDiagnostics(): PlayerRuntimeInfo {
    val stats = diagnostics.playbackStats
    return PlayerRuntimeInfo(
        decodeMode = when (renderPath.actualMode) {
            PlayerRenderActualMode.BUFFER_SW -> "software"
            PlayerRenderActualMode.BUFFER_HW, PlayerRenderActualMode.SURFACE_HW -> "hardware"
            else -> null
        },
        decoder = renderPath.decoderName,
        renderer = diagnostics.currentVo,
        videoCodec = videoInfo?.codec,
        dynamicRange = videoInfo?.dynamicRange?.name,
        videoWidthPx = videoInfo?.width?.takeIf { it > 0 },
        videoHeightPx = videoInfo?.height?.takeIf { it > 0 },
        videoBitRate = (stats.videoBitrateKbps ?: videoInfo?.bitrateKbps)?.times(1_000),
        audioBitRate = stats.audioBitrateKbps?.times(1_000),
        renderedDropCount = stats.droppedFrames.takeIf { stats.decodedFrames > 0 },
        bufferedDurationMs = stats.cacheAheadMs.takeIf { it > 0 },
    )
}
