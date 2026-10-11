@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package cn.mafei.cmp

import cn.mafei.cmp.core.domain.MediaDownload
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.feature.photo.PhotoSaver
import cn.mafei.cmp.feature.photo.PhotoSaveResult
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import platform.Photos.*
import platform.darwin.NSObject
import kotlin.coroutines.resume

/** Save the original file (including EXIF), never a re-encoded preview bitmap. */
internal object IosPhotoSaver : PhotoSaver {
    override val supported = true
    override suspend fun save(download: MediaDownload): PhotoSaveResult = suspendCancellableCoroutine { completion ->
        fun finish(result: PhotoSaveResult) { if (completion.isActive) completion.resume(result) }
        val original = HttpUrl.parse(download.url)
        var redirects = 0
        val delegate = object : NSObject(), NSURLSessionTaskDelegateProtocol {
            override fun URLSession(session: NSURLSession, task: NSURLSessionTask,
                willPerformHTTPRedirection: NSHTTPURLResponse, newRequest: NSURLRequest,
                completionHandler: (NSURLRequest?) -> Unit) {
                val next = newRequest.URL?.absoluteString?.let(HttpUrl::parseOrNull)
                if (!completion.isActive || ++redirects > 4 || next == null ||
                    next.scheme != original.scheme || next.host != original.host || next.port != original.port) {
                    completionHandler(null)
                    return
                }
                val redirected = NSMutableURLRequest(uRL = requireNotNull(newRequest.URL))
                download.headers.forEach { (name, value) -> redirected.setValue(value, forHTTPHeaderField = name) }
                completionHandler(redirected)
            }
        }
        val session = NSURLSession.sessionWithConfiguration(NSURLSessionConfiguration.ephemeralSessionConfiguration(), delegate, null)
        completion.invokeOnCancellation { session.invalidateAndCancel() }
        PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
            if (!completion.isActive) { session.invalidateAndCancel(); return@requestAuthorizationForAccessLevel }
            if (status != PHAuthorizationStatusAuthorized && status != PHAuthorizationStatusLimited) {
                session.invalidateAndCancel(); finish(PhotoSaveResult.Cancelled); return@requestAuthorizationForAccessLevel
            }
            val request = NSMutableURLRequest(uRL = requireNotNull(NSURL(string = download.url)))
            download.headers.forEach { (name, value) -> request.setValue(value, forHTTPHeaderField = name) }
            session.downloadTaskWithRequest(request) { temporary, response, error ->
                val http = response as? NSHTTPURLResponse
                if (temporary == null || error != null || http == null || http.statusCode !in 200L..299L || !completion.isActive) {
                    session.finishTasksAndInvalidate(); finish(PhotoSaveResult.Failed); return@downloadTaskWithRequest
                }
                val extension = download.fileName.substringAfterLast('.', "jpg").filter(Char::isLetterOrDigit).take(16).ifBlank { "jpg" }
                val saved = NSURL.fileURLWithPath(NSTemporaryDirectory() + "mafei-photo-" + NSUUID().UUIDString + "." + extension)
                val files = NSFileManager.defaultManager
                val size = temporary.path?.let { files.attributesOfItemAtPath(it, null)?.get(NSFileSize) as? NSNumber }
                    ?.longLongValue ?: -1L
                if (size !in 1L..MAX_ORIGINAL_BYTES || !completion.isActive) {
                    session.finishTasksAndInvalidate(); finish(PhotoSaveResult.Failed); return@downloadTaskWithRequest
                }
                if (!files.moveItemAtURL(temporary, saved, null)) {
                    session.finishTasksAndInvalidate(); finish(PhotoSaveResult.Failed); return@downloadTaskWithRequest
                }
                if (!completion.isActive) {
                    files.removeItemAtURL(saved, null)
                    session.invalidateAndCancel()
                    return@downloadTaskWithRequest
                }
                PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                    PHAssetChangeRequest.creationRequestForAssetFromImageAtFileURL(saved)
                }) { success, _ ->
                    files.removeItemAtURL(saved, null)
                    session.finishTasksAndInvalidate()
                    finish(if (success) PhotoSaveResult.Saved else PhotoSaveResult.Failed)
                }
            }.resume()
        }
    }

    private const val MAX_ORIGINAL_BYTES = 100 * 1024 * 1024L
}
