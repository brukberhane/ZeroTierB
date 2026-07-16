package com.zerotier.pylon.proxy.http

import android.util.Log
import com.zerotier.pylon.data.model.PylonNetwork
import com.zerotier.pylon.proxy.ProxyRulesEngine
import com.zerotier.pylon.proxy.RouteDecision
import com.zerotier.pylon.proxy.RouteResolver
import com.zerotier.pylon.proxy.dns.DnsResolver
import com.zerotier.pylon.proxy.ProxyConnection
import com.zerotier.sockets.ZeroTierSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class HttpProxyServer(
    private val port: Int,
    private val routeResolver: RouteResolver,
    private val dnsResolver: DnsResolver,
    private val rulesEngine: ProxyRulesEngine,
    private val networkLookup: (Long?) -> PylonNetwork?,
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    val listenPort: Int get() = port

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
                    if (running.get()) Log.w(TAG, "accept failed", it)
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
        relay(client, remote)
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
        relay(client, remote)
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

    private fun relay(client: Socket, remote: ProxyConnection) {
        val t1 = Thread {
            runCatching { pump(client.getInputStream(), remote.output) }
            runCatching { remote.close() }
        }
        val t2 = Thread {
            runCatching { pump(remote.input, client.getOutputStream()) }
            runCatching { client.shutdownOutput() }
        }
        t1.start()
        t2.start()
        t1.join()
        t2.join()
        runCatching { client.close() }
        runCatching { remote.close() }
    }

    private fun pump(input: java.io.InputStream, output: OutputStream) {
        val buffer = ByteArray(16_384)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: java.io.IOException) {
            // Either side closed the connection during relay.
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
