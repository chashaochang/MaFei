package cn.mafei.cmp.storage

import cn.mafei.cmp.core.secure_credential_api.CredentialAccessException
import cn.mafei.cmp.core.secure_credential_api.CredentialAlias
import cn.mafei.cmp.core.secure_credential_api.CredentialFailureKind
import cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import cn.mafei.cmp.core.storage_api.StorageCorruptedException
import cn.mafei.cmp.core.storage_api.StorageException
import cn.mafei.cmp.core.storage_api.StorageUnavailableException
import cn.mafei.cmp.core.storage_api.StorageUnsupportedException
import cn.mafei.cmp.platform.secure_storage_core.SecureSecretCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * HarmonyOS compatibility store matching the released app's private RDB/Preferences model.
 * The document is owner-only and atomically replaced, but intentionally does not claim
 * Keychain/AssetStore hardware protection. It keeps CMP usable on devices where the current
 * AssetStore native binding fails before legacy account migration can commit.
 */
internal class OhosPrivateCredentialStore(privateDirectory: String) : SecureCredentialStore {
    private val storage = createOhosCredentialSettings(privateDirectory)
    private val mutex = Mutex()

    override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) = locked {
        val key = key(alias)
        if (!overwrite && storage.contains(key)) {
            throw CredentialAccessException(CredentialFailureKind.AlreadyExists)
        }
        val encoded = SecureSecretCodec.encode(secret)
        try {
            storage.putBytes(key, encoded)
        } finally {
            encoded.fill(0)
        }
    }

    override suspend fun read(alias: CredentialAlias): SecureSecret? = locked {
        val encoded = storage.getBytes(key(alias)) ?: return@locked null
        try {
            SecureSecretCodec.decode(encoded)
        } finally {
            encoded.fill(0)
        }
    }

    override suspend fun exists(alias: CredentialAlias): Boolean = locked { storage.contains(key(alias)) }

    override suspend fun delete(alias: CredentialAlias): Boolean = locked { storage.remove(key(alias)) }

    override suspend fun listAliases(prefix: String?): List<CredentialAlias> = locked {
        storage.listKeys(KEY_PREFIX).map { CredentialAlias(it.removePrefix(KEY_PREFIX)) }
            .filter { prefix == null || it.value.startsWith(prefix) }
            .sortedBy(CredentialAlias::value)
    }

    override suspend fun deleteAll(prefix: String?): Int = locked {
        val keys = storage.listKeys(KEY_PREFIX).filter {
            prefix == null || it.removePrefix(KEY_PREFIX).startsWith(prefix)
        }
        if (keys.isNotEmpty()) storage.edit { keys.forEach(::remove) }
        keys.size
    }

    private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: CredentialAccessException) {
            throw failure
        } catch (failure: StorageException) {
            val kind = when (failure) {
                is StorageUnavailableException -> CredentialFailureKind.BackendUnavailable
                is StorageUnsupportedException -> CredentialFailureKind.Unsupported
                is StorageCorruptedException -> CredentialFailureKind.StorageFailure
                else -> CredentialFailureKind.StorageFailure
            }
            throw CredentialAccessException(kind, "HarmonyOS private credential storage failed")
        } catch (_: Exception) {
            throw CredentialAccessException(CredentialFailureKind.StorageFailure, "HarmonyOS private credential storage failed")
        }
    }

    private fun key(alias: CredentialAlias): String = KEY_PREFIX + alias.value

    private companion object {
        const val KEY_PREFIX = "credential/"
    }
}
