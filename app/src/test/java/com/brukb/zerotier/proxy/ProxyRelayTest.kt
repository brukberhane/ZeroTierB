package com.brukb.zerotier.proxy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ProxyRelayTest {
    @Test
    fun idleConnectTimesOut() {
        pairedSockets { browser, origin ->
            val started = System.currentTimeMillis()
            val done = CountDownLatch(1)
            Thread({
                ProxyRelay.relay(
                    browser.first,
                    browser.first.getInputStream(),
                    ProxyConnection.fromSocket(origin.first),
                    idleTimeoutMs = 400,
                    pollMs = 50,
                )
                done.countDown()
            }, "relay-idle").start()
            assertTrue("idle relay should return", done.await(3, TimeUnit.SECONDS))
            val elapsed = System.currentTimeMillis() - started
            assertTrue("elapsed=$elapsed", elapsed < 2_500)
        }
    }

    @Test
    fun trafficResetsIdleTimer() {
        pairedSockets { browser, origin ->
            val done = CountDownLatch(1)
            Thread({
                ProxyRelay.relay(
                    browser.first,
                    browser.first.getInputStream(),
                    ProxyConnection.fromSocket(origin.first),
                    idleTimeoutMs = 400,
                    pollMs = 50,
                )
                done.countDown()
            }, "relay-traffic").start()
            val until = System.currentTimeMillis() + 700
            while (System.currentTimeMillis() < until) {
                origin.second.outputStream.write(1)
                origin.second.outputStream.flush()
                // Drain so the origin write does not block on window.
                if (browser.second.inputStream.available() > 0) {
                    browser.second.inputStream.read()
                }
                Thread.sleep(80)
            }
            assertTrue(
                "traffic should keep relay alive past one idle window",
                done.count > 0,
            )
            origin.second.close()
            browser.second.close()
            assertTrue(done.await(3, TimeUnit.SECONDS))
        }
    }

    /**
     * Each pair is (proxySide, peerSide). [proxySide] is what the relay owns;
     * [peerSide] is the "browser" or "origin" the test drives.
     */
    private fun pairedSockets(block: (Pair<Socket, Socket>, Pair<Socket, Socket>) -> Unit) {
        val browser = localPair()
        val origin = localPair()
        try {
            block(browser, origin)
        } finally {
            runCatching { browser.first.close() }
            runCatching { browser.second.close() }
            runCatching { origin.first.close() }
            runCatching { origin.second.close() }
        }
    }

    private fun localPair(): Pair<Socket, Socket> {
        ServerSocket().use { server ->
            server.reuseAddress = true
            server.bind(InetSocketAddress("127.0.0.1", 0))
            val client = Socket()
            lateinit var accepted: Socket
            val accept = Thread {
                accepted = server.accept()
            }
            accept.start()
            client.connect(InetSocketAddress("127.0.0.1", server.localPort), 1_000)
            accept.join(1_000)
            return client to accepted
        }
    }
}
