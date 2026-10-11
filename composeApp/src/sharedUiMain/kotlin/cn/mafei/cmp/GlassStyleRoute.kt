package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import cn.mafei.cmp.feature.settings.GlassStyleScreen
import cn.mafei.cmp.session.GlassStyleStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun GlassStyleRoute(
    store: GlassStyleStore,
    liquidGlassIntensity: Float,
    onLiquidGlassIntensityChanged: (Float) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    GlassStyleScreen(
        onBack = onBack,
        modifier = modifier,
        style = style,
        liquidGlassIntensity = liquidGlassIntensity,
        saving = saving,
        error = error,
        onSelectStyle = { selected ->
            if (!saving && selected != style) {
                saving = true
                error = null
                scope.launch {
                    try {
                        store.save(selected)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = "保存失败，请重试"
                    } finally {
                        saving = false
                    }
                }
            }
        },
        onLiquidGlassIntensityChanged = onLiquidGlassIntensityChanged,
    )
}
