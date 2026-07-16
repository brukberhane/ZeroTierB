package com.zerotier.pylon.data

import com.zerotier.pylon.data.model.PylonNetwork
import kotlinx.coroutines.flow.Flow

class NetworkRepository(
    private val dao: NetworkDao,
) {
    fun observeAll(): Flow<List<PylonNetwork>> = dao.observeAll()

    fun observeEnabled(): Flow<List<PylonNetwork>> = dao.observeEnabled()

    suspend fun getById(networkId: String): PylonNetwork? = dao.getById(networkId)

    suspend fun upsert(network: PylonNetwork) = dao.upsert(network)

    suspend fun update(network: PylonNetwork) = dao.update(network)

    suspend fun delete(networkId: String) = dao.delete(networkId)

    suspend fun migrateStoredNetworkIds() {
        for (network in dao.getAll()) {
            if (!PylonNetwork.isValidNetworkId(network.networkId)) continue
            val normalized = PylonNetwork.normalizeNetworkId(network.networkId)
            if (normalized == network.networkId) continue
            dao.delete(network.networkId)
            dao.upsert(network.copy(networkId = normalized))
        }
    }
}
