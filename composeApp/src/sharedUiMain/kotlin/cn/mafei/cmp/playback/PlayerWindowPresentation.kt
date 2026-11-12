package cn.mafei.cmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.glass.GlassRenderingEnvironment
import cn.mafei.cmp.common.glass.currentGlassStyle
import cn.mafei.cmp.common.glass.rememberGlassRenderHealth
import cn.mafei.cmp.player.ui.LocalPlayerContentReportSubmitter
import cn.mafei.cmp.player.ui.LocalPlayerVideoBackdropSource

@Composable
internal fun PlayerWindowPresentation(
    window: PlayerWindowState,
    owner: Any,
    mediaKey: String,
    title: String,
    glassIntensity: Float,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val runtime = LocalApplicationRuntime.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glassStyle = currentGlassStyle()
    val reportSubmitter = LocalPlayerContentReportSubmitter.current
    val videoBackdrop = LocalPlayerVideoBackdropSource.current
    // Copy only application capabilities and visual preferences. The new root
    // supplies its own density, navigation, window metrics and render health.
    SideEffect {
        window.present(owner, mediaKey, title, onClose) {
            CompositionLocalProvider(
                LocalApplicationRuntime provides runtime,
                LocalPlayerContentReportSubmitter provides reportSubmitter,
                LocalPlayerVideoBackdropSource provides videoBackdrop,
            ) {
                MaFeiTheme(darkTheme = darkTheme) {
                    GlassRenderingEnvironment(rememberGlassRenderHealth(), glassIntensity, glassStyle) {
                        content()
                    }
                }
            }
        }
    }
    DisposableEffect(window, owner) {
        onDispose { window.dismiss(owner) }
    }
}
