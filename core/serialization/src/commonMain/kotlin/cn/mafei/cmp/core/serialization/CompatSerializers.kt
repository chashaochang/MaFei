package cn.mafei.cmp.core.serialization

import kotlin.reflect.KClass
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Serializes a String field carrying a UUID: decodes tolerant input
 * (`{...}`, `urn:uuid:`, mixed case) and always emits the canonical
 * lowercase dashed form. Invalid values fail with [SerializationException].
 */
data object UuidStringSerializer : KSerializer<String> {

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor = PrimitiveSerialDescriptor("mafei.UuidString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        val canonical = normalizeUuidOrNull(value)
            ?: throw SerializationException("Malformed UUID: '$value'")
        encoder.encodeString(canonical)
    }

    override fun deserialize(decoder: Decoder): String {
        val text = decoder.decodeString()
        return normalizeUuidOrNull(text)
            ?: throw SerializationException("Malformed UUID: '$text'")
    }
}

/**
 * Base for enum serializers that map unknown or renamed wire values onto a
 * dedicated fallback constant instead of failing the whole payload — required
 * so a server adding a new enum case never breaks older clients.
 *
 * Matching order: exact name, exact alias, case-insensitive name,
 * case-insensitive alias, then [unknownValue]. Encoding always uses the
 * canonical Kotlin enum name.
 */
@OptIn(ExperimentalSerializationApi::class)
abstract class UnknownTolerantEnumSerializer<E : Enum<E>>(
    values: Array<out E>,
    private val unknownValue: E,
    aliases: Map<String, E> = emptyMap(),
) : KSerializer<E> {

    protected abstract val serialName: String

    override val descriptor: SerialDescriptor by lazy {
        PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)
    }

    private val byExactName: Map<String, E> = values.associateBy { it.name }
    private val byExactAlias: Map<String, E> = aliases
    private val byLowerName: Map<String, E> = values.associateBy { it.name.lowercase() }
    private val byLowerAlias: Map<String, E> = aliases.entries.associate { (key, value) -> key.lowercase() to value }

    final override fun serialize(encoder: Encoder, value: E) {
        encoder.encodeString(value.name)
    }

    final override fun deserialize(decoder: Decoder): E = when (decoder) {
        is JsonDecoder -> {
            val primitive = decoder.decodeJsonElement() as? JsonPrimitive
                ?: return unknownValue
            match(primitive.content) ?: unknownValue
        }
        else -> match(decoder.decodeString()) ?: unknownValue
    }

    private fun match(raw: String): E? =
        byExactName[raw]
            ?: byExactAlias[raw]
            ?: byLowerName[raw.lowercase()]
            ?: byLowerAlias[raw.lowercase()]
}

/** Long that accepts JSON numbers or number-shaped strings (`"42"`, `"10.9"`). */
data object LenientLongSerializer : KSerializer<Long> {

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor = PrimitiveSerialDescriptor("mafei.LenientLong", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)

    override fun deserialize(decoder: Decoder): Long = when (decoder) {
        is JsonDecoder -> LenientNumberParsers.parseLong(decoder)
        else -> decoder.decodeLong()
    }
}

/** Int that accepts JSON numbers or number-shaped strings. */
data object LenientIntSerializer : KSerializer<Int> {

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor = PrimitiveSerialDescriptor("mafei.LenientInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)

    override fun deserialize(decoder: Decoder): Int = when (decoder) {
        is JsonDecoder -> LenientNumberParsers.parseInt(decoder)
        else -> decoder.decodeInt()
    }
}

/** Double that accepts JSON numbers or number-shaped strings. */
data object LenientDoubleSerializer : KSerializer<Double> {

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor = PrimitiveSerialDescriptor("mafei.LenientDouble", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)

    override fun deserialize(decoder: Decoder): Double = when (decoder) {
        is JsonDecoder -> LenientNumberParsers.parseDouble(decoder)
        else -> decoder.decodeDouble()
    }
}
