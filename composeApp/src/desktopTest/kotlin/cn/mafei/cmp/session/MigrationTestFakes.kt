package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.Account
import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.Server
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.secure_credential_api.CredentialAlias
import cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.core.storage_api.StorageEditor
import cn.mafei.cmp.data.migration.LegacyAccount
import cn.mafei.cmp.data.migration.LegacyServer
import cn.mafei.cmp.data.migration.LegacySnapshot
import cn.mafei.cmp.data.migration.LegacySnapshotReader
import cn.mafei.cmp.data.migration.LegacySource
import cn.mafei.cmp.data.migration.LegacySourceState
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import cn.mafei.cmp.data.migration.LegacyReadResult

internal class MemorySettings : SettingsStorage {
    private val values = linkedMapOf<String, String>()
    override suspend fun getString(key: String): String? = values[key]
    override suspend fun putString(key: String, value: String) { values[key] = value }
    override suspend fun getBytes(key: String): ByteArray? = values[key]?.encodeToByteArray()
    override suspend fun putBytes(key: String, value: ByteArray) { values[key] = value.decodeToString() }
    override suspend fun remove(key: String): Boolean = values.remove(key) != null
    override suspend fun contains(key: String): Boolean = values.containsKey(key)
    override suspend fun listKeys(prefix: String?): List<String> =
        values.keys.filter { prefix == null || it.startsWith(prefix) }.sorted()
    override suspend fun edit(mutate: StorageEditor.() -> Unit) {
        val staged = linkedMapOf<String, String?>()
        val editor = object : StorageEditor {
            override fun putString(key: String, value: String) { staged[key] = value }
            override fun putBytes(key: String, value: ByteArray) { staged[key] = value.decodeToString() }
            override fun remove(key: String) { staged[key] = null }
        }
        editor.mutate()
        for ((key, value) in staged) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }
}

internal class MemorySecure : SecureCredentialStore {
    val values = mutableMapOf<CredentialAlias, SecureSecret>()
    override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) { values[alias] = secret }
    override suspend fun read(alias: CredentialAlias) = values[alias]
    override suspend fun exists(alias: CredentialAlias) = alias in values
    override suspend fun delete(alias: CredentialAlias) = values.remove(alias) != null
    override suspend fun listAliases(prefix: String?) = values.keys.filter { prefix == null || it.value.startsWith(prefix) }
    override suspend fun deleteAll(prefix: String?) = error("unused")
}

internal class StubHttpTransport : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse = HttpResponse(
        200, cn.mafei.cmp.core.http_contract.HttpHeaders.EMPTY, ByteArray(0), request.url, request.url,
    )
    override fun close() { }
}

internal class NoEngine : PlayerRuntime {
    override val capabilities = PlayerCapabilities()
    override fun bind(eventSink: (RuntimeEvent) -> Unit) { }
    override fun execute(command: RuntimeCommand) { }
}

internal class FakeLegacyReader : LegacySnapshotReader {
    override suspend fun read() = LegacyReadResult.Captured(
        LegacySnapshot(
            1, "installation-1",
            LegacySource.entries.associateWith { LegacySourceState.Read },
            listOf(LegacyServer("j", ProviderKind.Jellyfin, "Jelly", "https://media.test/base")),
            listOf(LegacyAccount("j-account", "j", "remote-j", "J",
                SecureSecret.AuthToken("MediaBrowser", "J-SECRET"))),
            selectedAccountKey = "j-account",
        ),
    )
}
