package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.session.*

@Composable
internal fun GlassStyleEntryBinding(
    popMineRoute: () -> Unit,
    glassStyleStore: GlassStyleStore,
    currentLiquidGlassIntensity: State<Float>,
    currentLiquidGlassIntensityChanged: State<(Float) -> Unit>,
) {
    GlassStyleRoute(
        store = glassStyleStore,
        liquidGlassIntensity = currentLiquidGlassIntensity.value,
        onLiquidGlassIntensityChanged = currentLiquidGlassIntensityChanged.value,
        onBack = popMineRoute,
        modifier = Modifier.fillMaxSize(),
    )
}
