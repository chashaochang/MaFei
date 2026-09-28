package cn.mafei.cmp.common.design_system

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colors used by the account/detail popup canvas and its nested list items. */
data class MaFeiPopupColors(
    val container: Color,
    val item: Color,
    val primaryText: Color,
    val secondaryText: Color,
)

/** Reference palette sampled from the HarmonyOS tablet popup. */
val MaFeiPopupReferenceColors = MaFeiPopupColors(
    container = Color(0xFF323337),
    item = Color(0xFF5B5C5E),
    primaryText = Color.White,
    secondaryText = Color(0xFFC1C1C1),
)

/** Null outside popup content, so ordinary pages keep the application theme. */
val LocalMaFeiPopupColors = staticCompositionLocalOf<MaFeiPopupColors?> { null }
