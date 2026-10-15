package cn.mafei.cmp.core.secure_credential_api

/** Why a secure-credential operation failed. */
enum class CredentialFailureKind {
    /** Platform secure storage is missing or locked (e.g. no Keychain access). */
    BackendUnavailable,

    /** Platform cannot securely implement this operation; never degrade to plaintext. */
    Unsupported,

    /** The store refused access (permission denied, user rejection). */
    AccessDenied,

    /** Backend reported a storage-level failure. */
    StorageFailure,

    /** An alias conflict occurred while `overwrite = false`. */
    AlreadyExists,

    Unknown,
}

/** Raised by [SecureCredentialStore] implementations on failure. */
class CredentialAccessException(
    val kind: CredentialFailureKind,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: "Credential operation failed: ${kind.name}", cause)
