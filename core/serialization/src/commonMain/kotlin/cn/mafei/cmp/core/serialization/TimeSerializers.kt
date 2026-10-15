package cn.mafei.cmp.core.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/** Serializes epoch milliseconds as an ISO-8601 instant string. */
data object IsoInstantEpochMsSerializer : KSerializer<Long> {

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor = PrimitiveSerialDescriptor("mafei.IsoInstantEpochMs", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeString(formatIsoInstant(value))
    }

    override fun deserialize(decoder: Decoder): Long = when (decoder) {
        is JsonDecoder -> decodeEpochMs(decoder)
        else -> {
            val text = decoder.decodeString()
            parseIsoInstantToEpochMs(text)
                ?: throw SerializationException("Malformed ISO-8601 instant: '$text'")
        }
    }

    private fun decodeEpochMs(decoder: JsonDecoder): Long {
        val primitive = decoder.decodeJsonElement() as? JsonPrimitive
            ?: throw SerializationException("Expected a string instant")
        return parseIsoInstantToEpochMs(primitive.content)
            ?: throw SerializationException("Malformed ISO-8601 instant: '${primitive.content}'")
    }
}

/**
 * Serializes epoch seconds; accepts JSON numbers and integer-shaped strings
 * (some backends wrap numeric fields in strings). Fractional or out-of-range
 * inputs are rejected — see [LenientNumberParsers].
 */
data object EpochSecondsLongSerializer : KSerializer<Long> {

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor = PrimitiveSerialDescriptor("mafei.EpochSecondsLong", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long = when (decoder) {
        is JsonDecoder -> LenientNumberParsers.parseLong(decoder)
        else -> decoder.decodeLong()
    }
}

/** Serializes epoch milliseconds; tolerates integer-shaped strings like [EpochSecondsLongSerializer]. */
data object EpochMillisLongSerializer : KSerializer<Long> {

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor = PrimitiveSerialDescriptor("mafei.EpochMillisLong", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long = when (decoder) {
        is JsonDecoder -> LenientNumberParsers.parseLong(decoder)
        else -> decoder.decodeLong()
    }
}

/** Serializes a calendar date as `YYYY-MM-DD`; validates strictly on decode. */
data object IsoDateSerializer : KSerializer<String> {

    private val PATTERN = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor = PrimitiveSerialDescriptor("mafei.IsoDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        validate(value)
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        val text = decoder.decodeString()
        validate(text)
        return text
    }

    private fun validate(value: String) {
        val match = PATTERN.find(value)
            ?: throw SerializationException("Malformed ISO date (expected YYYY-MM-DD): '$value'")
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        val valid = month in 1..12 &&
            day >= 1 &&
            day <= (if (month == 2 && IsoInstant.isLeapYear(year)) 29 else DAYS_IN_MONTH[month - 1])
        if (!valid) throw SerializationException("Invalid calendar date: '$value'")
    }

    private val DAYS_IN_MONTH = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
}

/**
 * Lenient wire-number decoding. Frozen scope: values may arrive as JSON
 * numbers *or* integer-shaped strings (`"42"`); anything else is rejected.
 *
 * Deliberately NOT tolerated (no protocol evidence from Jellyfin/FNOS that
 * such payloads exist): fractional values are never truncated (`"10.9"` and
 * `10.9` fail) and out-of-range integers never saturate. If a real server is
 * proven to send such shapes, add a fixture-backed case here instead of
 * silently coercing.
 */
internal object LenientNumberParsers {

    fun parseLong(decoder: JsonDecoder): Long {
        val content = requirePrimitive(decoder)
        return content.toLongOrNull()
            ?: throw SerializationException("Expected an integer, got: '$content'")
    }

    fun parseInt(decoder: JsonDecoder): Int {
        val content = requirePrimitive(decoder)
        return content.toIntOrNull()
            ?: throw SerializationException("Expected an integer, got: '$content'")
    }

    fun parseDouble(decoder: JsonDecoder): Double {
        val content = requirePrimitive(decoder)
        return content.toDoubleOrNull()
            ?: throw SerializationException("Malformed number: '$content'")
    }

    private fun requirePrimitive(decoder: JsonDecoder): String {
        val primitive = decoder.decodeJsonElement() as? JsonPrimitive
            ?: throw SerializationException("Expected a number")
        return primitive.content.trim()
    }
}
