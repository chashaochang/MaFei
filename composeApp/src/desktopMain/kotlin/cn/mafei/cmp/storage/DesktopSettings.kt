package cn.mafei.cmp.storage

import cn.mafei.cmp.core.storage_api.SettingsStorage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions

/** Mac app-owned Application Support namespace, including the sandbox home when sandboxed. */
fun createDesktopSettings(): SettingsStorage {
    val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() } ?: unavailable()
    val directory = File(home, "Library/Application Support/cn.mafei.cmp/settings-v1").canonicalFile
    return DocumentSettingsStorage(DesktopSettingsDocument(directory))
}

internal class DesktopSettingsDocument(private val directory: File) : SettingsDocumentBackend {
    private fun target(): File {
        Files.createDirectories(directory.toPath())
        safe(directory)
        Files.setPosixFilePermissions(directory.toPath(), PosixFilePermissions.fromString("rwx------"))
        return File(directory, "settings.json").also(::safe)
    }
    override fun read(): ByteArray? {
        val file = target()
        val attributes = try { Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS) }
        catch (_: NoSuchFileException) { return null }
        if (!attributes.isRegularFile) corrupt()
        return FileInputStream(file).use { input ->
            val size = input.channel.size()
            if (size !in 1L..MAX_SETTINGS_DOCUMENT_BYTES.toLong()) corrupt()
            val bytes = ByteArray(size.toInt())
            var offset = 0
            while (offset < bytes.size) {
                val count = input.read(bytes, offset, bytes.size - offset)
                if (count <= 0) corrupt()
                offset += count
            }
            if (input.read() != -1) corrupt()
            bytes
        }
    }
    override fun replace(document: ByteArray) {
        val file = target()
        val temporary = Files.createTempFile(directory.toPath(), ".settings-", ".tmp",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        try {
            FileOutputStream(temporary.toFile()).use { output -> output.write(document); output.fd.sync() }
            // No non-atomic fallback. Unsupported atomic move is a storage error.
            Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { try { Files.deleteIfExists(temporary) } catch (_: Exception) { } }
    }
    private fun safe(file: File) { if (file.absoluteFile != file.canonicalFile) unavailable() }
}
