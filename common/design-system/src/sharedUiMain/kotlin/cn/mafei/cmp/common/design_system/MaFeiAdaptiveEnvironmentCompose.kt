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
)

val LocalMaFeiWindowFeatures = staticCompositionLocalOf { MaFeiWindowFeatures() }
