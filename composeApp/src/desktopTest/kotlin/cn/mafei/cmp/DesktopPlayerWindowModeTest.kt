package cn.mafei.cmp

import androidx.compose.ui.window.WindowPlacement
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPlayerWindowModeTest {
    @Test fun fullscreenRestoresFloatingOrMaximizedPlacement() {
        for (previous in listOf(WindowPlacement.Floating, WindowPlacement.Maximized)) {
            val mode = DesktopPlayerWindowMode()
            assertEquals(WindowPlacement.Fullscreen, mode.request(previous, true))
            mode.request(WindowPlacement.Fullscreen, true)
            assertEquals(previous, mode.request(WindowPlacement.Fullscreen, false))
        }
    }

    @Test fun closingThePlayerDoesNotExitFullscreenEnteredOutsideThePlayer() {
        val mode = DesktopPlayerWindowMode()
        assertEquals(WindowPlacement.Fullscreen, mode.playerClosed(WindowPlacement.Fullscreen))
        assertEquals(WindowPlacement.Maximized, mode.request(WindowPlacement.Maximized, false))
    }

    @Test fun closingThePlayerRestoresOnlyItsOwnFullscreenTransition() {
        val mode = DesktopPlayerWindowMode()
        mode.request(WindowPlacement.Maximized, true)
        assertEquals(WindowPlacement.Maximized, mode.playerClosed(WindowPlacement.Fullscreen))
    }

    @Test fun nativeExitDoesNotForceAnotherPlacementOrOverwriteTheNextRestoreTarget() {
        val mode = DesktopPlayerWindowMode()
        mode.request(WindowPlacement.Floating, true)
        assertEquals(WindowPlacement.Maximized, mode.request(WindowPlacement.Maximized, false))
        mode.request(WindowPlacement.Maximized, true)
        assertEquals(WindowPlacement.Maximized, mode.request(WindowPlacement.Fullscreen, false))
    }
}
