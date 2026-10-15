package cn.mafei.cmp.core.secure_credential_api

/**
 * Storage boundary for sensitive credential material (tokens, cookies,
 * passwords). Implementations live in `platform:secure-storage-*` and must map
 * to the platform keystore/keychain/secure-equivalent; falling back to
 * plaintext files is forbidden by contract.
 *
 * Rules:
 * - all operations are `suspend` and must not block callers;
 * - [read] returns `null` for missing aliases (absence is not an error);
 * - backend failures raise [CredentialAccessException] with a precise kind;
 * - implementations must never log secret values.
 */
interface SecureCredentialStore {

    /**
     * Stores [secret] under [alias]. With `overwrite = false` and an existing
     * value, throws [CredentialAccessException] with kind `AlreadyExists`.
     */
    suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean = true)

    /** Returns the stored secret, or `null` when nothing is stored under [alias]. */
    suspend fun read(alias: CredentialAlias): SecureSecret?

    /** Removes the secret; returns `true` when something was deleted. */
    suspend fun delete(alias: CredentialAlias): Boolean

    suspend fun exists(alias: CredentialAlias): Boolean

    /** Aliases starting with [prefix] (all when `null`), in stable order. */
    suspend fun listAliases(prefix: String? = null): List<CredentialAlias>

    /** Removes every alias matching [prefix]; returns the number removed. */
    suspend fun deleteAll(prefix: String? = null): Int
}
