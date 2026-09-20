@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp.providers.jellyfin.discovery

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.SOCK_DGRAM
import platform.posix.SO_BROADCAST
import platform.posix.SO_RCVBUF
import platform.posix.SO_RCVTIMEO
import platform.posix.SO_SNDBUF
import platform.posix.SOL_SOCKET
import platform.posix.bind
import platform.posix.close
import platform.posix.memset
import platform.posix.recv
import platform.posix.sendto
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.timeval

internal actual fun platformReceiveLocalJellyfinResponses(
    timeoutMs: Int,
    maxServers: Int,
): List<String> = memScoped {
    val descriptor = socket(AF_INET, SOCK_DGRAM, 0)
    check(descriptor >= 0) { "Unable to create Jellyfin discovery socket" }
    try {
        val yes = alloc<IntVar>().apply { value = 1 }
        check(setsockopt(descriptor, SOL_SOCKET, SO_BROADCAST, yes.ptr, sizeOf<IntVar>().convert()) == 0) {
            "Unable to enable Jellyfin discovery broadcast"
        }
        val bufferSize = alloc<IntVar>().apply { value = 100_000 }
        setsockopt(descriptor, SOL_SOCKET, SO_RCVBUF, bufferSize.ptr, sizeOf<IntVar>().convert())
        setsockopt(descriptor, SOL_SOCKET, SO_SNDBUF, bufferSize.ptr, sizeOf<IntVar>().convert())
        val timeout = alloc<timeval>().apply {
            tv_sec = (timeoutMs / 1_000).convert()
            tv_usec = ((timeoutMs % 1_000) * 1_000).convert()
        }
        check(setsockopt(descriptor, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert()) == 0) {
            "Unable to configure Jellyfin discovery timeout"
        }

        val local = alloc<sockaddr_in>()
        memset(local.ptr, 0, sizeOf<sockaddr_in>().convert())
        local.sin_family = AF_INET.convert()
        local.sin_addr.s_addr = 0u
        local.sin_port = networkPort(LOCAL_DISCOVERY_BIND_PORT)
        check(bind(descriptor, local.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0) {
            "Unable to bind Jellyfin discovery socket"
        }

        val broadcast = alloc<sockaddr_in>()
        memset(broadcast.ptr, 0, sizeOf<sockaddr_in>().convert())
        broadcast.sin_family = AF_INET.convert()
        broadcast.sin_addr.s_addr = 0xffffffffu
        broadcast.sin_port = networkPort(LOCAL_DISCOVERY_PORT)
        val request = LOCAL_DISCOVERY_MESSAGE.encodeToByteArray()
        val sent = request.usePinned { pinned ->
            sendto(
                descriptor,
                pinned.addressOf(0),
                request.size.convert(),
                0,
                broadcast.ptr.reinterpret(),
                sizeOf<sockaddr_in>().convert(),
            )
        }
        check(sent == request.size.toLong()) { "Unable to send Jellyfin discovery broadcast" }

        buildList {
            repeat(maxServers) {
                val bytes = ByteArray(LOCAL_DISCOVERY_RECEIVE_BUFFER_BYTES)
                val count = bytes.usePinned { pinned ->
                    recv(descriptor, pinned.addressOf(0), bytes.size.convert(), 0).toInt()
                }
                if (count <= 0) return@buildList
                add(bytes.decodeToString(0, count))
            }
        }
    } finally {
        close(descriptor)
    }
}

private fun networkPort(port: Int): UShort = (((port and 0xff) shl 8) or (port ushr 8)).toUShort()
