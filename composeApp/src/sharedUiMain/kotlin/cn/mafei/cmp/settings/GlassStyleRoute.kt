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
    val bottomNavigation by store.bottomNavigation.collectAsState()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun saveSetting(action: suspend () -> Unit) {
        if (saving) return
        saving = true
        error = null
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "保存失败，请重试"
            } finally {
                saving = false
            }
        }
    }
    GlassStyleScreen(
        onBack = onBack,
        modifier = modifier,
        style = style,
        bottomNavigationType = bottomNavigation,
        liquidGlassIntensity = liquidGlassIntensity,
        saving = saving,
        error = error,
        onSelectStyle = { selected ->
            if (selected != style) saveSetting { store.save(selected) }
        },
        onSelectBottomNavigation = { selected ->
            if (selected != bottomNavigation) saveSetting { store.saveBottomNavigation(selected) }
        },
        onLiquidGlassIntensityChanged = onLiquidGlassIntensityChanged,
    )
}
