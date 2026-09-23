package cn.mafei.cmp.player.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** 竖向手势：旧版 PlayerChrome 的左右分区、量程与反馈条比例。 */
class PlayerGestureFeedbackTest {

    @Test
    fun leftHalfDrivesBrightnessAndRightHalfDrivesVolume() {
        assertEquals(PlayerGestureLevel.Brightness, playerGestureLevelFor(0f, 1000f))
        assertEquals(PlayerGestureLevel.Brightness, playerGestureLevelFor(499f, 1000f))
        // 旧版是严格小于半宽：正好落在中线上归音量。
        assertEquals(PlayerGestureLevel.Volume, playerGestureLevelFor(500f, 1000f))
        assertEquals(PlayerGestureLevel.Volume, playerGestureLevelFor(999f, 1000f))
    }

    @Test
    fun brightnessNeverGoesBelowTheLegacyFloor() {
        // 旧版 PLAYER_MIN_BRIGHTNESS = 0.05：手势不会把屏幕拖到全黑。
        assertEquals(PlayerMinBrightness, playerGestureBrightness(0.5f, -1f))
        assertEquals(PlayerMinBrightness, playerGestureBrightness(0.05f, -0.2f))
        assertEquals(1f, playerGestureBrightness(0.9f, 0.5f))
    }

    @Test
    fun brightnessRampIsFullRangeOverTheVideoHeight() {
        assertEquals(0.7f, playerGestureBrightness(0.5f, 0.2f))
        assertEquals(0.3f, playerGestureBrightness(0.5f, -0.2f))
    }

    @Test
    fun volumeRampUsesStepsAndCannotExceedTheDeviceMaximum() {
        // 从 7/15 拖到顶部：percent 1.0 → 15 步封顶。
        assertEquals(15, playerGestureVolumeSteps(7, 15, 1f))
        assertEquals(0, playerGestureVolumeSteps(7, 15, -1f))
        // 1.5 步按四舍五入进位（旧版 Math.round 同规则）。
        assertEquals(9, playerGestureVolumeSteps(7, 15, 0.1f))
        assertEquals(7, playerGestureVolumeSteps(7, 15, 0f))
    }

    @Test
    fun aDeviceWithoutVolumeStepsNeverReportsAVolume() {
        // maxVolume = 0 表示这台设备报不出音量：结果只能是 0，调用方据此不介入。
        assertEquals(0, playerGestureVolumeSteps(3, 0, 1f))
        assertEquals(0f, playerGestureLevelProgress(PlayerGestureLevel.Volume, 0f, 3, 0))
    }

    @Test
    fun progressBarsFollowTheLegacyScale() {
        assertEquals(0.42f, playerGestureLevelProgress(PlayerGestureLevel.Brightness, 0.42f, 0, 0))
        assertEquals(0.5f, playerGestureLevelProgress(PlayerGestureLevel.Volume, 0f, 5, 10))
        // 亮度进度只认亮度值，音量进度只认步数：混着传不会串味。
        assertEquals(0f, playerGestureLevelProgress(PlayerGestureLevel.Brightness, 0f, 5, 10))
    }

    @Test
    fun longPressHudKeepsTheLegacyCapitalX() {
        assertEquals("3X 快进中", playerSpeedFeedbackText(3f))
    }
}
