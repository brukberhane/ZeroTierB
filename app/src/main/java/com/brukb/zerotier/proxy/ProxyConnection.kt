package com.brukb.zerotier.proxy

import com.zerotier.sockets.ZeroTierSocket
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class ProxyConnection(
    val input: InputStream,
    val output: OutputStream,
    private val closer: () -> Unit,
) {
    fun close() {
        runCatching { closer() }
    }

    companion object {
        fun fromSocket(socket: Socket): ProxyConnection {
            return ProxyConnection(socket.getInputStream(), socket.getOutputStream()) {
                socket.close()
            }
        }

        fun fromZeroTierSocket(socket: ZeroTierSocket): ProxyConnection {
            return ProxyConnection(socket.inputStream, socket.outputStream) {
                socket.close()
            }
        }
    }
}
