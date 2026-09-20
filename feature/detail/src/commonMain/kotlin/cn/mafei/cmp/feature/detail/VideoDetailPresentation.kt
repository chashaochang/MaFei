package cn.mafei.cmp.feature.detail

import cn.mafei.cmp.common.core.formatLocalDateTime
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaSourceInfo
import cn.mafei.cmp.core.domain.MediaStreamInfo
import cn.mafei.cmp.core.domain.MediaStreamKind
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.episodeLabel
import kotlin.math.abs
import kotlin.math.roundToLong

internal data class DetailMediaOption(
    val id: String,
    val streamIndex: Int,
    val compactLabel: String,
    val title: String,
    val summary: String,
)

internal data class DetailInfoRow(
    val title: String,
    val value: String,
    val dividerBefore: Boolean = false,
)

internal data class DetailInfoCard(
    val id: String,
    val title: String,
    val rows: List<DetailInfoRow>,
)

internal data class DetailInfoSection(
    val id: String,
    val title: String,
    val cards: List<DetailInfoCard>,
)

/** Exact tag keys emitted by the legacy BaseVideoDetailPresentationAdapter. */
internal enum class DetailVideoTag {
    Tag1080,
    Tag4K,
    Stereo,
    Hdr,
    Sdr,
    Dts,
    DolbyVision,
    DolbyAtmos,
    DolbySurround,
}

internal data class DetailPlaybackSource(
    val source: MediaSourceInfo,
    val id: String,
    val compactLabel: String,
    val title: String,
    val summary: String,
    val filePath: String,
    val fileSize: String,
    val fileCreatedDate: String,
    val addedDate: String,
    val tagKeys: List<DetailVideoTag>,
    val audioOptions: List<DetailMediaOption>,
    val subtitleOptions: List<DetailMediaOption>,
    val defaultAudioStreamIndex: Int?,
    val defaultSubtitleStreamIndex: Int,
    val videoSummary: String,
    val audioSummary: String,
    val subtitleSummary: String,
    val infoSections: List<DetailInfoSection>,
) {
    val hasFileInformation: Boolean
        get() = filePath.isNotBlank() || fileSize.isNotBlank() ||
            fileCreatedDate.isNotBlank() || addedDate.isNotBlank()

    val hasVideoInformation: Boolean
        get() = videoSummary.isNotBlank() || audioSummary.isNotBlank() ||
            subtitleSummary.isNotBlank() || infoSections.isNotEmpty()
}

/**
 * Exact `VideoDetailViewModel.applyDetail` source rule from the ArkUI client:
 * prefer sources that are not explicitly unplayable, but when the provider
 * marks every source unplayable keep the original list so file/media details
 * do not disappear from the page.
 */
internal fun selectLegacyDetailSources(sources: List<MediaSourceInfo>): List<MediaSourceInfo> {
    val playable = sources.filter { it.playable != false }
    return playable.ifEmpty { sources }
}

/** Exact legacy detail-button label after the episode queue has resolved. */
internal fun legacyDetailPlayLabel(item: MediaItem, target: MediaItem?): String {
    if ((item.mediaType == MediaType.Series || item.mediaType == MediaType.Episode) &&
        target?.mediaType == MediaType.Episode
    ) {
        return target.episodeLabel ?: if (target.userData.hasProgress) "继续播放" else "播放"
    }
    return if ((target ?: item).hasLegacyPlaybackProgress()) "继续播放" else "播放"
}

/** Exact start-position value passed by the ArkUI detail pages; do not invent a near-end reset. */
fun legacyPlaybackResumePositionMs(item: MediaItem): Long =
    item.userData.playbackPositionMs.coerceAtLeast(0L)

/** Exact `SeasonDetailViewModel.groupEpisodes` title, including its empty-number dot. */
internal fun legacyEpisodeCardTitle(item: MediaItem): String =
    "${item.episodeNumber ?: ""}.${item.title}".trim()

/** Exact `VideoDetailViewModel.formatDuration`: a positive sub-minute runtime is one minute. */
internal fun legacyDetailDurationLabel(runtimeMs: Long): String {
    if (runtimeMs <= 0L) return ""
    val totalMinutes = maxOf(1L, runtimeMs / 60_000L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}小时${minutes}分钟"
        hours > 0L -> "${hours}小时"
        else -> "${minutes}分钟"
    }
}

/** Exact `SeasonDetailViewModel.applyDetail` series-name fallback order. */
internal fun legacySeasonSeriesName(
    series: MediaItem,
    season: MediaItem,
    mediaName: String?,
): String = season.seriesTitle?.trim().takeUnless { it.isNullOrEmpty() }
    ?: mediaName?.trim().takeUnless { it.isNullOrEmpty() }
    ?: series.title

/** Mechanical Kotlin translation of the legacy video-detail presentation adapters. */
internal fun presentDetailPlaybackSources(
    provider: ProviderKind,
    sources: List<MediaSourceInfo>,
): List<DetailPlaybackSource> = sources.mapIndexed { sourceIndex, source ->
    val videos = source.streams.filter { it.kind == MediaStreamKind.Video }
    val audios = source.streams.filter { it.kind == MediaStreamKind.Audio }
    val subtitles = source.streams.filter { it.kind == MediaStreamKind.Subtitle }
    val video = videos.legacyPreferredVideoStream(source.defaultVideoIndex)
    val audio = audios.preferredStream(source.defaultAudioIndex)
    val defaultAudio = audios.preferredStreamIndex(source.defaultAudioIndex)
    val defaultSubtitle = when (source.defaultSubtitleIndex) {
        -1 -> -1
        else -> subtitles.preferredStreamIndex(source.defaultSubtitleIndex) ?: -1
    }
    DetailPlaybackSource(
        source = source,
        id = source.id?.trim().orEmpty().ifBlank { "source:$sourceIndex" },
        compactLabel = compactSourceLabel(provider, source, video, sourceIndex),
        title = source.name?.trim().orEmpty().ifBlank { "版本 ${sourceIndex + 1}" },
        summary = sourceSummary(provider, source, video, audio),
        filePath = source.path?.trim().orEmpty(),
        fileSize = fileSize(provider, source.sizeBytes ?: 0L),
        fileCreatedDate = source.fileCreatedEpochMs?.let {
            formatLocalDateTime(it, includeTime = provider != ProviderKind.Feiniu)
        }.orEmpty(),
        addedDate = source.dateAddedEpochMs?.let {
            formatLocalDateTime(it, includeTime = provider != ProviderKind.Feiniu)
        }.orEmpty(),
        tagKeys = videoTags(source.streams),
        audioOptions = audios.mapIndexed { index, stream -> audioOption(provider, stream, index) },
        subtitleOptions = listOf(noSubtitleOption()) +
            subtitles.mapIndexed { index, stream -> subtitleOption(provider, stream, index) },
        defaultAudioStreamIndex = defaultAudio,
        defaultSubtitleStreamIndex = defaultSubtitle,
        videoSummary = videoStreamSummary(provider, videos),
        audioSummary = audioStreamSummary(provider, audios),
        subtitleSummary = subtitleStreamSummary(subtitles),
        infoSections = infoSections(provider, videos, audios, subtitles),
    )
}

private fun List<MediaStreamInfo>.legacyPreferredVideoStream(preferredIndex: Int?): MediaStreamInfo? =
    preferredIndex?.let { preferred -> firstOrNull { it.index == preferred } }
        ?: firstOrNull()

private fun List<MediaStreamInfo>.preferredStream(preferredIndex: Int?): MediaStreamInfo? =
    preferredIndex?.let { preferred -> firstOrNull { it.index == preferred } }
        ?: firstOrNull { it.isDefault }
        ?: firstOrNull()

private fun List<MediaStreamInfo>.preferredStreamIndex(preferredIndex: Int?): Int? =
    preferredStream(preferredIndex)?.index

private fun compactSourceLabel(
    provider: ProviderKind,
    source: MediaSourceInfo,
    video: MediaStreamInfo?,
    index: Int,
): String {
    val resolution = resolutionLabel(video)
    val range = dynamicRangeLabel(video)
    val combined = "$resolution$range".trim()
    if (combined.isNotEmpty()) return combined
    val name = source.name?.trim().orEmpty()
    return name.ifBlank { if (provider == ProviderKind.Feiniu) "视频源 ${index + 1}" else "视频源 ${index + 1}" }
}

private fun sourceSummary(
    provider: ProviderKind,
    source: MediaSourceInfo,
    video: MediaStreamInfo?,
    audio: MediaStreamInfo?,
): String = buildList {
    addUnique(resolutionLabel(video))
    addUnique(video?.codec?.trim()?.uppercase())
    addUnique(dynamicRangeLabel(video))
    addUnique(audio?.audioType?.trim())
    addUnique(audio?.codec?.trim()?.uppercase())
    addUnique(audio?.let(::audioChannelText))
    addUnique(source.container?.trim()?.uppercase())
    addUnique(fileSize(provider, source.sizeBytes ?: 0L))
}.joinToString(" · ").ifBlank { "播放版本" }

private fun audioOption(provider: ProviderKind, stream: MediaStreamInfo, index: Int): DetailMediaOption {
    val language = legacyLanguageDisplayName(stream.language)
    val title = when {
        language != "未知" -> "${language}音频"
        provider == ProviderKind.Jellyfin -> "未知音频"
        else -> "音轨 ${index + 1}"
    }
    val codec = stream.codec?.trim()?.uppercase().orEmpty()
    val channels = audioChannelText(stream)
    val summary = buildList {
        val displayTitle = if (provider == ProviderKind.Feiniu) {
            stream.title?.takeUnless { candidate ->
                candidate.equals("und", ignoreCase = true) || candidate.equals(title, ignoreCase = true) ||
                    legacyLanguageDisplayName(candidate) != "未知"
            }
        } else {
            stream.displayTitle
        }
        addUnique(displayTitle)
        addUnique(codec)
        addUnique(channels)
        stream.sampleRateHz?.takeIf { it > 0 }?.let { addUnique("$it Hz") }
        if (stream.isDefault) addUnique("默认")
    }.joinToString(" · ")
    val compactLabel = if (provider == ProviderKind.Jellyfin) {
        compactOptionLabel(title, listOf(codec, channels))
    } else {
        title
    }
    return DetailMediaOption("audio:${stream.index}:$index", stream.index ?: index, compactLabel, title, summary)
}

private fun subtitleOption(provider: ProviderKind, stream: MediaStreamInfo, index: Int): DetailMediaOption {
    val language = legacyLanguageDisplayName(stream.language)
    val title = when {
        language != "未知" -> "${language}字幕"
        provider == ProviderKind.Jellyfin -> "未知字幕"
        else -> "字幕 ${index + 1}"
    }
    val codec = stream.codec?.trim()?.uppercase().orEmpty()
    val summary = buildList {
        val displayTitle = if (provider == ProviderKind.Feiniu) {
            stream.title?.takeUnless { candidate ->
                candidate.equals("und", ignoreCase = true) || candidate.equals(title, ignoreCase = true) ||
                    legacyLanguageDisplayName(candidate) != "未知"
            }
        } else {
            stream.displayTitle
        }
        addUnique(displayTitle)
        addUnique(codec)
        if (stream.isExternal) addUnique("外挂")
        if (stream.isForced) addUnique("强制")
        if (stream.isDefault) addUnique("默认")
    }.joinToString(" · ")
    val compactLabel = if (provider == ProviderKind.Jellyfin) compactOptionLabel(title, listOf(codec)) else title
    return DetailMediaOption("subtitle:${stream.index}:$index", stream.index ?: index, compactLabel, title, summary)
}

private fun noSubtitleOption() = DetailMediaOption(
    id = "subtitle:none",
    streamIndex = -1,
    compactLabel = "无字幕",
    title = "无字幕",
    summary = "",
)

private fun compactOptionLabel(title: String, details: List<String>): String = buildList {
    add(title)
    details.forEach { addUnique(it) }
}.joinToString(" ")

private fun videoStreamSummary(provider: ProviderKind, streams: List<MediaStreamInfo>): String {
    val video = streams.firstOrNull() ?: return ""
    if (provider == ProviderKind.Feiniu) {
        val main = buildList {
            val resolution = resolutionLabel(video)
            addUnique(
                resolution.takeIf { it.isNotBlank() }?.lowercase()
                    ?: if ((video.widthPx ?: 0) > 0 && (video.heightPx ?: 0) > 0) {
                        "${video.widthPx}x${video.heightPx}"
                    } else null,
            )
            addUnique(video.codec?.trim()?.uppercase())
            video.bitRate?.takeIf { it > 0 }?.let { addUnique(bitrateText(it)) }
        }.joinToString(" ")
        val bitDepth = video.bitDepth?.takeIf { it > 0 }?.let { "$it bit" }.orEmpty()
        return when {
            main.isNotBlank() && bitDepth.isNotBlank() -> "$main · $bitDepth"
            main.isNotBlank() -> main
            else -> bitDepth
        }
    }
    return streamSummary(video)
}

private fun audioStreamSummary(provider: ProviderKind, streams: List<MediaStreamInfo>): String {
    val audio = streams.firstOrNull() ?: return ""
    if (provider == ProviderKind.Feiniu) {
        val main = buildList {
            add(legacyLanguageDisplayName(audio.language))
            addUnique(audio.codec?.trim()?.uppercase())
        }.joinToString(" ")
        val sampleRate = audio.sampleRateHz?.takeIf { it > 0 }?.let { "$it Hz" }.orEmpty()
        return if (sampleRate.isBlank()) main else "$main · $sampleRate"
    }
    return streamSummary(audio)
}

private fun streamSummary(stream: MediaStreamInfo): String = buildList {
    addUnique(stream.displayTitle)
    stream.bitRate?.takeIf { it > 0 }?.let { addUnique(bitrateText(it)) }
    stream.bitDepth?.takeIf { it > 0 }?.let { addUnique("$it bit") }
}.joinToString(" · ")

private fun subtitleStreamSummary(streams: List<MediaStreamInfo>): String {
    val stream = streams.firstOrNull() ?: return ""
    return listOf(legacyLanguageDisplayName(stream.language), stream.codec?.trim()?.uppercase().orEmpty())
        .filter(String::isNotBlank)
        .joinToString(" · ")
}

private fun infoSections(
    provider: ProviderKind,
    videos: List<MediaStreamInfo>,
    audios: List<MediaStreamInfo>,
    subtitles: List<MediaStreamInfo>,
): List<DetailInfoSection> = buildList {
    videos.filter(::hasVideoDetails).mapIndexed(::videoInfoCard).takeIf { it.isNotEmpty() }?.let {
        add(DetailInfoSection("video", "视频", it))
    }
    audios.filter(::hasAudioDetails).mapIndexed(::audioInfoCard).takeIf { it.isNotEmpty() }?.let {
        add(DetailInfoSection("audio", "音频", it))
    }
    subtitles.filter(::hasSubtitleDetails).mapIndexed { index, subtitle ->
        subtitleInfoCard(provider, index, subtitle)
    }.takeIf { it.isNotEmpty() }?.let {
        add(DetailInfoSection("subtitle", "字幕", it))
    }
}

private fun hasVideoDetails(stream: MediaStreamInfo): Boolean = listOf(
    stream.title,
    stream.displayTitle,
    stream.codec,
    stream.profile,
    stream.videoRangeType,
    stream.colorPrimaries,
    stream.colorSpace,
    stream.colorTransfer,
    stream.pixelFormat,
    stream.aspectRatio,
    stream.resolutionType,
).any { !it.isNullOrBlank() } || listOf(
    stream.level,
    stream.widthPx,
    stream.heightPx,
    stream.codedWidthPx,
    stream.codedHeightPx,
    stream.rotation,
    stream.frameRate,
    stream.bitRate,
    stream.bitDepth,
    stream.referenceFrames,
    stream.dolbyVisionProfile,
).any { it != null } || stream.isBluray != null

private fun hasAudioDetails(stream: MediaStreamInfo): Boolean = listOf(
    stream.title,
    stream.displayTitle,
    stream.language,
    stream.codec,
    stream.profile,
    stream.audioType,
    stream.channelLayout,
).any { !it.isNullOrBlank() } || listOf(
    stream.channels,
    stream.sampleRateHz,
    stream.bitRate,
).any { it != null } || stream.isFake != null

private fun hasSubtitleDetails(stream: MediaStreamInfo): Boolean = listOf(
    stream.title,
    stream.displayTitle,
    stream.language,
    stream.codec,
    stream.source,
).any { !it.isNullOrBlank() } || stream.isExternal || stream.isDefault || stream.isForced ||
    stream.fileSizeBytes != null

private fun videoInfoCard(index: Int, video: MediaStreamInfo): DetailInfoCard {
    val title = buildList {
        addUnique(resolutionLabel(video))
        addUnique(video.codec?.trim()?.uppercase())
        addUnique(dynamicRangeLabel(video))
    }.joinToString(" ").ifBlank { "视频 ${index + 1}" }
    return DetailInfoCard(
        id = "video:${video.index}:$index",
        title = title,
        rows = listOf(
            infoRow("标题", video.title),
            infoRow("显示标题", video.displayTitle),
            infoRow("编码器", video.codec?.trim()?.lowercase()),
            infoRow("配置", video.profile),
            infoRow("等级", video.level?.let(::plainNumber)),
            infoRow("分辨率", detailedResolution(video)),
            infoRow("编码分辨率", codedResolution(video)),
            infoRow("宽高比", video.aspectRatio),
            infoRow("旋转", video.rotation?.let { "$it°" }),
            infoRow("蓝光源", video.isBluray?.yesNo()),
            infoRow("隔行扫描", video.interlaced?.yesNo(), dividerBefore = true),
            infoRow("帧率", video.frameRate?.let { "${plainNumber(it)} fps" }),
            infoRow("码率", video.bitRate?.let(::bitrateText)),
            infoRow("视频动态范围", detailedDynamicRange(video)),
            infoRow("色彩原色", video.colorPrimaries),
            infoRow("色彩空间", video.colorSpace, dividerBefore = true),
            infoRow("色彩转换", video.colorTransfer),
            infoRow("位深度", video.bitDepth?.let { "$it bit" }),
            infoRow("像素格式", video.pixelFormat),
            infoRow("参考帧", video.referenceFrames?.toString()),
        ),
    )
}

private fun audioInfoCard(index: Int, audio: MediaStreamInfo): DetailInfoCard {
    val language = legacyLanguageDisplayName(audio.language)
    val title = buildList {
        addUnique(language)
        addUnique(audio.codec?.trim()?.uppercase())
        addUnique(audioChannelText(audio))
    }.joinToString(" ").ifBlank { "音频 ${index + 1}" }
    return DetailInfoCard(
        id = "audio:${audio.index}:$index",
        title = title,
        rows = listOf(
            infoRow("标题", audio.title),
            infoRow("显示标题", audio.displayTitle),
            infoRow("语言", language),
            infoRow("编码器", audio.codec?.trim()?.lowercase()),
            infoRow("配置", audio.profile),
            infoRow("音频类型", audio.audioType),
            infoRow("伪音轨", audio.isFake?.yesNo()),
            infoRow("声道", audio.channels?.let { "$it ch" }, dividerBefore = true),
            infoRow("采样率", audio.sampleRateHz?.let { "$it Hz" }),
            infoRow("码率", audio.bitRate?.let(::bitrateText)),
            infoRow("布局", audio.channelLayout, dividerBefore = true),
            infoRow("默认", audio.isDefault.yesNo()),
        ),
    )
}

private fun subtitleInfoCard(provider: ProviderKind, index: Int, subtitle: MediaStreamInfo): DetailInfoCard {
    val language = legacyLanguageDisplayName(subtitle.language)
    val title = subtitle.title?.trim().orEmpty().ifBlank { language }
    return DetailInfoCard(
        id = "subtitle:${subtitle.index}:$index",
        title = title,
        rows = listOf(
            infoRow("标题", subtitle.title),
            infoRow("显示标题", subtitle.displayTitle),
            infoRow("语言", language),
            infoRow("编码器", subtitle.codec?.trim()?.lowercase()),
            infoRow("来源", subtitle.source),
            infoRow("文件大小", subtitle.fileSizeBytes?.let { fileSize(provider, it) }),
            infoRow("外置字幕", subtitle.isExternal.yesNo()),
            infoRow("默认", subtitle.isDefault.yesNo()),
            infoRow("强制", subtitle.isForced.yesNo()),
        ),
    )
}

private fun infoRow(title: String, value: String?, dividerBefore: Boolean = false) =
    DetailInfoRow(title, value?.trim().takeUnless { it.isNullOrEmpty() } ?: "--", dividerBefore)

private fun resolutionLabel(video: MediaStreamInfo?): String {
    video ?: return ""
    val explicit = video.resolutionType?.trim()?.uppercase().orEmpty()
    if (explicit.isNotEmpty()) {
        return if (explicit.contains("2160") || explicit.contains("UHD")) "4K" else explicit
    }
    val width = video.widthPx ?: 0
    val height = video.heightPx ?: 0
    return when {
        width >= 3800 || height >= 2000 -> "4K"
        width >= 2500 || height >= 1400 -> "1440P"
        width >= 1900 || height >= 1000 -> "1080P"
        width >= 1200 || height >= 700 -> "720P"
        video.displayTitle.orEmpty().uppercase().let { it.contains("4K") || it.contains("2160") || it.contains("UHD") } -> "4K"
        video.displayTitle.orEmpty().contains("1440", ignoreCase = true) -> "1440P"
        video.displayTitle.orEmpty().uppercase().let { it.contains("1080") || it.contains("FHD") } -> "1080P"
        video.displayTitle.orEmpty().contains("720", ignoreCase = true) -> "720P"
        height > 0 -> "${height}P"
        else -> ""
    }
}

private fun detailedResolution(video: MediaStreamInfo): String =
    if ((video.widthPx ?: 0) > 0 && (video.heightPx ?: 0) > 0) {
        "${video.widthPx} * ${video.heightPx}"
    } else {
        video.resolutionType?.trim().orEmpty()
            .ifBlank { video.displayTitle?.trim().orEmpty() }
            .ifBlank { "--" }
    }

private fun codedResolution(video: MediaStreamInfo): String =
    if ((video.codedWidthPx ?: 0) > 0 && (video.codedHeightPx ?: 0) > 0) {
        "${video.codedWidthPx} * ${video.codedHeightPx}"
    } else {
        ""
    }

private fun dynamicRangeLabel(video: MediaStreamInfo?): String {
    video ?: return ""
    if ((video.dolbyVisionProfile ?: 0) > 0) return "杜比"
    val normalized = listOf(video.videoRangeType, video.displayTitle, video.colorTransfer)
        .joinToString(" ") { it.orEmpty() }.uppercase()
    return when {
        listOf("DOVI", "DOLBY VISION", "杜比").any(normalized::contains) -> "杜比"
        listOf("HDR", "HLG", "VIVID", "PQ", "2084").any(normalized::contains) -> "HDR"
        normalized.contains("SDR") -> "SDR"
        else -> ""
    }
}

private fun detailedDynamicRange(video: MediaStreamInfo): String =
    video.dolbyVisionProfile?.takeIf { it > 0 }?.let { "Dolby Vision P$it" }
        ?: video.videoRangeType?.trim().takeUnless { it.isNullOrEmpty() }
        ?: "--"

private fun audioChannelText(audio: MediaStreamInfo): String {
    val layout = audio.channelLayout?.trim()?.lowercase().orEmpty()
    if (layout.isNotEmpty()) {
        if (layout.contains("stereo")) return "stereo"
        if (layout.contains("mono")) return "mono"
        return layout
    }
    return when (audio.channels ?: 0) {
        1 -> "mono"
        2 -> "stereo"
        6 -> "5.1"
        8 -> "7.1"
        0 -> ""
        else -> "${audio.channels} 声道"
    }
}

/** Mechanical translation of legacy BaseVideoDetailPresentationAdapter.videoTags. */
private fun videoTags(streams: List<MediaStreamInfo>): List<DetailVideoTag> = buildList {
    streams.forEach { stream ->
        when (stream.kind) {
            MediaStreamKind.Video -> {
                when (resolutionLabel(stream)) {
                    "4K" -> addTag(DetailVideoTag.Tag4K)
                    "1080P" -> addTag(DetailVideoTag.Tag1080)
                }
                when (dynamicRangeLabel(stream)) {
                    "杜比" -> addTag(DetailVideoTag.DolbyVision)
                    "HDR" -> addTag(DetailVideoTag.Hdr)
                    "SDR" -> addTag(DetailVideoTag.Sdr)
                }
            }

            MediaStreamKind.Audio -> {
                val layout = stream.channelLayout.orEmpty().lowercase()
                val codec = stream.codec.orEmpty().lowercase()
                if (layout.contains("stereo")) addTag(DetailVideoTag.Stereo)
                if (codec.contains("dts")) {
                    addTag(DetailVideoTag.Dts)
                } else if (codec.contains("dolby") || codec.contains("ac3") || codec.contains("eac3")) {
                    when {
                        layout.contains("7.1") -> addTag(DetailVideoTag.DolbySurround)
                        layout.contains("5.1") -> addTag(DetailVideoTag.DolbyAtmos)
                    }
                }
            }

            else -> Unit
        }
    }
}

private fun fileSize(provider: ProviderKind, bytes: Long): String {
    if (bytes <= 0L) return ""
    if (provider == ProviderKind.Jellyfin) {
        return when {
            bytes >= 1024L * 1024L * 1024L -> "${fixed(bytes.toDouble() / 1024 / 1024 / 1024, 1)} GB"
            bytes >= 1024L * 1024L -> "${fixed(bytes.toDouble() / 1024 / 1024, 1)} MB"
            else -> "${bytes / 1024L} KB"
        }
    }
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "${value.roundToLong()} ${units[unit]}" else "${fixed(value, 2)} ${units[unit]}"
}

private fun bitrateText(value: Int): String = when {
    value >= 1_000_000 -> "${fixed(value / 1_000_000.0, 2)} mbps"
    value >= 1_000 -> "${(value / 1_000.0).roundToLong()} kbps"
    value > 0 -> "$value bps"
    else -> ""
}

private fun fixed(value: Double, digits: Int): String {
    var scale = 1L
    repeat(digits) { scale *= 10L }
    val rounded = (value * scale).roundToLong()
    if (digits == 0) return rounded.toString()
    return "${rounded / scale}.${abs(rounded % scale).toString().padStart(digits, '0')}"
}

private fun plainNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun Boolean.yesNo(): String = if (this) "是" else "否"

private fun MediaItem.hasLegacyPlaybackProgress(): Boolean {
    val position = userData.playbackPositionMs.coerceAtLeast(0L)
    val percentage = (userData.playedPercentage ?: 0.0).coerceIn(0.0, 100.0)
    val duration = (runtimeMs ?: 0L).coerceAtLeast(0L)
    val displayPosition = when {
        position > 0L -> position.toDouble()
        percentage > 0.0 && percentage < 100.0 && duration > 0L -> duration * percentage / 100.0
        else -> 0.0
    }
    return displayPosition > 0.0 && duration > displayPosition && percentage < 100.0
}

private fun MutableList<String>.addUnique(value: String?) {
    val normalized = value?.trim().orEmpty()
    if (normalized.isEmpty() || any { it.equals(normalized, ignoreCase = true) }) return
    add(normalized)
}

private fun MutableList<DetailVideoTag>.addTag(value: DetailVideoTag) {
    if (value !in this) add(value)
}

/** Same sentinel handling and ISO 639 coverage as the legacy LanguageUtil. */
internal fun legacyLanguageDisplayName(value: String?): String {
    val normalized = value?.trim()?.lowercase().orEmpty()
    if (
        normalized.isEmpty() ||
        normalized == "und" ||
        normalized == "unknown" ||
        normalized == "zxx" ||
        normalized == "qaa-qtz" ||
        normalized == "zz-unknow" ||
        normalized == "_no_display_"
    ) {
        return "未知"
    }
    return legacyIsoLanguageDisplayName(normalized)
}
