package com.brukb.zerotier.proxy.http

import android.util.Log
import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.proxy.ProxyConnection
import com.brukb.zerotier.proxy.RouteDecision
import com.brukb.zerotier.proxy.RouteResolver
import com.brukb.zerotier.proxy.dns.DnsResolver
import com.zerotier.sockets.ZeroTierNative
import com.zerotier.sockets.ZeroTierSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
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
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    val boundPort: Int get() = serverSocket?.localPort ?: -1

    fun start() {
        if (!running.compareAndSet(false, true)) return
        // A previous service instance may have left its listener bound
        // (stopProxy stall); close it so only one accept loop is live.
        activeServer?.takeUnless { it === this }?.stop()
        activeServer = this
        serverSocket = ServerSocket()
        serverSocket?.reuseAddress = true
        serverSocket?.bind(InetSocketAddress("127.0.0.1", port))
        val actualPort = serverSocket?.localPort ?: port
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
        Log.i(TAG, "HTTP proxy listening on 127.0.0.1:$actualPort")
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
        if (activeServer === this) activeServer = null
        Log.i(TAG, "HTTP proxy stopped")
    }

    companion object {
        private const val TAG = "HttpProxyServer"
        private var activeServer: HttpProxyServer? = null
    }
}

class HttpProxySession(
    private val client: Socket,
    private val routeResolver: RouteResolver,
    private val dnsResolver: DnsResolver,
) {
    fun handle() {
        client.soTimeout = 60_000
        // Parse headers from a buffered byte stream and relay from the SAME
        // stream: a BufferedReader here would silently swallow request-body
        // bytes it had read ahead, corrupting every POST/PUT (seen in the
        // wild as Calibre-Web form login failing through the proxy only).
        val input = BufferedInputStream(client.getInputStream(), BUFFERED_STREAM_BYTES)
        val headerBytes = readHeaderBlock(input) ?: return
        val headerLines = String(headerBytes, Charsets.ISO_8859_1).split("\r\n")
        val requestLine = headerLines.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 3) return
        val method = parts[0]
        val target = parts[1]
        when (method.uppercase()) {
            "CONNECT" -> handleConnect(target, input)
            else -> handlePlainHttp(method, target, headerLines, input)
        }
    }

    /**
     * Read exactly up to and including the CRLF-CRLF header terminator —
     * never one byte beyond it, so the request body stays intact in [input].
     */
    private fun readHeaderBlock(input: InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream(1024)
        var state = 0
        while (true) {
            val b = input.read()
            if (b == -1) return null
            out.write(b)
            state = when (state) {
                0 -> if (b == '\r'.code) 1 else 0
                1 -> if (b == '\n'.code) 2 else if (b == '\r'.code) 1 else 0
                2 -> if (b == '\r'.code) 3 else 0
                3 -> if (b == '\n'.code) 4 else if (b == '\r'.code) 1 else 0
                else -> 0
            }
            if (state == 4) return out.toByteArray()
            if (out.size() >= MAX_HEADER_BYTES) return null
        }
    }

    private fun handleConnect(target: String, input: InputStream) {
        val (host, port) = parseHostPort(target, 443)
        val decision = resolveDecision(host)
        logRoute(host, port, decision)
        val remote = try {
            openConnection(host, port, decision)
        } catch (e: Exception) {
            Log.w(TAG, "connect failed $host:$port", e)
            writeResponse(client.getOutputStream(), 502, "Bad Gateway")
            return
        }
        writeRaw(client.getOutputStream(), "HTTP/1.1 200 Connection Established\r\n\r\n")
        relay(client, input, remote)
    }

    private fun handlePlainHttp(
        method: String,
        target: String,
        headerLines: List<String>,
        input: InputStream,
    ) {
        val url = java.net.URL(if (target.startsWith("http")) target else "http://$target")
        val host = url.host
        val port = if (url.port > 0) url.port else 80
        val path = url.path.ifEmpty { "/" } + (url.query?.let { "?$it" } ?: "")
        val decision = resolveDecision(host)
        logRoute(host, port, decision)
        val remote = try {
            openConnection(host, port, decision)
        } catch (e: Exception) {
            Log.w(TAG, "connect failed $host:$port", e)
            writeResponse(client.getOutputStream(), 502, "Bad Gateway")
            return
        }
        val output = remote.output
        output.write("$method $path HTTP/1.1\r\n".toByteArray())
        for (line in headerLines.subList(1, headerLines.size)) {
            if (line.isEmpty()) break
            output.write("$line\r\n".toByteArray())
        }
        output.write("\r\n".toByteArray())
        output.flush()
        relay(client, input, remote)
    }

    private fun resolveDecision(host: String): RouteDecision {
        val ipDecision = runCatching { routeResolver.resolveIpString(host) }.getOrNull()
        if (ipDecision != null && ipDecision.useZeroTier) {
            return ipDecision
        }
        val addresses = dnsResolver.resolve(host)
        return routeResolver.resolveHost(host, addresses)
    }

    private fun openConnection(host: String, port: Int, decision: RouteDecision): ProxyConnection {
        return if (decision.useZeroTier) {
            val netId = decision.networkId ?: 0L
            val online = ZeroTierNative.zts_node_is_online()
            val ready = if (netId != 0L) ZeroTierNative.zts_net_transport_is_ready(netId) else -1
            Log.i(TAG, "zt connect $host:$port nodeOnline=$online transportReady=$ready")
            // zts_connect takes an IP string only — resolve hostnames first.
            val ip = ztConnectAddress(host, decision)
                ?: throw IOException("No ZeroTier address for $host")
            val family = if (ip.contains(':')) {
                ZeroTierNative.ZTS_AF_INET6
            } else {
                ZeroTierNative.ZTS_AF_INET
            }
            val socket = ZeroTierSocket(family, ZeroTierNative.ZTS_SOCK_STREAM, 0)
            try {
                socket.connect(InetAddress.getByName(ip), port, ZT_CONNECT_TIMEOUT_MS)
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw e
            }
            ProxyConnection.fromZeroTierSocket(socket)
        } else {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 15_000)
            ProxyConnection.fromSocket(socket)
        }
    }

    private fun ztConnectAddress(host: String, decision: RouteDecision): String? {
        val literal = host.all { it.isDigit() || it == '.' } || host.contains(':')
        if (literal) return host
        val addresses = dnsResolver.resolve(host)
        return addresses.firstOrNull { addr ->
            val ip = addr.hostAddress ?: return@firstOrNull false
            routeResolver.resolveIpString(ip).let { it.useZeroTier && it.networkId == decision.networkId }
        }?.hostAddress
    }

    private fun relay(client: Socket, clientInput: InputStream, remote: ProxyConnection) {
        // Half-close only during relay: closing the ZT socket while the sibling
        // pump is blocked in zts_bsd_read aborts the process (destroyed mutex).
        val t1 = Thread {
            runCatching { pump(clientInput, remote.output) }
            remote.shutdownOutput()
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
        remote.close()
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
        } catch (_: IOException) {
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

    private fun writeRaw(output: OutputStream, text: String) {
        output.write(text.toByteArray())
        output.flush()
    }

    private fun writeResponse(output: OutputStream, code: Int, message: String) {
        val body = message
        writeRaw(
            output,
            "HTTP/1.1 $code $message\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n\r\n$body",
        )
    }

    private fun logRoute(host: String, port: Int, decision: RouteDecision) {
        Log.i(
            TAG,
            "route $host:$port -> useZeroTier=${decision.useZeroTier} " +
                "net=${decision.networkId?.let { java.lang.Long.toUnsignedString(it, 16) }} " +
                "reason=${decision.reason}",
        )
    }

    companion object {
        private const val TAG = "HttpProxySession"
        private const val ZT_CONNECT_TIMEOUT_MS = 10_000
        private const val MAX_HEADER_BYTES = 16 * 1024
        private const val BUFFERED_STREAM_BYTES = 16 * 1024
    }
}
