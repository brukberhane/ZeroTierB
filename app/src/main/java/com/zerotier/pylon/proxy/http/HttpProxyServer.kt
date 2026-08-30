package com.zerotier.pylon.proxy.http

import android.util.Log
import com.zerotier.pylon.data.model.PylonNetwork
import com.zerotier.pylon.proxy.ProxyConnection
import com.zerotier.pylon.proxy.ProxyRelay
import com.zerotier.pylon.proxy.ProxyRulesEngine
import com.zerotier.pylon.proxy.RouteDecision
import com.zerotier.pylon.proxy.RouteResolver
import com.zerotier.pylon.proxy.dns.DnsResolver
import com.zerotier.sockets.ZeroTierSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class HttpProxyServer(
    private val port: Int,
    private val routeResolver: RouteResolver,
    private val dnsResolver: DnsResolver,
    private val rulesEngine: ProxyRulesEngine,
    private val networkLookup: (Long?) -> PylonNetwork?,
    private val onDied: () -> Unit = {},
    private val onAccept: () -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
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
                    lastAcceptElapsed.set(android.os.SystemClock.elapsedRealtime())
                    onAccept()
                    launch {
                        runCatching {
                            HttpProxySession(
                                client,
                                routeResolver,
                                dnsResolver,
                                rulesEngine,
                                networkLookup,
                            ).handle()
                        }.onFailure {
                            Log.w(TAG, "session failed", it)
                            runCatching { client.close() }
                        }
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
        Log.i(TAG, "HTTP proxy listening on 127.0.0.1:$port")
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
        Log.i(TAG, "HTTP proxy stopped")
    }

    companion object {
        private const val TAG = "HttpProxyServer"
    }
}

class HttpProxySession(
    private val client: Socket,
    private val routeResolver: RouteResolver,
    private val dnsResolver: DnsResolver,
    private val rulesEngine: ProxyRulesEngine,
    private val networkLookup: (Long?) -> PylonNetwork?,
) {
    fun handle() {
        client.soTimeout = 60_000
        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 3) return
        val method = parts[0]
        val target = parts[1]
        when (method.uppercase()) {
            "CONNECT" -> handleConnect(target)
            else -> handlePlainHttp(method, target, reader)
        }
    }

    private fun handleConnect(target: String) {
        val (host, port) = parseHostPort(target, 443)
        val decision = resolveDecision(host)
        val network = networkLookup(decision.networkId)
        if (!rulesEngine.isAllowed(host, port, network, decision)) {
            writeResponse(client.getOutputStream(), 403, "Forbidden")
            return
        }
        if (decision.block) {
            writeResponse(client.getOutputStream(), 403, "Forbidden")
            return
        }
        val remote = openConnection(host, port, decision)
        writeRaw(client.getOutputStream(), "HTTP/1.1 200 Connection Established\r\n\r\n")
        ProxyRelay.relay(client, remote)
    }

    private fun handlePlainHttp(method: String, target: String, reader: BufferedReader) {
        val url = java.net.URL(if (target.startsWith("http")) target else "http://$target")
        val host = url.host
        val port = if (url.port > 0) url.port else 80
        val path = url.path.ifEmpty { "/" } + (url.query?.let { "?$it" } ?: "")
        val decision = resolveDecision(host)
        val network = networkLookup(decision.networkId)
        if (!rulesEngine.isAllowed(host, port, network, decision) || decision.block) {
            writeResponse(client.getOutputStream(), 403, "Forbidden")
            return
        }
        val remote = openConnection(host, port, decision)
        val output = remote.output
        output.write("$method $path HTTP/1.1\r\n".toByteArray())
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            if (line.isNullOrEmpty()) break
            output.write("$line\r\n".toByteArray())
        }
        output.write("\r\n".toByteArray())
        output.flush()
        ProxyRelay.relay(client, remote)
    }

    private fun resolveDecision(host: String): RouteDecision {
        val ipDecision = runCatching { routeResolver.resolveIpString(host) }.getOrNull()
        if (ipDecision != null && (ipDecision.useZeroTier || ipDecision.block)) {
            return ipDecision
        }
        val addresses = dnsResolver.resolve(host)
        return routeResolver.resolveHost(host, addresses)
    }

    private fun openConnection(host: String, port: Int, decision: RouteDecision): ProxyConnection {
        return if (decision.useZeroTier) {
            ProxyConnection.fromZeroTierSocket(ZeroTierSocket(host, port))
        } else {
            val socket = Socket()
            socket.connect(java.net.InetSocketAddress(host, port), 15_000)
            ProxyConnection.fromSocket(socket)
        }
    }

    private fun parseHostPort(target: String, defaultPort: Int): Pair<String, Int> {
        if (target.startsWith("[")) {
            val end = target.indexOf(']')
            val host = target.substring(1, end)
            val port = target.substring(end + 1).removePrefix(":").toIntOrNull() ?: defaultPort
            return host to port
        }
        val colon = target.lastIndexOf(':')
        return if (colon > 0 && target.count { it == ':' } == 1) {
            target.substring(0, colon) to (target.substring(colon + 1).toIntOrNull() ?: defaultPort)
        } else {
            target to defaultPort
        }
    }

    private fun writeResponse(output: OutputStream, code: Int, message: String) {
        writeRaw(output, "HTTP/1.1 $code $message\r\nContent-Length: 0\r\n\r\n")
    }

    private fun writeRaw(output: OutputStream, text: String) {
        output.write(text.toByteArray())
        output.flush()
    }
}
