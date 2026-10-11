package cn.mafei.cmp.common.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Default product-wide liquid-glass background blur: a balanced setting. */
const val DefaultGlassIntensity = 0.5f

/**
 * Shell/window-owned failure latch. A recoverable blur/lens initialization
 * exception makes every surface in this scope opaque until the scope is rebuilt.
 * Changing theme, requested level or accessibility preferences does not reset it.
 * This does not promise recovery from native GPU crashes or capture/layer failures.
 */
@Stable
class GlassRenderHealth internal constructor() {
    var effectFailed: Boolean by mutableStateOf(false)
        private set

    internal val guard = GlassEffectGuard { effectFailed = true }

    /** For a platform effect self-check or an explicit probe; UI/render thread only. */
    fun reportEffectFailure() = guard.fail()
}

@Composable
fun rememberGlassRenderHealth(): GlassRenderHealth = remember { GlassRenderHealth() }

internal val LocalGlassRenderHealth = staticCompositionLocalOf<GlassRenderHealth?> { null }

/**
 * Product-wide liquid-glass background-blur strength. The liquid material itself
 * (lens, highlight, shadow and tint) remains enabled at every value.
 */
internal val LocalGlassIntensity = staticCompositionLocalOf { DefaultGlassIntensity }
internal val LocalGlassStyle = staticCompositionLocalOf { GlassStyle.Liquid }

/** No process-global latch: independent windows have independent rendering health. */
@Composable
fun GlassRenderingEnvironment(
    health: GlassRenderHealth,
    intensity: Float = DefaultGlassIntensity,
    style: GlassStyle = GlassStyle.Liquid,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGlassRenderHealth provides health,
        LocalGlassIntensity provides intensity.coerceIn(0f, 1f),
        LocalGlassStyle provides style,
        content = content,
    )
}
