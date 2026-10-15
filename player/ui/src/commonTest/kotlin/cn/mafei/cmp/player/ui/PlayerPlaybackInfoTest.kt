package cn.mafei.cmp.player.ui

import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerRuntimeInfo
import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.api.PlayerTrack
import cn.mafei.cmp.player.api.TrackKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 播放信息卡：分节、空值剔除与旧版格式化规则。 */
class PlayerPlaybackInfoTest {

    @Test
    fun emptyFactsNeverRenderPlaceholderRows() {
        val sections = buildPlayerInfoSections(
            facts = PlayerPlaybackFacts(title = "影片"),
            state = PlayerState(phase = PlayerPhase.Playing),
            tracks = emptyList(),
        )
        val labels = sections.flatMap { it.entries }.map { it.label }
        assertEquals(listOf("标题", "状态", "倍速"), labels)
        // "unknown" 是 provider 的占位值，不是事实。
        assertTrue(labels.none { it == "视频编码" })
    }

    @Test
    fun unknownProviderValuesAreDropped() {
        val sections = buildPlayerInfoSections(
            facts = PlayerPlaybackFacts(
                title = "影片",
                container = "unknown",
                protocol = "http",
                video = PlayerVideoFacts(codec = null, bitRate = null),
            ),
            state = PlayerState(phase = PlayerPhase.Paused),
            tracks = emptyList(),
        )
        val entries = sections.flatMap { it.entries }
        assertNull(entries.firstOrNull { it.label == "容器格式" })
        assertEquals("http", entries.first { it.label == "协议" }.value)
        // 视频分节里没有任何已知值 → 整节不出现。
        assertTrue(sections.none { it.title == "视频" })
    }

    @Test
    fun videoSectionJoinsCodecProfileAndResolution() {
        val sections = buildPlayerInfoSections(
            facts = PlayerPlaybackFacts(
                title = "影片",
                video = PlayerVideoFacts(
                    codec = "hevc",
                    profile = "Main 10",
                    widthPx = 3840,
                    heightPx = 2160,
                    frameRate = 23.976,
                    bitRate = 20_000_000,
                    bitDepth = 10,
                    videoRangeType = "HDR10",
                ),
            ),
            state = PlayerState(phase = PlayerPhase.Playing),
            tracks = emptyList(),
        )
        val entries = sections.first { it.title == "视频" }.entries.associate { it.label to it.value }
        assertEquals("HEVC · Main 10", entries["视频编码"])
        assertEquals("3840×2160 · 23.98 fps", entries["分辨率"])
        assertEquals("20 Mbps", entries["视频码率"])
        assertEquals("10 bit", entries["位深度"])
        assertEquals("HDR10", entries["动态范围"])
    }

    @Test
    fun subtitlesOffAreReportedAsNotEnabled() {
        val sections = buildPlayerInfoSections(
            facts = PlayerPlaybackFacts(title = "影片", subtitleStreamCount = 2),
            state = PlayerState(phase = PlayerPhase.Playing),
            tracks = emptyList(),
        )
        val entries = sections.first { it.title == "字幕" }.entries
        assertEquals("未启用", entries.single { it.label == "当前字幕" }.value)
    }

    @Test
    fun selectedSubtitleNamesItsSource() {
        val sections = buildPlayerInfoSections(
            facts = PlayerPlaybackFacts(
                title = "影片",
                subtitle = PlayerSubtitleFacts(title = "简体中文", codec = "srt", language = "zh", isExternal = true),
                subtitleStreamCount = 1,
            ),
            state = PlayerState(phase = PlayerPhase.Playing),
            tracks = emptyList(),
        )
        val entries = sections.first { it.title == "字幕" }.entries.associate { it.label to it.value }
        assertEquals("简体中文", entries["当前字幕"])
        assertEquals("SRT", entries["字幕格式"])
        assertEquals("外挂", entries["来源"])
    }

    @Test
    fun runtimeTrackNamesTheAudioWhenTheProviderIsSilent() {
        val sections = buildPlayerInfoSections(
            facts = PlayerPlaybackFacts(title = "影片"),
            state = PlayerState(phase = PlayerPhase.Playing),
            tracks = listOf(PlayerTrack("1", TrackKind.Audio, "国语 · AAC · 2ch", selected = true)),
        )
        assertEquals("国语 · AAC · 2ch", sections.first { it.title == "音频" }.entries.first().value)
    }

    @Test
    fun liveSectionCarriesOnlyControllerOwnedNumbers() {
        val state = PlayerState(
            phase = PlayerPhase.Playing,
            generation = PlaybackGeneration(),
            positionMs = 90_000,
            durationMs = 3_600_000,
            bufferedPositionMs = 240_000,
            speed = 1.5f,
        )
        val sections = buildPlayerInfoSections(
            facts = PlayerPlaybackFacts(title = "影片", protocol = "https", multiConnectionAllowed = false),
            state = state,
            tracks = emptyList(),
        )
        val liveEntries = sections.first { it.title == "播放状态" }.entries.associate { it.label to it.value }
        assertEquals("正在播放", liveEntries["状态"])
        assertEquals("1:30 / 1:00:00", liveEntries["进度"])
        assertEquals("1.5x", liveEntries["倍速"])
        val networkEntries = sections.first { it.title == "网络与缓冲" }.entries.associate { it.label to it.value }
        assertEquals("2 分 30 秒", networkEntries["缓冲时长"])
        assertEquals("单连接", networkEntries["连接方式"])
    }

    @Test
    fun mfplayerDiagnosticsRestoreTheLegacyTechnicalRows() {
        val sections = buildPlayerInfoSections(
            facts = PlayerPlaybackFacts(
                title = "影片",
                video = PlayerVideoFacts(codec = "hevc", bitRate = 20_000_000),
                audio = PlayerAudioFacts(codec = "aac"),
                multiConnectionAllowed = true,
            ),
            state = PlayerState(
                phase = PlayerPhase.Playing,
                runtimeInfo = PlayerRuntimeInfo(
                    decodeMode = "hardware",
                    decoder = "ohcodec-vk",
                    renderer = "gpu-next",
                    videoCodec = "hevc",
                    videoCodecProfile = "Main 10",
                    videoBitRate = 18_000_000,
                    videoWidthPx = 3840,
                    videoHeightPx = 2160,
                    dynamicRange = "Dolby Vision P5",
                    renderedDropCount = 2,
                    decoderDropCount = 1,
                    delayedFrameCount = 3,
                    audioBitRate = 640_000,
                    audioSampleRateHz = 48_000,
                    audioChannels = 6,
                    downloadSpeedBytesPerSecond = 3L * 1024L * 1024L,
                    bufferedDurationMs = 12_500,
                    networkSource = true,
                    configuredConnections = 4,
                    activeConnections = 3,
                ),
            ),
            tracks = emptyList(),
        )
        val playback = sections.first { it.title == "播放" }.entries.associate { it.label to it.value }
        val video = sections.first { it.title == "视频" }.entries.associate { it.label to it.value }
        val audio = sections.first { it.title == "音频" }.entries.associate { it.label to it.value }
        val network = sections.first { it.title == "网络与缓冲" }.entries.associate { it.label to it.value }

        assertEquals("gpu-next", playback["渲染器"])
        assertEquals("硬解 · ohcodec-vk", video["解码"])
        assertEquals("HEVC · Main 10", video["视频编码"])
        assertEquals("18 Mbps（源 20 Mbps）", video["视频码率"])
        assertEquals("Dolby Vision P5", video["动态范围"])
        assertEquals("渲染丢 2 · 解码丢 1 · 延迟 3", video["帧统计"])
        assertEquals("6 声道 · 48 kHz", audio["声道与采样率"])
        assertEquals("640 Kbps", audio["音频码率"])
        assertEquals("3 MB/s", network["实时网速"])
        assertEquals("12.5 秒", network["缓冲时长"])
        assertEquals("4 路并发，活跃 3", network["连接方式"])
    }

    @Test
    fun runtimeDiagnosticsNameTheConfirmedSingleConnectionPath() {
        val sections = buildPlayerInfoSections(
            facts = PlayerPlaybackFacts(title = "影片", protocol = "https", multiConnectionAllowed = true),
            state = PlayerState(
                phase = PlayerPhase.Playing,
                runtimeInfo = PlayerRuntimeInfo(
                    networkSource = true,
                    configuredConnections = 0,
                    activeConnections = 0,
                ),
            ),
            tracks = emptyList(),
        )

        val network = sections.first { it.title == "网络与缓冲" }.entries.associate { it.label to it.value }
        assertEquals("单连接", network["连接方式"])
    }

    @Test
    fun formattingMatchesTheLegacyHelpers() {
        // 旧版 trimFraction: 两位小数后去尾零。
        assertEquals("20", trimPlayerFraction(20.0))
        assertEquals("23.98", trimPlayerFraction(23.976))
        assertEquals("1.5", trimPlayerFraction(1.5))
        assertEquals("20 Mbps", formatPlayerBitrate(20_000_000))
        assertEquals("320 Kbps", formatPlayerBitrate(320_000))
        assertEquals("640 bps", formatPlayerBitrate(640))
        assertEquals("", formatPlayerBitrate(0))
        assertEquals("1x", formatPlayerSpeed(1f))
        assertEquals("1.5 MB/s", formatPlayerByteRate(1_572_864))
        assertEquals("12.5 秒", formatPlayerBufferedDuration(12_500))
    }
}
