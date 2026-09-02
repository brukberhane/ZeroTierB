package com.brukb.zerotier.proxy.http

import com.brukb.zerotier.log.AppLog
import com.brukb.zerotier.proxy.IpPrefix
import com.brukb.zerotier.proxy.ProxyConnection
import com.brukb.zerotier.proxy.ProxyDebugLog
import com.brukb.zerotier.proxy.ProxyRelay
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
    private val onDied: () -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val lastAcceptElapsed = java.util.concurrent.atomic.AtomicLong(0)

    val boundPort: Int get() = serverSocket?.localPort ?: -1
    val lastAcceptAtElapsed: Long get() = lastAcceptElapsed.get()
    val isListening: Boolean
        get() {
            val server = serverSocket
            return running.get() && server != null && server.isBound && !server.isClosed
        }

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
                    lastAcceptElapsed.set(android.os.SystemClock.elapsedRealtime())
                    ProxyDebugLog.i("accept src=${client.remoteSocketAddress}")
                    launch {
                        runCatching {
                            HttpProxySession(
                                client,
                                routeResolver,
                                dnsResolver,
                            ).handle()
                        }.onFailure {
                            AppLog.w(TAG, "session failed", it)
                            runCatching { client.close() }
                        }
                    }
                }.onFailure {
                    if (running.get()) {
                        AppLog.w(TAG, "accept failed", it)
                        if (running.compareAndSet(true, false)) {
                            onDied()
                        }
                    }
                }
            }
        }
        AppLog.i(TAG, "HTTP proxy listening on 127.0.0.1:$actualPort")
        ProxyDebugLog.i("listen 127.0.0.1:$actualPort")
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
        if (activeServer === this) activeServer = null
        AppLog.i(TAG, "HTTP proxy stopped")
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
        client.tcpNoDelay = true
        client.soTimeout = 60_000
        // Parse headers from a buffered byte stream and relay from the SAME
        // stream: a BufferedReader here would silently swallow request-body
        // bytes it had read ahead, corrupting every POST/PUT (seen in the
        // wild as Calibre-Web form login failing through the proxy only).
        val input = BufferedInputStream(client.getInputStream(), BUFFERED_STREAM_BYTES)
        // Keep-alive loop: every request gets its request line rewritten
        // (absolute -> origin form) and a fresh origin connection. Relaying
        // follow-up requests verbatim would send absolute-form lines the
        // origin cannot route (observed: auth retry -> 404 from Calibre-Web).
        while (true) {
            val headerBytes = readHeaderBlock(input) ?: return
            val headerLines = String(headerBytes, Charsets.ISO_8859_1).split("\r\n")
            val requestLine = headerLines.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 3) return
            val method = parts[0]
            val target = parts[1]
            val headers = headerLines.subList(1, headerLines.size).takeWhile { it.isNotEmpty() }
            val ua = headers.firstOrNull { it.startsWith("User-Agent:", ignoreCase = true) }
                ?.substringAfter(':')?.trim() ?: "-"
            AppLog.i(TAG, "request $method $target")
            if (method.equals("CONNECT", ignoreCase = true)) {
                handleConnect(target, input, ua)
                return
            }
            if (!handlePlainHttp(method, target, headers, input, ua)) {
                return
            }
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

    private fun handleConnect(target: String, input: InputStream, ua: String) {
        val (host, port) = parseHostPort(target, 443)
        val resolved = resolveTarget(host, port)
        logRoute(host, port, resolved.decision)
        ProxyDebugLog.i(
            "req method=CONNECT host=$host port=$port src=${client.remoteSocketAddress} ua=$ua",
        )
        val remote = try {
            openConnection(resolved)
        } catch (e: Exception) {
            AppLog.w(TAG, "connect failed $host:$port", e)
            ProxyDebugLog.w("connect FAIL host=$host port=$port via=zt=${resolved.decision.useZeroTier} err=${e.message}")
            ProxyDebugLog.w("reply CONNECT 502 host=$host port=$port")
            writeResponse(client.getOutputStream(), 502, "Bad Gateway")
            return
        }
        ProxyDebugLog.i("reply CONNECT 200 host=$host port=$port")
        writeRaw(client.getOutputStream(), "HTTP/1.1 200 Connection Established\r\n\r\n")
        ProxyRelay.relay(client, input, remote)
    }

    /**
     * Forward one plain-HTTP request to the origin (fresh connection,
     * Connection: close) and stream the full response back. Returns true if
     * the client connection is usable for another request.
     */
    private fun handlePlainHttp(
        method: String,
        target: String,
        headers: List<String>,
        input: InputStream,
        ua: String,
    ): Boolean {
        val url = java.net.URL(if (target.startsWith("http")) target else "http://$target")
        val host = url.host
        val port = if (url.port > 0) url.port else 80
        val path = url.path.ifEmpty { "/" } + (url.query?.let { "?$it" } ?: "")
        val resolved = resolveTarget(host, port)
        logRoute(host, port, resolved.decision)
        ProxyDebugLog.i(
            "req method=$method host=$host port=$port src=${client.remoteSocketAddress} ua=$ua",
        )
        val remote = try {
            openConnection(resolved)
        } catch (e: Exception) {
            AppLog.w(TAG, "connect failed $host:$port", e)
            ProxyDebugLog.w("connect FAIL host=$host port=$port via=zt=${resolved.decision.useZeroTier} err=${e.message}")
            writeResponse(client.getOutputStream(), 502, "Bad Gateway")
            return true
        }
        val contentLength = headers
            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toLongOrNull() ?: 0L
        val expectsContinue = headers.any { it.equals("Expect: 100-continue", ignoreCase = true) }
        val remoteIn = BufferedInputStream(remote.input, BUFFERED_STREAM_BYTES)
        val clientOut = client.getOutputStream()
        val output = remote.output
        output.write("$method $path HTTP/1.1\r\n".toByteArray())
        for (line in headers) {
            if (line.startsWith("Connection:", ignoreCase = true)) continue
            if (line.startsWith("Proxy-Connection:", ignoreCase = true)) continue
            if (line.startsWith("Keep-Alive:", ignoreCase = true)) continue
            output.write("$line\r\n".toByteArray())
        }
        output.write("Connection: close\r\n\r\n".toByteArray())
        output.flush()

        if (expectsContinue) {
            // The client holds the body until it sees 100 Continue.
            val interim = readHeaderBlock(remoteIn) ?: run {
                writeResponse(clientOut, 502, "Bad Gateway")
                remote.close()
                return true
            }
            clientOut.write(interim)
            clientOut.flush()
            val interimStatus = String(interim, Charsets.ISO_8859_1).split("\r\n").firstOrNull().orEmpty()
            if (!interimStatus.contains(" 100")) {
                // Origin answered with a final response (e.g. 417) instead.
                pump(remoteIn, clientOut)
                remote.close()
                return true
            }
        }

        if (contentLength > 0 && !pumpBytes(input, output, contentLength)) {
            AppLog.w(TAG, "request body truncated ($contentLength bytes expected)")
            remote.close()
            return false
        }
        // No shutdownOutput here: the request is fully self-describing
        // (Connection: close + Content-Length), and an early FIN makes some
        // origins reset before responding.

        val responseHeaders = readHeaderBlock(remoteIn) ?: run {
            writeResponse(clientOut, 502, "Bad Gateway")
            remote.close()
            return true
        }
        val statusLine = String(responseHeaders, Charsets.ISO_8859_1).split("\r\n").firstOrNull().orEmpty()
        AppLog.i(TAG, "response $statusLine")
        ProxyDebugLog.i("reply host=$host port=$port status=$statusLine")
        clientOut.write(responseHeaders)
        clientOut.flush()
        pump(remoteIn, clientOut)
        remote.close()
        return true
    }

    /** Pump exactly [bytes] from input to output; false if the stream ends early. */
    private fun pumpBytes(input: InputStream, output: OutputStream, bytes: Long): Boolean {
        val buffer = ByteArray(16_384)
        var remaining = bytes
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read <= 0) return false
            output.write(buffer, 0, read)
            remaining -= read
        }
        output.flush()
        return true
    }

    private fun resolveTarget(host: String, port: Int): ResolvedTarget {
        val addresses = if (IpPrefix.isIpLiteral(host)) {
            listOfNotNull(runCatching { InetAddress.getByName(host) }.getOrNull())
        } else {
            dnsResolver.resolve(host)
        }
        val decision = if (IpPrefix.isIpLiteral(host)) {
            routeResolver.resolveIpString(host)
        } else {
            routeResolver.resolveHost(host, addresses)
        }
        return ResolvedTarget(host, port, addresses, decision)
    }

    private fun openConnection(target: ResolvedTarget): ProxyConnection {
        val host = target.host
        val port = target.port
        val decision = target.decision
        val t0 = android.os.SystemClock.elapsedRealtime()
        return if (decision.useZeroTier) {
            val netId = decision.networkId ?: 0L
            val online = ZeroTierNative.zts_node_is_online()
            val ready = if (netId != 0L) ZeroTierNative.zts_net_transport_is_ready(netId) else -1
            AppLog.i(TAG, "zt connect $host:$port nodeOnline=$online transportReady=$ready")
            val ip = ztConnectAddress(target)
                ?: throw IOException("No ZeroTier address for $host")
            val family = if (ip.contains(':')) {
                ZeroTierNative.ZTS_AF_INET6
            } else {
                ZeroTierNative.ZTS_AF_INET
            }
            val socket = ZeroTierSocket(family, ZeroTierNative.ZTS_SOCK_STREAM, 0)
            try {
                socket.connect(InetAddress.getByName(ip), port, ZT_CONNECT_TIMEOUT_MS)
                runCatching { socket.setTcpNoDelayEnabled(true) }
            } catch (e: Exception) {
                val ms = android.os.SystemClock.elapsedRealtime() - t0
                ProxyDebugLog.w(
                    "connect FAIL via=zt host=$host ip=$ip port=$port ms=$ms " +
                        "online=$online ready=$ready err=${e.message}",
                )
                runCatching { socket.close() }
                throw e
            }
            val ms = android.os.SystemClock.elapsedRealtime() - t0
            ProxyDebugLog.i("connect OK via=zt host=$host ip=$ip port=$port ms=$ms online=$online ready=$ready")
            ProxyConnection.fromZeroTierSocket(socket)
        } else {
            val ip = target.addresses.firstOrNull()
                ?: throw IOException("No address for $host")
            val socket = Socket()
            socket.tcpNoDelay = true
            try {
                socket.connect(InetSocketAddress(ip, port), 15_000)
            } catch (e: Exception) {
                val ms = android.os.SystemClock.elapsedRealtime() - t0
                ProxyDebugLog.w(
                    "connect FAIL via=tcp host=$host ip=${ip.hostAddress} port=$port ms=$ms err=${e.message}",
                )
                runCatching { socket.close() }
                throw e
            }
            socket.soTimeout = 0
            val ms = android.os.SystemClock.elapsedRealtime() - t0
            ProxyDebugLog.i("connect OK via=tcp host=$host ip=${ip.hostAddress} port=$port ms=$ms")
            ProxyConnection.fromSocket(socket)
        }
    }

    private fun ztConnectAddress(target: ResolvedTarget): String? {
        if (IpPrefix.isIpLiteral(target.host)) return target.host
        return target.addresses.firstOrNull { addr ->
            val ip = addr.hostAddress ?: return@firstOrNull false
            routeResolver.resolveIpString(ip).let {
                it.useZeroTier && it.networkId == target.decision.networkId
            }
        }?.hostAddress
    }

    private data class ResolvedTarget(
        val host: String,
        val port: Int,
        val addresses: List<InetAddress>,
        val decision: RouteDecision,
    )

    private fun pump(input: InputStream, output: OutputStream) {
        ProxyRelay.pump(input, output)
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
        AppLog.i(
            TAG,
            "route $host:$port -> useZeroTier=${decision.useZeroTier} " +
                "net=${decision.networkId?.let { java.lang.Long.toUnsignedString(it, 16) }} " +
                "reason=${decision.reason}",
        )
        ProxyDebugLog.i(
            "route host=$host port=$port useZT=${decision.useZeroTier} " +
                "net=${decision.networkId?.let { java.lang.Long.toUnsignedString(it, 16) } ?: "-"} " +
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
