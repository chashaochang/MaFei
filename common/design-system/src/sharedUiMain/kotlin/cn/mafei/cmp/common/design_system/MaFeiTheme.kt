package cn.mafei.cmp.common.design_system

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassSurface

@Composable
fun MaFeiTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme(
            primary = Color(0xFF82BFFF),
            onPrimary = Color(0xFF05284C),
            primaryContainer = Color(0xFF173C60),
            onPrimaryContainer = Color(0xFFDAEBFF),
            secondary = Color(0xFFBACBDB),
            secondaryContainer = Color(0xFF293A49),
            onSecondaryContainer = Color(0xFFE1EBF3),
            background = Color(0xFF101820),
            onBackground = Color(0xFFF5F6F8),
            surface = Color(0xFF1B2732),
            surfaceVariant = Color(0xFF2C3A46),
            onSurface = Color(0xFFF5F6F8),
            onSurfaceVariant = Color(0xFFBFCBD6),
            outline = Color(0xFF8899A8),
            outlineVariant = Color(0xFF40505D),
        ) else lightColorScheme(
            primary = Color(0xFF1461B5),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCEBFA),
            onPrimaryContainer = Color(0xFF14334F),
            secondary = Color(0xFF476278),
            secondaryContainer = Color(0xFFE3EAF0),
            onSecondaryContainer = Color(0xFF243E51),
            background = Color(0xFFF5F4F1),
            onBackground = Color(0xFF19252E),
            surface = Color(0xFFFAFAF8),
            surfaceVariant = Color(0xFFE7EBEE),
            onSurface = Color(0xFF19252E),
            onSurfaceVariant = Color(0xFF52616D),
            outline = Color(0xFF778792),
            outlineVariant = Color(0xFFCFD7DD),
        ),
        typography = MaFeiTypography,
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
    backdrop: GlassBackdrop? = null,
    content: @Composable () -> Unit,
) {
    GlassSurface(backdrop, spec, MaterialTheme.colorScheme.surface, modifier) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}
