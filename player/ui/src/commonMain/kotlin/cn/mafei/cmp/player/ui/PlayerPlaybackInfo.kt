package cn.mafei.cmp.player.ui

import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.api.PlayerRuntimeInfo
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
 * Runtime-specific facts are read only from [PlayerState.runtimeInfo]. A
 * backend that cannot report a decoder, renderer, frame counters or network
 * speed simply produces fewer rows, matching the old append-if-present rule.
 */
fun buildPlayerInfoSections(
    facts: PlayerPlaybackFacts?,
    state: PlayerState,
    tracks: List<PlayerTrack>,
): List<PlayerInfoSection> {
    val sections = mutableListOf<PlayerInfoSection>()
    val runtime = state.runtimeInfo
    val playback = mutableListOf<PlayerInfoEntry>()
    facts?.title?.let { playback.append("标题", it) }
    facts?.transport?.let { playback.append("播放方式", it) }
    facts?.protocol?.let { playback.append("协议", it) }
    facts?.container?.let { playback.append("容器格式", it) }
    playback.append("渲染器", runtime?.renderer)
    sections.appendSection("播放", playback)

    val video = mutableListOf<PlayerInfoEntry>()
    facts?.video?.let { stream ->
        val codec = firstPlayerInfoValue(runtime?.videoCodec, stream.codec)?.uppercase()
        val profile = firstPlayerInfoValue(runtime?.videoCodecProfile, stream.profile)
        video.append("解码", playerDecodeLabel(runtime))
        video.append("视频编码", listOfNotNull(codec, profile).joinToString(" · "))
        video.append(
            "分辨率",
            resolutionLabel(runtime?.videoWidthPx ?: stream.widthPx, runtime?.videoHeightPx ?: stream.heightPx, stream.frameRate),
        )
        video.append("视频码率", playerVideoBitrateLabel(runtime?.videoBitRate, stream.bitRate))
        video.append("位深度", stream.bitDepth?.takeIf { it > 0 }?.let { "$it bit" }.orEmpty())
        video.append("色彩空间", playerColorSpaceLabel(runtime, stream))
        video.append("像素格式", firstPlayerInfoValue(runtime?.videoPixelFormat, stream.pixelFormat))
        video.append("动态范围", firstPlayerInfoValue(runtime?.dynamicRange, stream.videoRangeType))
        video.append("帧统计", playerFrameStatsLabel(runtime))
    }
    if (facts?.video == null && runtime != null) {
        video.append("解码", playerDecodeLabel(runtime))
        val codec = runtime.videoCodec?.uppercase()
        video.append("视频编码", listOfNotNull(codec, runtime.videoCodecProfile).joinToString(" · "))
        video.append("分辨率", resolutionLabel(runtime.videoWidthPx, runtime.videoHeightPx, null))
        video.append("视频码率", runtime.videoBitRate?.let(::formatPlayerBitrate))
        video.append("色彩空间", playerColorSpaceLabel(runtime, null))
        video.append("像素格式", runtime.videoPixelFormat)
        video.append("动态范围", runtime.dynamicRange)
        video.append("帧统计", playerFrameStatsLabel(runtime))
    }
    sections.appendSection("视频", video)

    val audio = mutableListOf<PlayerInfoEntry>()
    facts?.audio?.let { stream ->
        audio.append("音频编码", stream.codec?.uppercase().orEmpty())
        audio.append("语言", stream.language.orEmpty())
        audio.append(
            "声道与采样率",
            channelsAndSampleRate(runtime?.audioChannels ?: stream.channels, runtime?.audioSampleRateHz ?: stream.sampleRateHz),
        )
        audio.append("音频码率", (runtime?.audioBitRate ?: stream.bitRate)?.let(::formatPlayerBitrate))
    }
    if (facts?.audio == null && runtime != null) {
        audio.append("声道与采样率", channelsAndSampleRate(runtime.audioChannels, runtime.audioSampleRateHz))
        audio.append("音频码率", runtime.audioBitRate?.let(::formatPlayerBitrate))
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

    val network = mutableListOf<PlayerInfoEntry>()
    val networkPlayback = runtime?.networkSource ?: facts?.protocol?.let { !it.equals("file", ignoreCase = true) } ?: false
    if (networkPlayback) {
        runtime?.downloadSpeedBytesPerSecond?.takeIf { it > 0L }?.let { speed ->
            network.append("实时网速", formatPlayerByteRate(speed))
        }
        val bufferedDurationMs = runtime?.bufferedDurationMs?.takeIf { it > 0L }
            ?: (state.bufferedPositionMs - state.positionMs).takeIf { it > 0L }
        bufferedDurationMs?.let { network.append("缓冲时长", formatPlayerBufferedDuration(it)) }
        val configured = runtime?.configuredConnections?.takeIf { it > 1 }
        if (configured != null) {
            val active = runtime.activeConnections?.takeIf { it > 0 }?.let { "，活跃 $it" }.orEmpty()
            network.append("连接方式", "$configured 路并发$active")
        } else if (runtime != null || facts?.multiConnectionAllowed == false) {
            network.append("连接方式", "单连接")
        }
    }
    sections.appendSection("网络与缓冲", network)

    val live = mutableListOf<PlayerInfoEntry>()
    live.append("状态", playerPhaseStatus(state.phase))
    state.durationMs?.takeIf { it > 0L }?.let { duration ->
        live.append("进度", "${formatPlayerDuration(state.positionMs)} / ${formatPlayerDuration(duration)}")
    }
    live.append("倍速", formatPlayerSpeed(state.speed))
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

internal fun formatPlayerByteRate(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1024L * 1024L -> "${trimPlayerFraction(bytesPerSecond / 1024.0 / 1024.0)} MB/s"
    bytesPerSecond >= 1024L -> "${(bytesPerSecond / 1024.0).toInt().coerceAtLeast(1)} KB/s"
    bytesPerSecond >= 0L -> "$bytesPerSecond B/s"
    else -> ""
}

internal fun formatPlayerBufferedDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val seconds = durationMs / 1_000.0
    return if (seconds >= 60.0) {
        "${(seconds / 60.0).toInt()} 分 ${kotlin.math.round(seconds % 60.0).toInt()} 秒"
    } else {
        "${trimPlayerFraction(seconds)} 秒"
    }
}

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

private fun firstPlayerInfoValue(vararg values: String?): String? =
    values.firstNotNullOfOrNull(::normalizePlayerInfoValue)

private fun playerDecodeLabel(runtime: PlayerRuntimeInfo?): String? {
    runtime ?: return null
    val mode = normalizePlayerInfoValue(runtime.decodeMode)?.lowercase()
    val decoder = normalizePlayerInfoValue(runtime.decoder)
    if (mode == null && decoder == null) return null
    val software = mode in setOf("software", "soft", "no") || decoder.equals("no", ignoreCase = true)
    val label = if (software) "软解" else "硬解"
    return decoder?.takeUnless { it.equals("no", ignoreCase = true) }?.let { "$label · $it" } ?: label
}

private fun playerVideoBitrateLabel(actual: Int?, source: Int?): String? {
    val actualValue = actual?.takeIf { it > 0 }
    val sourceValue = source?.takeIf { it > 0 }
    if (actualValue == null) return sourceValue?.let(::formatPlayerBitrate)
    val actualText = formatPlayerBitrate(actualValue)
    return if (sourceValue != null && kotlin.math.abs(sourceValue - actualValue) > 1_000) {
        "$actualText（源 ${formatPlayerBitrate(sourceValue)}）"
    } else {
        actualText
    }
}

private fun playerColorSpaceLabel(runtime: PlayerRuntimeInfo?, source: PlayerVideoFacts?): String? {
    val sourceColor = listOfNotNull(
        firstPlayerInfoValue(runtime?.videoPrimaries, source?.colorPrimaries, source?.colorSpace),
        normalizePlayerInfoValue(runtime?.videoTransfer),
    ).joinToString(" · ")
    val outputColor = listOfNotNull(
        normalizePlayerInfoValue(runtime?.outputPrimaries),
        normalizePlayerInfoValue(runtime?.outputTransfer),
    ).joinToString(" · ")
    return when {
        sourceColor.isNotEmpty() && outputColor.isNotEmpty() && sourceColor != outputColor -> "$sourceColor → $outputColor"
        sourceColor.isNotEmpty() -> sourceColor
        outputColor.isNotEmpty() -> outputColor
        else -> null
    }
}

private fun playerFrameStatsLabel(runtime: PlayerRuntimeInfo?): String? {
    runtime ?: return null
    val parts = buildList {
        runtime.renderedDropCount?.takeIf { it >= 0 }?.let { add("渲染丢 $it") }
        runtime.decoderDropCount?.takeIf { it >= 0 }?.let { add("解码丢 $it") }
        runtime.delayedFrameCount?.takeIf { it >= 0 }?.let { add("延迟 $it") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
