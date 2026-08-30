package com.brukb.zerotier.proxy

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Shared CONNECT relay. Thread pool + large buffer + flush-when-idle.
 * Half-close only: closing a ZeroTier socket while the sibling pump is
 * blocked in zts_bsd_read aborts the process (destroyed mutex).
 */
object ProxyRelay {
    private val pool = Executors.newFixedThreadPool(64) { runnable ->
        Thread(runnable, "zt-relay").apply { isDaemon = true }
    }

    fun relay(client: Socket, clientInput: InputStream, remote: ProxyConnection) {
        val toRemote: Future<*> = pool.submit {
            pump(clientInput, remote.output)
            remote.shutdownOutput()
        }
        val toClient: Future<*> = pool.submit {
            pump(remote.input, client.getOutputStream())
            runCatching { client.shutdownOutput() }
        }
        runCatching { toRemote.get() }
        runCatching { toClient.get() }
        runCatching { client.close() }
        remote.close()
    }

    fun pump(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(65_536)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
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
}
