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

/**
 * 全应用只有**深色**一套配色。
 *
 * 这里原来还带一个 `darkTheme: Boolean` 与整套浅色 `lightColorScheme`，那套值逐条对应旧版鸿蒙
 * 的**浅色主题调色板**（`native_navigation_accent #0A59F7` → primary、`native_canvas_background
 * #F1F2F4` → background、`text_primary #121212` → onBackground、`bg_1 #FFFFFF` → surface…）。
 * 那个主题（旧版的"飞牛/原生浅色"）已定下暂时不做，深色是唯一形态，所以整套浅色方案连同它的
 * 服务对象（各处 `if (luminance >= .5f)` 的浅色分支）一起移除 —— 留着只会让旧调色板以
 * "反正用不到"的名义继续被引用。
 */
@Composable
fun MaFeiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
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
