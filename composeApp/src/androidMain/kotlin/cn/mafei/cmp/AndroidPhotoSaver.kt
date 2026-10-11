package cn.mafei.cmp

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import cn.mafei.cmp.core.domain.MediaDownload
import cn.mafei.cmp.feature.photo.PhotoSaver
import cn.mafei.cmp.feature.photo.PhotoSaveResult
import kotlinx.coroutines.*

internal class AndroidPhotoSaver(context: Context) : PhotoSaver {
    private val resolver = context.applicationContext.contentResolver
    override val supported = true
    override suspend fun save(download: MediaDownload): PhotoSaveResult = withContext(Dispatchers.IO) {
        val name = download.fileName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "photo.jpg" }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "image/jpeg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MaFei")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val destination = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext PhotoSaveResult.Failed
        var committed = false
        try {
            requireNotNull(resolver.openOutputStream(destination)).use { writePhoto(download, it) }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            check(resolver.update(destination, values, null, null) > 0)
            committed = true
            PhotoSaveResult.Saved
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { PhotoSaveResult.Failed }
        finally { if (!committed) try { resolver.delete(destination, null, null) } catch (_: Exception) { /* Preserve cancellation/failure. */ } }
    }
}
