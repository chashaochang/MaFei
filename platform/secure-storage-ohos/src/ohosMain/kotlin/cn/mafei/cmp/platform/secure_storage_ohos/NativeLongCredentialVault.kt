@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp.platform.secure_storage_ohos

import cn.mafei.cmp.core.secure_credential_api.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.native.concurrent.ThreadLocal

internal actual fun createLongCredentialVault(privateDirectory: String, namespace: String): LongCredentialVault =
    NativeLongCredentialVault(privateDirectory, namespace)

@ThreadLocal private var activeVaultDirectory: Int = -1

/** Descriptor-relative files, owner-only directory, no symlinks, Linux OFD cross-process lock. */
private class NativeLongCredentialVault(private val privateDirectory: String, namespace: String) : LongCredentialVault {
    private val child = "mafei-secure-" + namespace.also { AssetKeys(it) }
    init {
        require(privateDirectory.startsWith('/') && privateDirectory.length > 1 && '\u0000' !in privateDirectory) {
            "An absolute persistent app-private directory is required"
        }
        require(listOf("/tmp", "/private/tmp", "/data/local/tmp").none {
            privateDirectory == it || privateDirectory.startsWith("$it/")
        }) { "Temporary directories are not credential storage" }
    }

    override fun <T> locked(block: () -> T): T {
        check(activeVaultDirectory == -1) { "Nested credential transactions are unsupported" }
        val parent = open(privateDirectory, O_RDONLY or O_DIRECTORY or O_NOFOLLOW or O_CLOEXEC)
        if (parent < 0) fileFailure()
        try {
            validateFile(parent, directory = true, requirePrivate = false)
            if (mkdirat(parent, child, 448u) != 0) {
                if (errno != EEXIST) fileFailure()
            } else if (fsync(parent) != 0) fileFailure()
            val directory = openat(parent, child, O_RDONLY or O_DIRECTORY or O_NOFOLLOW or O_CLOEXEC)
            if (directory < 0) fileFailure()
            try {
                validateFile(directory, directory = true)
                val lock = openat(directory, ".lock", O_RDWR or O_CREAT or O_NOFOLLOW or O_CLOEXEC, 384)
                if (lock < 0) fileFailure()
                try {
                    validateFile(lock, directory = false)
                    memScoped {
                        val operation = alloc<flock>().apply {
                            l_type = F_WRLCK.toShort(); l_whence = SEEK_SET.toShort()
                            l_start = 0; l_len = 0; l_pid = 0
                        }
                        while (fcntl(lock, F_OFD_SETLKW, operation.ptr) != 0) {
                            if (errno != EINTR) fileFailure()
                        }
                    }
                    activeVaultDirectory = directory
                    try { return block() } finally { activeVaultDirectory = -1 }
                } finally { close(lock) } // Closing releases only this open-file-description's lock.
            } finally { close(directory) }
        } finally { close(parent) }
    }

    override fun random(size: Int): ByteArray {
        val fd = open("/dev/urandom", O_RDONLY or O_CLOEXEC or O_NOFOLLOW)
        if (fd < 0) fileFailure()
        val result = ByteArray(size)
        try { readFully(fd, result); return result }
        catch (failure: Throwable) { result.fill(0); throw failure }
        finally { close(fd) }
    }

    override fun encrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray =
        NativeGcm.crypt(true, key, nonce, aad, plaintext)
    override fun decrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray =
        NativeGcm.crypt(false, key, nonce, aad, ciphertext)

    override fun writeNew(id: String, ciphertext: ByteArray) {
        if (ciphertext.size !in 25..MAX_CIPHERTEXT_BYTES) invalidAssetResult()
        val fd = openat(root(), name(id), O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW or O_CLOEXEC, 384)
        if (fd < 0) fileFailure()
        var completed = false
        try {
            validateFile(fd, directory = false)
            ciphertext.usePinned { pinned ->
                var offset = 0
                while (offset < ciphertext.size) {
                    val count = write(fd, pinned.addressOf(offset), (ciphertext.size - offset).toULong())
                    if (count < 0 && errno == EINTR) continue
                    if (count <= 0) fileFailure()
                    offset += count.toInt()
                }
            }
            if (fsync(fd) != 0) fileFailure()
            if (fsync(root()) != 0) fileFailure()
            completed = true
        } finally {
            close(fd)
            // This call owns the exclusively-created file, never an EEXIST target.
            if (!completed) unlinkat(root(), name(id), 0)
        }
    }

    override fun read(id: String): ByteArray {
        val fd = openat(root(), name(id), O_RDONLY or O_NOFOLLOW or O_CLOEXEC)
        if (fd < 0) fileFailure()
        try {
            val length = validateFile(fd, directory = false)
            if (length !in 25L..MAX_CIPHERTEXT_BYTES.toLong()) invalidAssetResult()
            val result = ByteArray(length.toInt())
            readFully(fd, result)
            memScoped {
                val extra = alloc<ByteVar>()
                var count: Long
                do { count = platform.posix.read(fd, extra.ptr, 1u) } while (count < 0 && errno == EINTR)
                if (count != 0L) invalidAssetResult()
            }
            return result
        } finally { close(fd) }
    }

    override fun delete(id: String) {
        if (unlinkat(root(), name(id), 0) != 0 && errno != ENOENT) fileFailure()
        if (fsync(root()) != 0) fileFailure()
    }

    private fun name(id: String): String {
        require(id.length == 32 && id.all { it in '0'..'9' || it in 'a'..'f' }) { "Invalid credential record ID" }
        return "$id.mfc"
    }
    private fun root(): Int = activeVaultDirectory.also { check(it >= 0) { "Credential transaction required" } }
}

private fun validateFile(fd: Int, directory: Boolean, requirePrivate: Boolean = true): Long = memScoped {
    val info = alloc<stat>()
    if (fstat(fd, info.ptr) != 0) fileFailure()
    val expected = if (directory) S_IFDIR else S_IFREG
    if ((info.st_mode and S_IFMT.toUInt()) != expected.toUInt() || info.st_uid != geteuid() ||
        requirePrivate && (info.st_mode and 63u) != 0u || !directory && info.st_nlink != 1u) {
        throw CredentialAccessException(CredentialFailureKind.AccessDenied, "Unsafe credential filesystem object")
    }
    info.st_size
}

private fun readFully(fd: Int, bytes: ByteArray) = bytes.usePinned { pinned ->
    var offset = 0
    while (offset < bytes.size) {
        val count = platform.posix.read(fd, pinned.addressOf(offset), (bytes.size - offset).toULong())
        if (count < 0 && errno == EINTR) continue
        if (count <= 0) fileFailure()
        offset += count.toInt()
    }
}

private fun fileFailure(): Nothing {
    val code = errno
    throw CredentialAccessException(when (code) {
        EACCES, EPERM, ELOOP -> CredentialFailureKind.AccessDenied
        ENOSYS, EOPNOTSUPP, EINVAL -> CredentialFailureKind.Unsupported
        else -> CredentialFailureKind.StorageFailure
    }, "Encrypted credential filesystem operation failed")
}
