package cn.mafei.cmp.common.design_system

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassSurface

@Composable
fun MaFeiTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme(
            primary = Color(0xFF5291FF),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF173C60),
            onPrimaryContainer = Color(0xFFDAEBFF),
            secondary = Color(0xFFBFCBD6),
            secondaryContainer = Color(0xFF293A49),
            onSecondaryContainer = Color(0xFFE1EBF3),
            background = Color(0xFF161618),
            onBackground = Color.White,
            surface = Color(0xFF262629),
            surfaceVariant = Color(0xFF303034),
            onSurface = Color.White,
            onSurfaceVariant = Color(0x99FFFFFF),
            outline = Color(0x33FFFFFF),
            outlineVariant = Color(0x1FFFFFFF),
        ) else lightColorScheme(
            primary = Color(0xFF0A59F7), onPrimary = Color.White,
            primaryContainer = Color(0xFFDCE8FF), onPrimaryContainer = Color(0xFF0A285E),
            secondary = Color(0xFF526171), secondaryContainer = Color(0xFFE5EBF2),
            onSecondaryContainer = Color(0xFF253340),
            background = Color(0xFFF1F2F4), onBackground = Color(0xFF121212),
            surface = Color.White, surfaceVariant = Color(0xFFE7E8EB),
            onSurface = Color(0xFF121212), onSurfaceVariant = Color(0xFF606166),
            outline = Color(0xFFB8BAC0), outlineVariant = Color(0xFFD8D9DE),
        ),
        typography = MaFeiTypography,
        content = content,
    )
}

/** Shared opaque panel; keeps callers independent of the eventual glass engine. */
@Composable
fun MaFeiPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaFeiShape.panel
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Column(content = content)
    }
}

@Composable
fun MaFeiSurface(
    spec: GlassSurfaceSpec,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    /**
     * Records this surface's material into that layer, so glass children inside it
     * can sample page + surface instead of the page alone.
     */
    exportedBackdrop: GlassBackdrop? = null,
    content: @Composable () -> Unit,
) {
    GlassSurface(backdrop, spec, MaterialTheme.colorScheme.surface, modifier, exportedBackdrop) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}
