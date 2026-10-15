@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp.storage

import cn.mafei.cmp.core.storage_api.SettingsStorage
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.random.Random
import platform.Foundation.*

/** The system returns this app sandbox's persistent Application Support directory. */
fun createIosSettings(): SettingsStorage {
    val path = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: unavailable()
    return DocumentSettingsStorage(IosSettingsDocument(path))
}

/** Atomic visibility, bounded descriptor reads, owner-only files; one runtime writer. */
private class IosSettingsDocument(private val privateDirectory: String) : SettingsDocumentBackend {
    init {
        require(privateDirectory.startsWith('/') && privateDirectory.length > 1 && '\u0000' !in privateDirectory) {
            "Persistent private settings directory required"
        }
    }
    override fun read(): ByteArray? = inDirectory { directory ->
        val fd = openat(directory, "settings.json", O_RDONLY or O_NOFOLLOW or O_NONBLOCK or O_CLOEXEC)
        if (fd < 0) {
            if (errno == ENOENT) return@inDirectory null
            unavailable()
        }
        try {
            val size = checkFile(fd, directory = false)
            if (size !in 1L..MAX_SETTINGS_DOCUMENT_BYTES.toLong()) corrupt()
            val bytes = ByteArray(size.toInt())
            bytes.usePinned { pinned ->
                var offset = 0
                while (offset < bytes.size) {
                    val count = platform.posix.read(fd, pinned.addressOf(offset), (bytes.size - offset).convert())
                    if (count < 0 && errno == EINTR) continue
                    if (count <= 0) corrupt()
                    offset += count.toInt()
                }
            }
            memScoped {
                val extra = alloc<ByteVar>()
                var count: Long
                do { count = platform.posix.read(fd, extra.ptr, 1u) } while (count < 0 && errno == EINTR)
                if (count != 0L) corrupt()
            }
            bytes
        } finally { close(fd) }
    }

    override fun replace(document: ByteArray): Unit = inDirectory { directory ->
        val existing = openat(directory, "settings.json", O_RDONLY or O_NOFOLLOW or O_NONBLOCK or O_CLOEXEC)
        if (existing >= 0) {
            try { checkFile(existing, directory = false) } finally { close(existing) }
        } else if (errno != ENOENT) unavailable()
        // Randomness is for a unique temporary filename, not cryptography. O_EXCL is authoritative.
        val name = ".settings-" + Random.nextLong().toULong().toString(16) + ".tmp"
        val fd = openat(directory, name, O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW or O_CLOEXEC, 384)
        if (fd < 0) unavailable()
        try {
            checkFile(fd, directory = false)
            document.usePinned { pinned ->
                var offset = 0
                while (offset < document.size) {
                    val count = write(fd, pinned.addressOf(offset), (document.size - offset).convert())
                    if (count < 0 && errno == EINTR) continue
                    if (count <= 0) unavailable()
                    offset += count.toInt()
                }
            }
            if (fsync(fd) != 0) unavailable()
            // Commit point. No fallible work is reported after this succeeds.
            if (renameat(directory, name, directory, "settings.json") != 0) unavailable()
        } finally {
            close(fd)
            unlinkat(directory, name, 0) // best-effort cleanup of only this call's temporary file
        }
    }

    private fun <T> inDirectory(block: (Int) -> T): T {
        if (!NSFileManager.defaultManager.createDirectoryAtPath(privateDirectory, true, null, null)) unavailable()
        val parent = open(privateDirectory, O_RDONLY or O_DIRECTORY or O_NOFOLLOW or O_CLOEXEC)
        if (parent < 0) unavailable()
        try {
            checkFile(parent, directory = true, privatePermissions = false)
            if (mkdirat(parent, "mafei-settings-v1", 448u.convert()) != 0 && errno != EEXIST) unavailable()
            val directory = openat(parent, "mafei-settings-v1", O_RDONLY or O_DIRECTORY or O_NOFOLLOW or O_CLOEXEC)
            if (directory < 0) unavailable()
            try { checkFile(directory, directory = true); return block(directory) }
            finally { close(directory) }
        } finally { close(parent) }
    }
    private fun checkFile(fd: Int, directory: Boolean, privatePermissions: Boolean = true): Long = memScoped {
        val info = alloc<stat>()
        if (fstat(fd, info.ptr) != 0) unavailable()
        val expected = if (directory) S_IFDIR.toUInt() else S_IFREG.toUInt()
        if ((info.st_mode.toUInt() and S_IFMT.toUInt()) != expected || info.st_uid != geteuid() ||
            privatePermissions && (info.st_mode.toUInt() and 63u) != 0u) unavailable()
        info.st_size
    }
}
