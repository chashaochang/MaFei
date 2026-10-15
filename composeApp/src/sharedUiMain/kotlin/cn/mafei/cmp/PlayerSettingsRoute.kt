package cn.mafei.cmp

import androidx.compose.runtime.*
import cn.mafei.cmp.feature.settings.PlayerSettingsScreen
import cn.mafei.cmp.session.PlayerPreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Route-owned async presentation; the application store outlives route disposal. */
@Composable
internal fun PlayerSettingsRoute(store: PlayerPreferencesStore, engineSelection: Boolean = false, onBack: () -> Unit) {
    val preferences by store.state.collectAsState()
    var loading by remember(store) { mutableStateOf(true) }
    var loadFailed by remember(store) { mutableStateOf(false) }
    var saving by remember(store) { mutableStateOf(false) }
    var error by remember(store) { mutableStateOf<String?>(null) }
    var retry by remember(store) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(store, retry) {
        loading = true
        loadFailed = false
        try {
            store.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadFailed = true
        } finally {
            loading = false
        }
    }
    PlayerSettingsScreen(
        preferences = preferences,
        loading = loading,
        loadFailed = loadFailed,
        saving = saving,
        error = error,
        onRetry = { retry++ },
        onSelect = { next ->
            if (!saving && next != preferences) {
                saving = true
                error = null
                scope.launch {
                    try {
                        store.save(next)
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
        onBack = onBack,
        engineSelection = engineSelection,
    )
}
