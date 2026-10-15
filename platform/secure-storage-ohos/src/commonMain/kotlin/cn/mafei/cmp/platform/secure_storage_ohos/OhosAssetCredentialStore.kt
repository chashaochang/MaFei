package cn.mafei.cmp.platform.secure_storage_ohos

import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.platform.secure_storage_core.SecureSecretCodec
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Short values retain the original MFS1 Asset encoding. Long values use a fresh
 * per-write AES-256-GCM key in Asset and ciphertext-only immutable private files.
 * The Asset update is the commit point: the file is durable before its key/reference
 * is installed. No plaintext file, key regeneration, splitting, or truncation.
 *
 * All cooperating instances must use the same stable namespace and privateDirectory.
 * A synchronous native commit admitted before cancellation may still complete.
 * Crash-before-commit can leave ciphertext-only orphans, never a dangling live key.
 */
class OhosAssetCredentialStore internal constructor(
    namespace: String,
    private val backend: AssetBackend,
    private val dispatcher: CoroutineDispatcher,
    private val vault: LongCredentialVault? = null,
) : SecureCredentialStore {
    /** Pass the app's persistent private files directory, never a cache/temp directory. */
    constructor(privateDirectory: String, namespace: String = "cn.mafei.credentials.v1") : this(
        namespace, createAssetBackend(namespace), Dispatchers.Default,
        createLongCredentialVault(privateDirectory, namespace),
    )

    private val keys = AssetKeys(namespace)

    override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean): Unit = admitted { context ->
        val key = keys.encode(alias)
        val encoded = SecureSecretCodec.encode(secret)
        var previous: ByteArray? = null
        var reference: LongCredentialReference? = null
        var fileReady = false
        var committed = false
        try {
            if (vault != null) {
                previous = readAsset(key)
                if (previous != null && !overwrite) assetFailure(AssetStatus.DUPLICATED)
                previous?.let { if (LongCredentialReference.isReference(it)) LongCredentialReference(it) }
            }
            val payload = if (encoded.size <= MAX_ENCODED_BYTES) encoded else {
                val files = requireVault()
                val fresh = LongCredentialReference.create(files).also { reference = it }
                val encrypted = fresh.usingKey { aesKey, nonce ->
                    files.encrypt(aesKey, nonce, aad(key), encoded)
                }
                if (encrypted.size != encoded.size + 16) invalidAssetResult()
                context.ensureActive()
                files.writeNew(fresh.id, encrypted)
                fileReady = true
                fresh.bytes
            }
            for (attempt in 0 until 16) {
                context.ensureActive()
                when (val status = backend.add(key, payload)) {
                    AssetStatus.SUCCESS -> committed = true
                    AssetStatus.DUPLICATED -> {
                        if (!overwrite) assetFailure(status)
                        context.ensureActive()
                        when (val update = backend.update(key, payload)) {
                            AssetStatus.SUCCESS -> committed = true
                            AssetStatus.NOT_FOUND -> Unit
                            else -> assetFailure(update)
                        }
                    }
                    else -> assetFailure(status)
                }
                if (committed) break
            }
            if (!committed) throw CredentialAccessException(CredentialFailureKind.StorageFailure, "AssetStore write contention")
            previous?.let { bytes ->
                if (LongCredentialReference.isReference(bytes)) requireVault().delete(LongCredentialReference(bytes).id)
            }
        } finally {
            if (fileReady && !committed) reference?.let {
                // Failed writes never replace the old Asset. Cleanup failure only leaves
                // unreachable ciphertext; do not mask the original failure.
                try { vault?.delete(it.id) } catch (_: CredentialAccessException) { }
            }
            reference?.bytes?.fill(0)
            previous?.fill(0)
            encoded.fill(0)
        }
    }

    override suspend fun read(alias: CredentialAlias): SecureSecret? = admitted { readSecret(keys.encode(alias)) }

    override suspend fun exists(alias: CredentialAlias): Boolean = admitted {
        // Authenticate the referenced file, so corruption is never "present and valid".
        readSecret(keys.encode(alias)) != null
    }

    override suspend fun delete(alias: CredentialAlias): Boolean = admitted { deleteKey(keys.encode(alias)) }

    override suspend fun listAliases(prefix: String?): List<CredentialAlias> = admitted { listMatching(prefix, it) }

    override suspend fun deleteAll(prefix: String?): Int = admitted { context ->
        val aliases = listMatching(prefix, context)
        var count = 0
        for (alias in aliases) {
            context.ensureActive()
            if (deleteKey(keys.encode(alias))) count++
        }
        count
    }

    private fun readAsset(key: ByteArray): ByteArray? {
        val result = backend.read(key)
        if (result.status == AssetStatus.NOT_FOUND) { result.bytes?.fill(0); return null }
        if (result.status != AssetStatus.SUCCESS) { result.bytes?.fill(0); assetFailure(result.status) }
        val bytes = result.bytes ?: invalidAssetResult()
        if (bytes.size > MAX_ENCODED_BYTES) { bytes.fill(0); invalidAssetResult() }
        return bytes
    }

    private fun readSecret(key: ByteArray): SecureSecret? {
        val bytes = readAsset(key) ?: return null
        try {
            if (!LongCredentialReference.isReference(bytes)) return SecureSecretCodec.decode(bytes)
            val reference = LongCredentialReference(bytes)
            val files = requireVault()
            val encrypted = files.read(reference.id)
            if (encrypted.size !in 25..MAX_CIPHERTEXT_BYTES) invalidAssetResult()
            val plaintext = reference.usingKey { aesKey, nonce -> files.decrypt(aesKey, nonce, aad(key), encrypted) }
            try { return SecureSecretCodec.decode(plaintext) } finally { plaintext.fill(0) }
        } finally { bytes.fill(0) }
    }

    private fun deleteKey(key: ByteArray): Boolean {
        val previous = readAsset(key) ?: return false
        try {
            // Known-alias deletion can recover malformed metadata, leaving only
            // unreachable ciphertext when its record ID cannot safely be parsed.
            val reference = if (LongCredentialReference.isReference(previous) && previous.size == LongCredentialReference.SIZE)
                LongCredentialReference(previous) else null
            val deleted = found(backend.delete(key))
            if (deleted && reference != null) requireVault().delete(reference.id)
            return deleted
        } finally { previous.fill(0) }
    }

    private fun listMatching(prefix: String?, context: CoroutineContext): List<CredentialAlias> {
        val aliases = mutableListOf<CredentialAlias>()
        var offset = 0
        while (true) {
            context.ensureActive()
            val page = backend.aliases(offset, PAGE_SIZE)
            if (page.status == AssetStatus.NOT_FOUND) break
            if (page.status != AssetStatus.SUCCESS) assetFailure(page.status)
            if (page.keys.size > PAGE_SIZE) invalidAssetResult()
            aliases.addAll(page.keys.map(keys::decode))
            if (aliases.size > MAX_ALIASES) invalidAssetResult()
            if (page.keys.size < PAGE_SIZE) break
            offset += page.keys.size
        }
        return aliases.distinct().filter { prefix == null || it.value.startsWith(prefix) }.sortedBy { it.value }
    }

    private suspend fun <T> admitted(block: (CoroutineContext) -> T): T = withContext(dispatcher) {
        val context = currentCoroutineContext()
        context.ensureActive()
        val action = { context.ensureActive(); block(context) }
        if (vault != null) vault.locked(action) else action()
    }

    private fun requireVault(): LongCredentialVault = vault ?: throw CredentialAccessException(
        CredentialFailureKind.Unsupported, "Private encrypted credential directory is required",
    )

    private fun aad(key: ByteArray): ByteArray = LongCredentialReference.MAGIC + key

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
