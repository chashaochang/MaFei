package cn.mafei.cmp.storage

import android.content.Context
import android.system.Os
import android.system.ErrnoException
import android.system.OsConstants
import cn.mafei.cmp.core.storage_api.SettingsStorage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** One instance per app runtime. API 24-compatible POSIX rename, not SharedPreferences multi-key edits. */
fun createAndroidSettings(context: Context): SettingsStorage = DocumentSettingsStorage(
    AndroidSettingsDocument(File(context.applicationContext.filesDir, "mafei-settings-v1").canonicalFile),
)

private class AndroidSettingsDocument(private val directory: File) : SettingsDocumentBackend {
    private fun target(): File {
        if (!directory.exists() && !directory.mkdirs() || !directory.isDirectory) unavailable()
        safe(directory)
        Os.chmod(directory.path, 448)
        return File(directory, "settings.json").also(::safe)
    }
    override fun read(): ByteArray? {
        val file = target()
        val attributes = try { Os.lstat(file.path) } catch (failure: ErrnoException) {
            if (failure.errno == OsConstants.ENOENT) return null
            throw failure
        }
        if (!OsConstants.S_ISREG(attributes.st_mode)) corrupt()
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
        val temporary = File.createTempFile(".settings-", ".tmp", directory)
        try {
            Os.chmod(temporary.path, 384)
            FileOutputStream(temporary).use { output -> output.write(document); output.fd.sync() }
            // Same directory => same filesystem. A successful rename is the only commit point.
            Os.rename(temporary.path, file.path)
        } finally { if (temporary.exists()) temporary.delete() }
    }
    private fun safe(file: File) { if (file.absoluteFile != file.canonicalFile) unavailable() }
}
