package cn.mafei.cmp.player.ui

import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerPhase
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
        assertEquals(listOf("标题", "状态", "缓冲", "倍速"), labels)
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
            facts = PlayerPlaybackFacts(title = "影片", multiConnectionAllowed = false),
            state = state,
            tracks = emptyList(),
        )
        val entries = sections.first { it.title == "播放状态" }.entries.associate { it.label to it.value }
        assertEquals("正在播放", entries["状态"])
        assertEquals("1:30 / 1:00:00", entries["进度"])
        assertEquals("4:00", entries["缓冲"])
        assertEquals("1.5x", entries["倍速"])
        assertEquals("单连接", entries["连接方式"])
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
    }
}
