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
    fun compactScreenAllowsSensorRotationInPlayer() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            mafeiRequestedOrientation(compactScreen = true, playerScreenVisible = true),
        )
    }

    @Test
    fun largeScreenKeepsSystemOrientationPolicy() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            mafeiRequestedOrientation(compactScreen = false, playerScreenVisible = false),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            mafeiRequestedOrientation(compactScreen = false, playerScreenVisible = true),
        )
    }
}
