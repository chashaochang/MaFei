package cn.mafei.cmp.player.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 清晰度档位表：旧版 QualityOptionsProvider 的裁剪与标签规则。 */
class PlayerQualityOptionsTest {

    @Test
    fun autoEntryIsAlwaysLast() {
        val options = PlayerQualityOptions.applicable(1920, 1080, appliedBitrate = null)
        assertEquals(0, options.last().bitrate)
        assertEquals("自动", options.last().label)
    }

    @Test
    fun ladderIsTrimmedToTheVideoCeiling() {
        val options = PlayerQualityOptions.applicable(1920, 1080, appliedBitrate = null)
        // 1080p 视频不该出现 2160p 档位。
        assertTrue(options.none { it.label.startsWith("2160p") }, options.map { it.label }.toString())
        assertTrue(options.any { it.label.startsWith("1080p") })
        assertTrue(options.any { it.label.startsWith("360p") })
    }

    @Test
    fun fourThreeSourcesAreTreatedAsPillarboxed() {
        // 1440x1080 (4:3) 折算成 1920 宽 → 1080 档位可用。
        val options = PlayerQualityOptions.applicable(1440, 1080, appliedBitrate = null)
        assertTrue(options.any { it.label.startsWith("1080p") })
    }

    @Test
    fun unknownVideoSizeKeepsTheWholeLadder() {
        val options = PlayerQualityOptions.applicable(null, null, appliedBitrate = null)
        assertTrue(options.any { it.label.startsWith("2160p") })
    }

    @Test
    fun selectionMarksTheMatchingBitrate() {
        val options = PlayerQualityOptions.applicable(3840, 2160, appliedBitrate = 10_000_000)
        val selected = options.filter { it.selected }
        assertEquals(1, selected.size)
        assertEquals(10_000_000, selected.single().bitrate)
        assertEquals("1080p  10 Mbps", selected.single().label)
    }

    @Test
    fun autoSelectionMarksTheZeroBitrateEntry() {
        val options = PlayerQualityOptions.applicable(1920, 1080, appliedBitrate = 0)
        assertEquals("自动", options.single { it.selected }.label)
    }

    @Test
    fun providerLadderKeepsTheAutomaticEntryInFront() {
        val options = PlayerQualityOptions.fromProvider(
            renditions = listOf("4K" to 20_000_000, "1080P" to 8_000_000),
            appliedBitrate = null,
        )
        assertEquals(listOf(0, 20_000_000, 8_000_000), options.map { it.bitrate })
        assertEquals("自动", options.first().label)
        assertTrue(options.first().selected)
        assertEquals("4K  20 Mbps", options[1].label)
    }

    @Test
    fun providerLadderMarksTheRenditionThatIsActuallyInEffect() {
        val options = PlayerQualityOptions.fromProvider(
            renditions = listOf("4K" to 20_000_000, "1080P" to 8_000_000),
            appliedBitrate = 8_000_000,
        )
        // 只有实际在播的那一档被勾选；「自动」不再选中。
        assertEquals(listOf(8_000_000), options.filter { it.selected }.map { it.bitrate })
    }

    @Test
    fun providerLadderWithoutResolutionFallsBackToTheFormattedBitrate() {
        val options = PlayerQualityOptions.fromProvider(
            renditions = listOf(null to 4_000_000),
            appliedBitrate = 4_000_000,
        )
        assertEquals("4 Mbps", options[1].label)
    }
}
