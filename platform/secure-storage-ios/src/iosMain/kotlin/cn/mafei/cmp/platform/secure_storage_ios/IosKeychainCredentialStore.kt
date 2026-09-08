package cn.mafei.cmp.platform.secure_storage_ios

import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.platform.secure_storage_core.SecureSecretCodec
import kotlinx.coroutines.*
import platform.Security.*

internal data class KeychainRead(val status: Int, val bytes: ByteArray?)
internal data class KeychainAliases(val status: Int, val values: List<String>)
internal interface KeychainBackend {
    fun add(alias: String, encoded: ByteArray): Int
    fun update(alias: String, encoded: ByteArray): Int
    fun read(alias: String): KeychainRead
    fun delete(alias: String): Int
    fun aliases(): KeychainAliases
    fun contains(alias: String): Int {
        val result = read(alias)
        result.bytes?.fill(0)
        return result.status
    }
}

/**
 * Security.framework generic-password store restricted to one exact service namespace.
 * Local device only, AfterFirstUnlockThisDeviceOnly, non-synchronizable, no access group
 * sharing, no authentication UI and no plaintext fallback. The service must be stable
 * across app upgrades (and unique to this product/install flavor).
 *
 * Operations execute on [Dispatchers.Default], never block the calling UI thread. A
 * synchronous SecItem operation cannot be interrupted: cancellation after admission
 * may observe a completed write, never a promise of rollback. overwrite=false is one
 * atomic SecItemAdd. Overwrite retries bounded update/add races and fails explicitly
 * under continuing external deletion. Prefix deletion enumerates once and deletes each
 * exact alias; concurrent new aliases are not included and the return value counts
 * this call's successful deletes, not a claimed cross-item transaction.
 */
class IosKeychainCredentialStore internal constructor(
    private val backend: KeychainBackend,
    private val dispatcher: CoroutineDispatcher,
) : SecureCredentialStore {
    constructor(service: String) : this(SecurityKeychainBackend(validateService(service)), Dispatchers.Default)

    override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) = withContext(dispatcher) {
        validateAlias(alias.value)
        currentCoroutineContext().ensureActive()
        val encoded = SecureSecretCodec.encode(secret)
        try {
            repeat(MAX_WRITE_ATTEMPTS) {
                currentCoroutineContext().ensureActive()
                when (val added = backend.add(alias.value, encoded)) {
                    errSecSuccess -> return@withContext
                    errSecDuplicateItem -> {
                        if (!overwrite) keychainFailure(added)
                        currentCoroutineContext().ensureActive()
                        when (val updated = backend.update(alias.value, encoded)) {
                            errSecSuccess -> return@withContext
                            errSecItemNotFound -> yield()
                            else -> keychainFailure(updated)
                        }
                    }
                    else -> keychainFailure(added)
                }
            }
            throw CredentialAccessException(CredentialFailureKind.StorageFailure, "Keychain write contention")
        } finally { encoded.fill(0) }
    }

    override suspend fun read(alias: CredentialAlias): SecureSecret? = withContext(dispatcher) {
        validateAlias(alias.value); currentCoroutineContext().ensureActive()
        val result = backend.read(alias.value)
        try {
            if (result.status == errSecItemNotFound) return@withContext null
            if (result.status != errSecSuccess) keychainFailure(result.status)
            currentCoroutineContext().ensureActive()
            SecureSecretCodec.decode(result.bytes ?: invalidKeychainResult())
        } finally { result.bytes?.fill(0) }
    }

    override suspend fun exists(alias: CredentialAlias): Boolean = withContext(dispatcher) {
        validateAlias(alias.value); currentCoroutineContext().ensureActive()
        when (val status = backend.contains(alias.value)) {
            errSecSuccess -> true
            errSecItemNotFound -> false
            else -> keychainFailure(status)
        }
    }

    override suspend fun delete(alias: CredentialAlias): Boolean = withContext(dispatcher) {
        validateAlias(alias.value); currentCoroutineContext().ensureActive()
        deleteExact(alias.value)
    }

    override suspend fun listAliases(prefix: String?): List<CredentialAlias> = withContext(dispatcher) {
        validatePrefix(prefix); currentCoroutineContext().ensureActive()
        listMatching(prefix)
    }

    override suspend fun deleteAll(prefix: String?): Int = withContext(dispatcher) {
        validatePrefix(prefix); currentCoroutineContext().ensureActive()
        var count = 0
        for (alias in listMatching(prefix)) {
            currentCoroutineContext().ensureActive()
            if (deleteExact(alias.value)) count++
        }
        count
    }

    private fun deleteExact(alias: String): Boolean = when (val status = backend.delete(alias)) {
        errSecSuccess -> true
        errSecItemNotFound -> false
        else -> keychainFailure(status)
    }

    private fun listMatching(prefix: String?): List<CredentialAlias> {
        val result = backend.aliases()
        if (result.status == errSecItemNotFound) return emptyList()
        if (result.status != errSecSuccess) keychainFailure(result.status)
        if (result.values.size > MAX_ALIASES) invalidKeychainResult()
        return result.values.map { value ->
            try { validateAlias(value); CredentialAlias(value) }
            catch (_: IllegalArgumentException) { invalidKeychainResult() }
        }.filter { prefix == null || it.value.startsWith(prefix) }.distinct().sortedBy { it.value }
    }
}

internal const val MAX_ALIAS_BYTES = 1024
internal const val MAX_ALIASES = 10_000
private const val MAX_WRITE_ATTEMPTS = 16

private fun validateService(service: String): String {
    require(service.length in 1..255 && service.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in ".-_" }) { "Invalid Keychain service namespace" }
    return service
}
private fun validateAlias(alias: String) {
    require(alias.isNotBlank() && alias.trim() == alias) { "Invalid Keychain account alias" }
    validatePrefix(alias)
}
private fun validatePrefix(prefix: String?) {
    if (prefix == null) return
    require(prefix.length <= MAX_ALIAS_BYTES && '\u0000' !in prefix) { "Invalid Keychain alias length or NUL" }
    val bytes = try { prefix.encodeToByteArray(throwOnInvalidSequence = true) }
    catch (_: CharacterCodingException) { throw IllegalArgumentException("Invalid Keychain alias encoding") }
    require(bytes.size <= MAX_ALIAS_BYTES) { "Keychain alias exceeds UTF-8 limit" }
}

internal fun keychainFailure(status: Int): Nothing {
    val kind = when (status) {
        errSecDuplicateItem -> CredentialFailureKind.AlreadyExists
        errSecNotAvailable, errSecInteractionNotAllowed -> CredentialFailureKind.BackendUnavailable
        errSecAuthFailed, errSecUserCanceled, errSecMissingEntitlement -> CredentialFailureKind.AccessDenied
        else -> CredentialFailureKind.StorageFailure
    }
    // Do not use SecCopyErrorMessageString, query descriptions, aliases, data or causes.
    throw CredentialAccessException(kind, "Keychain operation failed (status=$status)")
}
internal fun invalidKeychainResult(): Nothing = throw CredentialAccessException(CredentialFailureKind.StorageFailure, "Invalid Keychain response")
