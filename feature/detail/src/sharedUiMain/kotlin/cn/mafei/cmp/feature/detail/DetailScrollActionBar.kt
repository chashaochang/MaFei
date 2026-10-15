package cn.mafei.cmp.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiActionBar

/** Scroll updates invalidate the toolbar, not the detail body and its artwork. */
@Composable
internal fun DetailScrollActionBar(
    title: String,
    onBack: () -> Unit,
    backdrop: GlassBackdrop,
    materialAlpha: State<Float>,
    titleAlpha: State<Float>,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    collapsedContentColor: Color = Color.White,
    expandedContentColor: Color = collapsedContentColor,
) {
    val progress = materialAlpha.value
    MaFeiActionBar(
        title = title,
        onBack = onBack,
        backdrop = backdrop,
        materialAlpha = if (loading) 1f else progress,
        titleAlpha = if (loading) 0f else titleAlpha.value,
        contentColor = lerp(expandedContentColor, collapsedContentColor, progress),
        modifier = modifier,
    )
}
