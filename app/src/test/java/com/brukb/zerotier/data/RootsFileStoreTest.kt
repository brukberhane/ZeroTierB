package com.brukb.zerotier.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class RootsFileStoreTest {
    @Test
    fun writeReadDeleteMoon() {
        val dir = Files.createTempDirectory("zt-worlds-test").toFile()
        val store = RootsFileStore(dir)
        val bytes = byteArrayOf(0x7f, 0x01, 0x02)
        store.writeMoon("deadbeef00", bytes)
        assertTrue(store.moonFile("deadbeef00").exists())
        assertArrayEquals(bytes, store.moonFile("deadbeef00").readBytes())
        store.deleteMoon("deadbeef00")
        assertFalse(store.moonFile("deadbeef00").exists())
        dir.deleteRecursively()
    }

    @Test
    fun customPlanetOverwrite() {
        val dir = Files.createTempDirectory("zt-worlds-planet").toFile()
        val store = RootsFileStore(dir)
        store.writeCustomPlanet(byteArrayOf(0x01))
        store.writeCustomPlanet(byteArrayOf(0x02))
        assertArrayEquals(byteArrayOf(0x02), store.customPlanetFile().readBytes())
        store.deleteCustomPlanet()
        assertFalse(store.customPlanetFile().exists())
        dir.deleteRecursively()
    }

    @Test
    fun writeDummyPlanet_roundTrip() {
        val dir = Files.createTempDirectory("zt-worlds-dummy").toFile()
        val store = RootsFileStore(dir)
        val bytes = byteArrayOf(
            0x01,
            0x5e, 0x20, 0x71, 0xe4.toByte(), 0xb0.toByte(), 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        store.writeDummyPlanet(bytes)
        assertTrue(store.dummyPlanetFile().path.startsWith(dir.path))
        assertEquals(RootsFileStore.DUMMY_PLANET_NAME, store.dummyPlanetFile().name)
        assertArrayEquals(bytes, store.dummyPlanetFile().readBytes())
        dir.deleteRecursively()
    }

    @Test
    fun moonFileStaysUnderDir() {
        val dir = Files.createTempDirectory("zt-worlds-path").toFile()
        val store = RootsFileStore(dir)
        val moon = store.moonFile("deadbeef00")
        assertTrue(moon.path.startsWith(dir.path))
        dir.deleteRecursively()
    }
}
