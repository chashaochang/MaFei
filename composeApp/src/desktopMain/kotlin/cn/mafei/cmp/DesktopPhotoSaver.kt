package cn.mafei.cmp

import cn.mafei.cmp.core.domain.MediaDownload
import cn.mafei.cmp.feature.photo.PhotoSaver
import cn.mafei.cmp.feature.photo.PhotoSaveResult
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.JFileChooser

internal object DesktopPhotoSaver : PhotoSaver {
    override val supported = true
    override suspend fun save(download: MediaDownload): PhotoSaveResult {
        val destination = withContext(Dispatchers.Main) {
            val chooser = JFileChooser().apply { selectedFile = java.io.File(download.fileName.substringAfterLast('/').substringAfterLast('\\')) }
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) null
            else chooser.selectedFile.toPath().takeIf {
                !Files.exists(it) || javax.swing.JOptionPane.showConfirmDialog(null, "替换已有照片？", "保存照片",
                    javax.swing.JOptionPane.OK_CANCEL_OPTION) == javax.swing.JOptionPane.OK_OPTION
            }
        } ?: return PhotoSaveResult.Cancelled
        return withContext(Dispatchers.IO) {
            // A failed/cancelled download must not damage an existing user file.
            val staged = Files.createTempFile(destination.toAbsolutePath().parent, ".mafei-photo-", ".part")
            try {
                Files.newOutputStream(staged).use { writePhoto(download, it) }
                coroutineContext.ensureActive()
                Files.move(staged, destination, StandardCopyOption.REPLACE_EXISTING)
                PhotoSaveResult.Saved
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { PhotoSaveResult.Failed }
            finally { Files.deleteIfExists(staged) }
        }
    }
}
