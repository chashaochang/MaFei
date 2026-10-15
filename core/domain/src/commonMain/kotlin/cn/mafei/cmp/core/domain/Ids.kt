package cn.mafei.cmp.core.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Type-safe identifiers shared by providers, data repositories and features.
 *
 * Every id serializes as its plain underlying string so JSON payloads stay
 * provider-shaped. Values must be non-blank; ids are opaque and no consumer
 * may derive meaning from their formatting.
 *
 * Implementation note: these are data classes rather than value classes
 * because the OHOS CMP toolchain's stdlib does not expose `@JvmInline` to
 * common/Native code while the JVM backend requires it — data classes keep
 * behavior identical on every target.
 */
@Serializable(with = ServerId.Serializer::class)
data class ServerId(val value: String) {
    init {
        require(value.isNotBlank()) { "ServerId must not be blank" }
    }

    override fun toString(): String = value

    data object Serializer : KSerializer<ServerId> by stringIdSerializer("ServerId", ::ServerId)
}

/** Identifies an account on a specific [ServerId]. */
@Serializable(with = AccountId.Serializer::class)
data class AccountId(val value: String) {
    init {
        require(value.isNotBlank()) { "AccountId must not be blank" }
    }

    override fun toString(): String = value

    data object Serializer : KSerializer<AccountId> by stringIdSerializer("AccountId", ::AccountId)
}

/** Identifies a playable or browsable media object. */
@Serializable(with = MediaId.Serializer::class)
data class MediaId(val value: String) {
    init {
        require(value.isNotBlank()) { "MediaId must not be blank" }
    }

    override fun toString(): String = value

    data object Serializer : KSerializer<MediaId> by stringIdSerializer("MediaId", ::MediaId)
}

/** Identifies a library/collection ("view") exposed by a server. */
@Serializable(with = CollectionId.Serializer::class)
data class CollectionId(val value: String) {
    init {
        require(value.isNotBlank()) { "CollectionId must not be blank" }
    }

    override fun toString(): String = value

    data object Serializer : KSerializer<CollectionId> by stringIdSerializer("CollectionId", ::CollectionId)
}

/** Identifies an active playback session used for progress reporting. */
@Serializable(with = PlaybackSessionId.Serializer::class)
data class PlaybackSessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "PlaybackSessionId must not be blank" }
    }

    override fun toString(): String = value

    data object Serializer : KSerializer<PlaybackSessionId> by stringIdSerializer("PlaybackSessionId", ::PlaybackSessionId)
}

/** Shared plumbing for identifiers that serialize as their raw string. */
private fun <T> stringIdSerializer(serialName: String, factory: (String) -> T): KSerializer<T> =
    StringIdSerializer(serialName, factory)

private class StringIdSerializer<T>(
    serialName: String,
    private val factory: (String) -> T,
) : KSerializer<T> {

    private val serialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor
        get() = serialDescriptor

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): T = factory(decoder.decodeString())
}
