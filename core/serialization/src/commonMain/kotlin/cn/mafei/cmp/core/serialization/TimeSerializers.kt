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
 * Serializes epoch seconds; accepts both JSON numbers and number-shaped
 * strings (some backends wrap numeric fields in strings).
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

/** Serializes epoch milliseconds; tolerates number-shaped strings like [EpochSecondsLongSerializer]. */
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

internal object LenientNumberParsers {

    fun parseLong(decoder: JsonDecoder): Long {
        val content = requirePrimitive(decoder)
        content.toLongOrNull()?.let { return it }
        content.toDoubleOrNull()?.let { return it.toLong() }
        throw SerializationException("Malformed number: '$content'")
    }

    fun parseInt(decoder: JsonDecoder): Int {
        val content = requirePrimitive(decoder)
        content.toIntOrNull()?.let { return it }
        content.toDoubleOrNull()?.let { return it.toInt() }
        throw SerializationException("Malformed number: '$content'")
    }

    fun parseDouble(decoder: JsonDecoder): Double {
        val content = requirePrimitive(decoder)
        content.toDoubleOrNull()?.let { return it }
        throw SerializationException("Malformed number: '$content'")
    }

    private fun requirePrimitive(decoder: JsonDecoder): String {
        val primitive = decoder.decodeJsonElement() as? JsonPrimitive
            ?: throw SerializationException("Expected a number")
        return primitive.content.trim()
    }
}
