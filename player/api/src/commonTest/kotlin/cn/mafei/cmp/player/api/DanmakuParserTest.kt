package cn.mafei.cmp.player.api

import kotlin.test.Test
import kotlin.test.assertEquals

class DanmakuParserTest {
    @Test fun xmlKeepsOriginalFieldMappingAndDecodesTextWithoutEntityExpansion() {
        val result = DanmakuParser.parseXml("""<i><d p="1.5,1,25,16711680,0,0,user,123">红&amp;白&#x1F600;</d><d p="2,5,255,user">顶</d></i>""")
        assertEquals(0xff0000, result[0].color); assertEquals("红&白😀", result[0].text)
        assertEquals(1500L, result[0].timeMs); assertEquals(5, result[1].mode)
        assertEquals(emptyList(), DanmakuParser.parseXml("<!DOCTYPE i [<!ENTITY x 'x'>]><i/>"))
    }
    @Test fun timelineIsSeekableAndBounded() {
        val entries = (0..1000).map { DanmakuEntry(it.toLong(), it * 100L, 1, 0xffffff, "x") }
        assertEquals(48, activeDanmaku(entries, 70_000).size)
        assertEquals(0, activeDanmaku(entries, -1).size)
        assertEquals(0L, activeDanmaku(entries, 0).single().second.timeMs)
        assertEquals(0L, activeDanmaku(entries, 0).single().second.timeMs)
    }
    @Test fun parsesLegacyFieldsSortsByTimeAndIgnoresMalformedComments() {
        val result = DanmakuParser.parse(
            listOf(
                DanmakuComment(2, "3.25,1,16777215,user", "later"),
                DanmakuComment(1, "1.5,4,0xff0000,user", "first"),
                DanmakuComment(3, "bad", "ignored"),
                DanmakuComment(4, "2.0,x,1,user", "ignored"),
                DanmakuComment(5, "2.0,1,1,user", "middle"),
            ),
        )
        assertEquals(listOf("first", "middle", "later"), result.map { it.text })
        assertEquals(listOf(1500L, 2000L, 3250L), result.map { it.timeMs })
        assertEquals(0xff0000, result.first().color)
    }
}
