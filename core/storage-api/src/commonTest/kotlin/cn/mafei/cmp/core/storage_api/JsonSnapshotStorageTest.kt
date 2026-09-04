package cn.mafei.cmp.core.storage_api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class HomeSnapshot(
    val serverId: String,
    val shelfTitles: List<String> = emptyList(),
    val itemCount: Int = 0,
)

class JsonSnapshotStorageTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private class MutableClock(var now: Long = 1_000_000L) {
        fun nowEpochMs(): Long = now
    }

    private class Fixture {
        val backing = InMemorySettingsStorage()
        val clock = MutableClock()
        val storage = JsonSnapshotStorage(
            storage = backing,
            json = Json { ignoreUnknownKeys = true },
            nowEpochMs = clock::nowEpochMs,
        )

        suspend fun writeRaw(key: String, raw: String) = backing.putString(key, raw)
    }

    @Test
    fun saveAndLoadRoundTrip() = runTest {
        val fixture = Fixture()
        val snapshot = HomeSnapshot(serverId = "srv-1", shelfTitles = listOf("Movies", "TV"), itemCount = 42)

        fixture.storage.save("home", snapshot, HomeSnapshot.serializer(), schemaVersion = 2, appVersion = "2.0.0")
        fixture.clock.now = 1_500_000L

        val entry = fixture.storage.loadEntry("home", HomeSnapshot.serializer())!!
        assertEquals(snapshot, entry.value)
        assertEquals(1_000_000L, entry.metadata.savedAtEpochMs)
        assertEquals(2L, entry.metadata.schemaVersion)
        assertEquals("2.0.0", entry.metadata.appVersion)
        assertEquals("home", entry.key)
        assertEquals(snapshot, fixture.storage.load("home", HomeSnapshot.serializer()))
    }

    @Test
    fun loadMissingReturnsNull() = runTest {
        val fixture = Fixture()
        assertNull(fixture.storage.load("nope", HomeSnapshot.serializer()))
        assertNull(fixture.storage.loadEntry("nope", HomeSnapshot.serializer()))
    }

    @Test
    fun payloadUnknownFieldsSurviveRoundTrip() = runTest {
        val fixture = Fixture()
        fixture.storage.save("home", HomeSnapshot(serverId = "srv-1"), HomeSnapshot.serializer())
        val raw = fixture.backing.getString("home")!!
        val needle = "\"payload\":{\"serverId\":\"srv-1\"}"
        val replacement = "\"payload\":{\"serverId\":\"srv-1\",\"futureField\":{\"x\":1}}"
        assertTrue(raw.contains(needle), "unexpected envelope: " + raw)

        // Simulate a payload written by a newer app version with extra fields.
        fixture.writeRaw("home", raw.replace(needle, replacement))

        assertEquals(HomeSnapshot(serverId = "srv-1"), fixture.storage.load("home", HomeSnapshot.serializer()))
    }

    @Test
    fun loadSaveRoundTripDropsUnknownFieldsByContract() = runTest {
        val fixture = Fixture()
        fixture.storage.save("home", HomeSnapshot(serverId = "srv-1"), HomeSnapshot.serializer())
        val needle = "\"payload\":{\"serverId\":\"srv-1\"}"
        val replacement = "\"payload\":{\"serverId\":\"srv-1\",\"futureField\":{\"x\":1}}"
        fixture.writeRaw("home", fixture.backing.getString("home")!!.replace(needle, replacement))

        // reading tolerates the unknown field...
        assertEquals(HomeSnapshot(serverId = "srv-1"), fixture.storage.load("home", HomeSnapshot.serializer()))

        // ...but saving again normalizes: unknown fields are dropped by contract
        fixture.storage.save("home", fixture.storage.load("home", HomeSnapshot.serializer())!!, HomeSnapshot.serializer())
        val normalized = fixture.backing.getString("home")!!
        assertTrue(!normalized.contains("futureField"), "save must normalize away unknown fields: " + normalized)
        assertEquals(HomeSnapshot(serverId = "srv-1"), fixture.storage.load("home", HomeSnapshot.serializer()))
    }

    @Test
    fun malformedEnvelopeThrowsCorrupted() = runTest {
        val fixture = Fixture()
        fixture.storage.save("home", HomeSnapshot(serverId = "s"), HomeSnapshot.serializer())
        fixture.writeRaw("home", "{not json")

        val failure = assertFailsWith<StorageCorruptedException> {
            fixture.storage.loadEntry("home", HomeSnapshot.serializer())
        }
        assertEquals("home", failure.key)
    }

    @Test
    fun undecodablePayloadThrowsCorrupted() = runTest {
        val fixture = Fixture()
        // valid envelope, payload of wrong shape
        fixture.writeRaw(
            "home",
            """{"savedAtEpochMs":1,"payload":{"serverId":123}}""",
        )

        assertFailsWith<StorageCorruptedException> {
            fixture.storage.loadEntry("home", HomeSnapshot.serializer())
        }
    }

    @Test
    fun deleteAndListWithPrefix() = runTest {
        val fixture = Fixture()
        fixture.storage.save("shelf/a", HomeSnapshot(serverId = "s"), HomeSnapshot.serializer())
        fixture.clock.now = 2_000_000L
        fixture.storage.save("shelf/b", HomeSnapshot(serverId = "s"), HomeSnapshot.serializer())
        fixture.storage.save("other", HomeSnapshot(serverId = "s"), HomeSnapshot.serializer())

        val infos = fixture.storage.listSnapshots("shelf/")
        assertEquals(listOf("shelf/a", "shelf/b"), infos.map { it.key })
        assertEquals(1_000_000L, infos[0].metadata.savedAtEpochMs)
        assertEquals(2_000_000L, infos[1].metadata.savedAtEpochMs)

        assertTrue(fixture.storage.delete("shelf/a"))
        assertEquals(listOf("shelf/b"), fixture.storage.listSnapshots("shelf/").map { it.key })
        assertTrue(fixture.storage.load("other", HomeSnapshot.serializer()) != null)
    }
}
