package cn.mafei.cmp.storage

import cn.mafei.cmp.core.storage_api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * One runtime owns one instance. Not a cross-process or multi-instance write coordinator.
 * Platform files are fully written and synced before atomic rename. This promises whole-document
 * visibility, not survival of the most recent directory rename across sudden power loss.
 * No backend raises a new fallible durability/cleanup check after the rename commit point.
 */
internal interface SettingsDocumentBackend {
    /** null means absent, never unreadable/corrupt. Caller receives an owned byte array. */
    fun read(): ByteArray?
    /** Replace the whole document atomically; errors must be reported before the commit point. */
    fun replace(document: ByteArray)
}

/** Ordinary non-sensitive settings only. Tokens/cookies/passwords belong in SecureCredentialStore. */
internal class DocumentSettingsStorage(
    private val backend: SettingsDocumentBackend,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SettingsStorage {
    private val mutex = Mutex()
    private var snapshot: Map<String, Value>? = null

    override suspend fun getString(key: String): String? = locked { validateKey(key); (load()[key] as? Value.Text)?.value }
    override suspend fun getBytes(key: String): ByteArray? = locked { validateKey(key); (load()[key] as? Value.Bytes)?.value?.copyOf() }
    override suspend fun putString(key: String, value: String) { edit { putString(key, value) } }
    override suspend fun putBytes(key: String, value: ByteArray) {
        val owned = value.copyOf() // before admission can suspend
        edit { putBytes(key, owned) }
    }
    override suspend fun contains(key: String): Boolean = locked { validateKey(key); key in load() }
    override suspend fun listKeys(prefix: String?): List<String> = locked {
        load().keys.filter { prefix == null || it.startsWith(prefix) }.sorted()
    }
    override suspend fun remove(key: String): Boolean = locked {
        validateKey(key)
        val current = load()
        if (key !in current) false else {
            commit(current - key); true
        }
    }
    override suspend fun edit(mutate: StorageEditor.() -> Unit): Unit = locked {
        val editor = Editor(load().toMutableMap())
        val proposed = try { editor.mutate(); editor.values.toMap() } finally { editor.active = false }
        commit(proposed)
    }

    private suspend fun commit(proposed: Map<String, Value>) {
        val document = encode(proposed)
        currentCoroutineContext().ensureActive()
        backendCall { backend.replace(document.copyOf()) }
        // No suspension between the atomic OS commit and updating the in-process mirror.
        snapshot = proposed
    }

    private fun load(): Map<String, Value> {
        snapshot?.let { return it }
        val bytes = backendCall { backend.read()?.copyOf() }
        return (if (bytes == null) emptyMap() else decode(bytes)).also { snapshot = it }
    }
    private suspend fun <T> locked(block: suspend () -> T): T = withContext(dispatcher) {
        mutex.withLock { currentCoroutineContext().ensureActive(); block() }
    }

    private sealed class Value {
        class Text(val value: String) : Value()
        class Bytes(val value: ByteArray) : Value()
    }
    private class Editor(val values: MutableMap<String, Value>) : StorageEditor {
        var active = true
        private var rawBytes = values.entries.sumOf { utf8(it.key).size.toLong() + valueSize(it.value) }
        override fun putString(key: String, value: String) {
            check(active) { "Settings editor is closed" }; validateKey(key); validateValueSize(utf8(value).size)
            put(key, Value.Text(value))
        }
        override fun putBytes(key: String, value: ByteArray) {
            check(active) { "Settings editor is closed" }; validateKey(key); validateValueSize(value.size)
            put(key, Value.Bytes(value.copyOf()))
        }
        override fun remove(key: String) {
            check(active) { "Settings editor is closed" }; validateKey(key)
            values.remove(key)?.let { rawBytes -= utf8(key).size + valueSize(it) }
        }
        private fun put(key: String, value: Value) {
            val old = values[key]
            val proposed = rawBytes - (old?.let(::valueSize) ?: 0) + valueSize(value) + if (old == null) utf8(key).size else 0
            if (proposed > MAX_SETTINGS_DOCUMENT_BYTES) quota()
            values[key] = value; rawBytes = proposed; validateCount(values.size)
        }
        private fun valueSize(value: Value): Int = when (value) {
            is Value.Text -> utf8(value.value).size
            is Value.Bytes -> value.value.size
        }
    }

    @Serializable private data class Document(val version: Int, val entries: List<Entry>)
    @Serializable private data class Entry(val key: String, val kind: String, val value: String)

    @OptIn(ExperimentalEncodingApi::class)
    private fun encode(values: Map<String, Value>): ByteArray {
        validateCount(values.size)
        val entries = values.keys.sorted().map { key -> when (val value = values.getValue(key)) {
            is Value.Text -> Entry(key, "string", value.value)
            is Value.Bytes -> Entry(key, "bytes", Base64.encode(value.value))
        } }
        val bytes = utf8(json.encodeToString(Document(1, entries)))
        if (bytes.size > MAX_SETTINGS_DOCUMENT_BYTES) quota()
        return bytes
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decode(bytes: ByteArray): Map<String, Value> {
        try {
            if (bytes.isEmpty() || bytes.size > MAX_SETTINGS_DOCUMENT_BYTES) corrupt()
            val document = json.decodeFromString<Document>(bytes.decodeToString(throwOnInvalidSequence = true))
            if (document.version != 1) corrupt()
            validateCount(document.entries.size)
            val result = linkedMapOf<String, Value>()
            for (entry in document.entries) {
                validateKey(entry.key)
                if (entry.key in result) corrupt()
                val value = when (entry.kind) {
                    "string" -> { validateValueSize(utf8(entry.value).size); Value.Text(entry.value) }
                    "bytes" -> {
                        val decoded = Base64.decode(entry.value)
                        validateValueSize(decoded.size)
                        if (Base64.encode(decoded) != entry.value) corrupt()
                        Value.Bytes(decoded)
                    }
                    else -> corrupt()
                }
                result[entry.key] = value
            }
            return result
        } catch (_: Exception) { corrupt() }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = false; isLenient = false; coerceInputValues = false }
        private fun utf8(value: String): ByteArray = try { value.encodeToByteArray(throwOnInvalidSequence = true) }
        catch (_: CharacterCodingException) { throw IllegalArgumentException("Invalid settings text encoding") }
        private fun validateKey(key: String) {
            require(key.isNotEmpty() && '\u0000' !in key && utf8(key).size <= 1024) { "Invalid settings key" }
        }
        private fun validateValueSize(size: Int) { if (size > 2 * 1024 * 1024) quota() }
        private fun validateCount(count: Int) { if (count > 4096) quota() }
        private fun quota(): Nothing = throw StorageQuotaExceededException("Settings document limit exceeded")
    }
}

internal const val MAX_SETTINGS_DOCUMENT_BYTES = 8 * 1024 * 1024
internal fun corrupt(): Nothing = throw StorageCorruptedException(null, "Invalid settings document")
internal fun unavailable(): Nothing = throw StorageUnavailableException("Settings persistence unavailable")

private fun <T> backendCall(block: () -> T): T = try { block() }
catch (failure: CancellationException) { throw failure }
catch (_: StorageCorruptedException) { corrupt() }
catch (_: StorageQuotaExceededException) { throw StorageQuotaExceededException("Settings document limit exceeded") }
catch (_: StorageUnsupportedException) { throw StorageUnsupportedException("atomicReplace", "Atomic settings persistence unsupported") }
catch (_: Exception) { unavailable() } // Do not export paths, keys, values or platform exception causes.
