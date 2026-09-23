package cn.mafei.cmp

import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaSourceInfo
import cn.mafei.cmp.data.adapter_contract.PlaybackTrack
import cn.mafei.cmp.data.adapter_contract.PlaybackTransport
import cn.mafei.cmp.data.adapter_contract.PlaybackTrackKind
import cn.mafei.cmp.player.ui.PlayerAudioFacts
import cn.mafei.cmp.player.ui.PlayerPlaybackFacts
import cn.mafei.cmp.player.ui.PlayerSubtitleFacts
import cn.mafei.cmp.player.ui.PlayerVideoFacts

/**
 * Domain → 「播放信息」 facts.
 *
 * Lives in the wiring layer because it is the only place that may see the
 * catalogue domain, the adapter contract and the player UI model at once. Every
 * value is copied through unchanged; nothing is inferred that the provider did
 * not state, so an entry the provider omits simply never appears on the card.
 *
 * [playingMediaSourceId] must be the source the CURRENT negotiation selected
 * (the coordinator publishes it) — not the user's pending selection, which may
 * belong to a version that is not on screen.
 *
 * [transport] likewise comes from the negotiation, never from "the user picked
 * a quality": a ceiling the server ignored still has to read as 直连.
 */
internal fun playerPlaybackFacts(
    item: MediaItem,
    playingMediaSourceId: String?,
    transport: PlaybackTransport?,
    providerTracks: List<PlaybackTrack>,
): PlayerPlaybackFacts? {
    val source = item.mediaSources.firstOrNull { it.id != null && it.id == playingMediaSourceId }
        ?: item.mediaSources.firstOrNull()
    val audio = selectedAudioStream(source, providerTracks)
    val subtitle = selectedSubtitleFacts(source, providerTracks)
    val video = source?.videoStream
    if (source == null && providerTracks.isEmpty()) return null
    return PlayerPlaybackFacts(
        title = item.title,
        transport = when (transport) {
            PlaybackTransport.DirectPlay -> "直连"
            // 原码流换封装：字节没重编，但已经不是原文件本身。
            PlaybackTransport.DirectStream -> "转封装"
            PlaybackTransport.Transcode -> "转码"
            null -> null
        },
        container = source?.container,
        audio = audio?.let {
            PlayerAudioFacts(
                codec = it.codec,
                language = it.language,
                channels = it.channels,
                sampleRateHz = it.sampleRateHz,
                bitRate = it.bitRate,
            )
        },
        subtitle = subtitle,
        subtitleStreamCount = source?.subtitleStreams?.size ?: 0,
        video = video?.let {
            PlayerVideoFacts(
                codec = it.codec,
                profile = it.profile,
                widthPx = it.widthPx,
                heightPx = it.heightPx,
                frameRate = it.frameRate,
                bitRate = it.bitRate,
                bitDepth = it.bitDepth,
                colorSpace = it.colorSpace,
                colorPrimaries = it.colorPrimaries,
                pixelFormat = it.pixelFormat,
                videoRangeType = it.videoRangeType,
            )
        },
    )
}

/**
 * The provider stream a selected audio track points at. Falls back to the
 * source's default so a provider that reports no tracks still names the audio.
 */
private fun selectedAudioStream(
    source: MediaSourceInfo?,
    tracks: List<PlaybackTrack>,
): cn.mafei.cmp.core.domain.MediaStreamInfo? {
    val streams = source?.audioStreams.orEmpty()
    if (streams.isEmpty()) return null
    val selectedIndex = tracks.firstOrNull { it.kind == PlaybackTrackKind.Audio && it.selected }?.index
    return streams.firstOrNull { selectedIndex != null && it.index == selectedIndex }
        ?: streams.firstOrNull { it.isDefault }
        ?: streams.first()
}

/**
 * Only a SELECTED subtitle gets facts: with subtitles off the card must say
 * 「未启用」 rather than describe a track that is not being drawn.
 *
 * The catalogue stream is preferred for its codec/language detail; a track the
 * source list cannot resolve (an external file the provider appended at
 * negotiation time) still reports the facts the adapter published.
 */
private fun selectedSubtitleFacts(
    source: MediaSourceInfo?,
    tracks: List<PlaybackTrack>,
): PlayerSubtitleFacts? {
    val selected = tracks.firstOrNull { it.kind == PlaybackTrackKind.Subtitle && it.selected } ?: return null
    val stream = source?.subtitleStreams?.firstOrNull { it.index == selected.index }
    return if (stream != null) {
        PlayerSubtitleFacts(
            title = stream.displayTitle ?: stream.title,
            codec = stream.codec,
            language = stream.language,
            isExternal = stream.isExternal,
        )
    } else {
        PlayerSubtitleFacts(
            title = selected.displayTitle ?: selected.title,
            codec = selected.codec,
            language = selected.language,
            isExternal = selected.isExternal,
        )
    }
}
