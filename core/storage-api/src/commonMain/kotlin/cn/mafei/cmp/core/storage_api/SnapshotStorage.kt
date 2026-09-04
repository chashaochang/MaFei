package cn.mafei.cmp.core.storage_api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** Descriptive metadata persisted alongside every snapshot. */
@Serializable
data class SnapshotMetadata(
    val schemaVersion: Long? = null,
    val savedAtEpochMs: Long,
    val appVersion: String? = null,
)

/** Snapshot handle returned by listing operations. */
data class SnapshotInfo(
    val key: String,
    val metadata: SnapshotMetadata,
)

/** Snapshot value plus its metadata. */
data class SnapshotEntry<T>(
    val key: String,
    val metadata: SnapshotMetadata,
    val value: T,
)

/**
 * Serialization-backed snapshot persistence over plain [SettingsStorage].
 *
 * Snapshots are larger structured documents (e.g. the legacy migration state
 * or home shelves) written as a whole and read back as a whole. Unknown
 * payload fields written by *newer* app versions are tolerated on read so an
 * older client never fails to load a newer snapshot — but this tolerance is
 * read-only by contract: loading decodes into the caller's model and the
 * unknown fields are gone. Saving again persists the model as encoded by the
 * caller's serializer, which **drops** the unknown fields. Callers that must
 * preserve opaque payloads across versions (e.g. the legacy migration
 * snapshot) keep the raw envelope untouched or version the schema explicitly.
 *
 * Malformed envelopes and undecodable payloads surface as
 * [StorageCorruptedException] so callers can trigger re-fetch/migration
 * instead of crashing.
 *
 * The clock is injected (`nowEpochMs`) to keep writes deterministic in tests.
 */
class JsonSnapshotStorage(
    private val storage: SettingsStorage,
    private val json: Json,
    private val nowEpochMs: () -> Long,
) {

    suspend fun <T> save(
        key: String,
        snapshot: T,
        serializer: KSerializer<T>,
        schemaVersion: Long? = null,
        appVersion: String? = null,
    ) {
        val payload: JsonElement = json.encodeToJsonElement(serializer, snapshot)
        val envelope = Envelope(
            schemaVersion = schemaVersion,
            savedAtEpochMs = nowEpochMs(),
            appVersion = appVersion,
            payload = payload,
        )
        storage.putString(key, json.encodeToString(Envelope.serializer(), envelope))
    }

    /** Decodes the snapshot; `null` when no snapshot exists under [key]. */
    suspend fun <T> load(key: String, serializer: KSerializer<T>): T? =
        loadEntry(key, serializer)?.value

    suspend fun <T> loadEntry(key: String, serializer: KSerializer<T>): SnapshotEntry<T>? {
        val raw = storage.getString(key) ?: return null
        val envelope = try {
            json.decodeFromString(Envelope.serializer(), raw)
        } catch (e: Exception) {
            throw StorageCorruptedException(key = key, message = "Snapshot envelope for '$key' is malformed", cause = e)
        }
        val value = try {
            json.decodeFromJsonElement(serializer, envelope.payload)
        } catch (e: Exception) {
            throw StorageCorruptedException(key = key, message = "Snapshot payload for '$key' cannot be decoded", cause = e)
        }
        return SnapshotEntry(
            key = key,
            metadata = SnapshotMetadata(
                schemaVersion = envelope.schemaVersion,
                savedAtEpochMs = envelope.savedAtEpochMs,
                appVersion = envelope.appVersion,
            ),
            value = value,
        )
    }

    suspend fun delete(key: String): Boolean = storage.remove(key)

    suspend fun listSnapshots(prefix: String? = null): List<SnapshotInfo> =
        storage.listKeys(prefix).mapNotNull { key ->
            val raw = storage.getString(key) ?: return@mapNotNull null
            val envelope = try {
                json.decodeFromString(Envelope.serializer(), raw)
            } catch (e: Exception) {
                throw StorageCorruptedException(key = key, message = "Snapshot envelope for '$key' is malformed", cause = e)
            }
            SnapshotInfo(
                key = key,
                metadata = SnapshotMetadata(
                    schemaVersion = envelope.schemaVersion,
                    savedAtEpochMs = envelope.savedAtEpochMs,
                    appVersion = envelope.appVersion,
                ),
            )
        }

    @Serializable
    private data class Envelope(
        val schemaVersion: Long? = null,
        val savedAtEpochMs: Long,
        val appVersion: String? = null,
        val payload: JsonElement,
    )
}
