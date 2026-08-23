package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.LinkMode
import com.brukb.zerotier.data.model.LinkProfile
import kotlinx.coroutines.flow.Flow

class LinkProfileRepository(
    private val dao: LinkProfileDao,
) {
    fun observeAll(): Flow<List<LinkProfile>> = dao.observeAll()

    suspend fun getById(id: String): LinkProfile? = dao.getById(id)

    suspend fun getBySsid(ssid: String): LinkProfile? = dao.getBySsid(ssid)

    suspend fun getBySubscriptionId(subscriptionId: Int): LinkProfile? =
        dao.getBySubscriptionId(subscriptionId)

    suspend fun seedOther() {
        dao.ensureOther()
    }

    suspend fun upsertMobile(
        subscriptionId: Int,
        simSlotIndex: Int?,
        label: String,
        iccId: String?,
    ) {
        val existing = dao.getBySubscriptionId(subscriptionId)
            ?: dao.getById(LinkProfile.mobileId(subscriptionId))
        val merged = LinkProfile.mergeMobile(
            existing = existing,
            subscriptionId = subscriptionId,
            simSlotIndex = simSlotIndex,
            label = label,
            iccId = iccId,
        )
        dao.upsert(merged)
    }

    suspend fun upsertWifi(ssid: String, mode: LinkMode = LinkMode.PROXY) {
        val merged = LinkProfile.mergeWifi(dao.getBySsid(ssid), ssid, mode)
        dao.upsert(merged)
    }
}
