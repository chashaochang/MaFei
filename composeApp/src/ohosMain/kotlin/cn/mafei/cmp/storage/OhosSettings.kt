@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp.storage

import cn.mafei.cmp.core.storage_api.SettingsStorage
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.random.Random

/** Host supplies its persistent filesDir; no inferred/temp path and no ArkTS business storage. */
fun createOhosSettings(privateDirectory: String): SettingsStorage =
    DocumentSettingsStorage(OhosSettingsDocument(privateDirectory, "mafei-settings-v2", "settings.json"))

/** Legacy-compatible CMP credential document, isolated from ordinary settings and old ArkUI data. */
internal fun createOhosCredentialSettings(privateDirectory: String): SettingsStorage =
    DocumentSettingsStorage(OhosSettingsDocument(privateDirectory, "mafei-credentials-v2", "credentials.json"))

/** Atomic visibility, bounded descriptor reads, owner-only files; one runtime writer. */
private class OhosSettingsDocument(
    private val privateDirectory: String,
    private val storageDirectory: String,
    private val storageFile: String,
) : SettingsDocumentBackend {
    init {
        require(privateDirectory.startsWith('/') && privateDirectory.length > 1 && '\u0000' !in privateDirectory) {
            "Persistent private settings directory required"
        }
        require(storageDirectory.matches(Regex("[a-z0-9-]{1,64}")) && storageFile.matches(Regex("[a-z0-9.-]{1,64}")))
    }

    override fun read(): ByteArray? = inDirectory { directory ->
        val fd = openat(directory, storageFile, O_RDONLY or O_NOFOLLOW or O_NONBLOCK or O_CLOEXEC)
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
        } finally {
            close(fd)
        }
    }

    override fun replace(document: ByteArray): Unit = inDirectory { directory ->
        val existing = openat(directory, storageFile, O_RDONLY or O_NOFOLLOW or O_NONBLOCK or O_CLOEXEC)
        if (existing >= 0) {
            try { checkFile(existing, directory = false) } finally { close(existing) }
        } else if (errno != ENOENT) {
            unavailable()
        }

        val temporary = ".settings-" + Random.nextLong().toULong().toString(16) + ".tmp"
        val fd = openat(directory, temporary, O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW or O_CLOEXEC, 384)
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
            if (renameat(directory, temporary, directory, storageFile) != 0) unavailable()
        } finally {
            close(fd)
            unlinkat(directory, temporary, 0)
        }
    }

    private fun <T> inDirectory(block: (Int) -> T): T {
        val parent = open(privateDirectory, O_RDONLY or O_DIRECTORY or O_NOFOLLOW or O_CLOEXEC)
        if (parent < 0) unavailable()
        try {
            checkFile(parent, directory = true, privatePermissions = false)
            // This new CMP-only directory avoids the broken development NDK Preferences store.
            // It does not modify the legacy ArkUI databases and preferences being migrated.
            if (mkdirat(parent, storageDirectory, 448u.convert()) != 0 && errno != EEXIST) unavailable()
            val directory = openat(parent, storageDirectory, O_RDONLY or O_DIRECTORY or O_NOFOLLOW or O_CLOEXEC)
            if (directory < 0) unavailable()
            try {
                checkFile(directory, directory = true)
                return block(directory)
            } finally {
                close(directory)
            }
        } finally {
            close(parent)
        }
    }

    private fun checkFile(fd: Int, directory: Boolean, privatePermissions: Boolean = true): Long = memScoped {
        val info = alloc<stat>()
        if (fstat(fd, info.ptr) != 0) unavailable()
        val expected = if (directory) S_IFDIR.toUInt() else S_IFREG.toUInt()
        if ((info.st_mode.toUInt() and S_IFMT.toUInt()) != expected || info.st_uid != geteuid() ||
            privatePermissions && (info.st_mode.toUInt() and 63u) != 0u
        ) unavailable()
        info.st_size
    }
}
