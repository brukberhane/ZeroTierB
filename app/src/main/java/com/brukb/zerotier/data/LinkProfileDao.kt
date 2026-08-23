package com.brukb.zerotier.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.brukb.zerotier.data.model.LinkProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkProfileDao {
    @Query("SELECT * FROM link_profiles ORDER BY kind, label, id")
    fun observeAll(): Flow<List<LinkProfile>>

    @Query("SELECT * FROM link_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LinkProfile?

    @Query("SELECT * FROM link_profiles WHERE ssid = :ssid LIMIT 1")
    suspend fun getBySsid(ssid: String): LinkProfile?

    @Query("SELECT * FROM link_profiles WHERE subscriptionId = :subscriptionId LIMIT 1")
    suspend fun getBySubscriptionId(subscriptionId: Int): LinkProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: LinkProfile)

    @Query(
        """
        INSERT OR IGNORE INTO link_profiles
        (id, kind, mode, ssid, subscriptionId, simSlotIndex, label, iccId)
        VALUES ('other', 'OTHER', 'PROXY', NULL, NULL, NULL, 'Other', NULL)
        """,
    )
    suspend fun ensureOther()
}
