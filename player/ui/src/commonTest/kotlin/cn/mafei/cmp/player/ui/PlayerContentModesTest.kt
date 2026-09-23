package cn.mafei.cmp.player.ui

import cn.mafei.cmp.player.api.PlayerContentMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 画面比例：旧版 PLAYER_CONTENT_MODE_OPTIONS 的顺序、标签与视频区几何。 */
class PlayerContentModesTest {

    @Test
    fun choicesKeepTheLegacyOrderAndLabels() {
        assertEquals(
            listOf(
                PlayerContentMode.Fit,
                PlayerContentMode.Fill,
                PlayerContentMode.Original,
                PlayerContentMode.Ratio16x9,
                PlayerContentMode.Ratio4x3,
                PlayerContentMode.Ratio21x9,
            ),
            playerContentModeChoices,
        )
        assertEquals(
            listOf("适应", "填满", "原始比例", "16:9", "4:3", "21:9"),
            playerContentModeChoices.map(::playerContentModeLabel),
        )
    }

    @Test
    fun onlyFillAsksTheSurfaceToCrop() {
        assertTrue(PlayerContentMode.Fill.fills)
        playerContentModeChoices.filter { it != PlayerContentMode.Fill }
            .forEach { assertTrue(!it.fills, "$it must be letterboxed") }
    }

    @Test
    fun fitAndFillKeepTheWholePage() {
        listOf(PlayerContentMode.Fit, PlayerContentMode.Fill).forEach { mode ->
            val area = playerVideoArea(mode, 1920, 1080)
            assertTrue(area.fillsPage, "$mode must use the page area")
        }
    }

    @Test
    fun ratioModesShapeTheAreaAndIgnoreTheSourceSize() {
        assertEquals(16f / 9f, playerVideoArea(PlayerContentMode.Ratio16x9, 1920, 1080).aspectRatio)
        assertEquals(4f / 3f, playerVideoArea(PlayerContentMode.Ratio4x3, null, null).aspectRatio)
        assertEquals(21f / 9f, playerVideoArea(PlayerContentMode.Ratio21x9, 3840, 1600).aspectRatio)
    }

    @Test
    fun originalUsesTheSourcePixelsOneToOne() {
        val area = playerVideoArea(PlayerContentMode.Original, 1920, 800)
        assertEquals(PixelSize(1920, 800), area.oneToOnePixels)
        assertNull(area.aspectRatio)
    }

    @Test
    fun originalFallsBackToThePageWhenTheSizeIsUnknown() {
        // 测不出尺寸时给不出 1:1 的区域，退回整页而不是猜一个尺寸。
        assertTrue(playerVideoArea(PlayerContentMode.Original, null, 1080).fillsPage)
        assertTrue(playerVideoArea(PlayerContentMode.Original, 1920, 0).fillsPage)
    }
}
