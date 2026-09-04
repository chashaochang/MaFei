package cn.mafei.cmp.core.secure_credential_api

/**
 * Credential material handed to [SecureCredentialStore]. Values are never
 * serializable through shared JSON machinery, and `toString` always redacts
 * so accidental logging cannot leak secrets.
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

    /** Raw secret bytes (e.g. encrypted session keys). */
    class RawBytes(
        val value: ByteArray,
    ) : SecureSecret() {
        override fun equals(other: Any?): Boolean =
            other is RawBytes && other.value.contentEquals(value)

        override fun hashCode(): Int = value.contentHashCode()

        override fun toString(): String = "SecureSecret.RawBytes(size=${value.size})"
    }
}
