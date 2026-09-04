package cn.mafei.cmp.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class IdsTest {

    private val json = Json

    @Test
    fun idRejectsBlankValues() {
        assertFailsWith<IllegalArgumentException> { ServerId("") }
        assertFailsWith<IllegalArgumentException> { AccountId("   ") }
        assertFailsWith<IllegalArgumentException> { MediaId("") }
        assertFailsWith<IllegalArgumentException> { CollectionId(" ") }
        assertFailsWith<IllegalArgumentException> { PlaybackSessionId("\t") }
    }

    @Test
    fun idToStringExposesRawValue() {
        assertEquals("srv-1", ServerId("srv-1").toString())
        assertEquals("acc-7", AccountId("acc-7").toString())
        assertEquals("m-42", MediaId("m-42").toString())
        assertEquals("col-3", CollectionId("col-3").toString())
        assertEquals("pb-9", PlaybackSessionId("pb-9").toString())
    }

    @Test
    fun idsRoundTripThroughJsonAsPlainStrings() {
        val source = IdFixture(
            serverId = ServerId("srv-1"),
            accountId = AccountId("acc-7"),
            mediaId = MediaId("m-42"),
            collectionId = CollectionId("col-3"),
            playbackSessionId = PlaybackSessionId("pb-9"),
        )

        val encoded = json.encodeToString(IdFixture.serializer(), source)
        assertEquals(
            """{"serverId":"srv-1","accountId":"acc-7","mediaId":"m-42",""" +
                """"collectionId":"col-3","playbackSessionId":"pb-9"}""",
            encoded,
        )

        val decoded = json.decodeFromString(IdFixture.serializer(), encoded)
        assertEquals(source, decoded)
    }

    @Test
    fun nullableIdRoundTripsOmittedOrNull() {
        val json = Json { explicitNulls = false; ignoreUnknownKeys = true }
        val withCollection = OptionalIdFixture(collectionId = CollectionId("col-3"))
        val encoded = json.encodeToString(OptionalIdFixture.serializer(), withCollection)
        assertEquals("""{"collectionId":"col-3"}""", encoded)

        val decoded = json.decodeFromString(OptionalIdFixture.serializer(), encoded)
        assertEquals(withCollection, decoded)
    }

    @Test
    fun idListRoundTrips() {
        val ids = listOf(MediaId("m-1"), MediaId("m-2"))
        val serializer: KSerializer<List<MediaId>> = ListSerializer(MediaId.serializer())
        val encoded = json.encodeToString(serializer, ids)
        assertEquals("""["m-1","m-2"]""", encoded)
        assertEquals(ids, json.decodeFromString(serializer, encoded))
    }

    @Test
    fun decodingNonStringIdFails() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<ServerId>(serializer(), "123")
        }
    }

    @Test
    fun sameValueIdsAreStructurallyEqual() {
        assertEquals(ServerId("a"), ServerId("a"))
        assertEquals(ServerId("a").hashCode(), ServerId("a").hashCode())
    }
}

@kotlinx.serialization.Serializable
private data class IdFixture(
    val serverId: ServerId,
    val accountId: AccountId,
    val mediaId: MediaId,
    val collectionId: CollectionId,
    val playbackSessionId: PlaybackSessionId,
)

@kotlinx.serialization.Serializable
private data class OptionalIdFixture(
    val collectionId: CollectionId? = null,
)
