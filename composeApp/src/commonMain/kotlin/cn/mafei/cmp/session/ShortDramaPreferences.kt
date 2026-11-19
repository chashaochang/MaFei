package cn.mafei.cmp.session

import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.core.storage_api.putBoolean

internal class ShortDramaPreferences(private val settings: SettingsStorage) {
    suspend fun load(scope: String, library: Boolean, id: String): Boolean? =
        settings.getString(key(scope, library, id))?.toBooleanStrictOrNull()

    suspend fun save(scope: String, library: Boolean, id: String, value: Boolean?) {
        val key = key(scope, library, id)
        if (value == null) settings.remove(key) else settings.putBoolean(key, value)
    }

    private fun key(scope: String, library: Boolean, id: String): String =
        "short-drama.mode.${scope.length}:$scope.${if (library) "library" else "work"}.$id"
}
