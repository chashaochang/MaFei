package cn.mafei.cmp.player.ui

import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.api.PlayerTrack
import cn.mafei.cmp.player.api.TrackKind
import kotlin.math.floor

/** One `标签：值` row of the 「播放信息」 card (legacy `PlaybackInfoEntry`). */
data class PlayerInfoEntry(val label: String, val value: String)

/** One titled group of rows (legacy `PlaybackInfoSection`). */
data class PlayerInfoSection(val title: String, val entries: List<PlayerInfoEntry>)

/**
 * Provider-declared facts about the row that is playing.
 *
 * Every field is optional and must stay that way: the legacy card applied an
 * "append only when non-empty" rule to each entry, which is what makes a
 * provider that reports fewer facts produce a shorter card instead of a card
 * full of zeros or "未知".
 */
data class PlayerPlaybackFacts(
    val title: String,
    /** 直连 / 转封装 / 转码 — the three shapes the adapter contract can prove. */
    val transport: String? = null,
    val container: String? = null,
    val protocol: String? = null,
    val video: PlayerVideoFacts? = null,
    val audio: PlayerAudioFacts? = null,
    val subtitle: PlayerSubtitleFacts? = null,
    /** Number of subtitle streams the source declares, used for the 「未启用」 hint. */
    val subtitleStreamCount: Int = 0,
    /** null when the provider does not state it. */
    val multiConnectionAllowed: Boolean? = null,
)

data class PlayerVideoFacts(
    val codec: String? = null,
    val profile: String? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val frameRate: Double? = null,
    val bitRate: Int? = null,
    val bitDepth: Int? = null,
    val colorSpace: String? = null,
    val colorPrimaries: String? = null,
    val pixelFormat: String? = null,
    /** Provider range label: SDR / HDR10 / DolbyVision. */
    val videoRangeType: String? = null,
)

data class PlayerAudioFacts(
    val codec: String? = null,
    val language: String? = null,
    val channels: Int? = null,
    val sampleRateHz: Int? = null,
    val bitRate: Int? = null,
)

data class PlayerSubtitleFacts(
    val title: String? = null,
    val codec: String? = null,
    val language: String? = null,
    val isExternal: Boolean = false,
)

/**
 * Builds the legacy 「播放信息」 sections from provider facts plus live player
 * state.
 *
 * Deliberately absent, because this layer cannot prove them: decoder/renderer
 * names, frame-drop counters, live download speed and buffering percentage.
 * The legacy card got those from mfplayer's own counters; inventing them from
 * `PlayerState` would be the kind of fabricated telemetry this project refuses.
 * The 「播放状态」 section carries the live numbers the controller really owns.
 */
fun buildPlayerInfoSections(
    facts: PlayerPlaybackFacts?,
    state: PlayerState,
    tracks: List<PlayerTrack>,
): List<PlayerInfoSection> {
    val sections = mutableListOf<PlayerInfoSection>()
    val playback = mutableListOf<PlayerInfoEntry>()
    facts?.title?.let { playback.append("标题", it) }
    facts?.transport?.let { playback.append("播放方式", it) }
    facts?.protocol?.let { playback.append("协议", it) }
    facts?.container?.let { playback.append("容器格式", it) }
    sections.appendSection("播放", playback)

    val video = mutableListOf<PlayerInfoEntry>()
    facts?.video?.let { stream ->
        val codec = stream.codec?.let { value -> value.uppercase() }
        video.append("视频编码", listOfNotNull(codec, stream.profile).joinToString(" · "))
        video.append("分辨率", resolutionLabel(stream.widthPx, stream.heightPx, stream.frameRate))
        video.append("视频码率", stream.bitRate?.let(::formatPlayerBitrate).orEmpty())
        video.append("位深度", stream.bitDepth?.takeIf { it > 0 }?.let { "$it bit" }.orEmpty())
        video.append("色彩空间", listOfNotNull(stream.colorPrimaries, stream.colorSpace).joinToString(" · "))
        video.append("像素格式", stream.pixelFormat.orEmpty())
        video.append("动态范围", stream.videoRangeType.orEmpty())
    }
    sections.appendSection("视频", video)

    val audio = mutableListOf<PlayerInfoEntry>()
    facts?.audio?.let { stream ->
        audio.append("音频编码", stream.codec?.uppercase().orEmpty())
        audio.append("语言", stream.language.orEmpty())
        audio.append("声道与采样率", channelsAndSampleRate(stream.channels, stream.sampleRateHz))
        audio.append("音频码率", stream.bitRate?.let(::formatPlayerBitrate).orEmpty())
    }
    // The provider timeline wins only when it names a track; otherwise the
    // runtime's own selected track is the only evidence of what is audible.
    audio.append("当前音轨", selectedTrackLabel(tracks, TrackKind.Audio).orEmpty())
    sections.appendSection("音频", audio)

    val subtitle = mutableListOf<PlayerInfoEntry>()
    val subtitleStream = facts?.subtitle
    if (subtitleStream != null) {
        subtitle.append("当前字幕", listOfNotNull(subtitleStream.title).joinToString(" · "))
        subtitle.append("字幕格式", subtitleStream.codec?.uppercase().orEmpty())
        subtitle.append("语言", subtitleStream.language.orEmpty())
        subtitle.append("来源", if (subtitleStream.isExternal) "外挂" else "内嵌")
    } else {
        val runtimeSubtitle = tracks.firstOrNull { it.kind == TrackKind.Subtitle && it.selected }
        if (runtimeSubtitle != null) {
            subtitle.append("当前字幕", runtimeSubtitle.label)
            subtitle.append("来源", "内嵌")
        } else if ((facts?.subtitleStreamCount ?: 0) > 0) {
            subtitle.append("当前字幕", "未启用")
        }
    }
    sections.appendSection("字幕", subtitle)

    val live = mutableListOf<PlayerInfoEntry>()
    live.append("状态", playerPhaseStatus(state.phase))
    state.durationMs?.takeIf { it > 0L }?.let { duration ->
        live.append("进度", "${formatPlayerDuration(state.positionMs)} / ${formatPlayerDuration(duration)}")
    }
    live.append("缓冲", formatPlayerDuration(state.bufferedPositionMs))
    live.append("倍速", formatPlayerSpeed(state.speed))
    facts?.multiConnectionAllowed?.let { allowed ->
        live.append("连接方式", if (allowed) "多连接" else "单连接")
    }
    sections.appendSection("播放状态", live)

    return sections
}

private fun MutableList<PlayerInfoEntry>.append(label: String, rawValue: String?) {
    val value = normalizePlayerInfoValue(rawValue) ?: return
    add(PlayerInfoEntry(label, value))
}

private fun MutableList<PlayerInfoSection>.appendSection(title: String, entries: List<PlayerInfoEntry>) {
    if (entries.isNotEmpty()) add(PlayerInfoSection(title, entries))
}

/** Blank and provider-reported "unknown" values are dropped, never rendered. */
internal fun normalizePlayerInfoValue(raw: String?): String? {
    val text = raw?.trim().orEmpty()
    return text.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
}

internal fun formatPlayerBitrate(bitsPerSecond: Int): String = when {
    bitsPerSecond >= 1_000_000 -> "${trimPlayerFraction(bitsPerSecond / 1_000_000.0)} Mbps"
    bitsPerSecond >= 1_000 -> "${trimPlayerFraction(bitsPerSecond / 1_000.0)} Kbps"
    bitsPerSecond > 0 -> "$bitsPerSecond bps"
    else -> ""
}

internal fun formatPlayerFrameRate(frameRate: Double): String =
    if (frameRate > 0) "${trimPlayerFraction(frameRate)} fps" else ""

internal fun formatPlayerSpeed(speed: Float): String = if (speed == 1f) "1x" else "${trimPlayerFraction(speed.toDouble())}x"

/** Legacy `trimFraction`: two decimals, trailing zeros removed (20.00 → 20). */
internal fun trimPlayerFraction(value: Double): String {
    val fixed = kotlin.math.round(value * 100.0) / 100.0
    if (fixed == floor(fixed)) return fixed.toLong().toString()
    val text = fixed.toString()
    return text.trimEnd('0').trimEnd('.')
}

internal fun resolutionLabel(widthPx: Int?, heightPx: Int?, frameRate: Double?): String {
    val frame = frameRate?.let(::formatPlayerFrameRate).orEmpty()
    if (widthPx == null || heightPx == null || widthPx <= 0 || heightPx <= 0) return frame
    return listOf("${widthPx}×$heightPx", frame).filter { it.isNotEmpty() }.joinToString(" · ")
}

private fun channelsAndSampleRate(channels: Int?, sampleRateHz: Int?): String = listOfNotNull(
    channels?.takeIf { it > 0 }?.let { "$it 声道" },
    sampleRateHz?.takeIf { it > 0 }?.let { "${kotlin.math.round(it / 1000.0).toInt()} kHz" },
).joinToString(" · ")

private fun selectedTrackLabel(tracks: List<PlayerTrack>, kind: TrackKind): String? =
    tracks.firstOrNull { it.kind == kind && it.selected }?.label?.let { normalizePlayerInfoValue(it) }
