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
            primary = Color(0xFFBCB7FF),
            onPrimary = Color(0xFF252052),
            primaryContainer = Color(0xFF35314F),
            onPrimaryContainer = Color(0xFFE5DEFF),
            secondary = Color(0xFFA5C8D1),
            secondaryContainer = Color(0xFF233C43),
            onSecondaryContainer = Color(0xFFD0E8ED),
            background = Color(0xFF111318),
            onBackground = Color(0xFFF2F1F6),
            surface = Color(0xFF1C1E26),
            surfaceVariant = Color(0xFF2B2D39),
            onSurface = Color(0xFFF2F1F6),
            onSurfaceVariant = Color(0xFFACAFBD),
            outline = Color(0xFF777985),
            outlineVariant = Color(0xFF393C49),
        ) else lightColorScheme(
            primary = Color(0xFF6253AB),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE9E3FB),
            onPrimaryContainer = Color(0xFF30274E),
            secondary = Color(0xFF416B76),
            secondaryContainer = Color(0xFFDAECF0),
            onSecondaryContainer = Color(0xFF243E46),
            background = Color(0xFFF4F3F7),
            onBackground = Color(0xFF22232B),
            surface = Color(0xFFFDFCFD),
            surfaceVariant = Color(0xFFE9E7F0),
            onSurface = Color(0xFF22232B),
            onSurfaceVariant = Color(0xFF636573),
            outline = Color(0xFF80818E),
            outlineVariant = Color(0xFFDBD9E3),
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
    backdrop: GlassBackdrop? = null,
    content: @Composable () -> Unit,
) {
    GlassSurface(backdrop, spec, MaterialTheme.colorScheme.surface, modifier) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}
