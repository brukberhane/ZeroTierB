package com.brukb.zerotier.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityHomeAllowlistTest {
    @Test
    fun allowsPlanetAndRoots() {
        assertTrue(IdentityHomeAllowlist.isAllowedRelative("planet"))
        assertTrue(IdentityHomeAllowlist.isAllowedRelative("roots"))
    }

    @Test
    fun allowsMoonFile() {
        assertTrue(IdentityHomeAllowlist.isAllowedRelative("moons.d/000000deadbeef00.moon"))
    }

    @Test
    fun deniesIdentityAndNetworks() {
        assertFalse(IdentityHomeAllowlist.isAllowedRelative("identity.public"))
        assertFalse(IdentityHomeAllowlist.isAllowedRelative("identity.secret"))
        assertFalse(IdentityHomeAllowlist.isAllowedRelative("networks.d/foo.conf"))
        assertFalse(IdentityHomeAllowlist.isAllowedRelative("peers.d/abc"))
    }

    @Test
    fun deniesTraversalAndAppStore() {
        assertFalse(IdentityHomeAllowlist.isAllowedRelative("moons.d/../identity.secret"))
        assertFalse(IdentityHomeAllowlist.isAllowedRelative("zt-worlds/planet"))
        assertFalse(IdentityHomeAllowlist.isAllowedRelative("planet.bak"))
    }

    @Test
    fun deniesBadMoonFilename() {
        assertFalse(IdentityHomeAllowlist.isAllowedRelative("moons.d/nothex.moon"))
    }
}
