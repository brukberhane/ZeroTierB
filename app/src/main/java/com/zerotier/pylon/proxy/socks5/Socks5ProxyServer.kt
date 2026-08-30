package com.zerotier.pylon.proxy.socks5

import android.os.SystemClock
import android.util.Log
import com.zerotier.pylon.data.model.PylonNetwork
import com.zerotier.pylon.proxy.ProxyConnection
import com.zerotier.pylon.proxy.ProxyRelay
import com.zerotier.pylon.proxy.ProxyRulesEngine
import com.zerotier.pylon.proxy.RouteResolver
import com.zerotier.pylon.proxy.dns.DnsResolver
import com.zerotier.sockets.ZeroTierSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class Socks5ProxyServer(
    private val port: Int,
    private val routeResolver: RouteResolver,
    private val dnsResolver: DnsResolver,
    private val rulesEngine: ProxyRulesEngine,
    private val networkLookup: (Long?) -> PylonNetwork?,
    private val onDied: () -> Unit = {},
    private val onAccept: () -> Unit = {},
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val lastAcceptElapsed = AtomicLong(0)

    val listenPort: Int get() = port
    val lastAcceptAtElapsed: Long get() = lastAcceptElapsed.get()
    val isListening: Boolean
        get() {
            val server = serverSocket
            return running.get() && server != null && server.isBound && !server.isClosed
        }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        serverSocket = ServerSocket()
        serverSocket?.reuseAddress = true
        serverSocket?.bind(InetSocketAddress("127.0.0.1", port))
        acceptJob = scope.launch {
            val server = serverSocket ?: return@launch
            while (isActive && running.get()) {
                runCatching {
                    val client = server.accept()
                    lastAcceptElapsed.set(SystemClock.elapsedRealtime())
                    onAccept()
                    launch {
                        runCatching { Socks5Session(client, routeResolver, dnsResolver, rulesEngine, networkLookup).handle() }
                            .onFailure { Log.w(TAG, "socks5 session failed", it) }
                    }
                }.onFailure {
                    if (running.get()) {
                        Log.w(TAG, "accept failed", it)
                        if (running.compareAndSet(true, false)) {
                            onDied()
                        }
                    }
                }
            }
        }
        Log.i(TAG, "SOCKS5 proxy listening on 127.0.0.1:$port")
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    companion object {
        private const val TAG = "Socks5ProxyServer"
    }
}

private class Socks5Session(
    private val client: Socket,
    private val routeResolver: RouteResolver,
    private val dnsResolver: DnsResolver,
    private val rulesEngine: ProxyRulesEngine,
    private val networkLookup: (Long?) -> PylonNetwork?,
) {
    fun handle() {
        val input = client.getInputStream()
        val output = client.getOutputStream()
        if (input.read() != 0x05) return
        val methodCount = input.read()
        repeat(methodCount) { input.read() }
        output.write(byteArrayOf(0x05, 0x00))

        val header = ByteArray(4)
        readFully(input, header)
        if (header[1].toInt() != 0x01) {
            reply(output, 0x07)
            return
        }
        val (host, port) = readAddress(input, header[3].toInt())
        val addresses = dnsResolver.resolve(host)
        val decision = routeResolver.resolveHost(host, addresses)
        val network = networkLookup(decision.networkId)
        if (!rulesEngine.isAllowed(host, port, network, decision) || decision.block) {
            reply(output, 0x02)
            return
        }
        val remote = if (decision.useZeroTier) {
            ProxyConnection.fromZeroTierSocket(ZeroTierSocket(host, port))
        } else {
            val socket = Socket().apply { connect(InetSocketAddress(host, port), 15_000) }
            ProxyConnection.fromSocket(socket)
        }
        reply(output, 0x00)
        ProxyRelay.relay(client, remote)
    }

    private fun readAddress(input: InputStream, atyp: Int): Pair<String, Int> {
        return when (atyp) {
            0x01 -> {
                val addr = ByteArray(4)
                readFully(input, addr)
                val host = "${addr[0].toInt() and 0xFF}.${addr[1].toInt() and 0xFF}.${addr[2].toInt() and 0xFF}.${addr[3].toInt() and 0xFF}"
                host to readPort(input)
            }
            0x03 -> {
                val len = input.read()
                val domain = ByteArray(len)
                readFully(input, domain)
                String(domain) to readPort(input)
            }
            0x04 -> {
                val addr = ByteArray(16)
                readFully(input, addr)
                val host = buildString {
                    for (i in 0 until 8) {
                        val value = ((addr[i * 2].toInt() and 0xFF) shl 8) or (addr[i * 2 + 1].toInt() and 0xFF)
                        append(Integer.toHexString(value))
                        if (i < 7) append(':')
                    }
                }
                host to readPort(input)
            }
            else -> error("unsupported atyp $atyp")
        }
    }

    private fun readPort(input: InputStream): Int {
        val bytes = ByteArray(2)
        readFully(input, bytes)
        return ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read <= 0) error("unexpected eof")
            offset += read
        }
    }

    private fun reply(output: OutputStream, code: Int) {
        output.write(byteArrayOf(0x05, code.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        output.flush()
    }
}
