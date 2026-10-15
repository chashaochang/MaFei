package cn.mafei.cmp.data.repositories.session

import cn.mafei.cmp.core.secure_credential_api.CredentialAlias
import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.core.storage_api.StorageCorruptedException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Non-secret write-ahead index. All access is serialized by SessionRepository. */
internal class SessionAccountIndex(private val settings: SettingsStorage) {
    @Serializable
    internal data class Entry(
        val selection: SessionSelection,
        val aliases: List<String>,
        val populatedAliases: List<String>,
        val identityGeneration: Long,
        val revision: Long,
        val committed: Boolean,
    )

    @Serializable
    internal data class Index(
        val version: Int = 1,
        val accounts: List<Entry> = emptyList(),
        val current: SessionSelection? = null,
    )

    suspend fun read(): Index {
        val raw = settings.getString(KEY) ?: return Index()
        try {
            require(raw.length <= 1_048_576)
            val index = Json.decodeFromString<Index>(raw)
            require(index.version == 1 && index.accounts.size <= 256)
            require(index.accounts.map { it.selection }.distinct().size == index.accounts.size)
            val allAliases = mutableSetOf<String>()
            index.accounts.forEach { entry ->
                require(entry.identityGeneration >= 0 && entry.revision >= 0)
                require(entry.aliases.isNotEmpty() && entry.aliases.size <= 64)
                require(entry.aliases.distinct().size == entry.aliases.size)
                require(entry.populatedAliases.distinct().size == entry.populatedAliases.size)
                require(entry.aliases.containsAll(entry.populatedAliases))
                entry.aliases.forEach { alias -> CredentialAlias(alias); require(allAliases.add(alias)) }
            }
            require(index.current == null || index.accounts.any { it.selection == index.current && it.committed })
            return index
        } catch (_: Exception) {
            // Neither malformed JSON nor a nested decoder's raw value reaches logs.
            throw StorageCorruptedException(KEY, "Invalid session account index")
        }
    }

    /** Publish Pending before touching any member of the secure credential group. */
    suspend fun begin(selection: SessionSelection, aliases: Set<CredentialAlias>, snapshot: SessionCredentialSnapshot): Set<CredentialAlias> {
        val index = read()
        val old = index.accounts.find { it.selection == selection }
        val owned = (old?.aliases.orEmpty() + aliases.map { it.value }).distinct()
        require(index.accounts.filter { it.selection != selection }.none { other -> other.aliases.any { it in owned } }) {
            "Credential aliases belong to another saved account"
        }
        val pending = Entry(selection, owned, snapshot.secrets.keys.map { it.value }, snapshot.identityGeneration, snapshot.revision, false)
        save(index.copy(accounts = index.accounts.filterNot { it.selection == selection } + pending,
            current = index.current.takeUnless { it == selection }))
        return owned.map(::CredentialAlias).toSet()
    }

    suspend fun commit(selection: SessionSelection, aliases: Set<CredentialAlias>, snapshot: SessionCredentialSnapshot) {
        val index = read()
        check(index.accounts.any { it.selection == selection && !it.committed })
        val entry = Entry(selection, aliases.map { it.value }, snapshot.secrets.keys.map { it.value }, snapshot.identityGeneration, snapshot.revision, true)
        save(index.copy(accounts = index.accounts.filterNot { it.selection == selection } + entry, current = selection))
    }

    suspend fun remove(selection: SessionSelection) {
        val index = read()
        save(index.copy(accounts = index.accounts.filterNot { it.selection == selection }, current = index.current.takeUnless { it == selection }))
    }

    /** Stop automatic startup restore while keeping the account restorable from history. */
    suspend fun clearCurrent(selection: SessionSelection) {
        val index = read()
        if (index.current == selection) save(index.copy(current = null))
    }

    private suspend fun save(index: Index) {
        // Check the outgoing bounds too, so a successful write never poisons restart.
        require(index.accounts.size <= 256 && index.accounts.all { it.aliases.size <= 64 })
        val encoded = Json.encodeToString(index)
        require(encoded.length <= 1_048_576)
        settings.edit { putString(KEY, encoded) }
    }

    private companion object { const val KEY = "mafei.session-accounts.v1" }
}
