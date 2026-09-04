package cn.mafei.cmp.common.design_system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassSurfaceSpec

@Composable
fun MaFeiTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

@Composable
fun MaFeiSurface(
    spec: GlassSurfaceSpec,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val fallbackColor = when (spec.level) {
        GlassLevel.Full -> MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        GlassLevel.Reduced -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        GlassLevel.Opaque -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier.background(
            color = if (fallbackColor.alpha > 0f) fallbackColor else Color.Black,
        ),
    ) {
        content()
    }
}
