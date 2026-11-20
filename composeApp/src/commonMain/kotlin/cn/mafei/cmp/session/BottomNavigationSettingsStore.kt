package cn.mafei.cmp.session

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

class BottomNavigationSettingsStore(private val settings: SettingsStorage) {
    private val mutex = Mutex()
    private val mutableBottomNavigation = MutableStateFlow(BottomNavigationType.Floating)
    val bottomNavigation = mutableBottomNavigation.asStateFlow()

    suspend fun load() = mutex.withLock {
        val navigationValue = settings.getString(NAVIGATION_KEY)
        val navigation = BottomNavigationType.entries.firstOrNull { it.storageValue == navigationValue }
            ?: BottomNavigationType.Floating
        mutableBottomNavigation.value = navigation
    }

    suspend fun saveBottomNavigation(type: BottomNavigationType) = mutex.withLock {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            settings.putString(NAVIGATION_KEY, type.storageValue)
            mutableBottomNavigation.value = type
        }
    }

    internal companion object {
        const val NAVIGATION_KEY = "mafei.ui.bottom-navigation.v1"
    }
}
