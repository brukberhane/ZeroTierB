package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.MainNetworkSelector
import com.brukb.zerotier.data.model.PinHelpers
import com.brukb.zerotier.data.model.ZerotierBNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNetworkSelectorTest {
    @Test
    fun emptyReturnsNull() {
        assertNull(MainNetworkSelector.select(emptyList()))
    }

    @Test
    fun pinWinsOverCreatedAt() {
        val pinned = net("bbbb", createdAt = 200, pinned = true)
        val older = net("aaaa", createdAt = 50, pinned = false)
        assertEquals(pinned, MainNetworkSelector.select(listOf(older, pinned)))
    }

    @Test
    fun olderCreatedAtWinsWhenNoPin() {
        val a = net("aaaa", createdAt = 100)
        val b = net("bbbb", createdAt = 50)
        assertEquals(b, MainNetworkSelector.select(listOf(a, b)))
    }

    @Test
    fun zeroCreatedAtSortsAfterReal() {
        val zero = net("zzzz", createdAt = 0)
        val real = net("aaaa", createdAt = 50)
        assertEquals(real, MainNetworkSelector.select(listOf(zero, real)))
    }

    @Test
    fun allZeroCreatedAtUsesLowerNetworkId() {
        val high = net("ffff", createdAt = 0)
        val low = net("0001", createdAt = 0)
        assertEquals(low, MainNetworkSelector.select(listOf(high, low)))
    }

    @Test
    fun applyPinClearsOthers() {
        val a = net("a", pinned = true)
        val b = net("b", pinned = false)
        val c = net("c", pinned = false)
        val pinned = PinHelpers.applyPin(listOf(a, b, c), b.networkId)
        assertTrue(pinned.single { it.networkId == b.networkId }.isPinnedMain)
        assertEquals(1, pinned.count { it.isPinnedMain })
    }

    private fun net(
        id: String,
        createdAt: Long = 0L,
        pinned: Boolean = false,
    ) = ZerotierBNetwork(
        networkId = id.padStart(16, '0'),
        name = id,
        createdAt = createdAt,
        isPinnedMain = pinned,
    )
}
