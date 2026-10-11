package cn.mafei.cmp.session

import cn.mafei.cmp.common.design_system.AppearanceMode
import cn.mafei.cmp.core.storage_api.SettingsStorage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppearanceStore(private val settings: SettingsStorage) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(AppearanceMode.Dark)
    val state = mutableState.asStateFlow()

    suspend fun load() = mutex.withLock {
        val stored = settings.getString(KEY)
        val mode = AppearanceMode.entries.firstOrNull { it.storageValue == stored } ?: AppearanceMode.Dark
        mutableState.value = mode
        mode
    }

    suspend fun save(mode: AppearanceMode) = mutex.withLock {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            settings.putString(KEY, mode.storageValue)
            mutableState.value = mode
        }
    }

    suspend fun storedMode(): String? = mutex.withLock {
        settings.getString(KEY)?.takeIf { value -> AppearanceMode.entries.any { it.storageValue == value } }
    }

    internal companion object { const val KEY = "mafei.ui.appearance-mode.v1" }
}
