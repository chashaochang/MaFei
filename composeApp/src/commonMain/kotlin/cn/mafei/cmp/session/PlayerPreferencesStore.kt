package cn.mafei.cmp.session

import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.player.api.PlayerPlaybackPreferences
import cn.mafei.cmp.player.api.PlayerEngineType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Process-owned non-secret settings. Failed writes never publish an unsaved selection. */
class PlayerPreferencesStore(private val settings: SettingsStorage) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(PlayerPlaybackPreferences())
    val state = mutableState.asStateFlow()

    suspend fun load(): PlayerPlaybackPreferences = mutex.withLock {
        val parts = settings.getString(KEY)?.split(':')
        val hardware = parts?.getOrNull(0)?.toBooleanStrictOrNull()
        val cache = parts?.getOrNull(1)?.toIntOrNull()
        val engine = parts?.getOrNull(2)?.let { name -> PlayerEngineType.entries.firstOrNull { it.name == name } }
        val value = if (parts?.size == 3 && hardware != null && cache in PlayerPlaybackPreferences.CacheSizesMb && engine != null) {
            PlayerPlaybackPreferences(hardware, requireNotNull(cache), engine)
        } else PlayerPlaybackPreferences()
        mutableState.value = value
        value
    }

    suspend fun save(value: PlayerPlaybackPreferences) = mutex.withLock {
        currentCoroutineContext().ensureActive()
        // Once admitted, a route disappearing must not split disk and runtime state.
        withContext(NonCancellable) {
            settings.putString(KEY, "${value.useHardwareDecode}:${value.cacheSizeMb}:${value.engine.name}")
            mutableState.value = value
        }
    }

    internal companion object { const val KEY = "player.playback-preferences.v1" }
}
