package cn.mafei.cmp.common.core

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.localtime_r
import platform.posix.strftime
import platform.posix.time_tVar
import platform.posix.tm

@OptIn(ExperimentalForeignApi::class)
internal actual fun formatLocalDateTimePlatform(epochMilliseconds: Long, includeTime: Boolean): String = memScoped {
    val seconds = alloc<time_tVar>()
    seconds.value = epochMilliseconds / 1000L
    val local = alloc<tm>()
    if (localtime_r(seconds.ptr, local.ptr) == null) return@memScoped ""
    val buffer = allocArray<ByteVar>(20)
    val pattern = if (includeTime) "%Y-%m-%d %H:%M" else "%Y-%m-%d"
    if (strftime(buffer, 20.convert(), pattern, local.ptr) == 0UL) "" else buffer.toKString()
}
