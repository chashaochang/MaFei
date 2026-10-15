package cn.mafei.cmp.providers.jellyfin.wire

import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/** Photos group by the server's calendar date, not its UTC-converted day. */
object ItemWithOriginalDatesSerializer : KSerializer<BaseItemDto> {
    private val delegate = BaseItemDto.serializer()
    override val descriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: BaseItemDto) = delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): BaseItemDto {
        if (decoder !is JsonDecoder) return delegate.deserialize(decoder)
        val tree = decoder.decodeJsonElement()
        val item = decoder.json.decodeFromJsonElement(delegate, tree)
        val fields = tree as? JsonObject ?: return item
        return item.copy(premiereDateIso = (fields["PremiereDate"] as? JsonPrimitive)?.contentOrNull,
            dateCreatedIso = (fields["DateCreated"] as? JsonPrimitive)?.contentOrNull)
    }
}
