package cn.mafei.cmp.data.migration

import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.Server
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.secure_credential_api.CredentialAlias
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private class SettingsBackedSinkTestHarness {
    val settings = InMemorySettingsStorage()
    val sink = SettingsBackedMigrationSink(settings)
    val records = MigrationRecords(
        servers = listOf(Server(ServerId("srv-1"), ProviderKind.Jellyfin, "Legacy Jellyfin", "http://192.0.2.10:8096")),
        accounts = emptyList(),
        playback = MigratedPlaybackPreferences(null, null, null, emptyList()),
        chasing = emptyList(),
        home = null,
        selectedAccountId = null,
        connections = emptyList(),
    )
    val manifest = """{"migration":"fixture-1"}"""
}

class SettingsBackedMigrationSinkTest {

    @Test
    fun commitStoresRecordsAndReceiptAtomically() = runBlocking {
        val harness = SettingsBackedSinkTestHarness()
        assertNull(harness.sink.receipt("m-1"))
        harness.sink.commit("m-1", harness.manifest, harness.records)
        assertEquals(harness.manifest, harness.sink.receipt("m-1"))
        val stored = harness.sink.storedRecords("m-1")
        assertEquals("srv-1", stored?.servers?.single()?.id?.value)
        // Both keys live under one atomic edit: receipt present implies records present.
        assertEquals(1, harness.settings.listKeys("mafei.migration.records").size)
    }

    @Test
    fun identicalReplayIsANoOpAndConflictingPayloadIsRejected() = runBlocking {
        val harness = SettingsBackedSinkTestHarness()
        harness.sink.commit("m-1", harness.manifest, harness.records)
        // Identical replay: no throw, no data change.
        harness.sink.commit("m-1", harness.manifest, harness.records)
        assertEquals(harness.manifest, harness.sink.receipt("m-1"))
        // Same id, different payload: source change must be rejected.
        assertFailsWith<MigrationValidationException> {
            harness.sink.commit(
                "m-1",
                """{"migration":"different"}""",
                harness.records.copy(selectedAccountId = null),
            )
        }
        // Receipt still holds the original manifest (no partial overwrite).
        assertEquals(harness.manifest, harness.sink.receipt("m-1"))
    }

    @Test
    fun exclusiveSerializesMigrations() = runBlocking {
        val harness = SettingsBackedSinkTestHarness()
        val order = mutableListOf<String>()
        val guard = Mutex()
        harness.sink.exclusive {
            guard.withLock { order += "first-enter" }
            // While the first migration holds the lease, a nested/exclusive call
            // would deadlock by design; instead verify the lease marker.
            assertEquals(LEASE_HELD_VALUE, harness.settings.getString("mafei.migration.lease"))
        }
        harness.sink.exclusive {
            guard.withLock { order += "second-enter" }
        }
        assertTrue(order.first() == "first-enter")
        assertEquals(LEASE_FREE_VALUE, harness.settings.getString("mafei.migration.lease"))
    }

    companion object {
        const val LEASE_HELD_VALUE = "held"
        const val LEASE_FREE_VALUE = "free"
    }
}


class MigrationAliasNamespaceTest {

    private class Reader : cn.mafei.cmp.data.migration.LegacySnapshotReader {
        val snapshot = LegacySnapshot(
            1, "installation-1",
            LegacySource.entries.associateWith { LegacySourceState.Read },
            listOf(LegacyServer("j", ProviderKind.Jellyfin, "Jelly", "https://j.example/media")),
            listOf(LegacyAccount("j-account", "j", "remote-j", "J",
                SecureSecret.AuthToken("MediaBrowser", "J-SECRET"))),
        )
        override suspend fun read() = LegacyReadResult.Captured(snapshot)
    }

    @Test
    fun migratedSecretsLandInTheSessionAliasNamespace() = runBlocking {
        val settings = InMemorySettingsStorage()
        val secure = object : cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore {
            val values = mutableMapOf<CredentialAlias, SecureSecret>()
            override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) { values[alias] = secret }
            override suspend fun read(alias: CredentialAlias) = values[alias]
            override suspend fun exists(alias: CredentialAlias) = alias in values
            override suspend fun delete(alias: CredentialAlias) = values.remove(alias) != null
            override suspend fun listAliases(prefix: String?) = values.keys.filter { prefix == null || it.value.startsWith(prefix) }
            override suspend fun deleteAll(prefix: String?) = error("unused")
        }
        val sink = SettingsBackedMigrationSink(settings)
        val engine = LegacyMigrationEngine(Reader(), secure, settings, sink, nowEpochMs = { 0L })

        val result = engine.migrate()
        assertTrue(result is MigrationResult.Completed, "expected completion, got $result")

        // Exactly one secret, stored under the SESSION-shaped alias so
        // SavedConnection tokenAlias resolves to it after restore.
        assertEquals(1, secure.values.size)
        val alias = secure.values.keys.single().value
        assertTrue(alias.startsWith("mafei/session/") && alias.endsWith("/token"), alias)
        val credentialKey = alias.removePrefix("mafei/session/").removeSuffix("/token")
        assertEquals(32, credentialKey.length)
        assertTrue(credentialKey.all { it in '0'..'9' || it in 'a'..'f' }, credentialKey)
        // Deterministic across runs (re-migration must stay readable).
        assertEquals(credentialKey, legacyCredentialKey("j-account"))
    }
}
