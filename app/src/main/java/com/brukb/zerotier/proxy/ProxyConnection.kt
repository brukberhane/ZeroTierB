package com.brukb.zerotier.proxy

import com.zerotier.sockets.ZeroTierSocket
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class ProxyConnection(
    val input: InputStream,
    val output: OutputStream,
    private val outputShutdown: () -> Unit,
    private val closer: () -> Unit,
    private val readTimeoutSetter: (Int) -> Unit = {},
    /**
     * ZeroTierInputStream maps SO_RCVTIMEO to EOF (-1), so the relay must not
     * use a short poll timeout on that stream (it would look like a close).
     */
    val recvTimeoutIsEof: Boolean = false,
) {
    fun shutdownOutput() {
        runCatching { outputShutdown() }
    }

    fun close() {
        runCatching { closer() }
    }

    fun setReadTimeout(ms: Int) {
        runCatching { readTimeoutSetter(ms) }
    }

    companion object {
        fun fromSocket(socket: Socket): ProxyConnection {
            return ProxyConnection(
                socket.getInputStream(),
                socket.getOutputStream(),
                { socket.shutdownOutput() },
                { socket.close() },
                { socket.soTimeout = it },
            )
        }

        fun fromZeroTierSocket(socket: ZeroTierSocket): ProxyConnection {
            return ProxyConnection(
                socket.inputStream,
                socket.outputStream,
                { socket.shutdownOutput() },
                { socket.close() },
                { socket.setSoTimeout(it) },
                recvTimeoutIsEof = true,
            )
        }
    }
}
