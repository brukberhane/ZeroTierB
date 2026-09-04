package com.brukb.zerotier.ztlib

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeIdTest {
    @Test
    fun isValidNodeId_table() {
        val cases = listOf(
            0L to false,
            -1L to false,
            -2L to false,
            -3L to false,
            -5L to false,
            1L to true,
            0x2721c17d93L to true,
            0xFFFFFFFFFFL to true,
            0x10000000000L to false,
            Long.MAX_VALUE to false,
        )
        for ((id, expected) in cases) {
            assertEquals("id=${id.toString(16)}", expected, ZeroTierNodeManager.isValidNodeId(id))
        }
    }

    @Test
    fun formatNodeIdentity_hidesLibztError() {
        assertEquals("invalid(-2)", ZeroTierNodeManager.formatNodeIdentity(-2L))
        assertEquals("2721c17d93", ZeroTierNodeManager.formatNodeIdentity(0x2721c17d93L))
    }
}
