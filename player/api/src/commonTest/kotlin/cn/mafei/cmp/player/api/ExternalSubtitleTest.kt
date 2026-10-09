package cn.mafei.cmp.player.api

import kotlin.test.*

class ExternalSubtitleTest {
    @Test fun srtAndVttUseActualTimestampsAndKeepMultipleLines() {
        val srt = ExternalSubtitleParser.parse("\uFEFF1\r\n00:00:01,500 --> 00:00:03,000\r\n<i>你好</i>\r\n世界\r\n\r\n2\r\n00:00:04,000 --> 00:00:04,000\r\ninvalid")
        assertEquals(listOf(SubtitleCue(1500, 3000, "你好\n世界")), srt)
        assertEquals(listOf(SubtitleCue(1000, 2500, "A & B")), ExternalSubtitleParser.parse("WEBVTT\n\ncaption\n00:01.000 --> 00:02.500 align:center\nA &amp; B"))
    }
    @Test fun assReadsDeclaredFormatAndTextCommasWithoutShowingControlTags() {
        val input = "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:01.23,0:00:03.50,Default,,0,0,0,,{\\an8}Hello, world\\N第二行"
        assertEquals(listOf(SubtitleCue(1230, 3500, "Hello, world\n第二行")), ExternalSubtitleParser.parse(input))
    }
    @Test fun overlappingCuesAndBackwardSeekDoNotLeaveStaleText() {
        val timeline = SubtitleTimeline(listOf(SubtitleCue(0, 5000, "long"), SubtitleCue(1000, 2000, "short"), SubtitleCue(6000, 7000, "later")))
        assertEquals("long\nshort", timeline.textAt(1500))
        assertEquals("long", timeline.textAt(3000))
        assertEquals("later", timeline.textAt(6000))
        assertEquals("", timeline.textAt(7000))
        assertEquals("long\nshort", timeline.textAt(1500))
    }
}
