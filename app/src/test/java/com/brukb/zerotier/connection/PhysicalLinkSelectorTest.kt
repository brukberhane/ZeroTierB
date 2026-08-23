package com.brukb.zerotier.connection

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PhysicalLinkSelectorTest {
    @Test
    fun emptyIsNull() {
        assertNull(PhysicalLinkSelector.pick(emptyList()))
    }

    @Test
    fun singleWifi() {
        val wifi = wifi()
        assertSame(wifi, PhysicalLinkSelector.pick(listOf(wifi)))
    }

    @Test
    fun vpnOnlyIsNull() {
        assertNull(PhysicalLinkSelector.pick(listOf(vpn())))
    }

    @Test
    fun vpnUnderlyingWifiPrefersWifi() {
        val wifi = wifi()
        val cell = cell()
        val picked = PhysicalLinkSelector.pick(
            listOf(vpn(underlyingWifi = true), wifi, cell),
        )
        assertSame(wifi, picked)
    }

    @Test
    fun vpnUnderlyingCellPrefersCell() {
        val wifi = wifi()
        val cell = cell()
        val picked = PhysicalLinkSelector.pick(
            listOf(vpn(underlyingCellular = true), wifi, cell),
        )
        assertSame(cell, picked)
    }

    @Test
    fun vpnNoUnderlyingPrefersWifi() {
        val wifi = wifi()
        val cell = cell()
        val picked = PhysicalLinkSelector.pick(listOf(vpn(), wifi, cell))
        assertSame(wifi, picked)
    }

    @Test
    fun cellOverOther() {
        val cell = cell()
        val other = other()
        assertSame(cell, PhysicalLinkSelector.pick(listOf(cell, other)))
    }

    @Test
    fun otherAlone() {
        val other = other()
        assertSame(other, PhysicalLinkSelector.pick(listOf(other)))
    }

    private fun wifi() = LinkCandidate(isVpn = false, isWifi = true, isCellular = false)
    private fun cell() = LinkCandidate(isVpn = false, isWifi = false, isCellular = true)
    private fun other() = LinkCandidate(isVpn = false, isWifi = false, isCellular = false)
    private fun vpn(
        underlyingWifi: Boolean = false,
        underlyingCellular: Boolean = false,
    ) = LinkCandidate(
        isVpn = true,
        isWifi = false,
        isCellular = false,
        underlyingWifi = underlyingWifi,
        underlyingCellular = underlyingCellular,
    )
}
