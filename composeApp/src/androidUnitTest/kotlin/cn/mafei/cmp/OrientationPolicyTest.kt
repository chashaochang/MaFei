package cn.mafei.cmp

import android.content.pm.ActivityInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class OrientationPolicyTest {
    @Test
    fun compactScreenStaysPortraitOutsidePlayer() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            mafeiRequestedOrientation(compactScreen = true, playerScreenVisible = false),
        )
    }

    @Test
    fun compactWindowedPlayerStaysPortrait() {
        // 必须锁竖屏，否则「退出全屏」在横着拿的手机上没有任何可见变化
        // （传感器持续把窗口留在横屏）。旧版手机同样只有全屏才是横屏。
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            mafeiRequestedOrientation(compactScreen = true, playerScreenVisible = true),
        )
    }

    @Test
    fun leavingFullscreenOnACompactScreenFallsBackToPortrait() {
        // 回退方向的回归守卫：退出全屏（fullscreen=false）必须给出手感可见的结果。
        val entered = mafeiRequestedOrientation(
            compactScreen = true,
            playerScreenVisible = true,
            playerFullscreenRequested = true,
        )
        val exited = mafeiRequestedOrientation(
            compactScreen = true,
            playerScreenVisible = true,
            playerFullscreenRequested = false,
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, entered)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, exited)
    }

    @Test
    fun fullscreenControlRestrictsCompactPlayerToLandscape() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            mafeiRequestedOrientation(
                compactScreen = true,
                playerScreenVisible = true,
                playerFullscreenRequested = true,
            ),
        )
    }

    @Test
    fun largeScreenKeepsNaturalOrientationOutsidePlayer() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            mafeiRequestedOrientation(compactScreen = false, playerScreenVisible = false),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            mafeiRequestedOrientation(compactScreen = false, playerScreenVisible = true),
        )
    }

    @Test
    fun largeScreenPlayerUsesLandscapeOnlyWhileFullscreen() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            mafeiRequestedOrientation(
                compactScreen = false,
                playerScreenVisible = true,
                playerFullscreenRequested = true,
            ),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            mafeiRequestedOrientation(
                compactScreen = false,
                playerScreenVisible = true,
                playerFullscreenRequested = false,
            ),
        )
    }
}
