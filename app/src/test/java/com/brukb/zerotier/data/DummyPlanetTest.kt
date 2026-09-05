package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.Moon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DummyPlanetTest {
    @Test
    fun isValid_rejectsEarthPlanet() {
        val earth = byteArrayOf(
            0x01,
            0x00, 0x00, 0x00, 0x00, 0x08, 0xea.toByte(), 0xc9.toByte(), 0x0a,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        assertFalse(DummyPlanet.isValid(earth))
    }

    @Test
    fun isValid_acceptsDummyWorldId() {
        val dummy = byteArrayOf(
            0x01,
            0x5e, 0x20, 0x71, 0xe4.toByte(), 0xb0.toByte(), 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        assertTrue(DummyPlanet.isValid(dummy))
        assertEquals(DummyPlanet.WORLD_ID_HEX, (WorldBlobParser.parse(dummy) as WorldBlobParseResult.Planet).worldId)
    }

    @Test
    fun ensureDummyPlanet_writesOnce() {
        val dir = Files.createTempDirectory("zt-dummy-planet").toFile()
        val store = RootsFileStore(dir)
        val repo = RootsRepository(FakeMoonDao(), store)
        var calls = 0
        val fixture = byteArrayOf(
            0x01,
            0x5e, 0x20, 0x71, 0xe4.toByte(), 0xb0.toByte(), 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        val generate = {
            calls += 1
            fixture
        }

        val first = repo.ensureDummyPlanet(generate)
        assertArrayEquals(fixture, first)
        assertEquals(1, calls)
        assertTrue(store.dummyPlanetFile().exists())

        val second = repo.ensureDummyPlanet(generate)
        assertArrayEquals(fixture, second)
        assertEquals(1, calls)

        dir.deleteRecursively()
    }

    @Test
    fun ensureDummyPlanet_regeneratesCorruptFile() {
        val dir = Files.createTempDirectory("zt-dummy-corrupt").toFile()
        val store = RootsFileStore(dir)
        val repo = RootsRepository(FakeMoonDao(), store)
        store.writeDummyPlanet(byteArrayOf(0x00))
        var calls = 0
        val fixture = byteArrayOf(
            0x01,
            0x5e, 0x20, 0x71, 0xe4.toByte(), 0xb0.toByte(), 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )

        val bytes = repo.ensureDummyPlanet {
            calls += 1
            fixture
        }
        assertArrayEquals(fixture, bytes)
        assertEquals(1, calls)

        dir.deleteRecursively()
    }

    private class FakeMoonDao : MoonDao {
        override fun observeAll(): Flow<List<Moon>> = flowOf(emptyList())

        override suspend fun getAll(): List<Moon> = emptyList()

        override suspend fun getById(worldId: String): Moon? = null

        override suspend fun insert(moon: Moon) {}

        override suspend fun delete(worldId: String) {}

        override suspend fun count(): Int = 0
    }
}
