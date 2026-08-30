package com.zerotier.pylon.proxy

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.Future

object ProxyRelay {
    private val pool = Executors.newFixedThreadPool(64) { runnable ->
        Thread(runnable, "pylon-relay").apply { isDaemon = true }
    }

    fun relay(client: Socket, remote: ProxyConnection) {
        val toRemote: Future<*> = pool.submit {
            pump(client.getInputStream(), remote.output)
            runCatching { remote.close() }
        }
        val toClient: Future<*> = pool.submit {
            pump(remote.input, client.getOutputStream())
            runCatching { client.shutdownOutput() }
        }
        runCatching { toRemote.get() }
        runCatching { toClient.get() }
        runCatching { client.close() }
        runCatching { remote.close() }
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
