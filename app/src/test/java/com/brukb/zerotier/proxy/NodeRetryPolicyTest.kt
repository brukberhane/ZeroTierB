package com.brukb.zerotier.proxy

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeRetryPolicyTest {
    @Test
    fun backoffSequence() {
        assertEquals(1_000L, NodeRetryPolicy.nextBackoffMs(0))
        assertEquals(5_000L, NodeRetryPolicy.nextBackoffMs(1_000))
        assertEquals(15_000L, NodeRetryPolicy.nextBackoffMs(5_000))
        assertEquals(30_000L, NodeRetryPolicy.nextBackoffMs(15_000))
        assertEquals(30_000L, NodeRetryPolicy.nextBackoffMs(30_000))
    }
}
