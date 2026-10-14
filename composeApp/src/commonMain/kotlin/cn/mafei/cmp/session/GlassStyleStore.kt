package cn.mafei.cmp.session

import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.design_system.BottomNavigationType
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
    private val mutableBottomNavigation = MutableStateFlow(BottomNavigationType.Floating)
    val bottomNavigation = mutableBottomNavigation.asStateFlow()

    suspend fun load(): GlassStyle = mutex.withLock {
        val stored = settings.getString(KEY)
        val style = GlassStyle.entries.firstOrNull { it.storageValue == stored } ?: GlassStyle.Liquid
        val navigationValue = settings.getString(NAVIGATION_KEY)
        val navigation = BottomNavigationType.entries.firstOrNull { it.storageValue == navigationValue }
            ?: BottomNavigationType.Floating
        mutableState.value = style
        mutableBottomNavigation.value = navigation
        style
    }

    suspend fun saveBottomNavigation(type: BottomNavigationType) = mutex.withLock {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            settings.putString(NAVIGATION_KEY, type.storageValue)
            mutableBottomNavigation.value = type
        }
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
        const val NAVIGATION_KEY = "mafei.ui.bottom-navigation.v1"
    }
}
