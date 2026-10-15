@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package cn.mafei.cmp

import cn.mafei.cmp.core.domain.MediaDownload
import cn.mafei.cmp.feature.photo.PhotoSaver
import cn.mafei.cmp.feature.photo.PhotoSaveResult
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.ArkTS.ArkTS_Napi_NativeModule.*

@Serializable
private data class PhotoSavePayload(val id: Long, val url: String, val fileName: String, val headers: Map<String, String>)

/** One explicit user save -> one host call. No polling, native UI or second HTTP catalog. */
internal object OhosPhotoSaver : PhotoSaver {
    private var environment: napi_env? = null
    private var callback: napi_ref? = null
    private var sequence = 0L
    private var pendingId = 0L
    private var pending: CompletableDeferred<PhotoSaveResult>? = null
    private val mutex = Mutex()
    override val supported: Boolean get() = callback != null

    fun register(env: napi_env?, handler: napi_value?): Boolean = memScoped {
        release()
        if (env == null || handler == null) return@memScoped false
        val reference = alloc<napi_refVar>()
        if (napi_create_reference(env, handler, 1u, reference.ptr) != napi_ok) return@memScoped false
        callback = reference.value
        environment = env
        true
    }

    fun release() {
        environment?.let { env -> callback?.let { napi_delete_reference(env, it) } }
        callback = null; environment = null
        pending?.complete(PhotoSaveResult.Cancelled)
    }

    override suspend fun save(download: MediaDownload): PhotoSaveResult = withContext(Dispatchers.Main) {
        mutex.withLock {
            val env = environment ?: return@withLock PhotoSaveResult.Failed
            val handler = callback ?: return@withLock PhotoSaveResult.Failed
            val id = ++sequence
            val completion = CompletableDeferred<PhotoSaveResult>()
            pendingId = id; pending = completion
            try {
                val accepted = memScoped {
                    val function = alloc<napi_valueVar>()
                    val receiver = alloc<napi_valueVar>()
                    val argument = alloc<napi_valueVar>()
                    val result = alloc<napi_valueVar>()
                    val payload = Json.encodeToString(PhotoSavePayload.serializer(), PhotoSavePayload(id, download.url, download.fileName, download.headers))
                    if (napi_get_reference_value(env, handler, function.ptr) != napi_ok || napi_get_global(env, receiver.ptr) != napi_ok ||
                        napi_create_string_utf8(env, payload, payload.encodeToByteArray().size.toULong(), argument.ptr) != napi_ok) return@memScoped false
                    if (napi_call_function(env, receiver.value, function.value, 1uL, argument.ptr, result.ptr) != napi_ok) {
                        napi_get_and_clear_last_exception(env, result.ptr)
                        return@memScoped false
                    }
                    true
                }
                if (!accepted) PhotoSaveResult.Failed else withTimeoutOrNull(300_000) { completion.await() } ?: PhotoSaveResult.Failed
            } finally { if (pendingId == id) { pending = null; pendingId = 0 } }
        }
    }

    fun complete(id: Long, result: Int) {
        if (id != pendingId) return
        pending?.complete(when (result) { 0 -> PhotoSaveResult.Saved; 1 -> PhotoSaveResult.Cancelled; else -> PhotoSaveResult.Failed })
    }
}

@CName("RegisterOhosPhotoSave")
fun RegisterOhosPhotoSave(env: napi_env?, handler: napi_value?): Boolean = OhosPhotoSaver.register(env, handler)

@CName("CompleteOhosPhotoSave")
fun CompleteOhosPhotoSave(id: Long, result: Int) = OhosPhotoSaver.complete(id, result)

@CName("ReleaseOhosPhotoSave")
fun ReleaseOhosPhotoSave() = OhosPhotoSaver.release()
