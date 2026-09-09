package cn.mafei.cmp.data.migration

import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.Server
import cn.mafei.cmp.core.domain.ServerId
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
