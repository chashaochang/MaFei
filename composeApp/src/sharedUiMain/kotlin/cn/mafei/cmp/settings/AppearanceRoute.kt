package cn.mafei.cmp

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.feature.settings.AppearanceScreen
import cn.mafei.cmp.session.AppearanceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AppearanceRoute(store: AppearanceStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val mode by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AppearanceScreen(onBack, modifier, mode = mode, saving = saving, error = error, onSelect = { selected ->
        if (!saving && selected != mode) {
            saving = true; error = null
            scope.launch {
                try { store.save(selected) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "保存失败，请重试" }
                finally { saving = false }
            }
        }
    })
}
