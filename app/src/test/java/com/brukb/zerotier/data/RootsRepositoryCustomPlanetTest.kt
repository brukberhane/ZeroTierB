package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.Moon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class RootsRepositoryCustomPlanetTest {
    @Test
    fun saveAndDeleteCustomPlanet_bumpsEpoch() = runTest {
        val dir = Files.createTempDirectory("zt-custom-epoch").toFile()
        val repo = RootsRepository(FakeMoonDao(), RootsFileStore(dir))
        assertEquals(0, repo.observeCustomPlanetEpoch().first())
        repo.saveCustomPlanet(byteArrayOf(0x01))
        assertEquals(1, repo.observeCustomPlanetEpoch().first())
        repo.deleteCustomPlanet()
        assertEquals(2, repo.observeCustomPlanetEpoch().first())
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
