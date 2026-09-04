package cn.mafei.cmp.core.serialization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Contextual
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
private data class InstantPayload(
    @Serializable(with = IsoInstantEpochMsSerializer::class) val at: Long,
)

@Serializable
private data class EpochSecondsPayload(
    @Serializable(with = EpochSecondsLongSerializer::class) val at: Long,
)

@Serializable
private data class EpochMillisPayload(
    @Serializable(with = EpochMillisLongSerializer::class) val at: Long,
)

@Serializable
private data class IsoDatePayload(
    @Serializable(with = IsoDateSerializer::class) val day: String,
)

@Serializable
private data class UuidPayload(
    @Serializable(with = UuidStringSerializer::class) val id: String,
)

@Serializable
private data class ModePayload(
    @Serializable(with = SampleModeSerializer::class) val mode: SampleMode,
)

@Serializable
private data class LenientLongPayload(
    @Serializable(with = LenientLongSerializer::class) val count: Long,
)

@Serializable
private data class LenientIntPayload(
    @Serializable(with = LenientIntSerializer::class) val count: Int,
)

@Serializable
private data class LenientDoublePayload(
    @Serializable(with = LenientDoubleSerializer::class) val rating: Double,
)

@Serializable
private data class DefaultModel(
    val name: String = "default",
    val count: Int = 7,
    val note: String? = null,
)

@Serializable
private data class ContextualInstant(
    @Contextual val at: Long,
)

/** Sample enum used to verify unknown-enum tolerance. */
enum class SampleMode { Direct, Transcode, Unknown }

data object SampleModeSerializer : UnknownTolerantEnumSerializer<SampleMode>(
    values = SampleMode.entries.toTypedArray(),
    unknownValue = SampleMode.Unknown,
    aliases = mapOf("Transcoded" to SampleMode.Transcode),
) {
    override val serialName: String = "SampleMode"
}

class SerializerCompatTest {

    private val json = MafeiJson.Default

    @Test
    fun instantSerializerRoundTripsAsIsoString() {
        val payload = InstantPayload(at = 1_704_067_200_123L)
        val encoded = json.encodeToString(InstantPayload.serializer(), payload)
        assertEquals("""{"at":"2024-01-01T00:00:00.123Z"}""", encoded)
        assertEquals(payload, json.decodeFromString(InstantPayload.serializer(), encoded))
    }

    @Test
    fun instantSerializerAcceptsOffsetsAndOmittedSeconds() {
        assertEquals(1_704_067_200_000L, json.decodeFromString(InstantPayload.serializer(), """{"at":"2024-01-01T08:00:00+08:00"}""").at)
        assertEquals(1_704_067_200_000L, json.decodeFromString(InstantPayload.serializer(), """{"at":"2024-01-01T00:00Z"}""").at)
        assertEquals(1_704_067_200_000L, json.decodeFromString(InstantPayload.serializer(), """{"at":"2024-01-01"}""").at)
    }

    @Test
    fun instantSerializerRejectsGarbage() {
        assertFailsWith<SerializationException> {
            json.decodeFromString(InstantPayload.serializer(), """{"at":"yesterday"}""")
        }
    }

    @Test
    fun epochSecondsSerializerRoundTrips() {
        val encoded = json.encodeToString(EpochSecondsPayload.serializer(), EpochSecondsPayload(at = 1_704_067_200L))
        assertEquals("""{"at":1704067200}""", encoded)
        assertEquals(1_704_067_200L, json.decodeFromString(EpochSecondsPayload.serializer(), encoded).at)
        // tolerates string-wrapped numbers
        assertEquals(42L, json.decodeFromString(EpochSecondsPayload.serializer(), """{"at":"42"}""").at)
    }

    @Test
    fun epochMillisSerializerRoundTrips() {
        assertEquals(
            """{"at":1704067200123}""",
            json.encodeToString(EpochMillisPayload.serializer(), EpochMillisPayload(at = 1_704_067_200_123L)),
        )
        assertEquals(7L, json.decodeFromString(EpochMillisPayload.serializer(), """{"at":"7"}""").at)
    }

    @Test
    fun isoDateSerializerRoundTrips() {
        assertEquals("""{"day":"2024-01-01"}""", json.encodeToString(IsoDatePayload.serializer(), IsoDatePayload("2024-01-01")))
        assertEquals("2024-01-01", json.decodeFromString(IsoDatePayload.serializer(), """{"day":"2024-01-01"}""").day)
        assertFailsWith<SerializationException> {
            json.decodeFromString(IsoDatePayload.serializer(), """{"day":"01/02/2024"}""")
        }
    }

    @Test
    fun uuidSerializerNormalizesCaseAndBraces() {
        assertEquals(
            """{"id":"0b8a4f3c-1d2e-4f5a-8b7c-6d5e4f3a2b1c"}""",
            json.encodeToString(UuidPayload.serializer(), UuidPayload("0B8A4F3C-1D2E-4F5A-8B7C-6D5E4F3A2B1C")),
        )
        assertEquals(
            "0b8a4f3c-1d2e-4f5a-8b7c-6d5e4f3a2b1c",
            json.decodeFromString(UuidPayload.serializer(), """{"id":"{0B8A4F3C-1D2E-4F5A-8B7C-6D5E4F3A2B1C}"}""").id,
        )
        assertEquals(
            "0b8a4f3c-1d2e-4f5a-8b7c-6d5e4f3a2b1c",
            json.decodeFromString(UuidPayload.serializer(), """{"id":"urn:uuid:0B8A4F3C-1D2E-4F5A-8B7C-6D5E4F3A2B1C"}""").id,
        )
        assertFailsWith<SerializationException> {
            json.decodeFromString(UuidPayload.serializer(), """{"id":"not-a-uuid"}""")
        }
    }

    @Test
    fun unknownEnumValuesFallBackInsteadOfFailing() {
        assertEquals(SampleMode.Unknown, decodeMode("Hologram"))
        assertEquals(SampleMode.Unknown, decodeMode("totally-new-mode"))
    }

    @Test
    fun knownEnumValuesAndAliasesDecode() {
        assertEquals(SampleMode.Direct, decodeMode("Direct"))
        assertEquals(SampleMode.Direct, decodeMode("direct"))
        assertEquals(SampleMode.Transcode, decodeMode("Transcoded"))
    }

    @Test
    fun knownEnumsEncodeWithCanonicalName() {
        assertEquals("""{"mode":"Transcode"}""", json.encodeToString(ModePayload.serializer(), ModePayload(SampleMode.Transcode)))
        assertEquals("""{"mode":"Unknown"}""", json.encodeToString(ModePayload.serializer(), ModePayload(SampleMode.Unknown)))
    }

    private fun decodeMode(raw: String): SampleMode =
        json.decodeFromString(ModePayload.serializer(), """{"mode":"$raw"}""").mode

    @Test
    fun lenientLongAcceptsStringOrNumber() {
        assertEquals(10L, json.decodeFromString(LenientLongPayload.serializer(), """{"count":10}""").count)
        assertEquals(10L, json.decodeFromString(LenientLongPayload.serializer(), """{"count":"10"}""").count)

        // frozen scope: fractions and out-of-range integers are rejected, not coerced
        assertFailsWith<SerializationException> {
            json.decodeFromString(LenientLongPayload.serializer(), """{"count":"10.9"}""")
        }
        assertFailsWith<SerializationException> {
            json.decodeFromString(LenientLongPayload.serializer(), """{"count":-10.9}""")
        }
        assertFailsWith<SerializationException> {
            json.decodeFromString(LenientLongPayload.serializer(), """{"count":"99999999999999999999"}""")
        }
    }

    @Test
    fun lenientIntAcceptsStringOrNumber() {
        assertEquals(3, json.decodeFromString(LenientIntPayload.serializer(), """{"count":3}""").count)
        assertEquals(3, json.decodeFromString(LenientIntPayload.serializer(), """{"count":"3"}""").count)
    }

    @Test
    fun lenientDoubleAcceptsStringOrNumber() {
        assertEquals(8.5, json.decodeFromString(LenientDoublePayload.serializer(), """{"rating":8.5}""").rating)
        assertEquals(8.5, json.decodeFromString(LenientDoublePayload.serializer(), """{"rating":"8.5"}""").rating)
    }

    @Test
    fun mafeiJsonIgnoresUnknownFieldsAndCoercesNulls() {
        val decoded = json.decodeFromString(
            DefaultModel.serializer(),
            """{"name":"real","count":null,"vendorExtra":{"nested":[1,2]},"futureField":"x"}""",
        )
        assertEquals("real", decoded.name)
        assertEquals(7, decoded.count)
    }

    @Test
    fun mafeiJsonOmitsDefaultsAndNullsOnEncode() {
        val encoded = json.encodeToString(DefaultModel.serializer(), DefaultModel())
        assertEquals("""{}""", encoded)
    }

    @Test
    fun mafeiJsonToleratesQuotedPrimitives() {
        @Serializable
        data class Model(val flag: Boolean, val count: Int)

        val decoded = MafeiJson.Default.decodeFromString(Model.serializer(), """{"flag":"true","count":"12"}""")
        assertEquals(true, decoded.flag)
        assertEquals(12, decoded.count)
    }

    @Test
    fun mafeiJsonDecodesStandardMaps() {
        val decoded = MafeiJson.Default.decodeFromString(
            MapSerializer(String.serializer(), String.serializer()),
            """{"a":"x","b":"y"}""",
        )
        assertEquals(mapOf("a" to "x", "b" to "y"), decoded)
    }

    @Test
    fun contextualInstantIsResolvable() {
        val contextualJson = Json {
            serializersModule = MafeiSerializers.module
            ignoreUnknownKeys = true
        }
        val decoded = contextualJson.decodeFromString(ContextualInstant.serializer(), """{"at":"2024-01-01T00:00:00Z"}""")
        assertEquals(1_704_067_200_000L, decoded.at)
    }
}
