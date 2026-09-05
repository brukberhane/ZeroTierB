package com.brukb.zerotier.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class IdentityHomeStoreTest {
    @Test
    fun writeReadPlanetAndMoon() {
        val home = Files.createTempDirectory("identity-home").toFile()
        val store = IdentityHomeStore(home)
        val planet = byteArrayOf(0x01, 0x02)
        store.write("planet", planet)
        assertArrayEquals(planet, store.read("planet"))
        val moon = byteArrayOf(0x7f)
        store.write("moons.d/000000deadbeef00.moon", moon)
        assertArrayEquals(moon, store.read("moons.d/000000deadbeef00.moon"))
        assertEqualsSet(setOf("000000deadbeef00"), store.listMoonWorldIds())
        store.delete("roots")
        home.deleteRecursively()
    }

    @Test
    fun deniedPathsThrow() {
        val home = Files.createTempDirectory("identity-deny").toFile()
        val store = IdentityHomeStore(home)
        assertThrows(IllegalArgumentException::class.java) {
            store.write("identity.secret", byteArrayOf(0x01))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.write("dummy.planet", byteArrayOf(0x01))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.write("networks.d/x.conf", byteArrayOf(0x01))
        }
        home.deleteRecursively()
    }

    @Test
    fun deleteRemovesAllowedFile() {
        val home = Files.createTempDirectory("identity-del").toFile()
        val store = IdentityHomeStore(home)
        store.write("roots", byteArrayOf(0x03))
        assertTrue(store.exists("roots"))
        store.delete("roots")
        assertFalse(store.exists("roots"))
        assertNull(store.read("roots"))
        home.deleteRecursively()
    }

    private fun assertEqualsSet(expected: Set<String>, actual: Set<String>) {
        org.junit.Assert.assertEquals(expected, actual)
    }

    private fun assertThrows(type: Class<out Throwable>, block: () -> Unit) {
        try {
            block()
            org.junit.Assert.fail("expected ${type.simpleName}")
        } catch (e: Throwable) {
            org.junit.Assert.assertTrue(type.isInstance(e))
        }
    }
}
