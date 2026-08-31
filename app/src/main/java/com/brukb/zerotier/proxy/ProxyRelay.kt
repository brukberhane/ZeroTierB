package com.brukb.zerotier.proxy

import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared CONNECT relay. Cached thread pool (no 32-session deadlock on a
 * fixed 64-thread pool) + shared idle timeout so HTTP/2 keep-alives cannot
 * accumulate forever. Half-close only: closing a ZeroTier socket while the
 * sibling pump is blocked in zts_bsd_read aborts the process (destroyed mutex).
 *
 * See android-http-proxy.mdc problem class `CONNECT relay stagnation`.
 */
object ProxyRelay {
    const val DEFAULT_IDLE_TIMEOUT_MS = 60_000L
    const val DEFAULT_POLL_MS = 5_000

    private val pool = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "zt-relay").apply { isDaemon = true }
    }

    fun relay(
        client: Socket,
        clientInput: InputStream,
        remote: ProxyConnection,
        idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
        pollMs: Int = DEFAULT_POLL_MS,
    ) {
        val lastActivity = AtomicLong(System.currentTimeMillis())
        client.soTimeout = pollMs
        val remoteTimeout = if (remote.recvTimeoutIsEof) {
            idleTimeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else {
            pollMs
        }
        remote.setReadTimeout(remoteTimeout)

        val toRemote: Future<*> = pool.submit {
            pump(clientInput, remote.output, lastActivity, idleTimeoutMs)
            remote.shutdownOutput()
        }
        val toClient: Future<*> = pool.submit {
            pump(remote.input, client.getOutputStream(), lastActivity, idleTimeoutMs)
            runCatching { client.shutdownOutput() }
        }
        runCatching { toRemote.get() }
        runCatching { toClient.get() }
        runCatching { client.close() }
        remote.close()
    }

    fun pump(input: InputStream, output: OutputStream) {
        pump(input, output, AtomicLong(System.currentTimeMillis()), Long.MAX_VALUE)
    }

    internal fun pump(
        input: InputStream,
        output: OutputStream,
        lastActivity: AtomicLong,
        idleTimeoutMs: Long,
    ) {
        val buffer = ByteArray(65_536)
        try {
            while (true) {
                val read = try {
                    input.read(buffer)
                } catch (_: SocketTimeoutException) {
                    if (idleExpired(lastActivity, idleTimeoutMs)) break
                    continue
                } catch (_: InterruptedIOException) {
                    if (idleExpired(lastActivity, idleTimeoutMs)) break
                    continue
                }
                if (read <= 0) break
                lastActivity.set(System.currentTimeMillis())
                output.write(buffer, 0, read)
                if (input.available() <= 0) {
                    output.flush()
                }
            }
            output.flush()
        } catch (_: IOException) {
            // Either side closed the connection during relay.
        }
    }

    private fun idleExpired(lastActivity: AtomicLong, idleTimeoutMs: Long): Boolean {
        return System.currentTimeMillis() - lastActivity.get() >= idleTimeoutMs
    }
}
