package cn.mafei.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import cn.mafei.cmp.feature.settings.BottomNavigationSettingsScreen
import cn.mafei.cmp.session.BottomNavigationSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun BottomNavigationSettingsRoute(
    store: BottomNavigationSettingsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    BottomNavigationSettingsScreen(
        onBack = onBack,
        modifier = modifier,
        bottomNavigationType = bottomNavigation,
        saving = saving,
        error = error,
        onSelectBottomNavigation = { selected ->
            if (selected != bottomNavigation) saveSetting { store.saveBottomNavigation(selected) }
        },
    )
}
