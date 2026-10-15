package cn.mafei.cmp.player.api

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerTrackDisplayLabelTest {
    @Test fun nativeAudioUsesLanguageAndCodecInsteadOfANumber() {
        assertEquals("英语 · AAC", playerTrackDisplayLabel("1", TrackKind.Audio, "", "eng", "aac"))
        assertEquals("中文 · AC3", playerTrackDisplayLabel("2", TrackKind.Audio, null, " chi ", " ac3 "))
    }

    @Test fun nativeSubtitleUsesLanguageFormatAndExternalStatus() {
        assertEquals("简体中文 · ASS · 外挂",
            playerTrackDisplayLabel("3", TrackKind.Subtitle, "", "zh-Hans", "ass", true))
        assertEquals("英语 · SUBRIP",
            playerTrackDisplayLabel("4", TrackKind.Subtitle, "", "eng", "subrip"))
    }

    @Test fun authoredTrackTitlesRemainAuthoritative() {
        assertEquals("导演评论音轨",
            playerTrackDisplayLabel("2", TrackKind.Audio, " 导演评论音轨 ", "eng", "aac"))
    }

    @Test fun missingMetadataDoesNotInventALanguageOrFormat() {
        assertEquals("音轨 4", playerTrackDisplayLabel("4", TrackKind.Audio, "", "und", null))
        assertEquals("字幕 2", playerTrackDisplayLabel("2", TrackKind.Subtitle, null, null, ""))
        assertEquals("AAC", playerTrackDisplayLabel("1", TrackKind.Audio, "", "und", "aac"))
    }

    @Test fun unrecognizedLanguagesKeepTheActualCode() {
        assertEquals("xyz · AAC", playerTrackDisplayLabel("1", TrackKind.Audio, "", "xyz", "aac"))
    }
}
