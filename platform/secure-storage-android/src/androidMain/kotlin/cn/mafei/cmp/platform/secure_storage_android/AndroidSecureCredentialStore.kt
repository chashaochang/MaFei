package cn.mafei.cmp.platform.secure_storage_android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import cn.mafei.cmp.core.secure_credential_api.*
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Production Android store (API 24+): non-exportable AndroidKeyStore AES-256 key,
 * GCM-authenticated records exclusively in Context.noBackupFilesDir. Namespace
 * must remain stable across app upgrades. There is no software-key/plaintext
 * fallback and no public API for injecting key material or external directories.
 *
 * Keystore does not guarantee hardware backing on every device; this class does
 * not falsely advertise StrongBox or biometric authorization. Ordinary background
 * session persistence uses a key without per-operation biometric prompts.
 * Reinstall/key loss cannot recover these credentials; callers must reauthenticate.
 */
class AndroidSecureCredentialStore(
    context: Context,
    namespace: String = "cn.mafei.credentials.v1",
) : SecureCredentialStore by androidBackend(context, namespace)

private fun androidBackend(context: Context, namespace: String): SecureCredentialStore {
    require(namespace.isNotBlank() && namespace.length <= 256) { "Invalid credential namespace" }
    val directory = File(context.applicationContext.noBackupFilesDir, "mafei-secure/${sha256Hex(namespace)}")
    return EncryptedCredentialFiles(
        directory = directory,
        namespace = namespace,
        keys = AndroidCredentialKeySource("cn.mafei.secure.${sha256Hex(namespace)}"),
        atomicReplace = AtomicCredentialReplace { source, target ->
            androidFileOperation { Os.rename(source.path, target.path) }
        },
        syncDirectory = { root ->
            androidFileOperation {
                val descriptor = Os.open(root.path, OsConstants.O_RDONLY, 0)
                try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
            }
        },
    )
}

private class AndroidCredentialKeySource(private val keyAlias: String) : CredentialKeySource {
    override fun key(allowCreate: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(keyAlias, null)
        if (existing != null) {
            if (existing !is SecretKey || existing.algorithm != KeyProperties.KEY_ALGORITHM_AES) {
                throw CredentialAccessException(CredentialFailureKind.BackendUnavailable, "Invalid Android Keystore key type")
            }
            return existing
        }
        if (!allowCreate) throw CredentialAccessException(
            CredentialFailureKind.BackendUnavailable, "Android Keystore key unavailable; reauthentication required",
        )
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build())
            generateKey()
        }
    }
}

private inline fun <T> androidFileOperation(block: () -> T): T = try { block() }
catch (failure: ErrnoException) {
    val kind = if (failure.errno == OsConstants.EACCES || failure.errno == OsConstants.EPERM) {
        CredentialFailureKind.AccessDenied
    } else CredentialFailureKind.StorageFailure
    throw CredentialAccessException(kind, "Android secure credential file operation failed", failure)
}
