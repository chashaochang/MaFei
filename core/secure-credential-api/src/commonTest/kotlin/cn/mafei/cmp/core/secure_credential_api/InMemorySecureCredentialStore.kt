package cn.mafei.cmp.core.secure_credential_api

/**
 * Contract fake: synchronous in-memory [SecureCredentialStore] mirroring the
 * documented semantics (overwrite policy, prefix listing, bulk delete).
 */
open class InMemorySecureCredentialStore : SecureCredentialStore {

    private val secrets = linkedMapOf<String, SecureSecret>()

    override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) {
        if (!overwrite && secrets.containsKey(alias.value)) {
            throw CredentialAccessException(
                kind = CredentialFailureKind.AlreadyExists,
                message = "Alias already exists: $alias",
            )
        }
        secrets[alias.value] = when (secret) {
            // copy byte-backed secrets at the boundary like real keystore backends must
            is SecureSecret.RawBytes -> SecureSecret.RawBytes(secret.value)
            else -> secret
        }
    }

    override suspend fun read(alias: CredentialAlias): SecureSecret? = secrets[alias.value]

    override suspend fun delete(alias: CredentialAlias): Boolean = secrets.remove(alias.value) != null

    override suspend fun exists(alias: CredentialAlias): Boolean = secrets.containsKey(alias.value)

    override suspend fun listAliases(prefix: String?): List<CredentialAlias> =
        secrets.keys.filter { prefix == null || it.startsWith(prefix) }.sorted().map { CredentialAlias(it) }

    override suspend fun deleteAll(prefix: String?): Int {
        val doomed = secrets.keys.filter { prefix == null || it.startsWith(prefix) }
        doomed.forEach { secrets.remove(it) }
        return doomed.size
    }
}
