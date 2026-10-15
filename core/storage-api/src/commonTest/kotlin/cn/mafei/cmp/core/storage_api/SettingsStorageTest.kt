package cn.mafei.cmp.core.storage_api

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsStorageTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    @Test
    fun stringRoundTripAndRemove() = runTest {
        val storage = InMemorySettingsStorage()
        assertEquals(null, storage.getString("missing"))

        storage.putString("theme", "dark")
        assertEquals("dark", storage.getString("theme"))
        assertTrue(storage.contains("theme"))

        assertTrue(storage.remove("theme"))
        assertFalse(storage.remove("theme"))
        assertFalse(storage.contains("theme"))
    }

    @Test
    fun bytesRoundTripIsCopied() = runTest {
        val storage = InMemorySettingsStorage()
        val payload = byteArrayOf(1, 2, 3)
        storage.putBytes("blob", payload)
        payload[0] = 9

        val readBack = storage.getBytes("blob")!!
        assertContentEquals(byteArrayOf(1, 2, 3), readBack)
        readBack[0] = 7
        assertContentEquals(byteArrayOf(1, 2, 3), storage.getBytes("blob")!!)
    }

    @Test
    fun stringAndBytesSlotsAreDistinct() = runTest {
        val storage = InMemorySettingsStorage()
        storage.putString("k", "text")
        storage.putBytes("k", byteArrayOf(5))

        assertEquals(null, storage.getString("k"))
        assertContentEquals(byteArrayOf(5), storage.getBytes("k"))
    }

    @Test
    fun listKeysFiltersByPrefixInStableOrder() = runTest {
        val storage = InMemorySettingsStorage()
        storage.putString("b/2", "v")
        storage.putString("a/1", "v")
        storage.putString("a/0", "v")
        storage.putBytes("z", byteArrayOf(1))

        assertEquals(listOf("a/0", "a/1"), storage.listKeys("a/"))
        assertEquals(listOf("a/0", "a/1", "b/2", "z"), storage.listKeys())
    }

    @Test
    fun editAppliesBatchAtomically() = runTest {
        val storage = InMemorySettingsStorage()
        storage.putString("keep", "original")
        storage.putString("drop", "present")

        storage.edit {
            putString("keep", "updated")
            remove("drop")
            putString("new", "added")
        }

        assertEquals("updated", storage.getString("keep"))
        assertEquals(null, storage.getString("drop"))
        assertEquals("added", storage.getString("new"))
    }

    @Test
    fun editLaterMutationWins() = runTest {
        val storage = InMemorySettingsStorage()
        storage.edit {
            putString("k", "first")
            putString("k", "second")
            remove("k")
            putString("k", "final")
        }
        assertEquals("final", storage.getString("k"))
    }

    @Test
    fun editFailureLeavesStoreUntouched() = runTest {
        val storage = InMemorySettingsStorage()
        storage.putString("k", "before")

        try {
            storage.edit {
                putString("k", "changed")
                throw IllegalStateException("boom")
            }
        } catch (_: IllegalStateException) {
        }

        assertEquals("before", storage.getString("k"))
    }

    @Test
    fun typedAccessorsRoundTripWithDefaults() = runTest {
        val storage = InMemorySettingsStorage()
        assertEquals(0L, storage.getLong("n"))
        assertEquals(5L, storage.getLong("n", default = 5L))
        storage.putLong("n", 42L)
        assertEquals(42L, storage.getLong("n"))

        storage.putInt("i", -7)
        assertEquals(-7, storage.getInt("i"))

        storage.putBoolean("b", true)
        assertTrue(storage.getBoolean("b"))
        storage.putBoolean("b", false)
        assertFalse(storage.getBoolean("b"))
        assertTrue(storage.getBoolean("missing", default = true))

        storage.putString("n", "not-a-number")
        assertEquals(99L, storage.getLong("n", default = 99L))
    }
}
