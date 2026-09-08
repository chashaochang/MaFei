package cn.mafei.cmp.platform.secure_storage_macos

import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.platform.secure_storage_core.SecureSecretCodec
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Security.framework file-based Keychain for Apple Silicon JVM Desktop.
 * Writes use the user's default Keychain (normally login); reads use the system
 * search list with exact service/account scope. System ACL/unlock prompts are
 * allowed, including after ad-hoc rebuilds. No Data Protection or plaintext fallback.
 * Namespace must be stable across updates; queries always
 * specify the full service, not a prefix/search of unrelated credentials.
 *
 * Native calls run on Dispatchers.IO. Their synchronous OS commit cannot be
 * rolled back by a racing cancellation; queued cancellation prevents admission.
 */
class MacOSSecureCredentialStore internal constructor(
    namespace: String,
    private val bridge: KeychainBridge,
) : SecureCredentialStore {
    constructor(namespace: String = "default") : this(namespace, JniKeychainBridge)

    init { require(namespace.isNotBlank() && namespace.length <= 256) { "Invalid Keychain namespace" } }
    private val service = "cn.mafei.credentials.v1/$namespace"
    private val mutex = locks.getOrPut(service) { Mutex() }

    override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) = locked {
        validateAlias(alias)
        val data = SecureSecretCodec.encode(secret)
        try {
            if (!overwrite) {
                currentCoroutineContext().ensureActive()
                checkStatus(bridge.add(service, alias.value, data))
            } else {
                // Update/Add retry handles another process adding or deleting
                // between calls. Never delete an old value as part of overwrite.
                var written = false
                repeat(8) {
                    if (!written) {
                        currentCoroutineContext().ensureActive()
                        when (val updated = bridge.update(service, alias.value, data)) {
                            KeychainStatus.Success -> written = true
                            KeychainStatus.NotFound -> {
                                currentCoroutineContext().ensureActive()
                                when (val added = bridge.add(service, alias.value, data)) {
                                    KeychainStatus.Success -> written = true
                                    KeychainStatus.Duplicate -> Unit
                                    else -> checkStatus(added)
                                }
                            }
                            else -> checkStatus(updated)
                        }
                    }
                }
                if (!written) throw CredentialAccessException(CredentialFailureKind.StorageFailure, "Keychain update contention exceeded retry limit")
            }
        } finally { data.fill(0) }
    }

    override suspend fun read(alias: CredentialAlias): SecureSecret? = locked {
        validateAlias(alias)
        val result = bridge.read(service, alias.value)
        val data = result.data
        try {
            if (result.status == KeychainStatus.NotFound) null else {
                checkStatus(result.status)
                if (data == null) corrupt()
                SecureSecretCodec.decode(data)
            }
        } finally { data?.fill(0) }
    }

    override suspend fun exists(alias: CredentialAlias): Boolean = read(alias) != null

    override suspend fun delete(alias: CredentialAlias): Boolean = locked {
        validateAlias(alias)
        when (val status = bridge.delete(service, alias.value)) {
            KeychainStatus.NotFound -> false
            else -> { checkStatus(status); true }
        }
    }

    override suspend fun listAliases(prefix: String?): List<CredentialAlias> = locked { aliases().filter { prefix == null || it.value.startsWith(prefix) } }

    override suspend fun deleteAll(prefix: String?): Int = locked {
        var removed = 0
        for (alias in aliases().filter { prefix == null || it.value.startsWith(prefix) }) {
            currentCoroutineContext().ensureActive()
            val status = bridge.delete(service, alias.value)
            if (status != KeychainStatus.NotFound) { checkStatus(status); removed++ }
        }
        removed
    }

    private fun aliases(): List<CredentialAlias> {
        val result = bridge.aliases(service)
        if (result.status == KeychainStatus.NotFound) return emptyList()
        checkStatus(result.status)
        val values = result.accounts ?: corrupt()
        return values.map { value ->
            try { CredentialAlias(value).also(::validateAlias) }
            catch (_: IllegalArgumentException) { corrupt() }
        }.also { if (it.distinct().size != it.size) corrupt() }.sortedBy { it.value }
    }

    private fun validateAlias(alias: CredentialAlias) {
        if (alias.value.length > 16 * 1024) corrupt()
        try { alias.value.encodeToByteArray(throwOnInvalidSequence = true) }
        catch (_: CharacterCodingException) { corrupt() }
    }

    private suspend fun <T> locked(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            try { block() }
            catch (failure: CancellationException) { throw failure }
            catch (failure: CredentialAccessException) { throw failure }
            // A raw backend exception/cause can embed credential material.
            // Preserve classification, never expose that cause to ordinary logs.
            catch (_: UnsatisfiedLinkError) { throw CredentialAccessException(CredentialFailureKind.BackendUnavailable, "Keychain JNI unavailable") }
            catch (_: SecurityException) { throw CredentialAccessException(CredentialFailureKind.AccessDenied, "Keychain access denied") }
            catch (_: Exception) { throw CredentialAccessException(CredentialFailureKind.Unknown, "Keychain operation failed") }
        }
    }

    companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()
        private fun corrupt(): Nothing = throw CredentialAccessException(CredentialFailureKind.StorageFailure, "Invalid Keychain credential data")
    }
}

internal data class KeychainRead(val status: Int, val data: ByteArray?) {
    override fun toString(): String = "KeychainRead(status=$status, data=redacted)"
}
internal data class KeychainAliases(val status: Int, val accounts: Array<String>?)

internal interface KeychainBridge {
    fun add(service: String, account: String, data: ByteArray): Int
    fun update(service: String, account: String, data: ByteArray): Int
    fun read(service: String, account: String): KeychainRead
    fun aliases(service: String): KeychainAliases
    fun delete(service: String, account: String): Int
}

internal object KeychainStatus {
    const val Success = 0
    const val NotFound = -25300
    const val Duplicate = -25299
    const val AuthFailed = -25293
    const val InteractionNotAllowed = -25308
    const val NotAvailable = -25291
    const val MissingEntitlement = -34018
    const val Decode = -26275
    const val Unimplemented = -4
}

internal fun checkStatus(status: Int) {
    if (status == KeychainStatus.Success) return
    val kind = when (status) {
        KeychainStatus.Duplicate -> CredentialFailureKind.AlreadyExists
        KeychainStatus.InteractionNotAllowed, KeychainStatus.NotAvailable, -25294 -> CredentialFailureKind.BackendUnavailable
        KeychainStatus.AuthFailed, KeychainStatus.MissingEntitlement, -25292, -25309, -128 -> CredentialFailureKind.AccessDenied
        KeychainStatus.Decode, -36, -25295, -25316, -50 -> CredentialFailureKind.StorageFailure
        KeychainStatus.Unimplemented -> CredentialFailureKind.Unsupported
        else -> CredentialFailureKind.Unknown
    }
    throw CredentialAccessException(kind, "macOS Keychain failed (OSStatus=$status, kind=${kind.name})")
}
