package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.Moon
import com.brukb.zerotier.data.model.PlanetSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RootsApplierTest {
    @Test
    fun earth_deletesPlanetAndRoots_withoutCallingGenerate() = runTest {
        val home = Files.createTempDirectory("roots-applier-home").toFile()
        val worldsDir = Files.createTempDirectory("roots-applier-worlds").toFile()
        val identity = IdentityHomeStore(home)
        identity.write("planet", byteArrayOf(0x01))
        identity.write("roots", byteArrayOf(0x02))
        val worlds = RootsFileStore(worldsDir)
        val repo = RootsRepository(FakeMoonDao(), worlds)
        var generateCalls = 0
        val applier = RootsApplier(
            prefs = FakeRootsApplyPrefs(),
            repo = repo,
            identity = identity,
            worlds = worlds,
        )
        val staged = applier.stageBeforeNode {
            generateCalls += 1
            dummyFixture()
        }
        assertEquals(LivePlanetSource.EARTH, staged.source)
        assertNull(staged.planetBytes)
        assertEquals(0, generateCalls)
        assertFalse(identity.exists("planet"))
        assertFalse(identity.exists("roots"))
        home.deleteRecursively()
        worldsDir.deleteRecursively()
    }

    @Test
    fun dummy_writesPlanetOnly_notDummyFileInHome() = runTest {
        val home = Files.createTempDirectory("roots-applier-dummy").toFile()
        val worldsDir = Files.createTempDirectory("roots-applier-dummy-w").toFile()
        val identity = IdentityHomeStore(home)
        val worlds = RootsFileStore(worldsDir)
        val repo = RootsRepository(FakeMoonDao(), worlds)
        val fixture = dummyFixture()
        val applier = RootsApplier(
            prefs = FakeRootsApplyPrefs(airgap = true, latch = true),
            repo = repo,
            identity = identity,
            worlds = worlds,
        )
        val staged = applier.stageBeforeNode { fixture }
        assertEquals(LivePlanetSource.DUMMY, staged.source)
        assertArrayEquals(fixture, staged.planetBytes)
        assertArrayEquals(fixture, identity.read("planet"))
        assertFalse(File(home, "dummy.planet").exists())
        assertTrue(worlds.dummyPlanetFile().exists())
        home.deleteRecursively()
        worldsDir.deleteRecursively()
    }

    @Test
    fun copiesMoonFile_andRemovesExtraMoonFromHome() = runTest {
        val home = Files.createTempDirectory("roots-applier-moon").toFile()
        val worldsDir = Files.createTempDirectory("roots-applier-moon-w").toFile()
        val identity = IdentityHomeStore(home)
        identity.write("moons.d/00000000cafebabe.moon", byteArrayOf(0x09))
        val worlds = RootsFileStore(worldsDir)
        val moonBytes = byteArrayOf(0x0a)
        worlds.writeMoon("00000000deadbeef", moonBytes)
        val moon = Moon(
            worldId = "00000000deadbeef",
            seed = null,
            hasMoonFile = true,
        )
        val repo = RootsRepository(FakeMoonDao(listOf(moon)), worlds)
        val applier = RootsApplier(
            prefs = FakeRootsApplyPrefs(),
            repo = repo,
            identity = identity,
            worlds = worlds,
        )
        val staged = applier.stageBeforeNode { dummyFixture() }
        assertArrayEquals(moonBytes, identity.read("moons.d/00000000deadbeef.moon"))
        assertFalse(identity.exists("moons.d/00000000cafebabe.moon"))
        assertEquals(setOf("00000000cafebabe"), staged.extraMoonIdsToDeorbit)
        assertEquals(setOf("00000000deadbeef"), staged.copiedMoonIds)
        home.deleteRecursively()
        worldsDir.deleteRecursively()
    }

    @Test
    fun applyOrbits_fileMissing_usesSeed_notZero() = runTest {
        val home = Files.createTempDirectory("roots-applier-seed").toFile()
        val worldsDir = Files.createTempDirectory("roots-applier-seed-w").toFile()
        val identity = IdentityHomeStore(home)
        val worlds = RootsFileStore(worldsDir)
        val moon = Moon(
            worldId = "00000000deadbeef",
            seed = "000000abcd",
            hasMoonFile = true,
        )
        val repo = RootsRepository(FakeMoonDao(listOf(moon)), worlds)
        val applier = RootsApplier(
            prefs = FakeRootsApplyPrefs(),
            repo = repo,
            identity = identity,
            worlds = worlds,
        )
        val staged = applier.stageBeforeNode { dummyFixture() }
        assertTrue(staged.copiedMoonIds.isEmpty())
        val orbits = mutableListOf<Pair<Long, Long>>()
        applier.applyOrbits(
            staged,
            orbit = { id, seed -> orbits.add(id to seed) },
            deorbit = {},
        )
        assertEquals(1, orbits.size)
        assertEquals(java.lang.Long.parseUnsignedLong("000000abcd", 16), orbits.single().second)
        home.deleteRecursively()
        worldsDir.deleteRecursively()
    }

    @Test
    fun applyOrbits_fileMissing_noSeed_skips() = runTest {
        val home = Files.createTempDirectory("roots-applier-skip").toFile()
        val worldsDir = Files.createTempDirectory("roots-applier-skip-w").toFile()
        val identity = IdentityHomeStore(home)
        val worlds = RootsFileStore(worldsDir)
        val moon = Moon(
            worldId = "00000000deadbeef",
            seed = null,
            hasMoonFile = true,
        )
        val repo = RootsRepository(FakeMoonDao(listOf(moon)), worlds)
        val applier = RootsApplier(
            prefs = FakeRootsApplyPrefs(),
            repo = repo,
            identity = identity,
            worlds = worlds,
        )
        val staged = applier.stageBeforeNode { dummyFixture() }
        val orbits = mutableListOf<Pair<Long, Long>>()
        applier.applyOrbits(staged, orbit = { id, seed -> orbits.add(id to seed) }, deorbit = {})
        assertTrue(orbits.isEmpty())
        home.deleteRecursively()
        worldsDir.deleteRecursively()
    }

    @Test
    fun orbitSeedForMoon_table() {
        val copied = Moon(worldId = "00000000deadbeef", seed = "000000abcd", hasMoonFile = true)
        val seedOnly = Moon(worldId = "00000000cafebabe", seed = "000000abcd", hasMoonFile = false)
        val neither = Moon(worldId = "0000000000000001", seed = null, hasMoonFile = true)
        assertEquals(0L, RootsApplier.orbitSeedForMoon(copied, setOf("00000000deadbeef")))
        assertEquals(
            java.lang.Long.parseUnsignedLong("000000abcd", 16),
            RootsApplier.orbitSeedForMoon(seedOnly, emptySet()),
        )
        assertNull(RootsApplier.orbitSeedForMoon(neither, emptySet()))
    }

    private fun dummyFixture(): ByteArray = byteArrayOf(
        0x01,
        0x5e, 0x20, 0x71, 0xe4.toByte(), 0xb0.toByte(), 0x00, 0x00, 0x01,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    private class FakeRootsApplyPrefs(
        private var airgap: Boolean = false,
        private var latch: Boolean = false,
        private var planetSource: PlanetSource = PlanetSource.EARTH,
    ) : RootsApplyPrefs {
        override suspend fun readAirgap(): Boolean = airgap

        override suspend fun readAirgapWithoutMoons(): Boolean = latch

        override suspend fun readPlanetSource(): PlanetSource = planetSource

        override suspend fun setAirgap(value: Boolean) {
            airgap = value
        }
    }

    private class FakeMoonDao(
        private val moons: List<Moon> = emptyList(),
    ) : MoonDao {
        override fun observeAll(): Flow<List<Moon>> = flowOf(moons)

        override suspend fun getAll(): List<Moon> = moons

        override suspend fun getById(worldId: String): Moon? = moons.find { it.worldId == worldId }

        override suspend fun insert(moon: Moon) {}

        override suspend fun delete(worldId: String) {}

        override suspend fun count(): Int = moons.size
    }
}
