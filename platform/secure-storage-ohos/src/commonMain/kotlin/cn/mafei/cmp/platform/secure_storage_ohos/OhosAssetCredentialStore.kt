package cn.mafei.cmp.platform.secure_storage_ohos

import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.platform.secure_storage_core.SecureSecretCodec
import kotlinx.coroutines.*

/**
 * Native AssetStore (API 11+) implementation for **short credentials only**.
 * [MAX_ENCODED_BYTES] includes the MFS1 codec header, not just the token length.
 * Oversize values fail Unsupported before any native mutation. This is not yet
 * the large-Cookie backend; there is no truncation, chunking or plaintext fallback.
 *
 * Device-first-unlock accessibility, no authentication prompt, no synchronization,
 * no persistent-after-uninstall permission, exact app-local namespace filtering.
 * Namespace must stay stable across upgrades. Operations run off the UI thread.
 * An admitted synchronous native operation can commit despite later cancellation.
 * Enumeration/bulk deletion are bounded, nontransactional snapshots; concurrent
 * writers can change pages and newly added entries are not guaranteed included.
 */
class OhosAssetCredentialStore internal constructor(
    namespace: String,
    private val backend: AssetBackend,
    private val dispatcher: CoroutineDispatcher,
) : SecureCredentialStore {
    constructor(namespace: String = "cn.mafei.credentials.v1") : this(namespace, createAssetBackend(namespace), Dispatchers.Default)

    private val keys = AssetKeys(namespace)

    override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) = withContext(dispatcher) {
        val key = keys.encode(alias)
        val encoded = SecureSecretCodec.encode(secret)
        try {
            if (encoded.size > MAX_ENCODED_BYTES) throw CredentialAccessException(
                CredentialFailureKind.Unsupported, "AssetStore short-credential capacity exceeded",
            )
            repeat(16) {
                currentCoroutineContext().ensureActive()
                when (val status = backend.add(key, encoded)) {
                    AssetStatus.SUCCESS -> return@withContext
                    AssetStatus.DUPLICATED -> {
                        if (!overwrite) assetFailure(status)
                        currentCoroutineContext().ensureActive()
                        when (val update = backend.update(key, encoded)) {
                            AssetStatus.SUCCESS -> return@withContext
                            AssetStatus.NOT_FOUND -> yield()
                            else -> assetFailure(update)
                        }
                    }
                    else -> assetFailure(status)
                }
            }
            throw CredentialAccessException(CredentialFailureKind.StorageFailure, "AssetStore write contention")
        } finally { encoded.fill(0) }
    }

    override suspend fun read(alias: CredentialAlias): SecureSecret? = withContext(dispatcher) {
        currentCoroutineContext().ensureActive()
        val result = backend.read(keys.encode(alias))
        try {
            if (result.status == AssetStatus.NOT_FOUND) return@withContext null
            if (result.status != AssetStatus.SUCCESS) assetFailure(result.status)
            val bytes = result.bytes ?: invalidAssetResult()
            if (bytes.size > MAX_ENCODED_BYTES) invalidAssetResult()
            SecureSecretCodec.decode(bytes)
        } finally { result.bytes?.fill(0) }
    }

    override suspend fun exists(alias: CredentialAlias): Boolean = withContext(dispatcher) {
        currentCoroutineContext().ensureActive()
        found(backend.exists(keys.encode(alias)))
    }

    override suspend fun delete(alias: CredentialAlias): Boolean = withContext(dispatcher) {
        currentCoroutineContext().ensureActive()
        found(backend.delete(keys.encode(alias)))
    }

    override suspend fun listAliases(prefix: String?): List<CredentialAlias> = withContext(dispatcher) { listMatching(prefix) }

    override suspend fun deleteAll(prefix: String?): Int = withContext(dispatcher) {
        var count = 0
        for (alias in listMatching(prefix)) {
            currentCoroutineContext().ensureActive()
            if (found(backend.delete(keys.encode(alias)))) count++
        }
        count
    }

    private suspend fun listMatching(prefix: String?): List<CredentialAlias> {
        val aliases = mutableListOf<CredentialAlias>()
        var offset = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = backend.aliases(offset, PAGE_SIZE)
            if (page.status == AssetStatus.NOT_FOUND) break
            if (page.status != AssetStatus.SUCCESS) assetFailure(page.status)
            if (page.keys.size > PAGE_SIZE) invalidAssetResult()
            // Validate every native result, including entries outside the requested
            // prefix. A foreign/malformed key must never be accepted as our own.
            aliases.addAll(page.keys.map(keys::decode))
            if (aliases.size > MAX_ALIASES) invalidAssetResult()
            if (page.keys.size < PAGE_SIZE) break
            offset += page.keys.size
        }
        return aliases.distinct().filter { prefix == null || it.value.startsWith(prefix) }.sortedBy { it.value }
    }

    companion object {
        const val MAX_ENCODED_BYTES = 1024
        internal const val PAGE_SIZE = 128
        internal const val MAX_ALIASES = 10000
    }
}

internal data class AssetRead(val status: Int, val bytes: ByteArray?)
internal data class AssetPage(val status: Int, val keys: List<ByteArray>)
internal interface AssetBackend {
    fun add(key: ByteArray, bytes: ByteArray): Int
    fun update(key: ByteArray, bytes: ByteArray): Int
    fun read(key: ByteArray): AssetRead
    fun exists(key: ByteArray): Int
    fun delete(key: ByteArray): Int
    fun aliases(offset: Int, limit: Int): AssetPage
}
internal expect fun createAssetBackend(namespace: String): AssetBackend

internal object AssetStatus {
    const val SUCCESS = 0
    const val NOT_FOUND = 24000002
    const val DUPLICATED = 24000003
}
private fun found(status: Int): Boolean = when (status) {
    AssetStatus.SUCCESS -> true
    AssetStatus.NOT_FOUND -> false
    else -> assetFailure(status)
}
internal fun assetFailure(status: Int): Nothing {
    val kind = when (status) {
        201, 24000004 -> CredentialFailureKind.AccessDenied
        24000001, 24000005, 24000010 -> CredentialFailureKind.BackendUnavailable
        AssetStatus.DUPLICATED -> CredentialFailureKind.AlreadyExists
        24000017 -> CredentialFailureKind.Unsupported
        else -> CredentialFailureKind.StorageFailure
    }
    throw CredentialAccessException(kind, "AssetStore operation failed (status=$status)")
}
internal fun invalidAssetResult(): Nothing = throw CredentialAccessException(CredentialFailureKind.StorageFailure, "Invalid AssetStore result")

/** Length-framed physical aliases avoid namespace separator ambiguity and hashing collisions. */
internal class AssetKeys(namespace: String) {
    val namespaceBytes: ByteArray
    init {
        require(namespace.length in 1..64 && namespace.all { it.isLetterOrDigit() && it.code < 128 || it in ".-_" }) { "Invalid AssetStore namespace" }
        namespaceBytes = namespace.encodeToByteArray()
    }
    fun encode(alias: CredentialAlias): ByteArray {
        require('\u0000' !in alias.value && alias.value.length <= 256) { "Invalid AssetStore alias" }
        val name = try { alias.value.encodeToByteArray(throwOnInvalidSequence = true) }
        catch (_: CharacterCodingException) { throw IllegalArgumentException("Invalid AssetStore alias encoding") }
        val length = 5 + namespaceBytes.size + name.size
        require(length <= 256) { "AssetStore physical alias exceeds 256 bytes" }
        return byteArrayOf(0x4d, 0x46, 0x41, 1, namespaceBytes.size.toByte()) + namespaceBytes + name
    }
    fun decode(key: ByteArray): CredentialAlias {
        if (key.size !in (6 + namespaceBytes.size)..256 ||
            key[0] != 0x4d.toByte() || key[1] != 0x46.toByte() || key[2] != 0x41.toByte() || key[3] != 1.toByte() ||
            key[4].toInt() != namespaceBytes.size || !key.copyOfRange(5, 5 + namespaceBytes.size).contentEquals(namespaceBytes)) invalidAssetResult()
        val value = try { key.decodeToString(5 + namespaceBytes.size, key.size, throwOnInvalidSequence = true) }
        catch (_: CharacterCodingException) { invalidAssetResult() }
        return try { CredentialAlias(value).also { if (!encode(it).contentEquals(key)) invalidAssetResult() } }
        catch (_: IllegalArgumentException) { invalidAssetResult() }
    }
}
