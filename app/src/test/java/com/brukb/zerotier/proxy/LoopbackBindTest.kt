package com.brukb.zerotier.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket

class LoopbackBindTest {
    @Test
    fun bindsEphemeralPortOnLoopbackOnly() {
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("127.0.0.1", 0))
            assertTrue(socket.localPort > 0)
            assertEquals("127.0.0.1", socket.inetAddress.hostAddress)
        }
    }
}
