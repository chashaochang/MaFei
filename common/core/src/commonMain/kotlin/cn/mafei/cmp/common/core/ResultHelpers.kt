package cn.mafei.cmp.common.core

import kotlinx.coroutines.CancellationException

/**
 * `runCatching` variant that never swallows [CancellationException] — required
 * wherever provider/data code converts failures into domain errors, so
 * structured cancellation keeps propagating while real failures are captured.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

/** Suspending counterpart of [runCatchingCancellable]; [block] may itself suspend. */
suspend inline fun <T> runCatchingSuspendCancellable(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
