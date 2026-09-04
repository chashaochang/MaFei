package cn.mafei.cmp.core.storage_api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionedStorageTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private class InMemoryVersionedStorage : InMemorySettingsStorage(), VersionedStorage

    @Test
    fun freshStoreHasNoSchemaVersion() = runTest {
        val storage = InMemoryVersionedStorage()
        assertNull(storage.schemaVersion())
        assertFalse(storage.isMigrationCompleted("anything"))
        assertEquals(emptySet(), storage.completedMigrations())
    }

    @Test
    fun schemaVersionRoundTrip() = runTest {
        val storage = InMemoryVersionedStorage()
        storage.setSchemaVersion(3)
        assertEquals(3L, storage.schemaVersion())
        storage.setSchemaVersion(4)
        assertEquals(4L, storage.schemaVersion())
    }

    @Test
    fun migrationMarkersAreIdempotent() = runTest {
        val storage = InMemoryVersionedStorage()
        storage.markMigrationCompleted("legacy-2023")
        storage.markMigrationCompleted("legacy-2023")
        assertTrue(storage.isMigrationCompleted("legacy-2023"))
        assertEquals(setOf("legacy-2023"), storage.completedMigrations())

        storage.markMigrationCompleted("legacy-2024")
        assertEquals(setOf("legacy-2023", "legacy-2024"), storage.completedMigrations())

        storage.clearMigration("legacy-2023")
        assertFalse(storage.isMigrationCompleted("legacy-2023"))
        assertEquals(setOf("legacy-2024"), storage.completedMigrations())
    }

    @Test
    fun reservedKeysDoNotCollideWithUserKeys() = runTest {
        val storage = InMemoryVersionedStorage()
        storage.putString("user-setting", "value")
        storage.setSchemaVersion(1)
        storage.markMigrationCompleted("m1")

        assertEquals("value", storage.getString("user-setting"))
        assertEquals(setOf("m1"), storage.completedMigrations())
        assertEquals(1L, storage.schemaVersion())
    }
}
