package cn.mafei.cmp.common.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

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

/** No process-global latch: independent windows have independent rendering health. */
@Composable
fun GlassRenderingEnvironment(health: GlassRenderHealth, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassRenderHealth provides health, content = content)
}
