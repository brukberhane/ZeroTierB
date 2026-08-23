package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.ZerotierBNetwork
import kotlinx.coroutines.flow.Flow

class NetworkRepository(
    private val dao: NetworkDao,
) {
    fun observeAll(): Flow<List<ZerotierBNetwork>> = dao.observeAll()

    fun observeEnabled(): Flow<List<ZerotierBNetwork>> = dao.observeEnabled()

    suspend fun getById(networkId: String): ZerotierBNetwork? = dao.getById(networkId)

    suspend fun upsert(network: ZerotierBNetwork) {
        val existing = dao.getById(network.networkId)
        val toSave = when {
            existing == null && network.createdAt == 0L ->
                network.copy(createdAt = System.currentTimeMillis())
            existing != null && network.createdAt == 0L ->
                network.copy(
                    createdAt = existing.createdAt,
                    isPinnedMain = network.isPinnedMain || existing.isPinnedMain,
                )
            else -> network
        }
        dao.upsert(toSave)
    }

    suspend fun update(network: ZerotierBNetwork) = dao.update(network)

    suspend fun delete(networkId: String) = dao.delete(networkId)

    suspend fun setPinnedMain(networkId: String) = dao.setPinnedMain(networkId)

    suspend fun migrateStoredNetworkIds() {
        for (network in dao.getAll()) {
            if (!ZerotierBNetwork.isValidNetworkId(network.networkId)) continue
            val normalized = ZerotierBNetwork.normalizeNetworkId(network.networkId)
            if (normalized == network.networkId) continue
            dao.delete(network.networkId)
            dao.upsert(network.copy(networkId = normalized))
        }
    }
}
