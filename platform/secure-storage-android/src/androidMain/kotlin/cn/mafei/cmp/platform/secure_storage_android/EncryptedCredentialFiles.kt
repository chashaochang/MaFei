package cn.mafei.cmp.platform.secure_storage_android

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.platform.secure_storage_core.SecureSecretCodec
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.nio.channels.OverlappingFileLockException
import java.security.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

/** Internal seam only. Production is always AndroidKeyStore; no default test key. */
internal fun interface CredentialKeySource {
    fun key(allowCreate: Boolean): SecretKey
}

internal fun interface AtomicCredentialReplace {
    /** Same-directory atomic rename; throw if the OS cannot provide it. */
    fun replace(source: File, target: File)
}

internal fun credentialRecordName(alias: CredentialAlias): String = sha256Hex(alias.value) + ".mfc"

internal fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.encodeToByteArray(throwOnInvalidSequence = true))
    .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

/**
 * AES-256-GCM record store. The non-sensitive alias is authenticated along with
 * the stable namespace and format header. Secret codec bytes are encrypted before
 * any file is opened; only ciphertext is written, including temporary files.
 *
 * Canonical-directory process mutexes coordinate independent instances; a file
 * lock also coordinates cooperating app processes. Queue admission is cancellable.
 * Commit is one synchronous atomic replacement after the last cancellation check;
 * cancellation racing that OS operation does not promise that it was rolled back.
 *
 * Record rename is atomic, not a transaction across several aliases. An error
 * fsyncing after rename can mean the new record exists but durability is unknown.
 * No key is regenerated while credential files remain, even if corrupt.
 */
internal class EncryptedCredentialFiles(
    private val directory: File,
    private val namespace: String,
    private val keys: CredentialKeySource,
    private val atomicReplace: AtomicCredentialReplace,
    private val beforeCommit: suspend () -> Unit = {},
    private val syncDirectory: (File) -> Unit = {},
) : SecureCredentialStore {
    init {
        require(namespace.isNotBlank() && namespace.length <= 256) { "Invalid credential namespace" }
    }

    override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) = locked { root ->
        val target = record(root, alias)
        if (!overwrite && target.exists()) throw CredentialAccessException(CredentialFailureKind.AlreadyExists)
        val bytes = encrypt(alias, secret, allowCreate = credentialFiles(root).isEmpty())
        currentCoroutineContext().ensureActive()
        val temporary = File.createTempFile(".credential-", ".tmp", root)
        try {
            FileOutputStream(temporary).use { output -> output.write(bytes); output.fd.sync() }
            beforeCommit()
            currentCoroutineContext().ensureActive()
            // The directory mutex + OS lock also protect overwrite=false here.
            atomicReplace.replace(temporary, target)
            syncDirectory(root)
        } finally {
            // An interrupted process can leave a ciphertext-only orphan; it is
            // not a committed record and never participates in restore/listing.
            if (temporary.exists()) temporary.delete()
        }
    }

    override suspend fun read(alias: CredentialAlias): SecureSecret? = locked { root ->
        val file = record(root, alias)
        if (!file.exists()) null else decrypt(file, expectedAlias = alias).second
    }

    override suspend fun exists(alias: CredentialAlias): Boolean = read(alias) != null

    override suspend fun delete(alias: CredentialAlias): Boolean = locked { root ->
        val file = record(root, alias)
        if (!file.exists()) false else {
            currentCoroutineContext().ensureActive()
            if (!file.delete()) throw IOException("Credential deletion failed")
            syncDirectory(root)
            true
        }
    }

    override suspend fun listAliases(prefix: String?): List<CredentialAlias> = locked { root ->
        authenticatedAliases(root).filter { prefix == null || it.value.startsWith(prefix) }
    }

    override suspend fun deleteAll(prefix: String?): Int = locked { root ->
        // Validate every record before deleting any, so corrupt data is never
        // mistaken for an empty successful clear. Known-alias delete can recover it.
        val aliases = authenticatedAliases(root).filter { prefix == null || it.value.startsWith(prefix) }
        var deleted = 0
        for (alias in aliases) {
            currentCoroutineContext().ensureActive()
            if (!record(root, alias).delete()) throw IOException("Credential deletion failed")
            syncDirectory(root)
            deleted++
        }
        deleted
    }

    private suspend fun authenticatedAliases(root: File): List<CredentialAlias> = credentialFiles(root).map { file ->
        currentCoroutineContext().ensureActive()
        decrypt(file).first
    }.sortedBy { it.value }

    private fun encrypt(alias: CredentialAlias, secret: SecureSecret, allowCreate: Boolean): ByteArray {
        val header = header(alias)
        val plaintext = SecureSecretCodec.encode(secret)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            // The Android provider generates the IV; passing a caller-selected
            // IV is prohibited by randomizedEncryptionRequired on the key.
            cipher.init(Cipher.ENCRYPT_MODE, keys.key(allowCreate))
            val nonce = cipher.iv
            if (nonce.size != NONCE_SIZE) invalidRecord()
            cipher.updateAAD(aad(header))
            val ciphertext = cipher.doFinal(plaintext)
            return header + nonce + ciphertext
        } finally {
            plaintext.fill(0)
        }
    }

    private fun decrypt(file: File, expectedAlias: CredentialAlias? = null): Pair<CredentialAlias, SecureSecret> {
        safePath(file)
        if (!file.isFile) invalidRecord()
        val bytes = FileInputStream(file).use { input ->
            // Bound the opened file descriptor, not a path stat that can race
            // replacement between the size check and the allocation.
            val length = input.channel.size()
            if (length !in MIN_RECORD_BYTES.toLong()..MAX_RECORD_BYTES.toLong()) invalidRecord()
            val size = length.toInt()
            val result = ByteArray(size)
            var read = 0
            while (read < size) {
                val count = input.read(result, read, size - read)
                if (count < 0) invalidRecord()
                read += count
            }
            if (input.read() != -1) invalidRecord()
            result
        }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        if (input.readInt() != MAGIC) invalidRecord()
        val aliasLength = input.readInt()
        if (aliasLength !in 1..MAX_ALIAS_BYTES || aliasLength > bytes.size - 8 - NONCE_SIZE - TAG_SIZE - 9) invalidRecord()
        val aliasBytes = ByteArray(aliasLength).also(input::readFully)
        val alias = CredentialAlias(aliasBytes.decodeToString(throwOnInvalidSequence = true))
        if (expectedAlias != null && alias != expectedAlias || file.name != credentialRecordName(alias)) invalidRecord()
        val headerSize = 8 + aliasLength
        val nonce = ByteArray(NONCE_SIZE).also(input::readFully)
        val ciphertext = ByteArray(bytes.size - headerSize - NONCE_SIZE).also(input::readFully)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keys.key(allowCreate = false), GCMParameterSpec(TAG_SIZE * 8, nonce))
        cipher.updateAAD(aad(bytes.copyOfRange(0, headerSize)))
        val plaintext = cipher.doFinal(ciphertext)
        try { return alias to SecureSecretCodec.decode(plaintext) }
        finally { plaintext.fill(0) }
    }

    private fun header(alias: CredentialAlias): ByteArray {
        val bytes = alias.value.encodeToByteArray(throwOnInvalidSequence = true)
        if (bytes.size !in 1..MAX_ALIAS_BYTES) invalidRecord()
        return ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output -> output.writeInt(MAGIC); output.writeInt(bytes.size); output.write(bytes) }
        }.toByteArray()
    }

    private fun aad(header: ByteArray): ByteArray = ByteArrayOutputStream().also { buffer ->
        val scope = namespace.encodeToByteArray(throwOnInvalidSequence = true)
        DataOutputStream(buffer).use { output ->
            output.writeInt(scope.size); output.write(scope); output.write(header)
        }
    }.toByteArray()

    private fun record(root: File, alias: CredentialAlias): File {
        if (alias.value.length > MAX_ALIAS_BYTES) invalidRecord()
        header(alias) // Applies the same strict UTF-8/byte bound on reads and writes.
        return File(root, credentialRecordName(alias)).also(::safePath)
    }

    private fun credentialFiles(root: File): List<File> =
        (root.listFiles() ?: throw IOException("Credential directory unavailable"))
            .filter { it.name.endsWith(".mfc") }.onEach { file ->
                safePath(file)
                if (!RECORD_NAME.matches(file.name) || !file.isFile) invalidRecord()
            }

    private fun safePath(file: File) {
        if (file.canonicalFile != file.absoluteFile) throw SecurityException("Credential symbolic link rejected")
    }

    private suspend fun <T> locked(block: suspend (File) -> T): T = withContext(Dispatchers.IO) {
        try {
            currentCoroutineContext().ensureActive()
            val root = directory.canonicalFile
            val mutex = directoryLocks.getOrPut(root.path) { Mutex() }
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                if (!root.exists() && !root.mkdirs() || !root.isDirectory) throw IOException("Credential directory unavailable")
                val lockFile = File(root, ".lock").also(::safePath)
                RandomAccessFile(lockFile, "rw").use { file ->
                    var lock: java.nio.channels.FileLock? = null
                    while (lock == null) {
                        currentCoroutineContext().ensureActive()
                        lock = try { file.channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                        if (lock == null) delay(10)
                    }
                    lock.use {
                        currentCoroutineContext().ensureActive()
                        block(root)
                    }
                }
            }
        } catch (failure: CancellationException) { throw failure }
        catch (failure: CredentialAccessException) { throw failure }
        catch (failure: Exception) { throw credentialFailure(failure) }
    }

    companion object {
        private const val MAGIC = 0x4d464531 // MFE1
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 16
        private const val MAX_ALIAS_BYTES = 16 * 1024
        private const val MIN_RECORD_BYTES = 8 + 1 + NONCE_SIZE + TAG_SIZE + 9
        private const val MAX_RECORD_BYTES = 8 + MAX_ALIAS_BYTES + NONCE_SIZE + TAG_SIZE + SecureSecretCodec.MAX_ENCODED_BYTES
        private val RECORD_NAME = Regex("[0-9a-f]{64}\\.mfc")
        // Never remove a mutex: removing a quiescent entry can race an admitted
        // waiter and allow two locks for one directory. Namespace count is small.
        private val directoryLocks = ConcurrentHashMap<String, Mutex>()
        private fun invalidRecord(): Nothing = throw CredentialAccessException(
            CredentialFailureKind.StorageFailure, "Invalid encrypted credential record",
        )
    }
}

internal fun credentialFailure(failure: Exception): CredentialAccessException {
    val kind = when (failure) {
        is UserNotAuthenticatedException, is SecurityException -> CredentialFailureKind.AccessDenied
        is KeyPermanentlyInvalidatedException, is UnrecoverableKeyException, is KeyStoreException,
        is InvalidKeyException, is ProviderException -> CredentialFailureKind.BackendUnavailable
        is NoSuchAlgorithmException, is NoSuchProviderException, is NoSuchPaddingException,
        is InvalidAlgorithmParameterException -> CredentialFailureKind.Unsupported
        is IOException, is BadPaddingException, is IllegalBlockSizeException,
        is IllegalArgumentException -> CredentialFailureKind.StorageFailure
        is GeneralSecurityException -> CredentialFailureKind.BackendUnavailable
        else -> CredentialFailureKind.Unknown
    }
    return CredentialAccessException(kind, "Android secure credential operation failed: ${kind.name}", failure)
}
