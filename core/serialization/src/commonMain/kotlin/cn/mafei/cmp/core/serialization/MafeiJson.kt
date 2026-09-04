package cn.mafei.cmp.core.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * Unified Json configuration for provider wire and local snapshots.
 *
 * Compatibility policy:
 * - unknown fields are ignored (servers evolve independently of app releases);
 * - unknown enum inputs fall back instead of failing (see
 *   [UnknownTolerantEnumSerializer], which also matches case-insensitively);
 * - `null` for a primitive with a default coerces to the default;
 * - explicit `null`s and defaults are omitted on encode to keep payloads
 *   stable and small;
 * - lenient parsing tolerates quoted numbers/booleans seen in the wild.
 */
@OptIn(ExperimentalSerializationApi::class)
data object MafeiJson {

    /** Default configuration for all provider wire traffic and snapshots. */
    val Default: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = false
    }

    /** Pretty variant for debug dumps; decodes identically to [Default]. */
    val Pretty: Json = Json(Default) { prettyPrint = true }
}

/**
 * Optional contextual serializers module. Registering it
 * (`serializersModule = MafeiSerializers.module`) lets wire models annotate
 * epoch-millisecond instants with plain `@Contextual`.
 */
data object MafeiSerializers {

    val module: SerializersModule = SerializersModule {
        contextual(Long::class, IsoInstantEpochMsSerializer)
    }
}
