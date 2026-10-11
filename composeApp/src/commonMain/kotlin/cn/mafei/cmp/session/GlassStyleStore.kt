package cn.mafei.cmp.session

import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.core.storage_api.SettingsStorage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class GlassStyleStore(private val settings: SettingsStorage) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(GlassStyle.Liquid)
    val state = mutableState.asStateFlow()

    suspend fun load(): GlassStyle = mutex.withLock {
        val stored = settings.getString(KEY)
        val style = GlassStyle.entries.firstOrNull { it.storageValue == stored } ?: GlassStyle.Liquid
        mutableState.value = style
        style
    }

    suspend fun save(style: GlassStyle) = mutex.withLock {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            settings.putString(KEY, style.storageValue)
            mutableState.value = style
        }
    }

    internal companion object {
        const val KEY = "mafei.ui.glass-style.v1"
    }
}
