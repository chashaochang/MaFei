package cn.mafei.cmp.common.design_system

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Effective interactive safe area for the current Compose window.
 *
 * Android/iOS/Desktop use the union of system bars and display-cutout insets;
 * IME is deliberately excluded because only the focused route may consume it.
 * HarmonyOS currently does not publish those values through CPF, so its thin
 * host injects the live system/nav/cutout avoid-area values. Taking the
 * maximum keeps one inset owner without double-consuming a platform that
 * eventually exposes both paths.
 */
@Immutable
data class MaFeiSafeArea(
    val start: Dp = 0.dp,
    val top: Dp = 0.dp,
    val end: Dp = 0.dp,
    val bottom: Dp = 0.dp,
)

val LocalMaFeiSafeArea = staticCompositionLocalOf { MaFeiSafeArea() }

@Composable
fun currentMaFeiSafeArea(): MaFeiSafeArea {
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    val displayCutout = WindowInsets.displayCutout.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val injected = LocalMaFeiWindowFeatures.current.safeInsets
    return MaFeiSafeArea(
        start = maxOf(
            systemBars.calculateStartPadding(layoutDirection),
            displayCutout.calculateStartPadding(layoutDirection),
            injected.left.dp,
        ),
        top = maxOf(
            systemBars.calculateTopPadding(),
            displayCutout.calculateTopPadding(),
            injected.top.dp,
        ),
        end = maxOf(
            systemBars.calculateEndPadding(layoutDirection),
            displayCutout.calculateEndPadding(layoutDirection),
            injected.right.dp,
        ),
        bottom = maxOf(
            systemBars.calculateBottomPadding(),
            displayCutout.calculateBottomPadding(),
            injected.bottom.dp,
        ),
    )
}
