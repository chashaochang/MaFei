package cn.mafei.cmp.providers.jellyfin.discovery

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

internal actual fun platformReceiveLocalJellyfinResponses(
    timeoutMs: Int,
    maxServers: Int,
): List<String> = receiveJvmLocalJellyfinResponses(timeoutMs, maxServers)

private fun receiveJvmLocalJellyfinResponses(timeoutMs: Int, maxServers: Int): List<String> {
    val socket = DatagramSocket(null)
    try {
        socket.reuseAddress = false
        socket.broadcast = true
        socket.receiveBufferSize = 100_000
        socket.sendBufferSize = 100_000
        socket.soTimeout = timeoutMs
        socket.bind(InetSocketAddress("0.0.0.0", LOCAL_DISCOVERY_BIND_PORT))

        val request = LOCAL_DISCOVERY_MESSAGE.encodeToByteArray()
        socket.send(
            DatagramPacket(
                request,
                request.size,
                InetAddress.getByName("255.255.255.255"),
                LOCAL_DISCOVERY_PORT,
            ),
        )

        return buildList {
            repeat(maxServers) {
                val bytes = ByteArray(LOCAL_DISCOVERY_RECEIVE_BUFFER_BYTES)
                val packet = DatagramPacket(bytes, bytes.size)
                try {
                    socket.receive(packet)
                    add(bytes.decodeToString(0, packet.length))
                } catch (_: SocketTimeoutException) {
                    return@buildList
                }
            }
        }
    } finally {
        socket.close()
    }
}
