package cn.mafei.cmp.core.secure_credential_api

/**
 * Credential material handed to [SecureCredentialStore]. Values are never
 * serializable through shared JSON machinery, and `toString` always redacts
 * so accidental logging cannot leak secrets.
 *
 * Byte-array ownership is frozen by contract: [SecureSecret.RawBytes] copies
 * on construction and on every read, so mutating the array handed in (or the
 * one returned from [SecureSecret.RawBytes.value]) can never alter stored
 * credential material. The same rule applies to store implementations —
 * secrets are copied at the save/read boundary as well.
 */
sealed class SecureSecret {

    /** Bearer/access token with its auth scheme (e.g. `MediaBrowser`, `Bearer`). */
    data class AuthToken(
        val scheme: String,
        val token: String,
    ) : SecureSecret() {
        override fun toString(): String = "SecureSecret.AuthToken(scheme=$scheme, token=██)"
    }

    /** Password used for username/password login. */
    data class Password(
        val value: String,
    ) : SecureSecret() {
        override fun toString(): String = "SecureSecret.Password(██)"
    }

    /** Cookie header text or cookie-jar blob kept opaque to this contract. */
    data class CookieText(
        val value: String,
    ) : SecureSecret() {
        override fun toString(): String = "SecureSecret.CookieText(██)"
    }

    /** Raw secret bytes (e.g. encrypted session keys). Defensive copies on construction and read. */
    class RawBytes private constructor(
        private val backing: ByteArray,
    ) : SecureSecret() {

        /** Returns a fresh copy; mutating it never affects this secret. */
        val value: ByteArray get() = backing.copyOf()

        override fun equals(other: Any?): Boolean =
            other is RawBytes && other.backing.contentEquals(backing)

        override fun hashCode(): Int = backing.contentHashCode()

        override fun toString(): String = "SecureSecret.RawBytes(size=${backing.size})"

        companion object {
            operator fun invoke(value: ByteArray): RawBytes = RawBytes(value.copyOf())
        }
    }
}
