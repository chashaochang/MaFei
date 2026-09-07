package cn.mafei.cmp.common.design_system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cn.mafei.cmp.common.glass.GlassSurfaceSpec

@Composable
fun MaFeiTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme(
            primary = Color(0xFFB9C7FF),
            background = Color(0xFF101218),
            surface = Color(0xFF181B24),
            surfaceVariant = Color(0xFF252A38),
            onSurface = Color(0xFFF0F1F7),
            onSurfaceVariant = Color(0xFFB8BECE),
        ) else lightColorScheme(
            primary = Color(0xFF425CA8),
            background = Color(0xFFF5F6FA),
            surface = Color(0xFFFCFCFF),
            surfaceVariant = Color(0xFFE8EBF4),
            onSurface = Color(0xFF191D29),
            onSurfaceVariant = Color(0xFF535C70),
        ),
        content = content,
    )
}

/** Shared opaque panel; keeps callers independent of the eventual glass engine. */
@Composable
fun MaFeiPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier = modifier, content = content)
}

@Composable
fun MaFeiSurface(
    spec: GlassSurfaceSpec,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Requested levels must remain opaque until the Backdrop adapter is wired.
    val fallbackColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier.background(
            color = if (fallbackColor.alpha > 0f) fallbackColor else Color.Black,
        ),
    ) {
        content()
    }
}
