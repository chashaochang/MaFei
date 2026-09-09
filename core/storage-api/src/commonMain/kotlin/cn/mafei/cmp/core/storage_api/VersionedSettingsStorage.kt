package cn.mafei.cmp.core.storage_api

/**
 * Promotes a plain [SettingsStorage] to [VersionedStorage] so migration
 * journals and schema markers can live in the same backend. All versioned
 * members are the interface defaults operating over the string contract.
 */
class VersionedSettingsStorage(private val delegate: SettingsStorage) : VersionedStorage, SettingsStorage by delegate
