package cn.mafei.cmp.data.adapter_contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackPreviewTest {
    private val info = PlaybackPreviewInfo(
        widthPx = 320,
        heightPx = 180,
        intervalMs = 10_000,
        thumbnailCount = 205,
        tileWidth = 10,
        tileHeight = 10,
    )

    @Test
    fun locationStartsAtFirstCell() {
        assertEquals(PlaybackPreviewLocation(0, 0, 0), PlaybackPreviewMath.locate(info, 0))
        assertEquals(PlaybackPreviewLocation(0, 0, 0), PlaybackPreviewMath.locate(info, 9_999))
    }

    @Test
    fun locationRollsToNextSheet() {
        assertEquals(PlaybackPreviewLocation(0, 9, 9), PlaybackPreviewMath.locate(info, 999_999))
        assertEquals(PlaybackPreviewLocation(1, 0, 0), PlaybackPreviewMath.locate(info, 1_000_000))
        assertEquals(PlaybackPreviewLocation(2, 0, 0), PlaybackPreviewMath.locate(info, 2_000_000))
    }

    @Test
    fun locationClampsPastEndAndBeforeStart() {
        assertEquals(PlaybackPreviewLocation(2, 4, 0), PlaybackPreviewMath.locate(info, Long.MAX_VALUE))
        assertEquals(PlaybackPreviewLocation(0, 0, 0), PlaybackPreviewMath.locate(info, -1))
    }

    @Test
    fun incompleteMetadataReturnsNull() {
        assertNull(PlaybackPreviewMath.locate(info.copy(intervalMs = 0), 1))
        assertNull(PlaybackPreviewMath.locate(info.copy(tileWidth = 0), 1))
    }
}
