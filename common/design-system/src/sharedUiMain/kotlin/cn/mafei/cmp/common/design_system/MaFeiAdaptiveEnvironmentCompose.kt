package cn.mafei.cmp.common.design_system

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Platform feature injection point. Window size still comes from the current
 * Compose constraints so rotation, split screen and desktop resizing reflow
 * immediately without rebuilding application state.
 */
data class MaFeiWindowFeatures(
    val safeInsets: MaFeiInsetsDp = MaFeiInsetsDp(),
    val occlusions: List<MaFeiRectDp> = emptyList(),
    val foldPosture: MaFeiFoldPosture = MaFeiFoldPosture.Unknown,
    val displayRole: MaFeiDisplayRole = MaFeiDisplayRole.Default,
    /**
     * Host role for desktop windows. This is not a device/model switch: the
     * desktop host also clamps its minimum window size, while the shared
     * layout still reflows from the current window bounds.
     */
    val desktopWindow: Boolean = false,
    /** A tablet/2-in-1 freeform window with native window chrome. */
    val windowedHost: Boolean = false,
)

val LocalMaFeiWindowFeatures = staticCompositionLocalOf { MaFeiWindowFeatures() }
