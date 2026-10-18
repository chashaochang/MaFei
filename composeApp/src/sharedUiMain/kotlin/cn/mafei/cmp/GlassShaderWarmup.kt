package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import cn.mafei.cmp.common.glass.prewarmGlassShaderPrograms

@Composable
internal fun BindGlassShaderWarmup(startupIdle: Boolean) {
    LaunchedEffect(startupIdle) {
        if (!startupIdle) return@LaunchedEffect
        // Let the entry frame finish before starting the process-owned CPU job.
        withFrameNanos { }
        withFrameNanos { }
        prewarmGlassShaderPrograms()
    }
}
