package cn.mafei.cmp.core.storage_api

/**
 * Local persistence failures surfaced by storage contracts. Callers translate
 * these into `core:domain` `StorageError` values at the repository boundary.
 */
sealed class StorageException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Stored data exists but cannot be decoded (schema drift, bit rot). */
class StorageCorruptedException(
    val key: String?,
    message: String,
    cause: Throwable? = null,
) : StorageException(message = message, cause = cause)

/** The storage backend is temporarily unavailable (locked, unmounted, ...). */
class StorageUnavailableException(
    message: String,
    cause: Throwable? = null,
) : StorageException(message = message, cause = cause)

/** The backend cannot honor an operation required by the contract. */
class StorageUnsupportedException(
    val operation: String,
    message: String,
) : StorageException(message = message)

/** Quota/disk-space exhausted. */
class StorageQuotaExceededException(
    message: String,
    cause: Throwable? = null,
) : StorageException(message = message, cause = cause)
